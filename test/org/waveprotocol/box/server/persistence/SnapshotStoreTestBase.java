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

package org.waveprotocol.box.server.persistence;

import junit.framework.TestCase;
import org.waveprotocol.box.server.persistence.SnapshotStore.SnapshotAccess;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.WaveletData;

/**
 */
public abstract class SnapshotStoreTestBase extends TestCase {
  private final WaveletName WAVE1_WAVELET1 =
    WaveletName.of(WaveId.of("example.com", "wave1"), WaveletId.of("example.com", "wavelet1"));
  private final WaveletName WAVE2_WAVELET1 =
    WaveletName.of(WaveId.of("example.com", "wave2"), WaveletId.of("example.com", "wavelet1"));
  private final ParticipantId USER = ParticipantId.ofUnsafe("user@example.com");
  private static final HashedVersion V1 = HashedVersion.unsigned(1L);
  private static final HashedVersion V2 = HashedVersion.unsigned(2L);
  private static final HashedVersion V3 = HashedVersion.unsigned(3L);

  /** Create and return a new delta store instance of the type being tested. */
  protected abstract SnapshotStore newSnapshotStore() throws Exception;

  public void testInitialSnapshotStore() throws Exception {
    SnapshotStore store = newSnapshotStore();

    SnapshotAccess wavelet = store.open(WAVE1_WAVELET1);

    WaveletData snapshot = newSnapshot(WAVE1_WAVELET1, V1);
    wavelet.writeInitialSnapshot(snapshot);

    WaveletData readSnapshot = wavelet.readInitialSnapshot();
    assertEquals(snapshot.getVersion(), readSnapshot.getVersion());

    wavelet.close();
  }

  public void testSnapshotHistoryStore() throws Exception {
    SnapshotStore store = newSnapshotStore();

    SnapshotAccess wavelet = store.open(WAVE1_WAVELET1);

    WaveletData snapshot = newSnapshot(WAVE1_WAVELET1, V1);
    wavelet.writeSnapshotToHistory(snapshot);

    WaveletData readSnapshot = wavelet.readNearestSnapshot(1);
    assertEquals(snapshot.getVersion(), readSnapshot.getVersion());

    readSnapshot = wavelet.readNearestSnapshot(2);
    assertEquals(snapshot.getVersion(), readSnapshot.getVersion());

    WaveletData snapshot1 = newSnapshot(WAVE1_WAVELET1, V2);
    wavelet.writeSnapshotToHistory(snapshot1);

    WaveletData snapshot2 = newSnapshot(WAVE1_WAVELET1, V3);
    wavelet.writeSnapshotToHistory(snapshot2);

    readSnapshot = wavelet.readNearestSnapshot(2);
    assertEquals(snapshot1.getVersion(), readSnapshot.getVersion());

    wavelet.close();
  }

  private WaveletData newSnapshot(WaveletName name, HashedVersion version) throws WaveServerException,
      OperationException {
    return WaveletDataUtil.createEmptyWavelet(name, USER, version, 1234567890L);
  }
}
