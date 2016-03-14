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
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.SnapshotStore;
import org.waveprotocol.box.server.shutdown.LifeCycle;
import org.waveprotocol.box.server.shutdown.ShutdownPriority;
import org.waveprotocol.box.stat.Timed;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.io.IOException;

/**
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class FileSnapshotStore implements SnapshotStore {
  final private String basePath;
  final private LifeCycle lifeCycle = new LifeCycle(FileSnapshotStore.class.getSimpleName(), ShutdownPriority.Storage);

  @Inject
  public FileSnapshotStore(@Named(CoreSettings.DELTA_STORE_DIRECTORY) String basePath) {
    Preconditions.checkNotNull(basePath, "Requested path is null");
    this.basePath = basePath;
    lifeCycle.start();
  }

  @Timed
  @Override
  public FileSnapshotAccess open(WaveletName waveletName) throws PersistenceException {
    try {
      lifeCycle.enter();
      return FileSnapshotAccess.open(waveletName, basePath);
    } catch (IOException e) {
      throw new PersistenceException("Failed to open snapshots for wavelet " + waveletName, e);
    } finally {
      lifeCycle.leave();
    }
  }

  @Timed
  @Override
  public void delete(WaveletName waveletName) throws PersistenceException {
    lifeCycle.enter();
    try {
      FileSnapshotAccess.delete(waveletName, basePath);
    } finally {
      lifeCycle.leave();
    }
  }

  @Override
  public ImmutableSet<WaveletId> lookup(WaveId waveId) throws PersistenceException {
    throw new UnsupportedOperationException();
  }


  @Override
  public ExceptionalIterator<WaveId, PersistenceException> getWaveIdIterator() {
    throw new UnsupportedOperationException();
  }
}
