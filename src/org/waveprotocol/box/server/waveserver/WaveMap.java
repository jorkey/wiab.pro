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

package org.waveprotocol.box.server.waveserver;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.executor.ExecutorAnnotations;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.WaveletStore;
import org.waveprotocol.box.server.persistence.deltas.DeltaStore;
import org.waveprotocol.box.server.shutdown.LifeCycle;
import org.waveprotocol.box.server.shutdown.ShutdownPriority;
import org.waveprotocol.box.server.shutdown.Shutdownable;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.box.stat.Timed;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.data.impl.WaveViewDataImpl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;

/**
 * A collection of wavelets, local and remote, cached in memory.
 *
 * @author soren@google.com (Soren Lassen) - All wavelets stored in memory
 * @author akaplanov@gmail.com (Andrew Kaplanov) - Cache release
 */

public class WaveMap {

  private static final Logger LOG = Logger.getLogger(WaveMap.class.getName());

  private final LoadingCache<WaveId, Wave> waves;
  private final Map<WaveId, ListenableFuture> closingWaves = new ConcurrentHashMap<>();

  /**
   * Returns a future whose result is the ids of stored wavelets in the given wave.
   * Any failure is reported as a {@link PersistenceException}.
   */
  private static ListenableFuture<ImmutableSet<WaveletId>> lookupWavelets(
      final WaveId waveId, final WaveletStore<?> waveletStore, Executor lookupExecutor) {
    ListenableFutureTask<ImmutableSet<WaveletId>> task =
        ListenableFutureTask.<ImmutableSet<WaveletId>>create(
            new Callable<ImmutableSet<WaveletId>>() {
              @Override
              public ImmutableSet<WaveletId> call() throws PersistenceException {
                return waveletStore.lookup(waveId);
              }
            });
    lookupExecutor.execute(task);
    return task;
  }

  private final DeltaStore store;

  final private LifeCycle lifeCycle = new LifeCycle(WaveMap.class.getSimpleName(), ShutdownPriority.Waves,
      new Shutdownable() {
    @Override
    public void shutdown() {
      waves.invalidateAll();
      for (Map.Entry<WaveId, ListenableFuture> entry : closingWaves.entrySet()) {
        try {
          entry.getValue().get(1, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
          LOG.warning("Expect the closing of " + entry.getKey().toString());
        }
      }
    }
  });

  @Inject
  public WaveMap(final DeltaStore store,
      final WaveletNotificationSubscriber notifiee,
      WaveBus dispatcher,
      final LocalWaveletContainer.Factory localFactory,
      final RemoteWaveletContainer.Factory remoteFactory,
      @Named(CoreSettings.WAVE_SERVER_DOMAIN) final String waveDomain,
      @Named(CoreSettings.WAVE_CACHE_SIZE) final int waveCacheSize,
      @Named(CoreSettings.WAVE_CACHE_EXPIRE) final int waveCacheExpire,
      WaveDigester digester,
      @ExecutorAnnotations.LookupExecutor final Executor lookupExecutor) {
    this.store = store;

    waves = CacheBuilder.newBuilder()
        .maximumSize(waveCacheSize)
        .expireAfterAccess(waveCacheExpire, TimeUnit.MINUTES)
        .removalListener(new RemovalListener<WaveId, Wave>() {
      @Override
      public void onRemoval(final RemovalNotification<WaveId, Wave> rn) {
        LOG.info("Wave " + rn.getKey() + " is evicted, current cache size " + waves.size());
        final ListenableFuture future = rn.getValue().close();
        closingWaves.put(rn.getKey(), future);
        future.addListener(new Runnable() {

          @Override
          public void run() {
            try {
              future.get();
            } catch (InterruptedException | ExecutionException ex) {
              LOG.log(Level.WARNING, "Closing wave exception", ex);
            }
            closingWaves.remove(rn.getKey());
          }
        }, MoreExecutors.sameThreadExecutor());
      }
    }).build(new CacheLoader<WaveId, Wave>() {
      @Override
      public Wave load(WaveId waveId) throws Exception {
        ListenableFuture<ImmutableSet<WaveletId>> lookedupWavelets =
            lookupWavelets(waveId, store, lookupExecutor);
        ListenableFuture closingFuture = closingWaves.get(waveId);
        if (closingFuture != null) {
          try {
            closingFuture.get();
          } catch (InterruptedException | ExecutionException ex) {
            throw new WaveletStateException("Previous instance is not closed", ex);
          }
        }
        return new Wave(waveId, lookedupWavelets, notifiee, localFactory, remoteFactory, waveDomain);
      }
    });
    lifeCycle.start();
  }

  public ImmutableSet<WaveletId> getWaveletIds(WaveId waveId) throws WaveletStateException {
    try {
      return waves.get(waveId).getWaveletIds();
    } catch (ExecutionException e) {
      throw new WaveletStateException("Failed to get wave " + waveId, e);
    }
  }

  public LocalWaveletContainer getLocalWavelet(WaveletName waveletName) throws WaveletStateException {
    try {
      return waves.get(waveletName.waveId).getLocalWavelet(waveletName.waveletId);
    } catch (ExecutionException e) {
      throw new WaveletStateException("Failed to get wave " + waveletName.waveId, e);
    }
  }

  public RemoteWaveletContainer getRemoteWavelet(WaveletName waveletName) throws WaveletStateException {
    try {
      return waves.get(waveletName.waveId).getRemoteWavelet(waveletName.waveletId);
    } catch (ExecutionException e) {
      throw new WaveletStateException("Failed to get wave " + waveletName.waveId, e);
    }
  }

  public LocalWaveletContainer getOrCreateLocalWavelet(WaveletName waveletName) throws WaveletStateException {
    try {
      return waves.get(waveletName.waveId).getOrCreateLocalWavelet(waveletName.waveletId);
    } catch (ExecutionException e) {
      throw new RuntimeException("Failed to get wave " + waveletName.waveId, e);
    }
  }

  public RemoteWaveletContainer getOrCreateRemoteWavelet(WaveletName waveletName) throws WaveletStateException {
    try {
      return waves.get(waveletName.waveId).getOrCreateRemoteWavelet(waveletName.waveletId);
    } catch (ExecutionException e) {
      throw new RuntimeException("Failed to get wave " + waveletName.waveId, e);
    }
  }

  @Timed
  public WaveViewData getWaveViewData(WaveId waveId) throws WaveletStateException {
    Set<WaveletId> wavelets = new HashSet<WaveletId>();
    Wave wave = waves.getUnchecked(waveId);
    wavelets.addAll(wave.getWaveletIds());
    WaveViewData view = WaveViewDataImpl.create(waveId);
    for (WaveletId waveletId : wavelets) {
      LocalWaveletContainer wavelet = wave.getLocalWavelet(waveletId);
      if (wavelet != null) {
        ReadableWaveletData snapshot = wavelet.getSnapshot();
        if (snapshot != null) {
          ObservableWaveletData waveletData = WaveletDataUtil.copyWavelet(snapshot);
          if (waveletData != null) {
            view.addWavelet(waveletData);
          }
        }
      }
    }
    return view;
  }

  public ExceptionalIterator<WaveId, WaveServerException> getWaveIds() {
    try {
      final ExceptionalIterator<WaveId, PersistenceException> it = store.getWaveIdIterator();
      return new ExceptionalIterator<WaveId, WaveServerException>() {

        @Override
        public boolean hasNext() throws WaveServerException {
          try {
            return it.hasNext();
          } catch (PersistenceException ex) {
            throw new WaveServerException(ex);
          }
        }

        @Override
        public WaveId next() throws WaveServerException {
          try {
            return it.next();
          } catch (PersistenceException ex) {
            throw new WaveServerException(ex);
          }
        }
      };
    } catch (PersistenceException ex) {
      throw new RuntimeException(ex);
    }
  }
}
