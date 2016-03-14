/**
 * Copyright 2010 Google Inc.
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

package org.waveprotocol.box.server.persistence.file;

import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.deltas.DeltaStore;
import org.waveprotocol.box.server.shutdown.LifeCycle;
import org.waveprotocol.box.server.shutdown.ShutdownPriority;
import org.waveprotocol.box.stat.Timed;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A flat file based implementation of DeltaStore.
 *
 * The delta store lives at some base directory. The directory structure looks like this:
 * base/encoded-wave-id/encoded-wavelet-id.delta
 * base/encoded-wave-id/encoded-wavelet-id.index
 *
 * See design doc:
 * https://sites.google.com/a/waveprotocol.org/wave-protocol/protocol/design-proposals/wave-store-design-for-wave-in-a-box
 *

 * @author josephg@gmail.com (Joseph Gentle)
 */
@Singleton
public class FileDeltaStore implements DeltaStore {
  /** The directory in which the wavelets are stored. */
  final private String basePath;

  final private LifeCycle lifeCycle = new LifeCycle(FileDeltaStore.class.getSimpleName(), ShutdownPriority.Storage);

  @Inject
  public FileDeltaStore(@Named(CoreSettings.DELTA_STORE_DIRECTORY) String basePath) {
    Preconditions.checkNotNull(basePath, "Requested path is null");
    this.basePath = basePath;
    lifeCycle.start();
  }

  @Timed
  @Override
  public FileDeltaAccess open(WaveletName waveletName) throws PersistenceException {
    try {
      lifeCycle.enter();
      return FileDeltaAccess.open(waveletName, basePath);
    } catch (IOException e) {
      throw new PersistenceException("Failed to open deltas for wavelet " + waveletName, e);
    } finally {
      lifeCycle.leave();
    }
  }

  @Timed
  @Override
  public void delete(WaveletName waveletName) throws PersistenceException {
    lifeCycle.enter();
    try {
      FileDeltaAccess.delete(waveletName, basePath);
    } finally {
      lifeCycle.leave();
    }
  }

  @Timed
  @Override
  public ImmutableSet<WaveletId> lookup(WaveId waveId) throws PersistenceException {
    lifeCycle.enter();
    try {
      String waveDirectory = FileUtils.waveIdToPathSegment(waveId);
      File waveDir = new File(basePath, waveDirectory);
      if (!waveDir.exists()) {
        return ImmutableSet.of();
      }

      File[] deltaFiles = waveDir.listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.endsWith(FileDeltaAccess.DELTAS_FILE_SUFFIX);
        }
      });

      ImmutableSet.Builder<WaveletId> results = ImmutableSet.builder();

      if (deltaFiles != null) {
        for(File deltaFile : deltaFiles) {
          String name = deltaFile.getName();
          String encodedWaveletId =
              name.substring(0, name.lastIndexOf(FileDeltaAccess.DELTAS_FILE_SUFFIX));
          WaveletId waveletId = FileUtils.waveletIdFromPathSegment(encodedWaveletId);
          /* Open of wavelet to check that it not empty was removed by Andrew Kaplanov,
           * because it causes conflict of index rebuilding.
           */
          results.add(waveletId);
        }
      }

      return results.build();
    } finally {
      lifeCycle.leave();
    }
  }

  @Override
  public ExceptionalIterator<WaveId, PersistenceException> getWaveIdIterator() {
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
        public int compare(File f1, File f2) {
          return Long.compare(f2.lastModified(), f1.lastModified());
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
