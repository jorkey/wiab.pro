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

import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.util.Preconditions;

import org.waveprotocol.box.server.persistence.blocks.VersionRange;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Request to raw fragments supplier.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
class FragmentsRequest {
  final static private long NO_VERSION = -1;

  public static class Builder {
    /** The start versions of segments to get by priority order. */
    private final ImmutableMap.Builder<SegmentId, VersionRange> rangesBuilder = ImmutableMap.builder();

    /** Get fragments from specified version (inclusive). */
    private long startVersion = NO_VERSION;

    /** Get fragments to specified version (inclusive). */
    private long endVersion = NO_VERSION;

    /** Get available fragments from the cache. */
    private boolean onlyFromCache = false;

    /** Max reply size. */
    private int maxReplySize = -1;

    public Builder addRange(SegmentId segmentId, VersionRange range) {
      this.rangesBuilder.put(segmentId, range);
      return this;
    }

    public Builder addRange(SegmentId segmentId, long version) {
      this.rangesBuilder.put(segmentId, VersionRange.of(version, version));
      return this;
    }

    public Builder addRanges(Collection<SegmentId> segmentIds, long startVersion, long endVersion) {
      for (SegmentId segmentId : segmentIds) {
        this.rangesBuilder.put(segmentId, VersionRange.of(startVersion, endVersion));
      }
      return this;
    }

    public Builder addRanges(Map<SegmentId, VersionRange> segments) {
      this.rangesBuilder.putAll(segments);
      return this;
    }

    public Builder setStartVersion(long startVersion) {
      this.startVersion = startVersion;
      return this;
    }

    public Builder setEndVersion(long endVersion) {
      this.endVersion = endVersion;
      return this;
    }

    public Builder setOnlyFromCache(boolean onlyFromCache) {
      this.onlyFromCache = onlyFromCache;
      return this;
    }

    public Builder setMaxReplySize(int maxReplySize) {
      this.maxReplySize = maxReplySize;
      return this;
    }

    public FragmentsRequest build() {
      ImmutableMap<SegmentId, VersionRange> ranges = rangesBuilder.build();
      if (ranges.isEmpty()) {
        Preconditions.checkArgument(startVersion != NO_VERSION, "start version is not specified");
        Preconditions.checkArgument(endVersion != NO_VERSION, "endVersion is not specified");
      } else {
        Preconditions.checkArgument(startVersion == NO_VERSION, "common start version specified with ranges");
        Preconditions.checkArgument(endVersion == NO_VERSION, "common end version specified with ranges");
      }
      return new FragmentsRequest(ranges, startVersion, endVersion, onlyFromCache, maxReplySize);
    }
  }

  /** The segments and its ranges to get by priority order. */
  final private ImmutableMap<SegmentId, VersionRange> ranges;

  /** Get info from specified version (inclusive). */
  final private long startVersion;

  /** Get info to specified version (inclusive). */
  final private long endVersion;

  /** Get available fragments from the cache. */
  final private boolean onlyFromCache;

  /** Max reply size. */
  final private int maxReplySize;

  private FragmentsRequest(ImmutableMap<SegmentId, VersionRange> range, long startVersion, long endVersion,
      boolean onlyFromCache, int maxReplySize) {
    this.ranges = range;
    this.startVersion = startVersion;
    this.endVersion = endVersion;
    this.onlyFromCache = onlyFromCache;
    this.maxReplySize = maxReplySize;
  }

  Set<SegmentId> getSegmentIds() {
    return Collections.unmodifiableSet(ranges.keySet());
  }

  Set<SegmentId> getNotBlipSegmentIds() {
    ImmutableSet.Builder<SegmentId> builder = ImmutableSet.builder();
    for (SegmentId segmentId : ranges.keySet()) {
      if (!segmentId.isBlip()) {
        builder.add(segmentId);
      }
    }
    return builder.build();
  }

  ImmutableMap<SegmentId, VersionRange> getVersionRanges() {
    return ranges;
  }

  long getStartVersion(SegmentId segmentId) {
    VersionRange range = ranges.get(segmentId);
    Preconditions.checkNotNull(range, "No segment " + segmentId.toString());
    return range.from();
  }

  long getEndVersion(SegmentId segmentId) {
    VersionRange range = ranges.get(segmentId);
    Preconditions.checkNotNull(range, "No segment " + segmentId.toString());
    return range.to();
  }

  long getStartVersion() {
    return startVersion;
  }

  long getEndVersion() {
    return endVersion;
  }

  boolean isOnlyFromCache() {
    return onlyFromCache;
  }

  int getMaxReplySize() {
    return maxReplySize;
  }
}
