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

package org.waveprotocol.box.server.persistence.deltas;

import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.deltas.DeltaStore.DeltaAccess;
import org.waveprotocol.wave.model.wave.data.WaveletData;

/**
 * Wavelet snapshot writer.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public interface WaveletSnapshotWriter {
  /**
   * Blocking call to write current snapshot.
   *
   * @param snapshot
   * @throws PersistenceException
   */
  void writeInitialSnapshot(WaveletData snapshot) throws PersistenceException;

  /**
   * Blocking call to append snapshot to snapshot history.
   *
   * @param snapshot
   * @throws PersistenceException
   */
  void writeSnapshotToHistory(WaveletData snapshot) throws PersistenceException;

  /**
   * Remakes the snapshots history.
   *
   * @param savingSnapshotPeriod interval in operations for snapshot saving.
   * @throws PersistenceException
   */
  void remakeSnapshotsHistory(DeltaAccess deltasAccess, int savingSnapshotPeriod) throws PersistenceException;
}
