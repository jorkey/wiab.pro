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

import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.deltas.DeltaStore;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.concurrent.ExecutionException;
import java.util.Map;

/**
 * A simple in-memory implementation of DeltaStore.
 *
 * @author josephg@google.com (Joseph Gentle)
 * @author akaplanov@google.com (A. Kaplanov)
 */
@Singleton
public class MemoryDeltaStore implements DeltaStore {

  @Inject
  public MemoryDeltaStore() {
  }

  private final LoadingCache<WaveId, LoadingCache<WaveletId, MemoryDeltaAccess>> access =
    CacheBuilder.newBuilder().build(new CacheLoader<WaveId, LoadingCache<WaveletId, MemoryDeltaAccess>>(){

    @Override
    public LoadingCache<WaveletId, MemoryDeltaAccess> load(final WaveId waveId) throws Exception {
      return CacheBuilder.newBuilder().build(new CacheLoader<WaveletId, MemoryDeltaAccess>(){

        @Override
        public MemoryDeltaAccess load(WaveletId waveletId) throws Exception {
          return new MemoryDeltaAccess(WaveletName.of(waveId, waveletId));
        }
      });
    }
  });

  @Override
  public void delete(WaveletName waveletName) throws PersistenceException {
    LoadingCache<WaveletId, MemoryDeltaAccess> waveData = access.getIfPresent(waveletName.waveId);
    if (waveData != null) {
      waveData.invalidate(waveletName.waveletId);
      if (waveData.size() == 0) {
        access.invalidate(waveletName.waveId);
      }
    }
  }

  @Override
  public ImmutableSet<WaveletId> lookup(WaveId waveId) {
    LoadingCache<WaveletId, MemoryDeltaAccess> waveData = access.getIfPresent(waveId);
    if (waveData == null) {
      return ImmutableSet.of();
    } else {
      ImmutableSet.Builder<WaveletId> builder = ImmutableSet.builder();
      for (MemoryDeltaAccess collection : waveData.asMap().values()) {
        builder.add(collection.getWaveletName().waveletId);
      }
      return builder.build();
    }
  }

  @Override
  public DeltaAccess open(WaveletName waveletName) {
    LoadingCache<WaveletId, MemoryDeltaAccess> waveData;
    try {
      waveData = access.get(waveletName.waveId);
      return waveData.get(waveletName.waveletId);
    } catch (ExecutionException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public synchronized ExceptionalIterator<WaveId, PersistenceException> getWaveIdIterator() {
    ImmutableSet.Builder<WaveId> builder = ImmutableSet.builder();
    // Filter out empty waves
    for (Map.Entry<WaveId, LoadingCache<WaveletId, MemoryDeltaAccess>> e : access.asMap().entrySet()) {
      builder.add(e.getKey());
    }
    return ExceptionalIterator.FromIterator.create(builder.build().iterator());
  }
}
