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

package org.waveprotocol.box.server.persistence.memory;

import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.util.CollectionUtils;

import org.waveprotocol.box.server.persistence.blocks.Block;
import org.waveprotocol.box.server.persistence.blocks.BlockStore.BlockAccess;
import org.waveprotocol.box.server.persistence.blocks.BlockIndex;
import org.waveprotocol.box.server.persistence.blocks.impl.BlockIndexImpl;
import org.waveprotocol.box.server.persistence.PersistenceException;

import java.util.List;
import java.util.Set;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Access to memory block store.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class MemoryBlockAccess implements BlockAccess {
  /** Maximum size of list with last modified blocks. */
  public static int MAX_STORED_LAST_BLOCKS_COUNT = 10;

  /** Name of wavelet. */
  private final WaveletName waveletName;

  /** Map of blocks. */
  private final Map<String, Block> blocksMap = CollectionUtils.newHashMap();

  /** Segments index. */
  private BlockIndex blockIndex = new BlockIndexImpl();

  MemoryBlockAccess(WaveletName waveletName) {
    this.waveletName = waveletName;
  }

  @Override
  public WaveletName getWaveletName() {
    return waveletName;
  }

  @Override
  public synchronized void close() throws IOException {
  }

  @Override
  public synchronized BlockIndex readBlockIndex() throws PersistenceException {
    return blockIndex;
  }

  @Override
  public synchronized Collection<Block> readBlocks(Set<String> blockIds) throws PersistenceException {
    List<Block> result = CollectionUtils.newLinkedList();
    for (String blockId : blockIds) {
      Block block = blocksMap.get(blockId);
      if (block == null) {
        throw new PersistenceException("No such block " + blockId);
      }
      result.add(block);
    }
    return result;
  }

  @Override
  public synchronized void writeBlock(Block block) throws PersistenceException {
    blocksMap.put(block.getBlockId(), block);
  }

  @Override
  public synchronized void writeBlockIndex(BlockIndex blockIndex) throws PersistenceException {
    this.blockIndex = blockIndex;
  }
}
