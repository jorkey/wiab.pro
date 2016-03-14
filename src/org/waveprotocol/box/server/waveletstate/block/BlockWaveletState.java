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

import org.waveprotocol.box.server.waveletstate.BlockFactory;
import org.waveprotocol.box.server.waveletstate.WaveletState;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.box.server.persistence.blocks.Block;
import org.waveprotocol.box.server.persistence.blocks.ReadableBlockIndex;

import org.waveprotocol.wave.model.version.HashedVersion;

import org.waveprotocol.wave.model.id.WaveletName;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Map;
import java.util.Set;
import org.waveprotocol.wave.model.util.Pair;

/**
 * Resident state of wavelet block access.
 * Provides asynchronous interface to BlockWaveletAccess.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public interface BlockWaveletState extends BlockFactory, WaveletState {

  interface GuiceFactory {
    BlockWaveletState create(WaveletName waveletName);
  }

  /**
   * Gets last modified version and time.
   */
  Pair<HashedVersion, Long> getLastModifiedVersionAndTime();

  /**
   * Reads blocks.
   *
   * @param blockIds the Ids of blocks to read.
   */
  ListenableFuture<Map<String, Block>> readBlocks(Set<String> blockIds) throws WaveletStateException;

  /**
   * Reads segments index.
   */
  ReadableBlockIndex getSegmentsIndex() throws WaveletStateException;
  
  /**
   * Sets last modified version ant time.
   */
  void setLastModifiedVersionAndTime(HashedVersion version, long time);

  /**
   * Marks as consistent with delta state.
   */
  void markAsConsistent();
  
  /**
   * Marks as inconsistent with delta state.
   */
  void markAsInconsistent() throws WaveletStateException;
}
