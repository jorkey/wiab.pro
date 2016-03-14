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

import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.waveserver.WaveletProvider.SubmitRequestCallback;

import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.raw.RawFragment;
import org.waveprotocol.wave.clientserver.ReturnCode;

import org.waveprotocol.box.server.persistence.blocks.VersionRange;

import java.util.List;
import java.util.Map;

/**
 * The client front-end handles requests from clients and directs them to
 * appropriate back-ends.
 *
 * Provides updates for wavelets that a client has opened and access to.
 *
 * @author unknown
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public interface ClientFrontend {

  /**
   * Receives the result of a fetch wave request.
   */
  interface FetchWaveViewRequestCallback {
    /**
     * Notifies the listener that fetch of wavelet is finished.
     */
    void onWaveletSuccess(WaveletId waveletId, long lastModifyTime, HashedVersion lastModifyVersion, 
      Map<SegmentId, RawFragment> fragments);

    /**
     * Notifies the listener that fetch of wave is finished.
     */
    void onFinish();

    /**
     * Notifies the listener that fetch failed.
     */
    void onFailure(ReturnCode responseCode, String errorMessage);
  }

  /**
   * Receives the result of a fetch fragments request.
   */
  interface FetchFragmentsRequestCallback {
    /**
     * Notifies the listener that fetch is finished.
     */
    void onSuccess(Map<SegmentId, RawFragment> fragments);

    /**
     * Notifies the listener that fetch is failed.
     */
    void onFailure(ReturnCode returnCode, String errorMessage);
  }

  /**
   * Receives the result of a open channel request.
   */
  interface OpenChannelRequestCallback {
    /**
     * Notifies the listener that the channel is opened.
     *
     * @param channelId assigned channel id.
     * @param connectVersion the begin version.
     * @param fragments differences to current version.
     * @param lastModifyVersion the last modify wavelet version.
     * @param lastModifyTime the last modify time of wavelet.
     * @param lastCommittedVersion the last committed version.
     * @param unacknowledgedDeltaVersion unacknowledged client delta is submitted.
     */
    void onSuccess(String channelId, Map<SegmentId, RawFragment> fragments,
      HashedVersion connectVersion, HashedVersion lastModifyVersion, long lastModifyTime,
      HashedVersion lastCommittedVersion, HashedVersion unacknowledgedDeltaVersion);

    /**
     * Notifies the listener that the delta failed to apply.
     */
    void onFailure(ReturnCode responseCode, String errorMessage);
  }

  /**
   * Listener provided to update requests.
   */
  interface UpdateChannelListener {
    /**
     * Called when an update delta is received.
     *
     * @param deltas received delta.
     * @param committedVersion optional commit notice.
     */
    void onUpdate(DeltaSequence deltas, HashedVersion committedVersion);

    /**
     * Called when the stream fails. No further updates will be received.
     */
    void onTerminate(ReturnCode responseCode, String errorMessage);
  }

  /**
   * Request to fetch a Wave. Optional waveletIdPrefixes allows the requester to
   * constrain which wavelets to include in the updates.
   *
   * @param loggedInUser which is doing the requesting.
   * @param waveId the wave id.
   * @param waveletIdFilter filter over wavelets to open.
   * @param fromLastRead open from last read state with diffs to current state.
   * @param minBlipReplySize minimum size of reply.
   * @param maxBlipReplySize maximum size of reply.
   * @param maxBlipCount maximum count of blips in the reply.
   * @param connectionId websocket connection id.
   * @param listener callback to send wavelets.
   */
  void fetchWaveViewRequest(ParticipantId loggedInUser, WaveId waveId, IdFilter waveletIdFilter,
      boolean fromLastRead, int minBlipReplySize, int maxBlipReplySize, int maxBlipCount, String connectionId,
      FetchWaveViewRequestCallback listener);

  /**
   * Request to fetch the blips.
   *
   * @param loggedInUser which is doing the requesting.
   * @param waveletName wavelet to fetch.
   * @param ranges version ranges of segments to fetch.
   * @param minReplySize minimum size of reply.
   * @param maxReplySize maximum size of reply.
   * @param connectionId websocket connection id.
   * @param callback callback to send fragments.
   */
  void fetchFragmentsRequest(ParticipantId loggedInUser, WaveletName waveletName,
    Map<SegmentId, VersionRange> ranges, int minReplySize, int maxReplySize, String connectionId, 
    FetchFragmentsRequestCallback callback);

  /**
   * Request the opening a new channel.
   *
   * @param loggedInUser which is doing the requesting.
   * @param waveletName wavelet to open.
   * @param segmentIds Id of segments uploaded on client.
   * @param knownVersions known versions.
   * @param unacknowledgedDelta unacknowledgedDelta.
   * @param connectionId websocket connection id.
   * @param openCallback callback to pass reply.
   * @param updateListener callback to send updates.
   */
  void openRequest(ParticipantId loggedInUser, WaveletName waveletName,
    List<SegmentId> segmentIds, List<HashedVersion> knownVersions,
    ProtocolWaveletDelta unacknowledgedDelta, String connectionId, OpenChannelRequestCallback openCallback,
    UpdateChannelListener updateListener);

  /**
   * Request closure of the channel.
   *
   * @param loggedInUser which is doing the requesting.
   * @param channelId channel id to close.
   */
  void closeRequest(ParticipantId loggedInUser, String channelId);

  /**
   * Request submission of a delta.
   *
   * @param loggedInUser which is doing the requesting.
   * @param channelId the client's channel ID.
   * @param delta the wavelet delta to submit.
   * @param listener callback for the result.
   */
  void submitRequest(ParticipantId loggedInUser, String channelId, ProtocolWaveletDelta delta,
      SubmitRequestCallback listener);

  /**
   * Websocket connection closed.
   *
   * @param connectionId the id of closed connection.
   * @param loggedInUser logged user in this connection.
   */
  void disconnect(ParticipantId loggedInUser, String connectionId);
}
