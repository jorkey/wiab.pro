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

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.waveprotocol.box.server.executor.ExecutorAnnotations;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.blocks.Block;
import org.waveprotocol.box.server.persistence.blocks.FragmentObserver;
import org.waveprotocol.box.server.persistence.blocks.BlockStore;
import org.waveprotocol.box.server.persistence.blocks.impl.BlockCache;
import org.waveprotocol.box.server.persistence.blocks.BlockIndex;
import org.waveprotocol.box.server.persistence.blocks.ReadableBlockIndex;
import org.waveprotocol.box.server.persistence.blocks.Fragment;
import org.waveprotocol.box.server.persistence.blocks.impl.BlockImpl;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.box.stat.Timed;

import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.util.logging.Log;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

/**
 * Resident state of wavelet block access.
 * Contains blocks cache. Delays writing blocks to store.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class BlockWaveletStateImpl implements BlockWaveletState {
  private static final Log LOG = Log.get(BlockWaveletStateImpl.class);

  /** Timeout between saving of blocks. */
  private static final int SAVING_BLOCKS_PERIOD_MS = 30000;

  /** Block store. */
  private final BlockStore blockStore;

  /** Executor for reading, saving blocks and close store. */
  private final ScheduledExecutorService persistExecutor;

  /** Block Id generator. */
  private final IdGenerator idGenerator;

  /** The name of wavelet. */
  private final WaveletName waveletName;

  /** Blocks waiting to be written. */
  private Set<Block> blocksToWrite = CollectionUtils.newHashSet();

  /** Saving blocks task. */
  private final Callable savingTask = new Callable() {

      @Override
      public Object call() throws Exception {
        try {
          writeWaitingBlocks();
        } catch (Exception ex) {
          LOG.severe("Writing blocks error", ex);
          writingFailed = true;
        }
        return null;
      }
    };

  /** Block cache. */
  private final BlockCache blockCache = new BlockCache();

  /** Block aссess. */
  private BlockStore.BlockAccess blockAccess;

  /** State is closing. */
  private boolean closing;

  /** Block to writing new fragments. */
  private Block blockToCreateNewFragments;

  /** Index of blocks. */
  private BlockIndex blockIndex;

  /** Blocks store consistent with deltas store. */
  private volatile boolean consistent;

  /** Future tasks for executing requests. */
  private final ConcurrentMap<String, SettableFuture<Block>> readBlocksFutures = new ConcurrentHashMap<>();

  /** Locks. */
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
  private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

  private boolean writingFailed = false;

  @Inject
  BlockWaveletStateImpl(BlockStore blockStore,
      @ExecutorAnnotations.BlockPersistExecutor ScheduledExecutorService persistExecutor,
      IdGenerator idGenerator, @Assisted WaveletName waveletName) {
    this.blockStore = blockStore;
    this.persistExecutor = persistExecutor;
    this.idGenerator = idGenerator;
    this.waveletName = waveletName;
  }

  @Timed
  @Override
  public void open() throws WaveletStateException {
    writeLock.lock();
    try {
      Preconditions.checkArgument(blockAccess == null, "Block state already opened");
      blockAccess = blockStore.open(waveletName);
      blockIndex = blockAccess.readBlockIndex();
      consistent = blockIndex.isConsistent();
    } catch (PersistenceException ex) {
      throw new WaveletStateException(ex);
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public ListenableFuture close() {
    writeLock.lock();
    try {
      checkOpened();
      closing = true;
    } finally {
      writeLock.unlock();
    }
    final SettableFuture future = SettableFuture.create();
    flush().addListener(new Runnable() {

      @Override
      public void run() {
        writeLock.lock();
        try {
          Preconditions.checkNotNull(blockAccess, "Store is not opened.");
          writeWaitingBlocks();
          blockAccess.close();
          LOG.info("Block wavelet state of " + waveletName.toString() + " is closed.");
          blockAccess = null;
          closing = false;
          future.set(null);
        } catch (PersistenceException | IOException ex) {
          future.setException(ex);
        } finally {
          writeLock.unlock();
        }
      }
    }, MoreExecutors.sameThreadExecutor());
    return future;
  }

  @Override
  public ListenableFuture flush() {
    checkOpenedOrClosing();
    ListenableFutureTask future = ListenableFutureTask.create(savingTask);
    persistExecutor.execute(future);
    return future;
  }

  @Override
  public Block getOrCreateBlockToWriteNewFragment() {
    writeLock.lock();
    try {
      checkOpened();
      if (blockToCreateNewFragments != null && blockToCreateNewFragments.getSize() < Block.LOW_WATER) {
        return blockToCreateNewFragments;
      }
      Block block = BlockImpl.create(idGenerator.newBlockId());
      LOG.info((blockToCreateNewFragments == null ? "No last block" : "Last block too large") +
        ", created new block " + block.getBlockId() + " for wavelet " + waveletName.toString());
      registerBlock(block);
      return block;
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public void setLastModifiedVersionAndTime(HashedVersion version, long time) {
    blockIndex.setLastModifiedVersionAndTime(version, time);
  }

  @Override
  public ListenableFuture<Map<String, Block>> readBlocks(final Set<String> blockIds) throws WaveletStateException {
    checkOpened();
    final Map<String, Block> blocks = new ConcurrentHashMap<>();
    Set<String> missingBlockIds = CollectionUtils.newHashSet();
    List<ListenableFuture<Block>> blockFutures = CollectionUtils.newLinkedList();
    for (String blockId : blockIds) {
      Block block = blockCache.getBlock(blockId);
      if (block != null) {
        blocks.put(block.getBlockId(), block);
      } else {
        ListenableFuture<Block> blockFuture = readBlocksFutures.get(blockId);
        if (blockFuture != null) {
          blockFutures.add(blockFuture);
        } else {
          missingBlockIds.add(blockId);
        }
      }
    }
    if (!missingBlockIds.isEmpty()) {
      blockFutures.addAll(executeReadBlocksRequest(missingBlockIds));
    }
    final SettableFuture<Map<String, Block>> future = SettableFuture.create();
    if (!blockFutures.isEmpty()) {
      for (final ListenableFuture<Block> blockFuture : blockFutures) {
        blockFuture.addListener(new Runnable() {

          @Override
          public void run() {
            try {
              blocks.put(blockFuture.get().getBlockId(), blockFuture.get());
              if (blocks.keySet().containsAll(blockIds)) {
                future.set(blocks);
              }
            } catch (InterruptedException | ExecutionException ex) {
              future.setException(ex);
            }
          }
        }, MoreExecutors.sameThreadExecutor());
      }
    } else {
      future.set(blocks);
    }
    return future;
  }

  @Override
  public ReadableBlockIndex getSegmentsIndex() throws WaveletStateException {
    return blockIndex;
  }

  @Override
  public WaveletName getWaveletName() {
    return waveletName;
  }

  @Override
  public Pair<HashedVersion, Long> getLastModifiedVersionAndTime() {
    return blockIndex.getLastModifiedVersionAndTime();
  }


  @Override
  public void markAsConsistent() {
    consistent = true;
  }

  @Override
  public void markAsInconsistent() throws WaveletStateException {
    if (consistent) {
      blockIndex.setConsistent(consistent = false);
      try {
        blockAccess.writeBlockIndex(blockIndex);
      } catch (PersistenceException ex) {
        throw new WaveletStateException(ex);
      }
    }
  }

  @Override
  public void clear() throws WaveletStateException {
    LOG.info("Clear block wavelet state");
    writeLock.lock();
    try {
      checkOpened();
      blocksToWrite.clear();
      readBlocksFutures.clear();
      blockToCreateNewFragments = null;
      blockAccess.close();
      blockStore.delete(waveletName);
      blockIndex.clear();
      blockAccess = blockStore.open(waveletName);
    } catch (PersistenceException | IOException ex) {
      throw new WaveletStateException(ex);
    } finally {
      writeLock.unlock();
    }
  }

  @Timed
  void writeWaitingBlocks() throws PersistenceException {
    Set<Block> blocks;
    writeLock.lock();
    try {
      blocks = blocksToWrite;
      blocksToWrite = CollectionUtils.newHashSet();
      if (!blocks.isEmpty()) {
        checkOpenedOrClosing();
        List<Block> blockList = CollectionUtils.newLinkedList(blocks);
        Collections.sort(blockList, new Comparator<Block>(){

          @Override
          public int compare(Block block1, Block block2) {
            return Long.compare(block1.getLastModifiedVersion(), block2.getLastModifiedVersion());
          }
        });
        for (Block block : blockList) {
          blockAccess.writeBlock(block);
        }
      }
      if (!blocks.isEmpty() || blockIndex.isConsistent() != consistent) {
        checkOpenedOrClosing();
        blockIndex.setConsistent(consistent);
        blockAccess.writeBlockIndex(blockIndex);
      }
    } finally {
      writeLock.unlock();
    }
  }

  Collection<ListenableFuture<Block>> executeReadBlocksRequest(final Set<String> blockIds) {
    List<ListenableFuture<Block>> futures = CollectionUtils.newLinkedList();
    for (String blockId : blockIds) {
      SettableFuture<Block> future = SettableFuture.<Block>create();
      futures.add(future);
      readBlocksFutures.put(blockId, future);
    }
    persistExecutor.execute(new Runnable() {

      @Override
      public void run() {
        try {
          Collection<Block> blocks = blockAccess.readBlocks(blockIds);
          for (Block block : blocks) {
            registerBlock(block);
            SettableFuture<Block> future = readBlocksFutures.remove(block.getBlockId());
            if (future != null) {
              future.set(block);
            }
          }
        } catch (Exception ex) {
          for (String blockId : blockIds) {
            SettableFuture<Block> future = readBlocksFutures.remove(blockId);
            if (future != null) {
              future.setException(ex);
            }
          }
        }
      }
    });
    return futures;
  }

  void registerBlock(final Block block)  {
    blockCache.putBlock(block);
    writeLock.lock();
    try {
      if (block.getSize() < Block.LOW_WATER) {
        blockToCreateNewFragments = block;
      }
    } finally {
      writeLock.unlock();
    }
    block.addObserver(new FragmentObserver(){

      @Override
      public void onFragmentModified(Fragment fragment) {
        writeLock.lock();
        checkOpened();
        try {
          blockIndex.update(fragment);
          if (blocksToWrite.isEmpty()) {
            persistExecutor.schedule(savingTask, SAVING_BLOCKS_PERIOD_MS, TimeUnit.MILLISECONDS);
          }
          blocksToWrite.add(block);
        } finally {
          writeLock.unlock();
        }
      }
    });
  }

  private void checkOpened() {
    readLock.lock();
    try {
      Preconditions.checkArgument(!writingFailed, "Block state of wavelet " + waveletName.toString() + " failed");
      Preconditions.checkArgument(blockAccess != null && !closing, "Wavelet " + waveletName.toString()
          + " is not opened or closing");
    } finally {
      readLock.unlock();
    }
  }

  private void checkOpenedOrClosing() {
    readLock.lock();
    try {
      Preconditions.checkArgument(!writingFailed, "Block state of wavelet " + waveletName.toString() + " failed");
      Preconditions.checkArgument(blockAccess != null, "Wavelet " + waveletName.toString() + " is not opened");
    } finally {
      readLock.unlock();
    }
  }
}
