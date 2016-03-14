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
import org.waveprotocol.wave.clientserver.ReturnStatus;
import org.waveprotocol.wave.model.version.HashedVersion;

/**
 * Receives the result of submitting a delta.
 *
 * @author anorth@google.com (Alex North)
 */
public interface SubmitCallback {
  /**
   * Called when submission succeeds (an acknowledgment).
   *
   * @param opsApplied number of operations applied by the server
   * @param newVersion new server version
   * @param timestampAfterApplication timestamp on the server
   * @param returnStatus status of the request
   * @throws ChannelException if the channel fails when processing the ack
   */
  void onResponse(int opsApplied, HashedVersion newVersion, long timestampAfterApplication,
      ReturnStatus returnStatus) throws ChannelException;
  
  /**
   * Called when submission fails due to network or channel failure.
   *
   * @param reason indicates reason for failure
   * @throws ChannelException if the channel fails when processing the failure
   */
  void onFailure(ReturnStatus responseStatus) throws ChannelException;
}
