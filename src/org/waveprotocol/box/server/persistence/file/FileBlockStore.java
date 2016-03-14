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

package org.waveprotocol.box.server.persistence.file;

import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.persistence.FileNotFoundPersistenceException;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.blocks.BlockStore;
import org.waveprotocol.box.server.shutdown.LifeCycle;
import org.waveprotocol.box.server.shutdown.ShutdownPriority;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

/**
 * File block store.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class FileBlockStore implements BlockStore {
  /** Store directory. */
  private final String basePath;

  /** Cache of access to store. */
  private final LoadingCache<WaveletName, BlockAccess> access =
    CacheBuilder.newBuilder().build(new CacheLoader<WaveletName, BlockAccess>() {

      @Override
      public BlockAccess load(WaveletName waveletName) throws Exception {
        return new FileBlockAccess(waveletName, basePath);
      }
    });

  final private LifeCycle lifeCycle = new LifeCycle(FileBlockStore.class.getSimpleName(), ShutdownPriority.Storage);

  @Inject
  public FileBlockStore(@Named(CoreSettings.BLOCK_STORE_DIRECTORY) String basePath) {
    this.basePath = basePath;
    lifeCycle.start();
  }

  @Override
  public BlockAccess open(WaveletName waveletName) throws PersistenceException {
    lifeCycle.enter();
    try {
      return access.get(waveletName);
    } catch (ExecutionException ex) {
      throw new PersistenceException(ex);
    } finally {
      lifeCycle.leave();
    }
  }

  @Override
  public void delete(WaveletName waveletName) throws PersistenceException, FileNotFoundPersistenceException {
    lifeCycle.enter();
    try {
      access.invalidate(waveletName);
      FileBlockAccess.delete(waveletName, basePath);
    } finally {
      lifeCycle.leave();
    }
  }

  @Override
  public ImmutableSet<WaveletId> lookup(WaveId waveId) throws PersistenceException {
    lifeCycle.enter();
    try {
      String waveDirectory = FileUtils.waveIdToPathSegment(waveId);
      File waveDir = new File(basePath, waveDirectory);
      if (!waveDir.exists()) {
        return ImmutableSet.of();
      }

      File[] waveletDirs = waveDir.listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          try {
            FileUtils.waveletIdFromPathSegment(name);
            return true;
          } catch (IllegalArgumentException e) {
            return false;
          }
        }
      });

      ImmutableSet.Builder<WaveletId> results = ImmutableSet.builder();

      if (waveletDirs != null) {
        for(File waveletDir : waveletDirs) {
          WaveletId waveletId = FileUtils.waveletIdFromPathSegment(waveletDir.getName());
          results.add(waveletId);
        }
      }

      return results.build();
    } finally {
      lifeCycle.leave();
    }
  }

  @Override
  public ExceptionalIterator<WaveId, PersistenceException> getWaveIdIterator() throws PersistenceException {
    lifeCycle.enter();
    try {
      File baseDir = new File(basePath);
      if (!baseDir.exists()) {
        return ExceptionalIterator.Empty.create();
      }

      File[] waveDirs = baseDir.listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          try {
            FileUtils.waveIdFromPathSegment(name);
            return true;
          } catch (IllegalArgumentException e) {
            return false;
          }
        }
      });
      Arrays.sort(waveDirs, new Comparator<File>(){

        @Override
        public int compare(File waveDir1, File waveDir2) {
          return Long.compare(waveDir2.lastModified(), waveDir1.lastModified());
        }
      });

      final ImmutableList.Builder<WaveId> results = ImmutableList.builder();
      for (File waveDir : waveDirs) {
        results.add(FileUtils.waveIdFromPathSegment(waveDir.getName()));
      }

      return new ExceptionalIterator<WaveId, PersistenceException>() {
        private final Iterator<WaveId> iterator = results.build().iterator();
        private boolean nextFetched = false;
        private WaveId nextWaveId = null;

        private void fetchNext() throws PersistenceException {
          while(!nextFetched) {
            if (iterator.hasNext()) {
              nextWaveId = iterator.next();
              if (!lookup(nextWaveId).isEmpty()) {
                nextFetched = true;
              }
            } else {
              nextFetched = true;
              nextWaveId = null;
            }
          }
        }

        @Override
        public boolean hasNext() throws PersistenceException {
          fetchNext();
          return nextWaveId != null;
        }

        @Override
        public WaveId next() throws PersistenceException {
          fetchNext();
          if (nextWaveId == null) {
            throw new NoSuchElementException();
          } else {
            nextFetched = false;
            return nextWaveId;
          }
        }

      };
    } finally {
      lifeCycle.leave();
    }
  }

}
