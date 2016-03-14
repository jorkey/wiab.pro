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

import org.waveprotocol.box.server.persistence.blocks.Block;
import org.waveprotocol.box.server.persistence.blocks.BlockIndex;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.box.server.waveletstate.block.BlockWaveletState;

import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.util.CollectionUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.Map;
import java.util.Set;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.version.HashedVersion;

/**
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class BlockWaveletStateStub implements BlockWaveletState {
  private HashedVersion lastModifiedVersion = HashedVersion.unsigned(0);
  private long lastModifiedTime = 0;
  private final BlockIndex segmentsIndex;
  private Map<String, Block> blocks = CollectionUtils.newHashMap();
  private int readBlocksCalls;

  public BlockWaveletStateStub(BlockIndex segmentsIndex) {
    this.segmentsIndex = segmentsIndex;
  }

  @Override
  public ListenableFuture<Map<String, Block>> readBlocks(Set<String> blockIds) throws WaveletStateException {
    readBlocksCalls++;
    SettableFuture<Map<String, Block>> ret = SettableFuture.create();
    ImmutableMap.Builder<String, Block> builder = ImmutableMap.builder();
    for (String blockId : blockIds) {
      Block block = blocks.get(blockId);
      if (block != null) {
        builder.put(blockId, block);
      }
    }
    ret.set(builder.build());
    return ret;
  }

  @Override
  public BlockIndex getSegmentsIndex() throws WaveletStateException {
    return segmentsIndex;
  }

  @Override
  public Block getOrCreateBlockToWriteNewFragment() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void open() throws WaveletStateException {
  }

  @Override
  public ListenableFuture close() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ListenableFuture flush() {
    throw new UnsupportedOperationException();
  }

  @Override
  public WaveletName getWaveletName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markAsConsistent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markAsInconsistent() throws WaveletStateException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() throws WaveletStateException {
    throw new UnsupportedOperationException();
  }

  void addBlock(Block block) {
    this.blocks.put(block.getBlockId(), block);
  }

  int getReadBlockCalls() {
    return readBlocksCalls;
  }

  @Override
  public Pair<HashedVersion, Long> getLastModifiedVersionAndTime() {
    return Pair.of(lastModifiedVersion, lastModifiedTime);
  }

  @Override
  public void setLastModifiedVersionAndTime(HashedVersion version, long time) {
    this.lastModifiedVersion = version;
    this.lastModifiedTime = time;
  }
}
