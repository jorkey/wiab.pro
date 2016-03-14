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

package org.waveprotocol.box.server.frontend;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.frontend.ClientFrontend.UpdateChannelListener;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.util.logging.Log;
import org.waveprotocol.wave.clientserver.ReturnCode;

import java.util.Collection;
import java.util.List;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * A client's subscription to a wavelet.
 *
 * @author anorth@google.com (Alex North)
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
final class WaveletSubscription {

  /**
   * State of a wavelet endpoint.
   */
  private static final class WaveletChannelState {
    /**
     * Resulting versions of deltas submitted on this wavelet for which
     * the outbound delta has not yet been seen.
     */
    public final Collection<Long> submittedEndVersions = Sets.newHashSet();
    /**
     * Resulting version of the most recent outbound delta.
     */
    public HashedVersion lastVersion = null;
    /**
     * Whether a submit request is awaiting a response.
     */
    public boolean hasOutstandingSubmit = false;
    /**
     * Outbound deltas held back while a submit is in-flight.
     */
    public List<TransformedWaveletDelta> heldBackDeltas = Lists.newLinkedList();
    /**
     * Version of asked uncommitted submit.
     */
    private HashedVersion uncommittedVersion = null;
  }

  private static final Log LOG = Log.get(WaveletSubscription.class);

  private final WaveletName waveletName;
  private final ParticipantId participantId;
  private final UpdateChannelListener updateListener;
  private final String channelId;
  private final String connectionId;
  private final WaveletChannelState state = new WaveletChannelState();

  private boolean unsubscribeStarted = true;

  public WaveletSubscription(WaveletName waveletName, ParticipantId participantId, String channelId,
      String connectionId, UpdateChannelListener openListener) {
    Preconditions.checkNotNull(waveletName, "null wavelet id");
    Preconditions.checkNotNull(openListener, "null listener");
    Preconditions.checkNotNull(channelId, "null channel id");

    this.waveletName = waveletName;
    this.participantId = participantId;
    this.channelId = channelId;
    this.connectionId = connectionId;
    this.updateListener = openListener;
  }

  public WaveletName getWaveletName() {
    return waveletName;
  }

  public ParticipantId getParticipantId() {
    return participantId;
  }

  public String getChannelId() {
    return channelId;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public ClientFrontend.UpdateChannelListener getUpdateListener() {
    return updateListener;
  }

  public boolean isUnsubscribeStarted() {
    return unsubscribeStarted;
  }
  
  /** This client sent a submit request */
  public synchronized void submitRequest() {
    // A given client can only have one outstanding submit per wavelet.
    Preconditions.checkState(!state.hasOutstandingSubmit,
        "Received overlapping submit requests to subscription %s", this);
    LOG.info("Submit outstanding on channel " + channelId);
    state.hasOutstandingSubmit = true;
  }

  /**
   * A submit response for the given version has been received.
   */
  public synchronized void submitResponse(HashedVersion version) {
    Preconditions.checkNotNull(version, "Null delta application version");
    Preconditions.checkState(state.hasOutstandingSubmit);
    state.submittedEndVersions.add(version.getVersion());
    state.hasOutstandingSubmit = false;
    LOG.info("Submit resolved on channel " + channelId);

    forwardQueuedDeltas(state.heldBackDeltas);
  }

  public synchronized void submitError() {
    Preconditions.checkState(state.hasOutstandingSubmit);
    state.hasOutstandingSubmit = false;
    LOG.info("Submit error on channel " + channelId);

    forwardQueuedDeltas(state.heldBackDeltas);
  }

  /**
   * Sends deltas for this subscription (if appropriate).
   *
   * If the update contains a delta for a wavelet where the delta is actually
   * from this client, the delta is dropped. If there's an outstanding submit
   * request the delta is queued until the submit finishes.
   */
  public synchronized void onUpdate(DeltaSequence deltas) {
    if (unsubscribeStarted && !deltas.isEmpty()) {
      checkUpdateVersion(deltas, state);
      state.lastVersion = deltas.getEndVersion();
      if (state.hasOutstandingSubmit) {
        state.heldBackDeltas.addAll(deltas);
      } else {
        forwardQueuedDeltas(deltas);
      }
    }
  }

  /**
   * Filters any deltas sent by this client from a list of received deltas.
   *
   * @param deltas received deltas
   * @param state channel state
   * @return deltas, if none are from this client, or a copy with own client's
   *         deltas removed
   */
  private List<TransformedWaveletDelta> filterOwnDeltas(List<TransformedWaveletDelta> deltas,
      WaveletChannelState state) {
    List<TransformedWaveletDelta> filteredDeltas = deltas;
    if (!state.submittedEndVersions.isEmpty()) {
      filteredDeltas = Lists.newArrayList();
      for (TransformedWaveletDelta delta : deltas) {
        long deltaEndVersion = delta.getResultingVersion().getVersion();
        if (!state.submittedEndVersions.remove(deltaEndVersion)) {
          filteredDeltas.add(delta);
        }
      }
    }
    return filteredDeltas;
  }

  /**
   * Sends a commit notice for this subscription.
   */
  public void onCommit(HashedVersion committedVersion) {
    sendUpdate(null, committedVersion);
    if (state.uncommittedVersion != null) {
      if (state.uncommittedVersion.getVersion() <= committedVersion.getVersion()) {
        state.uncommittedVersion = null;
      }
    }
  }

  /**
   * Starts and perform unsubscribe if client does not wait for ack or commit.
   */
  public boolean unsubscribe() {
    unsubscribeStarted = false;
    if (!state.hasOutstandingSubmit && state.uncommittedVersion == null) {
      forwardQueuedDeltas(state.heldBackDeltas);
      sendTerminate(ReturnCode.UNSUBSCRIBED, "User is unsubscribed");
      return true;
    }
    return false;
  }

  /**
   * Forward any queued deltas to client.
   */
  private void forwardQueuedDeltas(List<TransformedWaveletDelta> deltas) {
    List<TransformedWaveletDelta> filteredDeltas = filterOwnDeltas(deltas, state);
    if (!filteredDeltas.isEmpty()) {
      sendUpdate(DeltaSequence.of(filteredDeltas), null);
    }
    state.heldBackDeltas.clear();
  }

  /**
   * Sends an update to the client.
   */
  private void sendUpdate(DeltaSequence deltas, HashedVersion committedVersion) {
    updateListener.onUpdate(deltas, committedVersion);
  }

  /**
   * Sends an terminate message to the client.
   */
  private void sendTerminate(ReturnCode returnCode, String errorMessage) {
    updateListener.onTerminate(returnCode, errorMessage);
  }

  /**
   * Checks the update targets the next expected version.
   */
  private void checkUpdateVersion(DeltaSequence deltas, WaveletChannelState state) {
    if (state.lastVersion != null) {
      long expectedVersion = state.lastVersion.getVersion();
      long targetVersion = deltas.getStartVersion();
      Preconditions.checkState(targetVersion == expectedVersion,
          "Subscription expected delta for %s targeting %s, was %s", waveletName, expectedVersion,
          targetVersion);
    }
  }

  @Override
  public String toString() {
    return "[WaveletSubscription wavelet: " + waveletName + ", channel: " + channelId + "]";
  }
}
