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

package org.waveprotocol.box.server.waveletstate.segment;

import org.waveprotocol.box.server.persistence.blocks.impl.SegmentCache;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.waveletstate.WaveletStateTestBase;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.box.server.waveletstate.block.BlockWaveletState;
import org.waveprotocol.box.server.waveletstate.delta.DeltaWaveletState;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;

import static junit.framework.Assert.assertEquals;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class SegmentSnapshotTest extends WaveletStateTestBase {
  private SegmentCache segmentCache;
  private DeltaWaveletState deltaState;
  private BlockWaveletState blockState;
  private SegmentWaveletStateImpl state;

  @Override
  public void setUp() throws Exception {
    init();
    segmentCache = new SegmentCache();
    deltaState = waveletStateFactory.createDeltaWaveletState(WAVELET_NAME);
    deltaState.open();
    blockState = waveletStateFactory.createBlockWaveletState(WAVELET_NAME);
    blockState.open();
    state = new SegmentWaveletStateImpl(segmentCache, WAVELET_NAME, blockState);
    state.open();
  }

  public void testSnapshotMetadataReflectsDeltas() throws Exception {
    HashedVersion v2 = d1.getResultingVersion();
    appendDeltas(d1);

    assertEquals(v2.getVersion(), state.getLastModifiedVersion().getVersion());
    ReadableWaveletData snapshot = state.getSnapshot();
    assertEquals(AUTHOR, snapshot.getCreator());
    assertEquals(TS, snapshot.getCreationTime());
    assertEquals(TS, snapshot.getLastModifiedTime());
    assertEquals(2, snapshot.getVersion());

    HashedVersion v4 = d2.getResultingVersion();
    appendDeltas(d2);

    assertEquals(v4.getVersion(), state.getLastModifiedVersion().getVersion());
    snapshot = state.getSnapshot();
    assertEquals(4, snapshot.getVersion());

    snapshot = state.getSnapshot(v2);
    assertEquals(AUTHOR, snapshot.getCreator());
    assertEquals(TS, snapshot.getCreationTime());
    assertEquals(TS, snapshot.getLastModifiedTime());
    assertEquals(2, snapshot.getVersion());
  }

  /**
   * Applies a delta to the target.
   */
  private void appendDeltas(WaveletDeltaRecord... deltas) throws WaveletStateException {
    for (WaveletDeltaRecord delta : deltas) {
      state.appendDelta(delta);
      deltaState.appendDelta(delta);
    }
  }
}
