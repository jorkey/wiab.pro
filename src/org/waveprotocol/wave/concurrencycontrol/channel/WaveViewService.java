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
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.raw.RawFragment;
import org.waveprotocol.wave.clientserver.ReturnStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A service that provides a model object-based interface to the Wave RPC service.
 * Implementations should convert the model objects passed in to the appropriate
 * serializable classes and send them across the wire.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public interface WaveViewService {

  /**
   * Streaming callback for an open wave view connection to the server.
   */
  interface FetchWaveCallback {
    /**
     * Called when wavelets arrive.
     */
    public void onSuccess(WaveViewData waveView);

    /**
     * Called when the task fails.
     *
     * @param reason failure reason for this callback
     */
    public void onFailure(ReturnStatus status);
  }

  interface FetchFragmentsCallback {
    /**
     * Called when blips arrive.
     */
    public void onSuccess(Map<SegmentId, RawFragment> rawFragment);

    /**
     * Called when the task fails.
     */
    public void onFailure(ReturnStatus status);
  }

  /**
   * Callback for updates from from the server.
   */
  interface OpenChannelStreamCallback {
    /**
     * Called when channel is opens.
     */
    void onWaveletOpen(String channelId, HashedVersion connectVersion, HashedVersion lastModifiedVersion,
        long lastModifiedTime, HashedVersion lastCommittedVersion, HashedVersion unacknowledgedDeltaVersion,
        Map<SegmentId, RawFragment> rawFragment);

    /**
     * Called when update is arrives.
     */
    void onUpdate(List<TransformedWaveletDelta> deltas, HashedVersion commitedVersion);

    /**
     * Called channel is closed.
     */
    void onFailure(ReturnStatus status);
  }

  /**
   * Callback for submitting a delta to the server.
   */
  interface SubmitCallback {
    void onResponse(int opsApplied, HashedVersion version, long timestampAfterApplication,
        ReturnStatus status);

    void onFailure(ReturnStatus status);
  }

  /**
   * Callback for closing a connection to the server.
   */
  interface CloseCallback {
    void onSuccess();

    void onFailure(ReturnStatus status);
  }

  /**
   * Fetches wave view.
   */
  void viewFetchWave(IdFilter waveletFilter, boolean fromLastRead, 
    int minBlipReplySize, int maxBlipReplySize, int maxBlipCount, FetchWaveCallback callback);

  /**
   * Fetches fragments.
   */
  void viewFetchFragments(WaveletId waveletId, Map<SegmentId, Long> segments, long endVersion,
    int minReplySize, int maxReplySize, FetchFragmentsCallback calllback);

  /**
   * Opens a wavelet connection to the server.
   */
  void viewOpenWaveletChannel(WaveletId waveletId, Set<SegmentId> segmentIds, List<HashedVersion> knownVersions,
      WaveletDelta unacknowledgedDelta, OpenChannelStreamCallback listener);

  /**
   * Submits a delta to the server. On success, the server replies with
   * the latest version, the number of operations applied, and an error message
   * and/or response code.
   *
   * @return the request id that can be passed later into
   *     {@link #debugGetProfilingInfo(String)}
   */
  void viewSubmit(String channelId, WaveletDelta delta, SubmitCallback callback);

  /**
   * Closes a channel.
   */
  void viewChannelClose(String channelId, CloseCallback callback);
  
  /**
   * Shutdowns view. Suppresses all replies from this point.
   */
  void viewShutdown();
}
