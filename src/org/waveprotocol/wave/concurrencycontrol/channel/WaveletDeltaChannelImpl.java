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

package org.waveprotocol.wave.concurrencycontrol.channel;

import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.concurrencycontrol.common.ChannelException;
import org.waveprotocol.wave.concurrencycontrol.common.Recoverable;
import org.waveprotocol.wave.clientserver.ReturnCode;
import org.waveprotocol.wave.clientserver.ReturnStatus;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;

import static org.waveprotocol.wave.concurrencycontrol.common.Recoverable.NOT_RECOVERABLE;
import static org.waveprotocol.wave.model.wave.Constants.NO_VERSION;

import java.util.LinkedList;
import java.util.List;

public class WaveletDeltaChannelImpl implements WaveletDeltaChannel {

  /**
   * Base class for wave delta channel messages from wave server to wave client.
   *
   * datatype ServerMessage = Delta(WaveDeltaMessage delta)
   *                        | Committed(long version)
   *                        | Ack(int opsApplied, long version)
   *                        | Nack(String errorString, long version)
   */
  private abstract static class ServerMessage implements Comparable<ServerMessage> {

    private final long startVersion;
    private final long endVersion;

    /**
     * Constructs a new server message.
     *
     * @param startVersion wavelet version to which the message applies
     * @param endVersion wavelet version after the message
     */
    protected ServerMessage(long startVersion, long endVersion) {
      this.startVersion = startVersion;
      this.endVersion = endVersion;
    }

    final long startVersion() {
      return startVersion;
    }

    final long endVersion() {
      return endVersion;
    }

    /**
     * Delivers the server message to a receiver by calling the receiver
     * method that corresponds to the message type and passing the
     * the message contents as the method arguments.
     *
     * @param receiver to whom to pass this message
     * @throws ChannelException if the upstream channel fails
     */
    abstract void deliverTo(Receiver receiver) throws ChannelException;

    /**
     * {@inheritDoc}
     *
     * Messages are ordered lexicographically by (startVersion, endVersion).
     * Note: this class has a natural ordering that is inconsistent with equals.
     */
    @Override
    public int compareTo(ServerMessage other) {
      if (startVersion < other.startVersion) {
        return -1;
      } else if (startVersion > other.startVersion) {
        return 1;
      }
      // Long version below to avoid a cast to int.
      return (endVersion < other.endVersion) ? -1 : (endVersion > other.endVersion) ? 1 : 0;
    }

    /**
     * Delta update from another wave participant.
     */
    static final class ServerDelta extends ServerMessage {

      private final TransformedWaveletDelta delta;

      ServerDelta(TransformedWaveletDelta delta) {
        super(delta.getAppliedAtVersion(), delta.getResultingVersion().getVersion());
        this.delta = delta;
      }

      @Override
      void deliverTo(Receiver receiver) throws ChannelException {
        receiver.onDelta(delta);
      }

      @Override
      public String toString() {
        return "ServerDelta(" + startVersion() + ", " + summariseDelta(delta) + ")";
      }
    }

    /**
     * Notification that the wave server has committed operations up to the
     * specified version number to replicated, persistent storage.
     */
    static final class Committed extends ServerMessage {

      private final long committedVersion;

      Committed(long sequenceVersion, long committedVersion) {
        super(sequenceVersion, sequenceVersion);
        this.committedVersion = committedVersion;
      }

      @Override
      void deliverTo(Receiver receiver) throws ChannelException {
        receiver.onCommit(committedVersion);
      }

      @Override
      public String toString() {
        return "Committed(" + startVersion() + ", " + committedVersion + ")";
      }
    }

    /**
     * Positive acknowledgement (accept) of operations submitted by the wave
     * client on this connection.
     */
    static final class Ack extends ServerMessage {

      private final int opsApplied;
      private final HashedVersion hashedVersion;

      Ack(int opsApplied, HashedVersion endVersion) {
        super(endVersion.getVersion() - opsApplied, endVersion.getVersion());
        this.opsApplied = opsApplied;
        this.hashedVersion = endVersion;
      }

      @Override
      void deliverTo(Receiver receiver) throws ChannelException {
        receiver.onAck(opsApplied, hashedVersion);
      }

      @Override
      public String toString() {
        return "Ack(" + startVersion() + ", " + opsApplied + " ops, " + hashedVersion + ")";
      }
    }

    /**
     * Negative acknowledgment (reject) of an operation submitted by the wave
     * client on this connection.
     */
    static final class Nack extends ServerMessage {

      private final ReturnStatus responseStatus;

      Nack(long sequenceVersion, ReturnStatus responseStatus) {
        super(sequenceVersion, sequenceVersion);
        Preconditions.checkArgument(responseStatus.getCode() != ReturnCode.OK,
            "Shouldn't build NACK message for response with status OK");
        this.responseStatus = responseStatus;
      }

      @Override
      void deliverTo(Receiver receiver) throws ChannelException {
        receiver.onNack(responseStatus, endVersion());
      }

      @Override
      public String toString() {
        return "Nack(" + endVersion() + ", error " + responseStatus + ")";
      }
    }
  }

  private static enum State {
    INITIAL,
    CONNECTED
  }

  private final LoggerBundle logger;
  private State state = State.INITIAL;

  /** Channel on which to submit messages. */
  private final WaveletChannel channel;

  /** Listener for received messages. */
  private Receiver receiver;

  /** Tag which changes with each reconnection to identify late acks. */
  private int connectionTag = 0;

  /** Ready-to-transmit message. */
  private Transmitter transmitter;

  /**
   * The single in-flight outbound message, if any, otherwise null.
   * Only one "slot" is needed as outbound messages are serialized.
   */
  private WaveletDelta transmitDelta;

  /** The last delta submitted and responded to by the server. */
  private WaveletDelta lastTransmitDelta = null;

  /** Last submit acknowledgment from the server. */
  private HashedVersion lastAckedVersion = null;

  /** Version number of last delta or acknowledgment delivered to receiver. */
  private long lastServerVersion = NO_VERSION;
  /**
   * Queue to hold back out-of-order messages while awaiting the next in-order
   * message. Items in the queue are ordered by (startVersion, endVersion) and
   * the startVersion of each message should equal the endVersion of the
   * previous message.
   *
   * @see ServerMessage#compareTo(ServerMessage)
   */
  private final List<ServerMessage> queue = new LinkedList<>();

  /**
   * Constructs a new channel. The channel is initially disconnected until it
   * receives a first wavelet update. The channel must be
   * {@link #reset(org.waveprotocol.wave.concurrencycontrol.channel.WaveletDeltaChannel.Receiver)}
   * to install a receiver before any messages may be received.
   *
   * @param channel channel on which to submit deltas
   * @param logger logger for the channel
   */
  public WaveletDeltaChannelImpl(WaveletChannel channel, LoggerBundle logger) {
    this.channel = channel;
    this.logger = logger;
    logTrace("New delta channel created");
  }

  // WaveletDeltaChannel implementation.
  // Receives messages from the local client.

  @Override
  public void reset(Receiver receiver) {
    internalReset();
    this.receiver = receiver;
  }

  @Override
  public void send(Transmitter t) {
    if (state != State.CONNECTED) {
      throw new IllegalStateException(
          "Sending message over a channel that is not connected. state: " + state);
    }
    transmitter = t;
    tryTransmit();
  }

  @Override
  public void onConnection(HashedVersion connectVersion, HashedVersion lastModifiedVersion,
      HashedVersion lastCommittedVersion, HashedVersion unacknowledgedDeltaVersion,
      List<WaveletOperation> operations) throws ChannelException {
    // The first update must contain a committed version.
    state = State.CONNECTED;
    connectionTag++;

    if (receiver != null) {
      receiver.onConnection(connectVersion, lastModifiedVersion, unacknowledgedDeltaVersion, operations);
    }
    lastServerVersion = lastModifiedVersion.getVersion();
    processLastCommittedVersion(lastCommittedVersion);
    flushServerMessages();
  }

  // WaveletChannel.Listener implementation.
  // Receives messages from the remote server.

  @Override
  public void onWaveletUpdate(List<TransformedWaveletDelta> deltas, HashedVersion lastCommittedVersion)
      throws ChannelException {
    Preconditions.checkArgument(state == State.CONNECTED, "Process update on a channel that is not connected.");
    if (deltas != null) {
      processDeltas(deltas);
    }
    if (lastCommittedVersion != null) {
      processLastCommittedVersion(lastCommittedVersion);
    }
    flushServerMessages();
  }

  /**
   * Processes the last committed version (if any) from an update.
   *
   * @param lastCommittedVersion committed version information (may be null)
   */
  private void processLastCommittedVersion(HashedVersion lastCommittedVersion)
      throws ChannelException {
    if (lastCommittedVersion != null) {
      // Synthesize a "sequenceVersion" for the committed message to put it in
      // the most useful place in the message queue. If possible use the
      // committed version, which causes it to be delivered to the receiver in
      // sequence with other messages. If the queue has already progressed past
      // that, place the message at the beginning of the queue, namely at
      // lastServerVersion.
      long committedVersion = lastCommittedVersion.getVersion();
      long sequenceVersion = Math.max(committedVersion, lastServerVersion);
      onServerMessage(new ServerMessage.Committed(sequenceVersion, committedVersion));
    }
  }

  /**
   * Processes deltas from an incoming server message.
   *
   * @param deltas container with deltas
   */
  private void processDeltas(List<TransformedWaveletDelta> deltas) throws ChannelException {
    for (TransformedWaveletDelta delta : deltas) {
      logDelta("Incoming", delta);
      ServerMessage.ServerDelta serverDelta = new ServerMessage.ServerDelta(delta);
      int queuePos = onServerMessage(serverDelta);
      checkForMissingMessages(serverDelta, queuePos);
    }
  }

  /**
   * Queues up a message from the server in startVersion order.
   *
   * @param message server message to queue
   * @return the position in {@code queue} at which the message was inserted
   * @see #flushServerMessages()
   */
  private int onServerMessage(ServerMessage message) throws ChannelException {
    if (message.startVersion() < lastServerVersion) {
      throw new ChannelException("Delta channel: out of sequence server message with version "
          + message.startVersion() + ": " + message + ", " + this.toString()
          + "; lastServerVersion: " + lastServerVersion, NOT_RECOVERABLE);
    }
    int pos = queue.size();
    while (pos > 0 && (queue.get(pos - 1).compareTo(message) > 0)) {
      pos--;
    }
    queue.add(pos, message);
    return pos;
  }

  /**
   * Delivers queued messages from the server to the wave client in version
   * order. If there are missing versions (should mean that messages are
   * reordered and messages with the missing numbers are still in flight) we
   * hold back the queued messages with larger versions.
   */
  private void flushServerMessages() throws ChannelException {
    while (!queue.isEmpty() && queue.get(0).startVersion() == lastServerVersion) {
      ServerMessage message = queue.remove(0);
      if (message.endVersion < lastServerVersion) {
        Preconditions.illegalState("Delta channel queue is out of order. Message endVersion "
            + message.endVersion + ", lastServerVersion " + lastServerVersion);
      }
      lastServerVersion = message.endVersion();
      if (receiver != null) {
        logTrace("Releasing message ", message);
        message.deliverTo(receiver);
      }
    }

    tryTransmit();
  }

  /**
   * Checks that inserting an incoming message into the queue does not
   * leave the channel with a version gap.
   *
   * @param incoming incoming message, not yet in queue
   * @param queuePos where the message should go in the queue
   * @throws ChannelException if a missing message is detected
   */
  private void checkForMissingMessages(ServerMessage incoming, int queuePos)
      throws ChannelException {
    long expectedVersion;
    if (queuePos == 0) {
      expectedVersion = lastServerVersion;
    } else {
      ServerMessage previous = queue.get(queuePos - 1);
      expectedVersion = previous.endVersion();
    }

    // If there's a gap between the incoming message start version and
    // the previous message end version then it must be accounted for by an
    // in-flight submission, or there's a problem.
    long gap = incoming.startVersion() - expectedVersion;
    if (gap > 0) {
      // An in-flight delta can account for a gap of at most opListSize().
      // Server deltas are fully processed synchronously upon receiving them,
      // and the server sends them in order with no gaps except for our own
      // submissions, so a valid gap may only occur at the front of the queue.
      if (deltaIsInFlight() && (queuePos == 0)) {
        if (gap > transmitDelta.size()) {
          throw new ChannelException("Message missing! Incoming message " + incoming
              + " expected version " + expectedVersion + ", gap " + gap + ", in-flight delta has "
              + transmitDelta.size() + " ops", Recoverable.NOT_RECOVERABLE);
        }
      } else {
        throw new ChannelException("Message missing! Incoming message " + incoming
            + " expected version " + expectedVersion + ", gap " + gap + ", no in-flight delta",
            Recoverable.NOT_RECOVERABLE);
      }
    }
  }

  /**
   * Transmits a delta obtained from transmitter, if the conditions are right
   * (channel is connected, no other transmission is in flight, no queued
   * messages, etc).
   */
  private void tryTransmit() {
    if (state != State.CONNECTED) {
      Preconditions.illegalState("Cannot send to delta channel in state " + state);
    }

    if (!queue.isEmpty()) {
      // There are queued incoming messages. Don't transmit now
      // but wait until the queued messages are processed and
      // transformed with the transmitted message.
      //
      // NOTE: This condition does not to starve the transmitter,
      // because the queue is only non-empty when incoming stream
      // messages and responses to delta transmissions are reordered
      // on the wire, a transient condition that can occur only as a
      // result of the last transmission response. As soon as it
      // leaves the queue, the queue remains empty until the next
      // transmission response.
      return;
    }

    if (deltaIsInFlight()) {
      // Serialize messages from client to server.
      return;
    }

    // TODO(user): change this to: add wavelet channel facility to call
    // submit with a suspended argument ("thunk", "callback")
    // which is invoked when the RPC request is put on the wire,
    // and let the suspension invoke takeArgs
    // NOTE(anorth): the op channel does not currently take advantage
    // of this anyway.
    final WaveletDelta delta = takeArgs();

    if (delta == null) {
      return; // Transmission has been cancelled.
    }

    if (logger.trace().shouldLog()) {
      logTrace("Outgoing " + summariseDelta(delta));
    }

    transmitDelta = delta; // Flags that an outbound message is in flight.

    final int submitConnectionTag = connectionTag;
    channel.submit(delta, new SubmitCallback() {

      @Override
      public void onResponse(int opsApplied, HashedVersion newVersion, long timestampAfterApplication,
          ReturnStatus returnStatus) throws ChannelException {
        if (connectionIsCurrent()) {
          if (opsApplied < 0) {
            throw new ChannelException("Delta channel: invalid submit delta response, opsApplied: "
                + opsApplied, NOT_RECOVERABLE);
          }

          if (opsApplied > 0 || returnStatus.getCode() == ReturnCode.OK) {
            // It is not necessarily the case that
            // opsApplied == delta.getOpListSize() since ops may disappear in
            // transform. Zero ops applied is a valid ack when the server
            // transforms all operations away.
            lastAckedVersion = newVersion;
            onServerMessage(new ServerMessage.Ack(opsApplied, newVersion));
          }

          if (returnStatus.getCode() == ReturnCode.TOO_OLD) {
            throw new ChannelException(ReturnCode.TOO_OLD, "Delta targeted too old version",
                null, Recoverable.RECOVERABLE, null, null);
          } else if (returnStatus.getCode() != ReturnCode.OK) {
            // Using lastServerVersion when opsApplied is 0 is a harmless cop out to deal with the
            // fact that the view server access control responds with a bogus version and 0
            // opsApplied when it rejects a delta.
            onServerMessage(new ServerMessage.Nack(
                (opsApplied > 0) ? newVersion.getVersion() : lastServerVersion,
                returnStatus));
          }

          lastTransmitDelta = transmitDelta;
          transmitDelta = null; // Enables flushServerMessages() to transmit.
          flushServerMessages();

          // Check the ack didn't leave a gap in the queue.
          if (!queue.isEmpty() &&
              (queue.get(0).startVersion() != (newVersion.getVersion() - opsApplied))) {
            throw new ChannelException(
                "Delta channel couldn't flush messages after submit response: lastServerVersion "
                + lastServerVersion + ", queued message version " + queue.get(0).startVersion()
                + " response version " + newVersion + ", opsApplied " + opsApplied,
                Recoverable.NOT_RECOVERABLE);
          }
        }
      }

      @Override
      public void onFailure(ReturnStatus responseStatus) throws ChannelException {
        if (connectionIsCurrent()) {
          throw new ChannelException("Delta channel: submission failed: " + responseStatus,
              Recoverable.RECOVERABLE);
        }
      }

      /**
       * Checks whether the channel is connected and connection tag is current,
       * i.e. the channel has not been reconnected since the delta was sent.
       */
      private boolean connectionIsCurrent() {
        if (state != State.CONNECTED) {
          logTrace("Ignoring orphaned ack on disconnected channel");
          return false;
        }
        if (connectionTag != submitConnectionTag) {
          // Ignore, the channel has been reset since this was sent.
          logTrace("Ignoring ophaned ack on with connection tag ", submitConnectionTag,
              ", connectionTag now ", connectionTag);
          return false;
        }
        return true;
      }
    });
  }

  /**
   * Invokes the transmitter to get a (delta) and decorates/wraps it as in an
   * argument object. Returns null to abort transmission.
   */
  private WaveletDelta takeArgs() {
    if (transmitter == null) {
      return null; // Transmission has been cancelled.
    }

    Transmitter.ClientMessage message = transmitter.takeMessage();
    transmitter = null; // Transmitter is one-shot, will not be used again.
    return message.getDelta();
  }

  /** Checks whether a delta submission is pending response. */
  private boolean deltaIsInFlight() {
    return transmitDelta != null;
  }

  /**
   * Resets this channel, ready to receive another connection message.
   */
  private void internalReset() {
    logTrace("Delta channel reset");
    state = State.INITIAL;
    transmitter = null;
    transmitDelta = null;
    lastServerVersion = NO_VERSION;
    queue.clear();
  }

  @Override
  public String toString() {
    // Space before \n in case some logger swallows the newline.
    return "Delta Channel State = " +
        "[state:" + state + "] " + "[connectionTag:" + connectionTag + "] \n" +
        "[transmitDelta:" + summariseDelta(transmitDelta) + "] \n" +
        "[lastServerVersion:" + lastServerVersion + "] " +
        "[lastTransmitDelta:" + summariseDelta(lastTransmitDelta) + "] \n" +
        "[lastAckedVersion: " + lastAckedVersion + "] \n" +
        "[queue (" + queue.size() + " msgs):" + queue + "]";
  }

  /**
   * Logs the ops from a delta message.
   *
   * @param prefix message to print before each op message
   * @param delta delta to log
   */
  private void logDelta(String prefix, TransformedWaveletDelta delta) {
    if (logger.trace().shouldLog()) {
      logTrace(prefix + summariseDelta(delta));
    }
  }

  // TODO(anorth): move these log helpers somewhere common.
  /**
   * Logs a trace message, evaluating and concatenating components only if trace
   * is enabled.
   *
   * @param components message components, which will be evaluated with
   *        {@link String#valueOf(Object)}
   */
  private void logTrace(Object... components) {
    if (logger.trace().shouldLog()) {
      StringBuilder buffer = new StringBuilder();
      for (Object c : components) {
        buffer.append(c);
      }
      logger.trace().log(buffer.toString());
    }
  }

  private static String summariseDelta(WaveletDelta delta) {
    if (delta == null) {
      return "null";
    }
    StringBuilder b = new StringBuilder("delta ");
    b.append("version: ").append(delta.getTargetVersion()).append(", ");
    b.append("ops: ").append(delta.size()).append(", ");
    for (WaveletOperation op : delta) {
      b.append(op.toString()).append(", ");
    }
    return b.toString();
  }

  private static String summariseDelta(TransformedWaveletDelta delta) {
    if (delta == null) {
      return "null";
    }
    StringBuilder b = new StringBuilder("delta ");
    b.append("applied-version: ").append(delta.getAppliedAtVersion()).append(", ");
    b.append("end-version: ").append(delta.getResultingVersion()).append(", ");
    b.append("ops: ").append(delta.size()).append(", ");
    for (WaveletOperation op : delta) {
      b.append(op.toString()).append(", ");
    }
    return b.toString();
  }
}
