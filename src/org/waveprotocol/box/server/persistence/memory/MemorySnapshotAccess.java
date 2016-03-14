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

package org.waveprotocol.box.server.persistence.memory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.SnapshotStore.SnapshotAccess;
import org.waveprotocol.box.server.persistence.deltas.DeltaStore.DeltaAccess;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.data.WaveletData;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * An in-memory implementation of DeltasAccess
 *
 * @author josephg@google.com (Joseph Gentle)
 */
public class MemorySnapshotAccess implements SnapshotAccess {
  private final Map<Long, WaveletData> snapshots = Maps.newHashMap();
  private final List<Long> snapshotVersions = Lists.newArrayList();
  private final WaveletName waveletName;

  private WaveletData snapshot = null;
  private HashedVersion endVersion = null;

  public MemorySnapshotAccess(WaveletName waveletName) {
    Preconditions.checkNotNull(waveletName);
    this.waveletName = waveletName;
  }

  @Override
  public WaveletName getWaveletName() {
    return waveletName;
  }

  public synchronized boolean isEmpty() {
    return snapshots.isEmpty();
  }

  @Override
  public void close() {
    // Does nothing.
  }

  @Override
  public synchronized WaveletData readInitialSnapshot() throws IOException {
    return snapshot;
  }

  @Override
  public synchronized WaveletData readNearestSnapshot(long version) throws IOException {
    Long prev = null;
    for (Long ver : snapshotVersions) {
      if (ver > version) {
        if (prev == null) {
          return null;
        }
        return snapshots.get(prev);
      } else if (ver < version) {
        prev = ver;
      } else {
        return snapshots.get(ver);
      }
    }
    return (prev != null) ? snapshots.get(prev) : null;
  }

  @Override
  public synchronized long getLastHistorySnapshotVersion() {
    if (!snapshotVersions.isEmpty()) {
      return snapshotVersions.get(snapshotVersions.size()-1);
    }
    return 0;
  }

  @Override
  public synchronized void writeInitialSnapshot(WaveletData snapshot) throws PersistenceException {
    this.snapshot = snapshot;
  }

  @Override
  public synchronized void writeSnapshotToHistory(WaveletData snapshot) throws PersistenceException {
    snapshots.put(snapshot.getVersion(), snapshot);
    snapshotVersions.add(snapshot.getVersion());
  }

  @Override
  public void remakeSnapshotsHistory(DeltaAccess deltasAccess, int savingSnapshotPeriod) throws PersistenceException {
    throw new UnsupportedOperationException();
  }
}
