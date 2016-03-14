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

package org.waveprotocol.wave.client.concurrencycontrol;

import org.waveprotocol.wave.concurrencycontrol.channel.OperationChannelMultiplexer;
import org.waveprotocol.wave.clientserver.ReturnStatus;

/**
 * Makes the operation channels in a {@link OperationChannelMultiplexer} go
 * live.
 *
 */
public interface MuxConnector {

  interface Listener {
    /**
     * Notifies that mux is connected.
     */
    void onConnected();

    /**
     * Notifies that connection attempt is failed.
     */
    void onFailed(ReturnStatus status);
  }

  /**
   * Opens the underlying view channel, and connects the live operation channels
   * with those in the mux.
   */
  void connect(Listener streamListener);

  /**
   * Closes the underlying view channel.
   */
  void close();
}
