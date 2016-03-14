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

import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.SnapshotStore;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;

import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * A simple in-memory implementation of SnapshotStore.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class MemorySnapshotStore implements SnapshotStore {

    private final LoadingCache<WaveId, LoadingCache<WaveletId, MemorySnapshotAccess>> access =
      CacheBuilder.newBuilder().build(new CacheLoader<WaveId, LoadingCache<WaveletId, MemorySnapshotAccess>>(){

    @Override
    public LoadingCache<WaveletId, MemorySnapshotAccess> load(final WaveId waveId) throws Exception {
      return CacheBuilder.newBuilder().build(new CacheLoader<WaveletId, MemorySnapshotAccess>(){

        @Override
        public MemorySnapshotAccess load(WaveletId waveletId) throws Exception {
          return new MemorySnapshotAccess(WaveletName.of(waveId, waveletId));
        }
      });
    }
  });

  public MemorySnapshotStore() {
  }

  @Override
  public void delete(WaveletName waveletName) throws PersistenceException {
    LoadingCache<WaveletId, MemorySnapshotAccess> waveData = access.getIfPresent(waveletName.waveId);
    if (waveData != null) {
      waveData.invalidate(waveletName.waveletId);
      if (waveData.size() == 0) {
        access.invalidate(waveletName.waveId);
      }
    }
  }

  @Override
  public ImmutableSet<WaveletId> lookup(WaveId waveId) {
    LoadingCache<WaveletId, MemorySnapshotAccess> waveData = access.getIfPresent(waveId);
    if (waveData == null) {
      return ImmutableSet.of();
    } else {
      ImmutableSet.Builder<WaveletId> builder = ImmutableSet.builder();
      for (MemorySnapshotAccess collection : waveData.asMap().values()) {
        builder.add(collection.getWaveletName().waveletId);
      }
      return builder.build();
    }
  }

  @Override
  public SnapshotAccess open(WaveletName waveletName) {
    Map<WaveletId, MemorySnapshotAccess> waveData = getOrCreateWaveData(waveletName.waveId);

    MemorySnapshotAccess collection = waveData.get(waveletName.waveletId);
    if (collection == null) {
      collection = new MemorySnapshotAccess(waveletName);
      waveData.put(waveletName.waveletId, collection);
    }

    return collection;
  }

  @Override
  public ExceptionalIterator<WaveId, PersistenceException> getWaveIdIterator() {
    ImmutableSet.Builder<WaveId> builder = ImmutableSet.builder();
    // Filter out empty waves
    for (Map.Entry<WaveId, LoadingCache<WaveletId, MemorySnapshotAccess>> e : access.asMap().entrySet()) {
      builder.add(e.getKey());
    }
    return ExceptionalIterator.FromIterator.create(builder.build().iterator());
  }
  
  private Map<WaveletId, MemorySnapshotAccess> getOrCreateWaveData(WaveId waveId) {
    try {
      return access.get(waveId).asMap();
    } catch (ExecutionException ex) {
      throw new RuntimeException(ex);
    }
  }
}
