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

package org.waveprotocol.wave.concurrencycontrol.server;

import org.waveprotocol.box.common.ThrowableReceiver;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.wave.model.version.HashedVersion;

/**
 * This is allows you to access the history of delta as though
 * it's a simple list.
 *
 * @author zdwang@google.com (David Wang)
 */
public interface DeltaHistory {

  /**
   * @return The last signature.
   */
  public HashedVersion getCurrentVersion();

  /**
   * Gets the delta starting at the given version. If there is no such delta, then return null.
   * @param version
   * @return null if no such delta at version.
   */
  public WaveletDeltaRecord getDeltaStartingAt(HashedVersion version);

  /**
   * Gets the transformed deltas from the one applied at the given start version
   * until the one resulting in the given end version or receiver will interrupt.
   */
  void getDeltaHistory(HashedVersion startVersion, HashedVersion endVersion,
      ThrowableReceiver<WaveletDeltaRecord, WaveletStateException> receiver) throws WaveletStateException;

  /**
   * Does the given signature exist in our history.
   *
   * Knowing a signature does not mean we have a
   * delta at that signature. e.g. we don't have a delta for version 0 and
   * the most recent signature.
   *
   * @param version version and hash of wave.
   * @return false if know of no such signature information.
   */
  public boolean hasSignature(HashedVersion version);
}
