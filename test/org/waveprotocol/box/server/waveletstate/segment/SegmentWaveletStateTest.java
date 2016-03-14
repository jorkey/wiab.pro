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

import com.google.common.collect.ImmutableMap;
import org.waveprotocol.box.server.persistence.blocks.impl.SegmentCache;
import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.BlockIndex;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.box.server.persistence.blocks.impl.BlockIndexImpl;
import org.waveprotocol.box.server.waveletstate.WaveletStateTestBase;

import org.waveprotocol.wave.model.id.SegmentId;

import java.util.Map;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.waveprotocol.box.server.persistence.blocks.Block;
import org.waveprotocol.box.server.persistence.blocks.Fragment;
import org.waveprotocol.box.server.persistence.blocks.Segment;
import org.waveprotocol.box.server.waveletstate.delta.DeltaWaveletState;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class SegmentWaveletStateTest extends WaveletStateTestBase {
  private static final String BLOCK_ID1 = "block1";
  private static final String BLOCK_ID2 = "block2";

  private static final SegmentId SEGMENT_ID1 = SegmentId.ofBlipId("segment1");
  private static final SegmentId SEGMENT_ID2 = SegmentId.ofBlipId("segment2");

  private SegmentCache segmentCache;
  private BlockIndex segmentIndex;
  private DeltaWaveletState deltaState;
  private BlockWaveletStateStub blockState;
  private SegmentWaveletStateImpl state;

  @Override
  public void setUp() throws Exception {
    init();
    segmentCache = new SegmentCache();
    segmentIndex = new BlockIndexImpl();
    deltaState = waveletStateFactory.createDeltaWaveletState(WAVELET_NAME);
    deltaState.open();
    blockState = new BlockWaveletStateStub(segmentIndex);
    state = new SegmentWaveletStateImpl(segmentCache, WAVELET_NAME, blockState);
    state.open();
  }

  public void testReportsWaveletName() {
    assertEquals(WAVELET_NAME, state.getWaveletName());
  }

  public void testEmptyStateIsEmpty() throws Exception {
    assertNull(state.getSnapshot());
    assertEquals(0, state.getLastModifiedVersion().getVersion());
  }

  public void testGettingOfIntervalFromOneBlock() throws Exception {
    BlockStub block = new BlockStub(BLOCK_ID1, 0, 1, true);
    segmentIndex.update(createFragment(block, SEGMENT_ID1, 0, 1, true));
    blockState.addBlock(block);
    Map<SegmentId, Interval> intervals =
        state.getIntervals(ImmutableMap.<SegmentId, VersionRange>builder()
          .put(SEGMENT_ID1, VersionRange.of(0, Long.MAX_VALUE)).build(), false);

    assertEquals(1, intervals.size());
    Interval interval = intervals.values().iterator().next();
    assertEquals(0, interval.getRange().from());
    assertTrue(interval.getRange().contains(1));
    assertEquals(Long.MAX_VALUE, interval.getRange().to());

    Segment segment = segmentCache.getSegment(WAVELET_NAME, SEGMENT_ID1);
    assertNotNull(segment.getFragment(0));
    assertEquals(segment.getFragment(Long.MAX_VALUE).getLastModifiedVersion(), 1);
  }

  public void testGettingOfIntervalFromTwoBlocks() throws Exception {
    BlockStub block1 = new BlockStub(BLOCK_ID1, 0, 1, false);
    BlockStub block2 = new BlockStub(BLOCK_ID2, 2, 3, true);
    segmentIndex.update(createFragment(block1, SEGMENT_ID1, 0, 1, false));
    segmentIndex.update(createFragment(block2, SEGMENT_ID1, 2, 3, true));
    blockState.addBlock(block1);
    blockState.addBlock(block2);
    Map<SegmentId, Interval> intervals =
        state.getIntervals(ImmutableMap.<SegmentId, VersionRange>builder()
          .put(SEGMENT_ID1, VersionRange.of(0, Long.MAX_VALUE)).build(), false);

    assertEquals(1, intervals.size());
    Interval interval = intervals.values().iterator().next();
    assertEquals(0, interval.getRange().from());
    assertTrue(interval.getRange().contains(1));
    assertTrue(interval.getRange().contains(2));
    assertTrue(interval.getRange().contains(3));
    assertEquals(Long.MAX_VALUE, interval.getRange().to());

    Segment segment = segmentCache.getSegment(WAVELET_NAME, SEGMENT_ID1);
    assertNotNull(segment.getFragment(0));
    assertNotNull(segment.getFragment(2));
    assertEquals(segment.getFragment(Long.MAX_VALUE).getLastModifiedVersion(), 3);
  }

  public void testExceptionWhenFewBlocks() throws Exception {
    BlockStub block = new BlockStub(BLOCK_ID1, 0, 1, false);
    segmentIndex.update(createFragment(block, SEGMENT_ID1, 0, 1, false));
    segmentIndex.update(createFragment(block, SEGMENT_ID1, 2, 1, true));
    blockState.addBlock(block);
    try {
      state.getIntervals(ImmutableMap.<SegmentId, VersionRange>builder()
        .put(SEGMENT_ID1, VersionRange.of(0, Long.MAX_VALUE)).build(), false);
      fail("Exception is not throwed");
    } catch (Exception ex) {
    }
  }

  public void testExceptionWhenIntervalIncomplete() throws Exception {
    BlockStub block = new BlockStub(BLOCK_ID2, 0, 1, false);
    segmentIndex.update(createFragment(block, SEGMENT_ID1, 2, 3, true));
    blockState.addBlock(block);
    try {
      state.getIntervals(ImmutableMap.<SegmentId, VersionRange>builder()
        .put(SEGMENT_ID1, VersionRange.of(0, Long.MAX_VALUE)).build(), false);
      fail("Exception is not throwed");
    } catch (Exception ex) {
    }
  }

  public void testSecondRequestDoesNotGetBlocks() throws Exception {
    BlockStub block = new BlockStub(BLOCK_ID1, 0, 1, true);
    segmentIndex.update(createFragment(block, SEGMENT_ID1, 0, 1, true));
    blockState.addBlock(block);

    state.getIntervals(ImmutableMap.<SegmentId, VersionRange>builder()
      .put(SEGMENT_ID1, VersionRange.of(0, Long.MAX_VALUE)).build(), false);

    int readBlockCalls = blockState.getReadBlockCalls();

    state.getIntervals(ImmutableMap.<SegmentId, VersionRange>builder()
      .put(SEGMENT_ID1, VersionRange.of(0, Long.MAX_VALUE)).build(), false);

    assertEquals(readBlockCalls, blockState.getReadBlockCalls());
  }

  public void testRestoreOfReleasedFragment() throws Exception {
    BlockStub block = new BlockStub(BLOCK_ID1, 0, 1, true);
    segmentIndex.update(createFragment(block, SEGMENT_ID1, 0, 1, true));
    blockState.addBlock(block);

    state.getIntervals(ImmutableMap.<SegmentId, VersionRange>builder()
      .put(SEGMENT_ID1, VersionRange.of(0, Long.MAX_VALUE)).build(), false);

    Segment segment = segmentCache.getSegment(WAVELET_NAME, SEGMENT_ID1);
    assertNotNull(segment.getFragment(0));
    segment.releaseFragment(block.getFragment(SEGMENT_ID1));
    assertNull(segment.getFragment(0));

    state.getIntervals(ImmutableMap.<SegmentId, VersionRange>builder()
      .put(SEGMENT_ID1, VersionRange.of(0, Long.MAX_VALUE)).build(), false);
    assertNotNull(segment.getFragment(0));
  }

  private Fragment createFragment(Block block, SegmentId segmentId, long startVersion, long endVersion, boolean last) {
    FragmentStub fragment = new FragmentStub(block, startVersion, endVersion, last);
    Segment segment = mock(Segment.class);
    when(segment.getSegmentId()).thenReturn(segmentId);
    fragment.setSegment(segment);
    return fragment;
  }
}
