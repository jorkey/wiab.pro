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
import com.google.common.collect.Maps;

import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.server.persistence.deltas.DeltaStore.DeltaAccess;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import org.waveprotocol.box.common.ThrowableReceiver;

/**
 * An in-memory implementation of DeltasAccess
 *
 * @author josephg@google.com (Joseph Gentle)
 */
public class MemoryDeltaAccess implements DeltaAccess {
  private final Map<Long, WaveletDeltaRecord> startDeltas = Maps.newHashMap();
  private final Map<Long, WaveletDeltaRecord> endDeltas = Maps.newHashMap();
  private final Map<Long, WaveletDeltaRecord> deltas = Maps.newHashMap();
  private final WaveletName waveletName;

  private HashedVersion lastModifyVersion;
  private long lastModifyTime;

  public MemoryDeltaAccess(WaveletName waveletName) {
    Preconditions.checkNotNull(waveletName);
    this.waveletName = waveletName;
  }

  @Override
  public boolean isEmpty() {
    return startDeltas.isEmpty();
  }

  @Override
  public WaveletName getWaveletName() {
    return waveletName;
  }

  @Override
  public synchronized HashedVersion getLastModifiedVersion() {
    return lastModifyVersion;
  }

  @Override
  public synchronized long getLastModifiedTime() {
    return lastModifyTime;
  }

  @Override
  public synchronized WaveletDeltaRecord getDeltaByStartVersion(long version) {
    return startDeltas.get(version);
  }

  @Override
  public synchronized WaveletDeltaRecord getDeltaByEndVersion(long version) {
    return endDeltas.get(version);
  }

  @Override
  public synchronized WaveletDeltaRecord getDeltaByArbitraryVersion(long version) throws IOException {
    return deltas.get(version);
  }

  @Override
  public synchronized void getDeltasFromVersion(long version, 
      ThrowableReceiver<WaveletDeltaRecord, IOException> receiver) throws IOException {
    for (;;) {
      WaveletDeltaRecord delta = deltas.get(version);
      if (delta == null) {
        break;
      }
      if (!receiver.put(delta)) {
        break;
      }
      version = delta.getResultingVersion().getVersion();
    }
  }

  @Override
  public void close() {
    // Does nothing.
  }

  @Override
  public synchronized void append(Collection<WaveletDeltaRecord> newDeltas) {
    for (WaveletDeltaRecord delta : newDeltas) {
      // Before:   ... |   D   |
      //            start     end
      // After:    ... |   D   |  D + 1 |
      //                     start     end
      long startVersion = delta.getTransformedDelta().getAppliedAtVersion();
      Preconditions.checkState(
          (startVersion == 0 && lastModifyVersion == null) ||
          (startVersion == lastModifyVersion.getVersion()));
      startDeltas.put(startVersion, delta);
      lastModifyVersion = delta.getTransformedDelta().getResultingVersion();
      lastModifyTime = delta.getApplicationTimestamp();
      endDeltas.put(lastModifyVersion.getVersion(), delta);
      for (long ver=delta.getAppliedAtVersion().getVersion(); ver < delta.getResultingVersion().getVersion(); ver++) {
        deltas.put(ver, delta);
      }
    }
  }
}
