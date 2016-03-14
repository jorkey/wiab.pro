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

package org.waveprotocol.box.server.waveletstate.segment;

import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;

import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.waveletstate.delta.DeltaWaveletState;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.box.server.waveletstate.block.BlockWaveletState;
import org.waveprotocol.box.server.waveletstate.IndexingState;
import org.waveprotocol.box.server.waveletstate.WaveletState;
import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.box.common.Receiver;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Resident state of wavelet segment access.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public interface SegmentWaveletState extends WaveletState {
  interface GuiceFactory {
    SegmentWaveletState create(WaveletName waveletName, BlockWaveletState blockState);
  }

  /**
   * Checks that index consistent with deltas.
   */
  boolean isConsistent();

  /**
   * Returns the current version.
   */
  HashedVersion getLastModifiedVersion();

  /**
   * Returns the current timestamp.
   */
  long getLastModifiedTime();

  /**
   * @return last modified version and time.
   */
  Pair<HashedVersion, Long> getLastModifiedVersionAndTime();

  /**
   * Returns a snapshot copy of the current wavelet state.
   */
  ReadableWaveletData getSnapshot() throws WaveletStateException;

  /**
   * Returns a snapshot copy of the previous wavelet state.
   */
  ReadableWaveletData getSnapshot(HashedVersion version) throws WaveletStateException;

  /**
   * Gets creator.
   */
  ParticipantId getCreator() throws WaveletStateException;

  /**
   * Gets creation time.
   */
  long getCreationTime() throws WaveletStateException;

  /**
   * Gets participants of current state.
   */
  Set<ParticipantId> getParticipants() throws WaveletStateException;

  /**
   * Gets participants of previous state.
   */
  Set<ParticipantId> getParticipants(long version) throws WaveletStateException;

  /**
   * Synchronizes blocks store with delta store.
   */
  IndexingState startSynchronization(DeltaWaveletState deltaState, Executor executor) throws WaveletStateException;

  /**
   * Returns all segment Ids in the wavelet.
   *
   * @param version the version from which to get info.
   * @return segments ids from cache or from storage reply.
   */
  Set<SegmentId> getSegmentIds(long version) throws WaveletStateException;

  /**
   * Returns all intervals, or executes request to block storage.
   *
   * @param version the version of which to get info.
   * @return intervals from cache or from storage reply.
   */
  Map<SegmentId, Interval> getIntervals(long version) throws WaveletStateException;

  /**
   * Returns intervals with specified Ids.
   *
   * @param ranges versions ranges of requested segments.
   * @param onlyFromCache get intervals available in cache only.
   */
  Map<SegmentId, Interval> getIntervals(Map<SegmentId, VersionRange> ranges,
      boolean onlyFromCache) throws WaveletStateException;

  /**
   * Returns intervals with specified Ids.
   *
   * @param ranges versions ranges of requested segments.
   * @param onlyFromCache get intervals available in cache only.
   * @param receiver the receiver of intervals.
   */
  void getIntervals(Map<SegmentId, VersionRange> ranges, boolean onlyFromCache,
      Receiver<Pair<SegmentId, Interval>> receiver) throws WaveletStateException;

  /**
   * Appends the delta.
   */
  void appendDelta(WaveletDeltaRecord deltaRecord) throws WaveletStateException;

  /**
   * Starts remaking of blocks.
   */
  IndexingState startRemaking(DeltaWaveletState deltaState, Executor executor) throws WaveletStateException;

  /**
   * Marks as consistent with delta state.
   */
  void markAsConsistent();

  /**
   * Marks as inconsistent with delta state.
   */
  void markAsInconsistent() throws WaveletStateException;
}
