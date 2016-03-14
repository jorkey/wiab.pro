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

import org.waveprotocol.wave.concurrencycontrol.common.ChannelException;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.version.HashedVersion;

import java.util.List;

/**
 * A raw channel for messages on a single wavelet.
 *
 * @author anorth@google.com (Alex North)
 */
public interface WaveletChannel {

  /**
   * Receiver for wavelet updates.
   */
  interface Listener {
    /**
     * Called when channel is connected.
     *
     * @param connectVersion the wavelet version at which the channel connection
     * @param lastModifiedVersion the most recent wavelet version at the time of
     *        connection.
     * @param lastCommittedVersion the last committed version.
     * @param unacknowledgedDeltaVersion unacknowledged delta is submitted.
     * @param operations the changes in uploaded fragments since connect version.
     * @throws ChannelException if the channel fails
     */
    public void onConnection(HashedVersion connectVersion, HashedVersion lastModifiedVersion, 
      HashedVersion lastCommittedVersion, HashedVersion unacknowledgedDeltaVersion, 
      List<WaveletOperation> operations) throws ChannelException;

    /**
     * Called when a stream update is received. At least one of {@code deltas} and
     * {@code lastCommittedVersion} will be non-null or non-empty. {@code
     * currentSignedVersion} is non-null only in the first message when a stream
     * is reconnecting.
     *
     * @param deltas optional updates
     * @param lastCommittedVersion optional committed version (may be null)
     * @throw ChannelException if the channel fails in processing the update
     */
    public void onWaveletUpdate(List<TransformedWaveletDelta> deltas, HashedVersion lastCommittedVersion)
        throws ChannelException;
  }

  /**
   * Submits a delta on this channel.
   *
   * @param delta delta to submit
   * @param callback invoked with the submission response
   */
  void submit(WaveletDelta delta, SubmitCallback callback);
}
