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

package org.waveprotocol.box.server.waveserver;

import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.common.ThrowableReceiver;
import org.waveprotocol.box.common.Receiver;

import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;

import org.waveprotocol.wave.clientserver.ReturnCode;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.util.Pair;

import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides wavelet snapshots and history, and accepts delta submissions to
 * wavelets.
 */
public interface WaveletProvider {
  /**
   * Receives the result of a open request.
   */
  interface OpenRequestCallback {
    /**
     * Notifies the listener that the delta was successfully applied.
     *
     * @param connectVersion the version known to server for connect.
     * @param lastCommittedVersion the last committed version.
     */
    void onSuccess(HashedVersion connectVersion, HashedVersion lastCommittedVersion);

    /**
     * Notifies the listener that the delta failed to apply.
     */
    void onFailure(ReturnCode responseCode, String errorMessage);
  }

  /**
   * Receives the result of a delta submission request.
   */
  interface SubmitRequestCallback {
    /**
     * Notifies the listener that the delta was successfully applied.
     *
     * @param operationsApplied number of operations applied
     * @param hashedVersionAfterApplication wavelet hashed version after the delta
     * @param applicationTimestamp timestamp of delta application
     */
    void onSuccess(int operationsApplied, HashedVersion hashedVersionAfterApplication,
        long applicationTimestamp);

    /**
     * Notifies the listener that the delta failed to apply.
     */
    void onFailure(ReturnCode responseCode, String errorMessage);
  }

  /**
   * Initializes the provider from storage. No other method is valid until
   * initialization is complete.
   */
  void initialize() throws WaveServerException;

  /**
   * Requests to open specified wavelet.
   *
   * @param waveletName name of wavelet.
   * @param knownVersions known client signatures.
   * @param participantId the participant.
   * @return begin version, known to server.
   */
  void openRequest(WaveletName waveletName, List<HashedVersion> knownVersions, ParticipantId participantId, OpenRequestCallback callback);

  /**
   * Requests that a given delta is submitted to the wavelet.
   *
   * @param waveletName name of wavelet.
   * @param delta to be submitted to the server.
   * @param listener callback which will return the result of the submission.
   */
  void submitRequest(WaveletName waveletName, ProtocolWaveletDelta delta, SubmitRequestCallback listener);

  /**
   * Retrieves hashed version, equals or nearest less to specified version.
   *
   * @param waveletName name of wavelet.
   * @param version
   *
   * @return hashed version.
   */
  HashedVersion getNearestHashedVersion(WaveletName waveletName, long version) throws WaveServerException;

  /**
   * Checks if the wavelet is exists.
   *
   * @param waveletName name of wavelet.
   * @return true if the wavelet exists.
   * @throws WaveServerException if storage access fails or if the wavelet is in
   *         a bad state
   */
  boolean checkExistence(WaveletName waveletName) throws WaveServerException;

  /**
   * Checks if the specified participantId has access to the named wavelet.
   *
   * @param waveletName name of wavelet.
   * @param participantId id of participant attempting to gain access to
   *        wavelet, or null if the user isn't logged in.
   * @return true if the wavelet exists and the participant is a participant on
   *         the wavelet.
   * @throws WaveServerException if storage access fails or if the wavelet is in
   *         a bad state
   */
  boolean checkAccessPermission(WaveletName waveletName, ParticipantId participantId)
      throws WaveServerException;

  /**
   * Returns an iterator over all waves in the server.
   *
   * The iterator may or may not include waves created after the iterator is returned.
   *
   * @return an iterator over the ids of all waves
   * @throws WaveServerException if storage access fails
   */
  ExceptionalIterator<WaveId, WaveServerException> getWaveIds() throws WaveServerException;

  /**
   * Looks up all wavelets in a wave.
   *
   * @param waveId wave to look up
   * @return ids of all non-empty wavelets
   * @throws WaveServerException if storage access fails
   */
  ImmutableSet<WaveletId> getWaveletIds(WaveId waveId) throws WaveServerException;

  /**
   * Requests the current version of wavelet.
   *
   * @param waveletName the name of the wavelet
   * @throws WaveletStateException if the wavelet is in a state unsuitable for
   *         getting LCV.
   */
  HashedVersion getLastModifiedVersion(WaveletName waveletName) throws WaveServerException;

  /**
   * Requests the current version and timestamp of wavelet.
   *
   * @param waveletName the name of the wavelet
   * @throws WaveletStateException if the wavelet is in a state unsuitable for
   *         getting LCV.
   */
  Pair<HashedVersion, Long> getLastModifiedVersionAndTime(WaveletName waveletName) throws WaveServerException;

  /**
   * The Last Committed Version returns when the local or remote wave server
   * committed the wavelet.
   *
   * @param waveletName the name of the wavelet
   * @throws WaveletStateException if the wavelet is in a state unsuitable for
   *         getting LCV.
   */
  HashedVersion getLastCommittedVersion(WaveletName waveletName) throws WaveServerException;

  /**
   * Request the creator of the wavelet.
   */
  ParticipantId getCreator(WaveletName waveletName) throws WaveServerException;

  /**
   * Request the creation time of the wavelet.
   */
  long getCreationTime(WaveletName waveletName) throws WaveServerException;

  /**
   * Checks is the participant id is a participant of the wavelet.
   */
  boolean hasParticipant(WaveletName waveletName, ParticipantId participant) throws WaveServerException;

  /**
   * Request wavelet participants of current version.
   */
  Set<ParticipantId> getParticipants(WaveletName waveletName) throws WaveServerException;

  /**
   * Requests wavelet participants of specified version.
   */
  Set<ParticipantId> getParticipants(WaveletName waveletName, long version) throws WaveServerException;

  /**
   * Request the current state of the wavelet.
   *
   * @param waveletName the name of the wavelet
   * @return the wavelet, or null if the wavelet doesn't exist
   * @throws WaveServerException if storage access fails or if the wavelet is in
   *         a bad state
   */
  ReadableWaveletData getSnapshot(WaveletName waveletName) throws WaveServerException;

  /**
   * Requests the previous state of the wavelet.
   *
   * @param waveletName the name of the wavelet
   * @param version of snapshot
   * @return the wavelet, or null if the wavelet doesn't exist
   * @throws WaveServerException if storage access fails or if the wavelet is in
   *         a bad state
   */
  ReadableWaveletData getSnapshot(WaveletName waveletName, HashedVersion version) throws WaveServerException;

  /**
   * Looks up all segments in a wavelet.
   *
   * @param waveletName the name of the wavelet
   * @param version the version of wavelet
   * @return ids of segment ids.
   * @throws WaveServerException if storage access fails
   */
  Set<SegmentId> getSegmentIds(WaveletName waveletName, long version) throws WaveServerException;

  /**
   * Returns intervals.
   *
   * @param waveletName name of wavelet.
   * @param ranges the ranges of intervals.
   * @param onlyFromCache get intervals existing in the cache.
   * @throws WaveServerException if storage access fails or if the wavelet is in
   *         a bad state
   */
  void getIntervals(WaveletName waveletName, Map<SegmentId, VersionRange> ranges,
      boolean onlyFromCache, Receiver<Pair<SegmentId, Interval>> receiver) throws WaveServerException;

  /**
   * Retrieves the wavelet history of deltas applied to the wavelet.
   *
   * @param waveletName name of wavelet.
   * @param versionStart start version (inclusive), minimum 0.
   * @param versionEnd end version (exclusive).
   * @param receiver of deltas.
   * @throws AccessControlException if {@code versionStart} or
   *         {@code versionEnd} are not in the wavelet history.
   * @throws WaveServerException if storage access fails or if the wavelet is in
   *         a bad state
   */
  void getDeltaHistory(WaveletName waveletName, HashedVersion versionStart, HashedVersion versionEnd,
      ThrowableReceiver<WaveletDeltaRecord, WaveServerException> receiver) throws WaveServerException;

  /**
   * Remakes the history of snapshots.
   *
   * @param waveletName the name of the wavelet
   * @throws WaveServerException if storage access fails or if the wavelet is in
   *         a bad state
   */
  void remakeIndex(WaveletName waveletName) throws WaveServerException;
}
