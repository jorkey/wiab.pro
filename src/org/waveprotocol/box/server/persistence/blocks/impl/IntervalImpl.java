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

import org.waveprotocol.box.server.persistence.blocks.*;
import org.waveprotocol.wave.model.util.Preconditions;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Continuous interval of history.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class IntervalImpl implements Interval {
  /** Version range. */
  private VersionRange range;

  /** Segment. */
  private Segment segment;

  /** Range map of fragments. */
  private final RangeMap<Long, ReadableFragment> rangeMap = TreeRangeMap.create();

  /** Last snapshot. */
  private final ImmutableSegmentSnapshot lastSnapshot;

  public IntervalImpl() {
    range = VersionRange.of();
    lastSnapshot = null;
  }

  public IntervalImpl(VersionRange range, Segment segment, Collection<ReadableFragment> fragments,
      ImmutableSegmentSnapshot lastSnapshot) {
    this.range = range;
    this.segment = segment;
    Preconditions.checkArgument(!range.isEmpty(), "Empty range");
    Preconditions.checkArgument(!fragments.isEmpty(), "No fragments");
    ReadableFragment prevFragment = null;
    Iterator<ReadableFragment> it = fragments.iterator();
    while (it.hasNext()) {
      long endVersion;
      ReadableFragment fragment = it.next();
      if (prevFragment == null) {
        Preconditions.checkArgument(range.from() >= fragment.getStartVersion(), "First fragment is not start of interval");
      } else {
        Preconditions.checkArgument(
          prevFragment.getLastModifiedVersion() + 1 == fragment.getStartVersion(), "Fragments are not sequential");
      }
      if (it.hasNext()) {
        Preconditions.checkArgument(fragment.getLastModifiedVersion() < range.to(), "Too many fragments");
        endVersion = fragment.getLastModifiedVersion();
      } else {
        endVersion = Math.max(fragment.getLastModifiedVersion(), range.to());
      }
      rangeMap.put(Range.closed(fragment.getStartVersion(), endVersion), fragment);
      prevFragment = fragment;
    }
    this.lastSnapshot = lastSnapshot;
  }

  @Override
  public boolean isEmpty() {
    return range.isEmpty();
  }

  @Override
  public Segment getSegment() {
    return segment;
  }

  @Override
  public VersionRange getRange() {
    return range;
  }

  @Override
  public Map<Range<Long>, ReadableFragment> getMapOfRanges() {
    return rangeMap.asMapOfRanges();
  }

  @Override
  public VersionNode getNearestSnapshotNode(long version) {
    ReadableFragment fragment = getFragment(version);
    return fragment.getNearestSnapshotNode(version);
  }

  @Override
  public ReadableSegmentSnapshot getLastSnapshot() {
    return lastSnapshot;
  }

  @Override
  public ReadableSegmentSnapshot getSnapshot(long version) {
    if (lastSnapshot != null && version == range.to()) {
      return lastSnapshot;
    }
    ReadableFragment fragment = getFragment(version);
    return fragment.getSnapshot(version);
  }

  @Override
  public long getLastModifiedTime(long version) {
    ReadableFragment fragment = getFragment(version);
    return fragment.getTimestamp(version);
  }

  @Override
  public Collection<SegmentOperation> getHistory(long fromVersion, long toVersion) {
    ReadableFragment fragment = getFragment(fromVersion);
    VersionNode node = fragment.getNode(fromVersion);
    return HistoryNavigator.getHistory(fragment, node, toVersion);
  }

  @Override
  public void append(Interval interval) {
    Preconditions.checkArgument(!interval.isEmpty(), "Empty interval");
    if (range.isEmpty()) {
      range = interval.getRange();
    } else if (interval.getRange().to() == range.from()- 1) {
      range = VersionRange.of(interval.getRange().from(), range.to());
    } else if (interval.getRange().from() == range.to() + 1) {
      range = VersionRange.of(range.from(), interval.getRange().to());
    } else {
      Preconditions.illegalState("Invalid interval range");
    }
    for (Map.Entry<Range<Long>, ReadableFragment> entry :  interval.getMapOfRanges().entrySet()) {
      rangeMap.put(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public String toString() {
    return range.toString();
  }

  private ReadableFragment getFragment(long version) {
    ReadableFragment fragment = rangeMap.get(version);
    Preconditions.checkNotNull(fragment, "Can't get fragment of version " + version);
    return fragment;
  }
}
