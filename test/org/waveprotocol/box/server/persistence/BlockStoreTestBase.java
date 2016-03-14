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

import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.persistence.blocks.BlockStore;
import org.waveprotocol.box.server.persistence.blocks.BlockStore.BlockAccess;
import org.waveprotocol.box.server.persistence.blocks.ReadableBlock;
import org.waveprotocol.box.server.persistence.blocks.Block;
import org.waveprotocol.box.server.persistence.blocks.Fragment;
import org.waveprotocol.box.server.persistence.blocks.VersionNode;
import org.waveprotocol.box.server.persistence.blocks.impl.VersionNodeImpl;
import org.waveprotocol.box.server.persistence.blocks.impl.BlockImpl;
import org.waveprotocol.box.server.persistence.blocks.Segment;
import org.waveprotocol.box.server.persistence.blocks.impl.SegmentOperationImpl;
import org.waveprotocol.box.server.persistence.blocks.impl.VersionInfoImpl;

import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.operation.wave.AddParticipant;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.NoSuchElementException;
import junit.framework.TestCase;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.waveprotocol.wave.model.util.CollectionUtils;

/**
 * Tests for all BlockStore implementations.
 *
 * @author A. Kaplanov (akaplanov@gmail.com)
 */
public abstract class BlockStoreTestBase extends TestCase {
  private static final WaveletName WAVE1_WAVELET1 =
    WaveletName.of(WaveId.of("example.com", "wave1"), WaveletId.of("example.com", "wavelet1"));
  private static final WaveletName WAVE2_WAVELET1 =
    WaveletName.of(WaveId.of("example.com", "wave2"), WaveletId.of("example.com", "wavelet1"));
  private static final ParticipantId USER = ParticipantId.ofUnsafe("user@example.com");

  private static final String BLOCK_ID1 = "block1";
  private static final String BLOCK_ID2 = "block2";
  private static final String BLOCK_ID3 = "block3";
  private static final String BLOCK_ID4 = "block4";
  private static final String BLOCK_ID5 = "block5";

  private static final HashedVersion V0 = HashedVersion.unsigned(0L);
  private static final HashedVersion V1 = HashedVersion.unsigned(1L);
  private static final HashedVersion V2 = HashedVersion.unsigned(2L);
  private static final HashedVersion V3 = HashedVersion.unsigned(3L);
  private static final HashedVersion V4 = HashedVersion.unsigned(4L);
  private static final HashedVersion V5 = HashedVersion.unsigned(5L);

  /** Create and return a new block store instance of the type being tested. */
  protected abstract BlockStore newBlockStore() throws Exception;

  public void testOpenNonexistantWavelet() throws Exception {
    BlockStore store = newBlockStore();
    BlockAccess wavelet = store.open(WAVE1_WAVELET1);

    // Sanity check a bunch of values in the wavelet.
    assertEquals(WAVE1_WAVELET1, wavelet.getWaveletName());

    wavelet.close();
  }

  public void testWriteToNewWavelet() throws Exception {
    Pair<BlockStore, ReadableBlock> pair = newBlockStoreWithBlock(WAVE1_WAVELET1);
    BlockStore store = pair.first;
    ReadableBlock block = pair.second;

    BlockAccess wavelet = store.open(WAVE1_WAVELET1);

    assertEquals(WAVE1_WAVELET1, wavelet.getWaveletName());
    Collection<Block> blocks = wavelet.readBlocks(CollectionUtils.newHashSet(BLOCK_ID1));
    Block block1 = blocks.iterator().next();
    assertEquals(block.getBlockId(), block1.getBlockId());

    wavelet.close();
  }

  public void testDeleteWaveletRemovesBlocks() throws Exception {
    Pair<BlockStore, ReadableBlock> pair = newBlockStoreWithBlock(WAVE1_WAVELET1);
    BlockStore store = pair.first;
    ReadableBlock block = pair.second;

    store.delete(WAVE1_WAVELET1);
    BlockAccess wavelet = store.open(WAVE1_WAVELET1);
    boolean throwed = false;
    try {
      wavelet.readBlocks(CollectionUtils.newHashSet(BLOCK_ID1));
    } catch (PersistenceException ex) {
      throwed = true;
    }
    assertTrue(throwed);
    wavelet.close();
  }

  public void testLookupReturnsWavelets() throws Exception {
    Pair<BlockStore, ReadableBlock> pair = newBlockStoreWithBlock(WAVE1_WAVELET1);
    BlockStore store = pair.first;

    assertEquals(ImmutableSet.of(WAVE1_WAVELET1.waveletId), store.lookup(WAVE1_WAVELET1.waveId));
  }

  public void testLookupDoesNotReturnDeletedWavelets() throws Exception {
    Pair<BlockStore, ReadableBlock> pair = newBlockStoreWithBlock(WAVE1_WAVELET1);
    BlockStore store = pair.first;
    store.delete(WAVE1_WAVELET1);

    assertTrue(store.lookup(WAVE1_WAVELET1.waveId).isEmpty());
  }

  public void testWaveIdIteratorReturnsWaveIds() throws Exception {
    Pair<BlockStore, ReadableBlock> pair = newBlockStoreWithBlock(WAVE1_WAVELET1);
    BlockStore store = pair.first;

    ImmutableSet<WaveId> waveIds = setFromExceptionalIterator(store.getWaveIdIterator());

    assertEquals(ImmutableSet.of(WAVE1_WAVELET1.waveId), waveIds);
  }

  public void testWaveIdIteratorDoesNotReturnDeletedWavelets() throws Exception {
    Pair<BlockStore, ReadableBlock> pair = newBlockStoreWithBlock(WAVE1_WAVELET1);
    BlockStore store = pair.first;
    store.delete(WAVE1_WAVELET1);

    assertFalse(store.getWaveIdIterator().hasNext());
  }

  public void testWaveIdIteratorLimits() throws Exception {
    Pair<BlockStore, ReadableBlock> pair = newBlockStoreWithBlock(WAVE1_WAVELET1);
    BlockStore store = pair.first;
    BlockAccess wavelet = store.open(WAVE2_WAVELET1);

    Block block = BlockImpl.create(BLOCK_ID1);
    WaveletOperationContext context = new WaveletOperationContext(USER, 1234, 1, V1);
    block.writeSegmentOperation(new SegmentOperationImpl(new AddParticipant(context, USER)));
    wavelet.writeBlock(block);
    wavelet.close();

    ExceptionalIterator<WaveId, PersistenceException> iterator = store.getWaveIdIterator();
    assertTrue(iterator.hasNext());

    WaveId waveId1 = iterator.next();
    assertTrue(iterator.hasNext());

    WaveId waveId2 = iterator.next();

    // This is necessary because the order of waveIds is implementation specific.
    if (WAVE1_WAVELET1.waveId.equals(waveId1)) {
      assertEquals(WAVE2_WAVELET1.waveId, waveId2);
    } else {
      assertEquals(WAVE2_WAVELET1.waveId, waveId1);
      assertEquals(WAVE1_WAVELET1.waveId, waveId2);
    }

    assertFalse(iterator.hasNext());
    try {
      iterator.next();
      // Fail the test, it should have thrown an exception.
      fail();
    } catch (NoSuchElementException e) {
      // Test passes.
    }
  }

  public void testReadBlocks() throws Exception {
    Pair<BlockStore, ReadableBlock> pair = newBlockStoreWithBlock(WAVE1_WAVELET1);
    BlockStore store = pair.first;
    ReadableBlock block1 = pair.second;

    BlockAccess wavelet = store.open(WAVE1_WAVELET1);

    writeBlock(wavelet, BLOCK_ID2, V1, V2, true);
    writeBlock(wavelet, BLOCK_ID3, V2, V3, true);

    Collection<Block> blocks = wavelet.readBlocks(CollectionUtils.newHashSet(BLOCK_ID1, BLOCK_ID2, BLOCK_ID3));
    assertEquals(3, blocks.size());
    assertTrue(doesContainBlock(blocks, BLOCK_ID1));
    assertTrue(doesContainBlock(blocks, BLOCK_ID2));
    assertTrue(doesContainBlock(blocks, BLOCK_ID3));

    writeBlock(wavelet, BLOCK_ID4, V3, V4, true);
    writeBlock(wavelet, BLOCK_ID5, V4, V5, true);

    blocks = wavelet.readBlocks(CollectionUtils.newHashSet(BLOCK_ID4, BLOCK_ID5));
    assertEquals(2, blocks.size());
    assertTrue(doesContainBlock(blocks, BLOCK_ID4));
    assertTrue(doesContainBlock(blocks, BLOCK_ID5));

    wavelet.close();
  }

  // *** Helpers

  private Pair<BlockStore, ReadableBlock> newBlockStoreWithBlock(WaveletName waveletName)
      throws Exception {
    BlockStore store = newBlockStore();
    BlockAccess wavelet = store.open(waveletName);

    Block block = writeBlock(wavelet, BLOCK_ID1, V0, V1, true);
    wavelet.close();

    return new Pair<BlockStore, ReadableBlock>(store, block);
  }

  private boolean doesContainBlock(Collection<Block> blocks, String blockId) {
    for (Block block : blocks) {
      if (block.getBlockId().equals(blockId)) {
        return true;
      }
    }
    return false;
  }

  private static Block writeBlock(BlockAccess wavelet, String blockId,
      HashedVersion startVersion, HashedVersion endVersion, boolean finish) throws PersistenceException {
    Block block = BlockImpl.create(blockId);
    WaveletOperationContext context = new WaveletOperationContext(USER, 1234, endVersion.getVersion(), endVersion);
    block.writeSegmentOperation(new SegmentOperationImpl(new AddParticipant(context, USER)));
    Fragment fragment = block.createFragment(SegmentId.PARTICIPANTS_ID, false);
    Segment segment = mock(Segment.class);
    when(segment.getSegmentId()).thenReturn(SegmentId.PARTICIPANTS_ID);
    fragment.setSegment(segment);
    VersionNode node = new VersionNodeImpl();
    node.setVersionInfo(new VersionInfoImpl(startVersion.getVersion(), USER, 0));
    fragment.writeVersionNode(node);
    node = new VersionNodeImpl();
    node.setVersionInfo(new VersionInfoImpl(endVersion.getVersion(), USER, 0));
    fragment.writeVersionNode(node);
    if (finish) {
      fragment.finish(node.getVersion());
    }
    wavelet.writeBlock(block);
    return block;
  }

  private static <T, E extends Exception> ImmutableSet<T> setFromExceptionalIterator(
      ExceptionalIterator<T, E> iterator) throws E {
    ImmutableSet.Builder<T> builder = ImmutableSet.builder();
    while(iterator.hasNext()) {
      builder.add(iterator.next());
    }
    return builder.build();
  }
}
