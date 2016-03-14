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

import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.raw.RawFragment;

import org.waveprotocol.wave.clientserver.ReturnStatus;
import org.waveprotocol.wave.concurrencycontrol.common.ChannelException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulates the WaveView RPC as a channel.
 */
public interface ViewChannel {

  /**
   * Callback for wave view fetch request.
   */
  interface FetchWaveViewCallback {
    /**
     * Notifies this listener of a wave view fetch results.
     *
     * @param waveView wave view data.
     */
    void onWaveViewFetch(WaveViewData waveView);

     /**
     * Notifies this listener that server returned error status.
     */
    void onFailure(ReturnStatus status);
}

  /**
   * Callback for fragments fetch request.
   */
  interface FetchFragmentsCallback {
    /**
     * Notifies this listener of a fragments fetch results.
     *
     * @param fragments the fetched fragments.
     */
    void onWaveFragmentsFetch(Map<SegmentId, RawFragment> fragments);

    /**
     * Notifies this listener that server returned error status.
     */
    void onFailure(ReturnStatus status);
  }
  
  /**
   * Callback to indicate that indexing is in process.
   */
  interface IndexingCallback {
    
    void onIndexing(long totalVersions, long indexedVersions);
    void onIndexingComplete();
  }

  /**
   * Listener of ViewChannel lifecycle events and incoming updates.
   */
  interface Listener {
    /**
     * Notifies this listener that the channel is now connected, and deltas
     * can be submitted.
     */
    void onWaveletOpened(WaveletId waveletId, HashedVersion connectVersion, HashedVersion lastModifiedVersion,
      long lastModifiedTime, HashedVersion lastCommittedVersion, HashedVersion unacknowledgedDeltaVersion,
      Map<SegmentId, RawFragment> fragments) throws ChannelException;

    /**
     * Notifies this listener that all wavelets are now opened.
     */
    void onConnected() throws ChannelException;

    /**
     * Notifies this listener that the connection has terminated.
     */
    void onDisconnected();

    /**
     * Notifies this listener that the channel is closed.
     *
     * Interpreted as client-side termination, either
     * through a {@link #close() requested close} or through a failure.
     * It does not necessarily mean that the server has acknowledge the
     * channel closure.  Indeed, there may be update messages in transit
     * both to and from the server.  It is up to a higher layer of the
     * communication stack to deal with such outstanding messages.
     */
    void onClosed();

    /**
     * Notifies this listener of an update on the stream.
     *
     * @param waveletId             id of the wavelet being updated
     * @param deltas                update deltas
     * @param lastCommittedVersion  the last commit version
     */
    void onUpdate(WaveletId waveletId, List<TransformedWaveletDelta> deltas,
        HashedVersion lastCommittedVersion) throws ChannelException;

    /**
     * Notifies this listener that server returned error status.
     */
    void onFailure(ReturnStatus status);

    /**
     * Notifies this listener that an exception has occurred. The ViewChannel is not
     * closed yet when this method is called, but it will be closed after this call.
     *
     * @param ex The exception that occured whilst handling responses from the server.
     */
    void onException(ChannelException ex);
  }

  /**
   * Fetches this wave by specified wavelet filter.
   */
  void fetchWaveView(IdFilter waveletFilter, boolean fromLastRead, 
    int minBlipReplySize, int maxBlipReplySize, int maxBlipCount, FetchWaveViewCallback callback);

  /**
   * Fetches specified fragments.
   */
  void fetchFragments(WaveletId waveletId, Map<SegmentId, Long> segments, long endVersion, 
    int minReplySize, int maxReplySize, FetchFragmentsCallback callback);

  /**
   * Opens this WaveView channel. Can be called only once.
   */
  void open(Map<WaveletId, List<HashedVersion>> knownWavelets,
      Map<WaveletId, Set<SegmentId>> segmentIds, Map<WaveletId, WaveletDelta> unacknowledgedDeltas,
      Listener listener);

  /**
   * Disconnects this WaveView channel.
   */
  void disconnect();

  /**
   * Closes this WaveView channel.
   */
  void close();

  /**
   * Submits a delta on this channel.
   *
   * @param waveletId       id of the target wavelet
   * @param delta           delta to apply
   * @param callback        callback to notify of the submit response
   */
  void submitDelta(WaveletId waveletId, WaveletDelta delta, SubmitCallback callback);
}
