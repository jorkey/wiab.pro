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

import org.waveprotocol.wave.model.wave.data.WaveletData;

import java.io.IOException;

/**
 * Wavelet snapshot reader.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public interface WaveletSnapshotReader {
  /**
   * Reads initial snapshot.
   *
   * @return initial snapshot of wavelet.
   */
  WaveletData readInitialSnapshot() throws IOException;

  /**
   * Reads nearest snapshot to specified wavelet version.
   *
   * @return snapshot of wavelet with specified or less version.
   */
  WaveletData readNearestSnapshot(long version) throws IOException;

  /**
   * Gets last version of snapshot in history.
   *
   * @return version
   */
  long getLastHistorySnapshotVersion();
}
