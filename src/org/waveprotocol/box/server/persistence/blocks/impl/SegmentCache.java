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

package org.waveprotocol.box.server.persistence.blocks.impl;

import org.waveprotocol.box.server.persistence.blocks.Segment;
import org.waveprotocol.box.server.waveletstate.BlockFactory;

import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.id.SegmentName;
import org.waveprotocol.wave.util.logging.Log;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.Collection;

import javax.annotation.Nullable;

/**
 * Cache of segments.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class SegmentCache {
  private static final Log LOG = Log.get(SegmentCache.class);

  /**
   * Cache of segments.
   */
  private final Cache<SegmentName, Segment> cache = CacheBuilder.newBuilder().softValues().build();

  /** Gets segment from cache.
   *
   * @return segment if present or null
   */
  @Nullable
  public Segment getSegment(WaveletName waveletName, SegmentId segmentId) {
    return cache.getIfPresent(SegmentName.of(waveletName, segmentId));
  }

  /** Gets segment from cache or create new. */
  public Segment getOrCreateSegment(WaveletName waveletName, final SegmentId segmentId, final BlockFactory blockProvider) {
    try {
      return cache.get(SegmentName.of(waveletName, segmentId), new Callable<Segment>(){

        @Override
        public Segment call() throws Exception {
          return new SegmentImpl(segmentId, blockProvider);
        }
      });
    } catch (ExecutionException ex) {
      throw new RuntimeException(ex);
    }
  }

  /** Gets available segments. */
  public Collection<Segment> getAvailableSegments() {
    return cache.asMap().values();
  }

  /** Removes segments of specified wavelet. */
  public void removeWavelet(WaveletName waveletName) {
    for (SegmentName segmentName : cache.asMap().keySet()) {
      if (segmentName.waveletName.equals(waveletName)) {
        cache.invalidate(segmentName);
      }
    }
  }
}
