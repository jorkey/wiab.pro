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

package org.waveprotocol.box.server.frontend;

import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.raw.RawFragment;
import org.waveprotocol.wave.model.raw.RawFragmentFactory;
import org.waveprotocol.wave.model.raw.RawOperation;
import org.waveprotocol.wave.model.util.Preconditions;

import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
class FragmentsBuffer {
  private final WaveletName waveletName;
  private final Map<SegmentId, Interval> intervals = new LinkedHashMap<>();
  private final Map<SegmentId, RawFragment> rawFragments = new LinkedHashMap<>();
  private final Set<SegmentId> toSerialize = new LinkedHashSet<>();

  private boolean serializeSnapshots = true;
  private HashedVersion lastModifiedVersion;
  private long lastModifiedTime;
  private int serializedSize = 0;

  FragmentsBuffer(WaveletName waveletName) {
    this.waveletName = waveletName;
  }

  void setSerializeSnapshots(boolean serializeSnapshots) {
    this.serializeSnapshots = serializeSnapshots;
  }

  void setLastModifiedTime(long lastModifiedTime) {
    this.lastModifiedTime = lastModifiedTime;
  }

  void setLastModifiedVersion(HashedVersion lastModifiedVersion) {
    this.lastModifiedVersion = lastModifiedVersion;
  }

  void addInterval(SegmentId segmentId, Interval interval) {
    Preconditions.checkArgument(!intervals.containsKey(segmentId), "Segment " + segmentId.toString() + " already exists");
    intervals.put(segmentId, interval);
    toSerialize.add(segmentId);
  }

  void removeSegment(SegmentId segmentId) {
    intervals.remove(segmentId);
    rawFragments.remove(segmentId);
    toSerialize.remove(segmentId);
  }

  WaveletName getWaveletName() {
    return waveletName;
  }

  boolean isEmpty() {
    return intervals.isEmpty();
  }

  HashedVersion getLastModifiedVersion() {
    return lastModifiedVersion;
  }

  long getLastModifiedTime() {
    return lastModifiedTime;
  }

  Map<SegmentId, Interval> getIntervals() {
    return Collections.unmodifiableMap(intervals);
  }

  Map<SegmentId, RawFragment> getRawFragments() {
    serializePending();
    return Collections.unmodifiableMap(rawFragments);
  }

  int getSerializedSize() {
    serializePending();
    return serializedSize;
  }

  private void serializePending() {
    Timer timer = Timing.start("FragmentsBuffer.serializePending");
    try {
      Iterator<SegmentId> it = toSerialize.iterator();
      while (it.hasNext()) {
        SegmentId segmentId = it.next();
        RawFragment rawFragment = RawFragmentFactory.makeRawFragment(segmentId, intervals.get(segmentId), serializeSnapshots);
        rawFragments.put(segmentId, rawFragment);
        serializedSize += getFragmentSize(rawFragment);
        it.remove();
      }
    } finally {
      Timing.stop(timer);
    }
  }

  private static int getFragmentSize(RawFragment fragment) {
    int size = 0;
    if (fragment.hasSnapshot()) {
      size += fragment.getSnapshot().serialize().getData().length();
    }
    if (fragment.hasAdjustOperations()) {
      for (RawOperation op : fragment.getAdjustOperations()) {
        size += op.serialize().getData().length();
      }
    }
    if (fragment.hasDiffOperations()) {
      for (RawOperation op : fragment.getDiffOperations()) {
        size += op.serialize().getData().length();
      }
    }
    return size;
  }
}
