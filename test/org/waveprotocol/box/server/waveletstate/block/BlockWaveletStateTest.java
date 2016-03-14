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

package org.waveprotocol.box.server.waveletstate.block;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;

import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.persistence.FileNotFoundPersistenceException;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.blocks.Block;
import org.waveprotocol.box.server.persistence.blocks.BlockStore;
import org.waveprotocol.box.server.persistence.blocks.BlockStore.BlockAccess;
import org.waveprotocol.box.server.persistence.blocks.impl.BlockIndexImpl;
import org.waveprotocol.box.server.waveletstate.WaveletStateTestBase;
import org.waveprotocol.box.server.persistence.blocks.BlockIndex;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class BlockWaveletStateTest extends WaveletStateTestBase {

  private static final String BLOCK_ID = "b123";

  class BlockStoreStub implements BlockStore {

    @Override
    public BlockAccess open(WaveletName waveletName) throws PersistenceException {
      return access;
    }

    @Override
    public void delete(WaveletName waveletName) throws PersistenceException, FileNotFoundPersistenceException {
      throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableSet<WaveletId> lookup(WaveId waveId) throws PersistenceException {
      throw new UnsupportedOperationException();
    }

    @Override
    public ExceptionalIterator<WaveId, PersistenceException> getWaveIdIterator() throws PersistenceException {
      throw new UnsupportedOperationException();
    }
  }

  static class BlockWaveletAccessStub implements BlockAccess {

    private final BlockIndex blockIndex = new BlockIndexImpl();
    private final List<Block> blocksList = new LinkedList<>();
    private int readLastModfiedIndexCount = 0;
    private int readBlocksCount = 0;

    @Override
    public WaveletName getWaveletName() {
      return WAVELET_NAME;
    }

    @Override
    public void close() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public BlockIndex readBlockIndex() throws PersistenceException {
      readLastModfiedIndexCount++;
      return blockIndex;
    }

    @Override
    public Collection<Block> readBlocks(Set<String> blockIds) throws PersistenceException {
      readBlocksCount++;
      return getBlocks();
    }

    @Override
    public void writeBlock(Block block) throws PersistenceException {
    }

    @Override
    public void writeBlockIndex(BlockIndex segmentsIndex) throws PersistenceException {
    }

    public synchronized void putBlocks(List<Block> blocks) {
      blocksList.addAll(blocks);
      notify();
    }

    public int getReadLastModfiedBlocksCount() {
      return readLastModfiedIndexCount;
    }

    public int getReadBlocksCount() {
      return readBlocksCount;
    }

    private synchronized List<Block> getBlocks() {
      while (blocksList.isEmpty()) {
        try {
          wait();
        } catch (InterruptedException ex) {
          throw new RuntimeException(ex);
        }
      }
      List<Block> list = new ArrayList<>(blocksList);
      return list;
    }
  }

  private BlockWaveletState state;
  private BlockStoreStub store;
  private BlockWaveletAccessStub access;

  @Override
  public void setUp() throws Exception {
    init();

    store = new BlockStoreStub();
    access = new BlockWaveletAccessStub();

    state = new BlockWaveletStateImpl(store, Executors.newScheduledThreadPool(1),
      injector.getInstance(IdGenerator.class), WAVELET_NAME);
    putBlock(makeBlock());
    state.open();
  }

  public void testSecondRequestDoesNotGetsNewBlock() throws Exception {
    ListenableFuture<Map<String, Block>> blockFuture = state.readBlocks(Sets.newHashSet(BLOCK_ID));

    putBlock(makeBlock());

    ListenableFuture<Map<String, Block>> blockFuture1 = state.readBlocks(Sets.newHashSet(BLOCK_ID));

    Map<String, Block> blocks = blockFuture.get();
    Map<String, Block> blocks1 = blockFuture1.get();

    assertSame(blocks.get(BLOCK_ID), blocks1.get(BLOCK_ID));
  }

  public void testSimultaneousBlocksReadingOfSingleBlock() throws Exception {
    ListenableFuture<Map<String, Block>> blockFuture = state.readBlocks(Sets.newHashSet(BLOCK_ID));
    ListenableFuture<Map<String, Block>> blockFuture1 = state.readBlocks(Sets.newHashSet(BLOCK_ID));

    putBlock(makeBlock());

    Map<String, Block> blocks = blockFuture.get();
    Map<String, Block> blocks1 = blockFuture1.get();

    assertSame(blocks.get(BLOCK_ID), blocks1.get(BLOCK_ID));
  }

  public void testNewFragmentDoesNotOpenedInLowWaterBlock() throws Exception {
    Block block = makeBlock();
    when(block.getSize()).thenReturn(Block.LOW_WATER);
    putBlock(block);

    assertTrue(block != state.getOrCreateBlockToWriteNewFragment());
  }

  private void putBlock(Block block) {
    List<Block> blocks = new LinkedList<>();
    blocks.add(block);
    access.putBlocks(blocks);
  }

  private static Block makeBlock() {
    Block block = mock(Block.class);
    when(block.getBlockId()).thenReturn(BLOCK_ID);
    when(block.getLastModifiedVersion()).thenReturn(0L);
    return block;
  }
}
