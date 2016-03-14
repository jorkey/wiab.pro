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

package org.waveprotocol.wave.client.concurrencycontrol;

import org.waveprotocol.wave.concurrencycontrol.channel.OperationChannelMultiplexer;
import org.waveprotocol.wave.concurrencycontrol.channel.OperationChannelMultiplexer.StreamListener;
import org.waveprotocol.wave.concurrencycontrol.common.ChannelException;
import org.waveprotocol.wave.concurrencycontrol.common.Recoverable;
import org.waveprotocol.wave.clientserver.ReturnStatus;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletFragmentData;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.model.wave.opbased.WaveViewImpl;
import org.waveprotocol.wave.model.util.Preconditions;

import org.waveprotocol.box.webclient.client.WaveWebSocketClient;
import org.waveprotocol.box.webclient.client.WaveWebSocketClient.ConnectionListener;

import java.util.Map;
import java.util.Set;

/**
 * Multiplexor connector.
 *
 * Also manages reconnection.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class MuxConnectorImpl implements MuxConnector {
  protected static LoggerBundle LOG = new DomLogger("connector");

  private static enum State { INITIAL, CONNECTING, CONNECTED, DISCONNECTED, RECONNECTING, CLOSED }

  private final WaveWebSocketClient webSocket;
  private final OperationChannelMultiplexer mux;
  private final ChannelDataBinder binder;

  private Listener listener;
  private State state;

  private final ConnectionListener connectionListener = new ConnectionListener() {

    @Override
    public void onConnecting() {
    }

    @Override
    public void onConnected() {
      if (state == State.DISCONNECTED) {
        reconnect();
      }
    }

    @Override
    public void onDisconnected() {
      state = State.DISCONNECTED;
      mux.disconnect();
    }

    @Override
    public void onFinished(String error) {
      state = State.DISCONNECTED;
      mux.disconnect();
    }
  };

  public MuxConnectorImpl(WaveWebSocketClient webSocket, OperationChannelMultiplexer mux, ChannelDataBinder binder) {
    this.webSocket = webSocket;
    this.mux = mux;
    this.binder = binder;
    state = State.INITIAL;
    webSocket.addListener(connectionListener);
  }

  @Override
  public void connect(Listener listener) {
    this.listener = listener;
    state = State.CONNECTING;
    mux.open(binder.getKnownWavelets(), getKnownSegmentIds(binder), createStreamListener());
  }

  @Override
  public void close() {
    state = State.CLOSED;
    mux.close();
    webSocket.removeListener(connectionListener);
  }

  private void reconnect() {
    state = State.RECONNECTING;
    mux.reopen(getKnownSegmentIds(binder));
  }

  private static Map<WaveletId,Set<SegmentId>> getKnownSegmentIds(ChannelDataBinder binder) {
    Map<WaveletId,Set<SegmentId>> knownSegmentIds = CollectionUtils.newHashMap();
    WaveViewImpl<OpBasedWavelet> waveView = binder.getWaveView();
    Preconditions.checkNotNull(waveView, "No wave view");
    OpBasedWavelet wavelet = waveView.getWavelet(waveView.getRootId());
    Preconditions.checkNotNull(wavelet, "No wavelet");
    ObservableWaveletFragmentData rootWavelet = (ObservableWaveletFragmentData)wavelet.getWaveletData();
    if (rootWavelet != null) {
      knownSegmentIds.put(rootWavelet.getWaveletId(), rootWavelet.getSegmentIds());
    }
    return knownSegmentIds;
  }

  private OperationChannelMultiplexer.StreamListener createStreamListener() {
    return new StreamListener() {

      @Override
      public void onConnected() {
        if (state != State.CLOSED) {
          State oldState = state;
          state = State.CONNECTED;
          if (oldState == State.CONNECTING) {
            listener.onConnected();
          }
        }
      }

      @Override
      public void onFailed(ReturnStatus detail) {
        if (state != State.CLOSED) {
          state = State.DISCONNECTED;
          listener.onFailed(detail);
        }
      }

      @Override
      public void onException(ChannelException ex) {
        if (state != State.CLOSED) {
          state = State.DISCONNECTED;
          if (ex.getRecoverable() == Recoverable.RECOVERABLE) {
            reconnect();
          }
        }
      }
    };
  }
}
