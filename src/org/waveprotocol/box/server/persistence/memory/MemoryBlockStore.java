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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;

import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.persistence.FileNotFoundPersistenceException;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.blocks.BlockStore;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class MemoryBlockStore implements BlockStore {
  private final LoadingCache<WaveId, List<WaveletId>> wavelets =
    CacheBuilder.newBuilder().build(new CacheLoader<WaveId, List<WaveletId>>() {

      @Override
      public List<WaveletId> load(WaveId waveId) throws Exception {
        return new LinkedList<>();
      }
    });

  private final LoadingCache<WaveletName, BlockAccess> access =
    CacheBuilder.newBuilder().build(new CacheLoader<WaveletName, BlockAccess>() {

      @Override
      public BlockAccess load(WaveletName waveletName) throws Exception {
        return new MemoryBlockAccess(waveletName);
      }
    });

  @Inject
  public MemoryBlockStore() {
  }

  @Override
  public synchronized BlockAccess open(WaveletName waveletName) throws PersistenceException {
    try {
      wavelets.get(waveletName.waveId).add(waveletName.waveletId);
      return access.get(waveletName);
    } catch (ExecutionException ex) {
      throw new PersistenceException(ex);
    }
  }

  @Override
  public synchronized void delete(WaveletName waveletName) throws PersistenceException, FileNotFoundPersistenceException {
    try {
      access.invalidate(waveletName);
      List<WaveletId> waveletsList = wavelets.get(waveletName.waveId);
      waveletsList.remove(waveletName.waveletId);
      if (waveletsList.isEmpty()) {
        wavelets.invalidate(waveletName.waveId);
      }
    } catch (ExecutionException ex) {
      throw new PersistenceException(ex);
    }
  }

  @Override
  public synchronized ImmutableSet<WaveletId> lookup(WaveId waveId) throws PersistenceException {
    try {
      ImmutableSet.Builder<WaveletId> results = ImmutableSet.builder();
      for (WaveletId waveletId : wavelets.get(waveId)) {
        results.add(waveletId);
      }
      return results.build();
    } catch (ExecutionException ex) {
      throw new PersistenceException(ex);
    }
  }

  @Override
  public synchronized ExceptionalIterator<WaveId, PersistenceException> getWaveIdIterator() throws PersistenceException {
    final Iterator<WaveId> it = wavelets.asMap().keySet().iterator();
    return new ExceptionalIterator<WaveId, PersistenceException>() {

      @Override
      public boolean hasNext() throws PersistenceException {
        return it.hasNext();
      }

      @Override
      public WaveId next() throws PersistenceException {
        return it.next();
      }
    };
  }
}
