  /**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.webclient.client;

import static org.waveprotocol.wave.communication.gwt.JsonHelper.getPropertyAsInteger;
import static org.waveprotocol.wave.communication.gwt.JsonHelper.getPropertyAsObject;
import static org.waveprotocol.wave.communication.gwt.JsonHelper.getPropertyAsString;
import static org.waveprotocol.wave.communication.gwt.JsonHelper.setPropertyAsInteger;
import static org.waveprotocol.wave.communication.gwt.JsonHelper.setPropertyAsObject;
import static org.waveprotocol.wave.communication.gwt.JsonHelper.setPropertyAsString;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.user.client.Cookies;

import org.waveprotocol.wave.communication.gwt.JsonMessage;
import org.waveprotocol.wave.communication.json.JsonException;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.IntMap;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.common.logging.LoggerBundle;

import org.waveprotocol.wave.clientserver.OpenWaveletChannelStream;
import org.waveprotocol.wave.clientserver.SubmitDeltaResponse;
import org.waveprotocol.wave.clientserver.FetchWaveViewResponse;
import org.waveprotocol.wave.clientserver.FetchFragmentsResponse;
import org.waveprotocol.wave.clientserver.jso.CloseWaveletChannelRequestJsoImpl;
import org.waveprotocol.wave.clientserver.jso.FetchWaveViewResponseJsoImpl;
import org.waveprotocol.wave.clientserver.jso.OpenWaveletChannelRequestJsoImpl;
import org.waveprotocol.wave.clientserver.jso.OpenWaveletChannelStreamJsoImpl;
import org.waveprotocol.wave.clientserver.jso.SubmitDeltaRequestJsoImpl;
import org.waveprotocol.wave.clientserver.jso.SubmitDeltaResponseJsoImpl;
import org.waveprotocol.wave.clientserver.jso.FetchWaveViewRequestJsoImpl;
import org.waveprotocol.wave.clientserver.jso.TransportAuthenticationRequestJsoImpl;
import org.waveprotocol.wave.clientserver.jso.FetchFragmentsRequestJsoImpl;
import org.waveprotocol.wave.clientserver.jso.FetchFragmentsResponseJsoImpl;
import org.waveprotocol.wave.clientserver.jso.EmptyResponseJsoImpl;
import org.waveprotocol.wave.clientserver.jso.RpcFinishedJsoImpl;
import org.waveprotocol.wave.clientserver.EmptyResponse;

import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Wrapper around SocketIO that handles the FedOne client-server protocol.
 *
 * @author unknown
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class WaveWebSocketClient implements WaveSocket.WaveSocketCallback {
  private static final String FETCH_WAVE_VIEW_REQUEST = "FetchWaveViewRequest";
  private static final String FETCH_WAVE_VIEW_RESPONSE = "FetchWaveViewResponse";

  private static final String FETCH_FRAGMENTS_REQUEST = "FetchFragmentsRequest";
  private static final String FETCH_FRAGMENTS_RESPONSE = "FetchFragmentsResponse";

  private static final String OPEN_WAVELET_CHANNEL_REQUEST = "OpenWaveletChannelRequest";
  private static final String OPEN_WAVELET_CHANNEL_STREAM = "OpenWaveletChannelStream";

  private static final String CLOSE_WAVELET_CHANNEL_REQUEST = "CloseWaveletChannelRequest";

  private static final String SUBMIT_DELTA_REQUEST = "SubmitDeltaRequest";
  private static final String SUBMIT_DELTA_RESPONSE = "SubmitDeltaResponse";

  private static final String TRANSPORT_AUTHENTICATION_REQUEST = "TransportAuthenticationRequest";

  private static final String EMPTY_RESPONSE = "EmptyResponse";

  private static final String RPC_FINISHED = "RpcFinished";

  private static final int MAX_WEBSOCKET_ATTEMPTS = 2;
  private static final LoggerBundle LOG = new DomLogger("socket");
  private static final int RECONNECT_TIME_MS = 5000;
  private static final String JETTY_SESSION_TOKEN_NAME = "JSESSIONID";

  /**
   * The connection listener.
   */
  public interface ConnectionListener {
    /**
     * Notifies this listener that connection is establishing.
     */
    void onConnecting();

    /**
     * Notifies this listener that connection is established.
     */
    void onConnected();

    /**
     * Notifies this listener that connection is broken.
     */
    void onDisconnected();
    
    /**
     * Notifies this listener that connection is finished with error.
     */
    void onFinished(String error);
  }

  /**
   * Envelope for delivering arbitrary messages. Each envelope has a sequence
   * number and a message. The format must match the format used in the server's
   * WebSocketChannel.
   * <p>
   * Note that this message can not be described by a protobuf, because it
   * contains an arbitrary protobuf, which breaks the protobuf typing rules.
   */
  private static final class MessageWrapper extends JsonMessage {
    static MessageWrapper create(int seqno, String type, JsonMessage message) {
      MessageWrapper wrapper = JsonMessage.createJsonMessage().cast();
      setPropertyAsInteger(wrapper, "sequenceNumber", seqno);
      setPropertyAsString(wrapper, "messageType", type);
      setPropertyAsObject(wrapper, "message", message);
      return wrapper;
    }

    @SuppressWarnings("unused") // GWT requires an explicit protected ctor
    protected MessageWrapper() {
      super();
    }

    int getSequenceNumber() {
      return getPropertyAsInteger(this, "sequenceNumber");
    }

    String getType() {
      return getPropertyAsString(this, "messageType");
    }

    <T extends JsonMessage> T getPayload() {
      return getPropertyAsObject(this, "message").<T>cast();
    }
  }

  private WaveSocket socket;
  private final IntMap<ResponseCallback> requestsCallbacks;

  private enum ConnectState {
    CONNECTED, CONNECTING, DISCONNECTED
  }

  private ConnectState connected = ConnectState.DISCONNECTED;
  private int sequenceNo;

  private final Queue<JsonMessage> messages = CollectionUtils.createQueue();

  private final RepeatingCommand reconnectCommand = new RepeatingCommand() {
    @Override
    public boolean execute() {
      if (!connectedAtLeastOnce && !websocketNotAvailable && connectTry == MAX_WEBSOCKET_ATTEMPTS) {
        // Let's try to use socketio, seems that websocket it's not working
        // (we are under a proxy or similar)
        socket = WaveSocketFactory.create(true, urlBase, WaveWebSocketClient.this);
      }
      connectTry++;
      if (connected == ConnectState.DISCONNECTED) {
        if (!connectedAtLeastOnce) {
          LOG.trace().log("Attempting to connect");
        } else {
          LOG.trace().log("Attempting to reconnect");
        }
        connected = ConnectState.CONNECTING;
        socket.connect();
      }
      return true;
    }
  };

  private final boolean websocketNotAvailable;
  private final String urlBase;
  private boolean connectedAtLeastOnce = false;
  private long connectTry = 0;
  private List<ConnectionListener> listeners = new LinkedList<ConnectionListener>();

  public WaveWebSocketClient(boolean websocketNotAvailable, String urlBase) {
    this.websocketNotAvailable = websocketNotAvailable;
    this.urlBase = urlBase;
    requestsCallbacks = CollectionUtils.createIntMap();
    socket = WaveSocketFactory.create(websocketNotAvailable, urlBase, this);
  }

  public void addListener(ConnectionListener listener) {
    listeners.add(listener);
  }

  public void removeListener(ConnectionListener listener) {
    listeners.remove(listener);
  }

  /**
   * Opens this connection.
   */
  public void connect() {
    reconnectCommand.execute();
    Scheduler.get().scheduleFixedDelay(reconnectCommand, RECONNECT_TIME_MS);
  }

  @Override
  public void onConnect() {
    connected = ConnectState.CONNECTED;
    connectedAtLeastOnce = true;

    // Sends the session cookie to the server via an RPC to work around browser bugs.
    // See: http://code.google.com/p/wave-protocol/issues/detail?id=119
    String token = Cookies.getCookie(JETTY_SESSION_TOKEN_NAME);
    if (token != null) {
      TransportAuthenticationRequestJsoImpl auth = TransportAuthenticationRequestJsoImpl.create();
      auth.setToken(token);
      int requestId = sequenceNo++;
      requestsCallbacks.put(requestId, new ResponseCallback<EmptyResponse>() {
        @Override
        public void run(EmptyResponse response) {
        }
      });
      send(MessageWrapper.create(requestId, TRANSPORT_AUTHENTICATION_REQUEST, auth));
    }

    // Flush queued messages.
    while (!messages.isEmpty() && connected == ConnectState.CONNECTED) {
      send(messages.poll());
    }

    // Notify listeners.
    for (ConnectionListener listener : listeners) {
      listener.onConnected();
    }
  }

  @Override
  public void onDisconnect() {
    connected = ConnectState.DISCONNECTED;
    for (ConnectionListener listener : listeners) {
      listener.onDisconnected();
    }
  }

  @Override
  public void onMessage(final String message) {
    LOG.trace().log("received JSON message " + message);
    Timer timer = Timing.start("deserialize message");
    MessageWrapper wrapper;
    try {
      wrapper = MessageWrapper.parse(message);
    } catch (JsonException e) {
      LOG.error().log("invalid JSON message " + message, e);
      return;
    } finally {
      Timing.stop(timer);
    }
    String messageType = wrapper.getType();
    int seqno = wrapper.getSequenceNumber();
    if (OPEN_WAVELET_CHANNEL_STREAM.equals(messageType)) {
      OpenWaveletChannelStreamJsoImpl streamMessage = wrapper.<OpenWaveletChannelStreamJsoImpl>getPayload();
      ResponseCallback callback = requestsCallbacks.get(wrapper.getSequenceNumber());
      if (streamMessage.hasTerminator()) {
        requestsCallbacks.remove(wrapper.getSequenceNumber());
      }
      if (callback != null) {
        callback.run(streamMessage);
      } else {
        LOG.error().log("Can't find callback for response " + seqno);
      }
    } else if (SUBMIT_DELTA_RESPONSE.equals(messageType)) {
      processNotStreamResponse(seqno, wrapper.<SubmitDeltaResponseJsoImpl>getPayload());
    } else if (FETCH_FRAGMENTS_RESPONSE.equals(messageType)) {
      processNotStreamResponse(seqno, wrapper.<FetchFragmentsResponseJsoImpl>getPayload());
    } else if (FETCH_WAVE_VIEW_RESPONSE.equals(messageType)) {
      processNotStreamResponse(seqno, wrapper.<FetchWaveViewResponseJsoImpl>getPayload());
    } else if (EMPTY_RESPONSE.equals(messageType)) {
      processNotStreamResponse(seqno, wrapper.<EmptyResponseJsoImpl>getPayload());
    } else if (RPC_FINISHED.equals(messageType)) {
      for (ConnectionListener listener : listeners) {
        RpcFinishedJsoImpl status = wrapper.<RpcFinishedJsoImpl>getPayload();
        if (status.getFailed()) {
          listener.onFinished(status.getErrorText());
        }
      }
    }
  }

  public void fetchWaveView(FetchWaveViewRequestJsoImpl message, ResponseCallback<FetchWaveViewResponse> callback) {
    int requestId = sequenceNo++;
    requestsCallbacks.put(requestId, callback);
    send(MessageWrapper.create(requestId, FETCH_WAVE_VIEW_REQUEST, message));
  }

  public void fetchFragments(FetchFragmentsRequestJsoImpl message, ResponseCallback<FetchFragmentsResponse> callback) {
    int requestId = sequenceNo++;
    requestsCallbacks.put(requestId, callback);
    send(MessageWrapper.create(requestId, FETCH_FRAGMENTS_REQUEST, message));
  }

  public void open(OpenWaveletChannelRequestJsoImpl message, ResponseCallback<OpenWaveletChannelStream> callback) {
    int requestId = sequenceNo++;
    requestsCallbacks.put(requestId, callback);
    send(MessageWrapper.create(requestId, OPEN_WAVELET_CHANNEL_REQUEST, message));
  }

  public void submit(SubmitDeltaRequestJsoImpl message, ResponseCallback<SubmitDeltaResponse> callback) {
    int requestId = sequenceNo++;
    requestsCallbacks.put(requestId, callback);
    send(MessageWrapper.create(requestId, SUBMIT_DELTA_REQUEST, message));
  }

  public void close(CloseWaveletChannelRequestJsoImpl message, ResponseCallback<EmptyResponse> callback) {
    int requestId = sequenceNo++;
    requestsCallbacks.put(requestId, callback);
    send(MessageWrapper.create(requestId, CLOSE_WAVELET_CHANNEL_REQUEST, message));
  }

  private void processNotStreamResponse(int seqno, JsonMessage message) {
    ResponseCallback callback = requestsCallbacks.get(seqno);
    if (callback != null) {
      requestsCallbacks.remove(seqno);
      callback.run(message);
    } else {
      LOG.error().log("Can't find callback for response " + seqno);
    }
  }

  private void send(JsonMessage message) {
    switch (connected) {
      case CONNECTED:
        Timer timing = Timing.start("serialize message");
        String json;
        try {
          json = message.toJson();
        } finally {
          Timing.stop(timing);
        }
        LOG.trace().log("Sending JSON data " + json);
        try {
          socket.sendMessage(json);
        } catch (IllegalStateException ex) {
          messages.add(message);
          onDisconnect();
        }
        break;
      default:
        messages.add(message);
    }
  }

}
