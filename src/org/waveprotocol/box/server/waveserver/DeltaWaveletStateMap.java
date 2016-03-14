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

import org.waveprotocol.box.server.waveletstate.delta.DeltaWaveletState;
import org.waveprotocol.box.server.waveletstate.WaveletStateFactory;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.executor.ExecutorAnnotations;

import org.waveprotocol.wave.model.id.WaveletName;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class DeltaWaveletStateMap {
  private static final Logger LOG = Logger.getLogger(DeltaWaveletStateMap.class.getName());

  public interface DeltaWaveletStateAccessor {
    DeltaWaveletState get() throws WaveletStateException;
    DeltaWaveletState getIfPresent() throws WaveletStateException;
    ListenableFuture close();
  }

  private final LoadingCache<WaveletName, ListenableFuture<DeltaWaveletState>> states;
  private final Map<WaveletName, ListenableFuture> closingStates = new ConcurrentHashMap();

  @Inject
  public DeltaWaveletStateMap(final WaveletStateFactory waveletStateFactory,
      final Injector injector,
      @Named(CoreSettings.DELTA_STATE_CACHE_SIZE) final int cacheSize,
      @Named(CoreSettings.DELTA_STATE_CACHE_EXPIRE) final int cacheExpire,
      @ExecutorAnnotations.WaveletLoadingExecutor final Executor waveletLoadingExecutor) {

    states = CacheBuilder.newBuilder()
      .maximumSize(cacheSize)
      .expireAfterAccess(cacheExpire, TimeUnit.MINUTES)
      .removalListener(new RemovalListener<WaveletName, ListenableFuture<DeltaWaveletState>>() {

        @Override
        public void onRemoval(final RemovalNotification<WaveletName, ListenableFuture<DeltaWaveletState>> rn) {
          final ListenableFuture<DeltaWaveletState> future = rn.getValue();
          future.addListener(new Runnable() {

            @Override
            public void run() {
              DeltaWaveletState state;
              try {
                state = future.get();
              } catch (InterruptedException | ExecutionException ex) {
                LOG.log(Level.WARNING, "Opening delta state error", ex);
                return;
              }
              final ListenableFuture closeFuture = state.close();
              closingStates.put(rn.getKey(), closeFuture);
              closeFuture.addListener(new Runnable() {

                @Override
                public void run() {
                  try {
                    closeFuture.get();
                    closingStates.remove(rn.getKey());
                  } catch (InterruptedException | ExecutionException ex) {
                    LOG.log(Level.WARNING, "Closing delta state error", ex);
                  }
                }
              }, waveletLoadingExecutor);
            }
          }, MoreExecutors.sameThreadExecutor());
        }
      }).build(new CacheLoader<WaveletName, ListenableFuture<DeltaWaveletState>>() {

        @Override
        public ListenableFuture<DeltaWaveletState> load(WaveletName waveletName) throws Exception {
          ListenableFuture closingFuture = closingStates.get(waveletName);
          if (closingFuture != null) {
            try {
              closingFuture.get();
            } catch (InterruptedException | ExecutionException ex) {
              throw new WaveletStateException("Closing earlier opened delta state exception", ex);
            }
          }
          final DeltaWaveletState state = waveletStateFactory.createDeltaWaveletState(waveletName);
          ListenableFutureTask<DeltaWaveletState> future = ListenableFutureTask.create(new Callable<DeltaWaveletState>() {

            @Override
            public DeltaWaveletState call() throws Exception {
              state.open();
              return state;
            }
          });
          waveletLoadingExecutor.execute(future);
          return future;
        }
      });
  }

  public DeltaWaveletStateAccessor get(final WaveletName waveletName) {
    return new DeltaWaveletStateAccessor() {

      @Override
      public DeltaWaveletState get() throws WaveletStateException {
        return getState(states.getUnchecked(waveletName));
      }

      @Override
      public DeltaWaveletState getIfPresent() throws WaveletStateException {
        ListenableFuture<DeltaWaveletState> future = states.getIfPresent(waveletName);
        if (future == null) {
          return null;
        }
        return getState(future);
      }

      @Override
      public ListenableFuture close() {
        states.invalidate(waveletName);
        ListenableFuture future = closingStates.remove(waveletName);
        if (future == null) {
          SettableFuture existingFuture = SettableFuture.create();
          existingFuture.set(null);
          future = existingFuture;
        }
        return future;
      }

      private DeltaWaveletState getState(ListenableFuture<DeltaWaveletState> future) throws WaveletStateException {
        try {
          return future.get();
        } catch (InterruptedException | ExecutionException ex) {
          throw new WaveletStateException(ex);
        }
      }
    };
  }
}
