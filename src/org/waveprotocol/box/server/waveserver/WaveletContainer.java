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

import org.waveprotocol.box.common.ThrowableReceiver;

import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.util.Pair;

import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;

import org.waveprotocol.box.common.Receiver;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for a container class for a Wavelet's current state as well as its
 * delta history. Local and remote wavelet interfaces inherit from this one.
 */
interface WaveletContainer {

  /**
   * Manufactures wavelet containers.
   *
   * @param <T> type manufactured by this factory.
   */
  interface Factory<T extends WaveletContainer> {
    /**
     * @return a new wavelet container with the given wavelet name
     */
    T create(WaveletNotificationSubscriber notifiee, WaveletName waveletName, String waveDomain);
  }

  /** Wait for loading is complete. */
  void awaitLoad() throws WaveletStateException;

  /** Start closing resources */
  ListenableFuture close();
  
  /** Returns true if wavelet is corrupted. */
  boolean isCorrupted();

  /** Returns the name of the wavelet. */
  WaveletName getWaveletName();

  /** Returns last known server wavelet version. */
  HashedVersion getConnectVersion(List<HashedVersion> knownVersions) throws WaveletStateException;

  /**
   * @return the wavelet creator. This method doesn't acquire
   *         {@link WaveletContainer} lock since wavelet creator cannot change
   *         after wavelet creation and therefore it is save to concurrently
   *         read this property without lock.
   */
  ParticipantId getCreator() throws WaveletStateException;

  /** Returns creation time of wavelet. */
  long getCreationTime() throws WaveletStateException;

  /** Returns true if the participant id is a current participant of the wavelet. */
  boolean hasParticipant(ParticipantId participant) throws WaveletStateException;

  /** Returns the wavelet participants. */
  Set<ParticipantId> getParticipants() throws WaveletStateException;

  /** Returns the participants of specified wavelet version. */
  Set<ParticipantId> getParticipants(long version) throws WaveletStateException;

  /** Returns a snapshot of the wavelet current state. */
  ReadableWaveletData getSnapshot() throws WaveletStateException;

  /** Returns a snapshot of the wavelet previous state. */
  ReadableWaveletData getSnapshot(HashedVersion version) throws WaveletStateException;

  /** Returns segment id's. */
  Set<SegmentId> getSegmentIds(long version) throws WaveletStateException;

  /** Returns intervals. */
  void getIntervals(Map<SegmentId, VersionRange> ranges, boolean onlyFromCache,
      Receiver<Pair<SegmentId, Interval>> receiver) throws WaveletStateException;

  /** Remakes index of store. */
  void remakeIndex() throws WaveletStateException;

  /**
   * Retrieve the wavelet history of deltas applied to the wavelet, with
   * additional safety check that
   *
   * @param versionStart start version (inclusive), minimum 0.
   * @param versionEnd end version (exclusive).
   * @param receiver the deltas receiver.
   * @throws AccessControlException if {@code versionStart} or
   *         {@code versionEnd} are not in the wavelet history.
   * @throws WaveletStateException if the wavelet is in a state unsuitable for
   *         retrieving history.
   */
  void requestDeltaHistory(HashedVersion versionStart, HashedVersion versionEnd,
    ThrowableReceiver<WaveletDeltaRecord, WaveletStateException> receiver) throws AccessControlException, WaveletStateException;

  /**
   * Retrieve hashed version, equals or nearest less to specified version.
   *
   * @param waveletName name of wavelet.
   * @param version
   *
   * @return hashed version.
   */
  HashedVersion getNearestHashedVersion(long version) throws WaveServerException;

  /**
   * @param participantId id of participant attempting to gain access to
   *        wavelet, or null if the user isn't logged in.
   * @throws WaveletStateException if the wavelet is in a state unsuitable for
   *         checking permissions.
   * @return true if the participant is a participant on the wavelet or if the
   *         wavelet is empty or if a shared domain participant is participant
   *         on the wavelet.
   */
  boolean checkAccessPermission(ParticipantId participantId) throws WaveletStateException;

  /**
   * The current wavelet version.
   *
   * @throws WaveletStateException if the wavelet is in a state unsuitable for
   *         getting LCV.
   */
  HashedVersion getLastModifiedVersion() throws WaveletStateException;

  /**
   * The current wavelet version and timestamp.
   *
   * @throws WaveletStateException if the wavelet is in a state unsuitable for
   *         getting LCV.
   */
  Pair<HashedVersion, Long> getLastModifiedVersionAndTime() throws WaveletStateException;

  /**
   * The Last Committed Version returns when the local or remote wave server
   * committed the wavelet.
   *
   * @throws WaveletStateException if the wavelet is in a state unsuitable for
   *         getting LCV.
   */
  HashedVersion getLastCommittedVersion() throws WaveletStateException;

  /**
   * This method doesn't acquire {@link WaveletContainer} lock since shared
   * domain participant cannot change and therefore it is safe to concurrently
   * read this property without lock.
   *
   * @return the shared domain participant.
   */
  ParticipantId getSharedDomainParticipant();

  /**
   * @return true if the wavelet is at version zero, i.e., has no delta history
   */
  boolean isEmpty() throws WaveletStateException;

}
