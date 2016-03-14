/**
 * Copyright 2012 Apache Wave
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.waveprotocol.box.server.search;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.Closeable;
import java.io.IOException;
import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import com.google.wave.api.SearchResult;
import com.google.wave.api.SearchResult.Digest;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

import java.util.concurrent.TimeUnit;
import org.waveprotocol.box.server.waveserver.LocalWaveletContainer;
import org.waveprotocol.box.server.waveserver.WaveMap;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;

/**
 * @author yurize@apache.org (Yuri Zelikov)
 */
@Singleton
public class MemorySearchImpl implements WaveIndexer, SearchProvider, SearchBusSubscriber, Closeable {

  private static final Log LOG = Log.get(MemorySearchImpl.class);

  /**
   * The period of time in minutes the per user waves view should be actively
   * kept up to date after last access.
   */
  private static final int PER_USER_WAVES_VIEW_CACHE_MINUTES = 5;

  /** The loading cache that holds wave viev per each online user.*/
  public LoadingCache<ParticipantId, Multimap<WaveId, WaveletId>> explicitPerUserWaveViews;

  @Inject
  public MemorySearchImpl(final WaveMap waveMap) {
    // Let the view expire if it not accessed for some time.
    explicitPerUserWaveViews =
        CacheBuilder.newBuilder().expireAfterAccess(PER_USER_WAVES_VIEW_CACHE_MINUTES, TimeUnit.MINUTES).build(new CacheLoader<ParticipantId, Multimap<WaveId, WaveletId>>() {

      @Override
      public Multimap<WaveId, WaveletId> load(ParticipantId user) throws Exception {
        Multimap<WaveId, WaveletId> userView = HashMultimap.create();
        // Create initial per user waves view by looping over all waves
        // in the waves store.
        ExceptionalIterator<WaveId, WaveServerException> waveIds = waveMap.getWaveIds();
        while (waveIds.hasNext()) {
          WaveId waveId = waveIds.next();
          ImmutableSet<WaveletId> wavelets = waveMap.getWaveletIds(waveId);
          for (WaveletId waveletId : wavelets) {
            LocalWaveletContainer c = waveMap.getLocalWavelet(WaveletName.of(waveId, waveletId));
            try {
              if (!c.hasParticipant(user)) {
                continue;
              }
              // Add this wave to the user view.
              userView.put(waveId, waveletId);
            } catch (WaveletStateException e) {
              LOG.warning("Failed to access wavelet " + c.getWaveletName(), e);
            }
          }
        }
        LOG.info("Initalized waves view for user: " + user.getAddress()
            + ", number of waves in view: " + userView.size());
        return userView;
      }
    });
  }

  @Override
  public void waveletCommitted(WaveletName waveletName, HashedVersion version) {
  }

  @Override
  public void waveletUpdate(WaveletName waveletName, DeltaSequence deltas) {
  }

  @Override
  public SearchResult search(String query, int startAt, int numResults, ParticipantId user) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public Digest findWave(WaveId waveId, ParticipantId viewer) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void updateIndex(WaveId waveId) throws WaveletStateException, WaveServerException {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void close() throws IOException {
  }
}
