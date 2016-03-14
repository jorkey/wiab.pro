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
import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.SegmentOperation;
import org.waveprotocol.box.server.persistence.blocks.SegmentSnapshot;
import org.waveprotocol.box.server.persistence.blocks.*;
import org.waveprotocol.box.server.waveletstate.BlockFactory;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.util.logging.Log;

import com.google.common.collect.Range;
import com.google.common.collect.TreeRangeMap;
import com.google.common.collect.ImmutableList;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import java.lang.ref.SoftReference;
import java.util.List;

/**
 * Segment cache in memory.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class SegmentImpl implements Segment {

  private static final Log LOG = Log.get(SegmentImpl.class);

  /** Density of writing snapshots. */
  private static final float SNAPSHOT_RECORDING_DENSITY = (float)0.25;

  /** The Id of segment. */
  private final SegmentId segmentId;

  /** Block factory to provide block to write new fragment. */
  private final BlockFactory blockFactory;

  /** Range map of fragments. */
  private final TreeRangeMap<ReadableRangeValue, SoftReference<Fragment>> fragments = TreeRangeMap.create();

  /** Last fragment. */
  private Fragment lastFragment;

  /** Current snapshot of segment. */
  private SegmentSnapshot lastSnapshot;

  /** Last modified version. */
  private long lastModifiedVersion;

  /** Last modified time. */
  private long lastModifiedTime;

  /** Last snapshot version of segment. */
  private long lastStreamSnapshotVersion = 0;

  /** Version saved in the block. */
  private long savedVersion;

  /** Operations count recorded after snapshot. */
  private long operationsAfterSnapshot = 0;

  /** Summary operations size recorded after snapshot. */
  private long operationsSizeAfterSnapshot = 0;

  /** Interval to next attempt of writting snapshot. */
  private int writtingSnapshotAttemptInterval = 50;

  /** Locks. */
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
  private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

  public SegmentImpl(SegmentId segmentId, BlockFactory blockFactory) {
    this.segmentId = segmentId;
    this.blockFactory = blockFactory;
  }

  @Override
  public SegmentId getSegmentId() {
    return segmentId;
  }

  @Override
  public Fragment getFragment(long version) {
    readLock.lock();
    try {
      return getFragmentByVersion(RangeValue.of(version));
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Interval getInterval(long startVersion, long endVersion) {
    List<ReadableFragment> fragments = CollectionUtils.newLinkedList();
    readLock.lock();
    try {
      Fragment fragment = getFragmentByVersion(RangeValue.of(endVersion));
      if (fragment != null) {
        for (;;) {
          fragments.add(0, fragment);
          if (fragment.getStartVersion() <= startVersion) {
            break;
          }
          Fragment prevFragment = fragment.getPreviousFragment();
          if (prevFragment == null) {
            startVersion = fragment.getStartVersion();
            break;
          }
          fragment = prevFragment;
        };
      }
      if (!fragments.isEmpty()) {
        return new IntervalImpl(VersionRange.of(startVersion, endVersion), this, fragments,
          endVersion >= lastModifiedVersion ? getLastSnapshot() : null);
      }
      return new IntervalImpl();
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public ImmutableSegmentSnapshot getLastSnapshot() {
    readLock.lock();
    try {
      return lastSnapshot != null ? ImmutableSegmentSnapshot.create(lastSnapshot, lastFragment, lastModifiedVersion) : null;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public boolean hasContent() {
    return lastSnapshot != null && lastSnapshot.hasContent();
  }

  @Override
  public void registryFragment(final Fragment fragment) throws OperationException {
    writeLock.lock();
    try {
      if (getFragmentByVersion(RangeValue.of(fragment.getStartVersion())) != null) {
        LOG.warning("Fragment in this range already exists - avoid registry");
        return;
      }
      Preconditions.checkArgument(!fragment.isLast() || lastFragment == null,
        "Last fragment already exists");
      addFragment(fragment);
      if (fragment.isLast()) {
        initFromLastFragment(fragment);
      }
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public void releaseFragment(Fragment fragment) {
    writeLock.lock();
    try {
      fragments.remove(Range.<ReadableRangeValue>closed(
        RangeValue.of(fragment.getStartVersion()), fragment.getEndRangeValue()));
      if (fragment == lastFragment) {
        lastFragment = null;
      }
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public SegmentOperation addOperation(ParticipantId author, long timestamp, SegmentOperation segmentOperation)
      throws WaveletStateException, OperationException {
    Timer timer = Timing.start("SegmentImpl.addOperation");
    writeLock.lock();
    try {
      if (lastSnapshot == null) {
        if (!fragments.asMapOfRanges().isEmpty()) {
          Fragment fragment = fragments.get(fragments.span().upperEndpoint()).get();
          Preconditions.checkNotNull(fragment, "Existing fragment was released");
          initFromLastFragment(fragment);
        } else {
          lastSnapshot = SegmentSnapshotImpl.createSnapshot(segmentId);
        }
      }
      List<WaveletOperation> reverseOperations = CollectionUtils.newLinkedList();
      WaveletOperation lastOp = null;
      int opsApplied = 0;
      try {
        for (WaveletOperation op : segmentOperation.getOperations()) {
          lastOp = op;
          reverseOperations.addAll(0, lastSnapshot.applyAndReturnReverse(op));
          opsApplied++;
        }
      } catch (OperationException ex) {
        rollbackOperations(reverseOperations);
        throw new OperationException("Only applied " + opsApplied + " of "
          + segmentOperation.getOperations().size() + ", rolling back, failed op was " + lastOp, ex);
      }
      lastModifiedVersion = segmentOperation.getTargetVersion();
      lastModifiedTime = segmentOperation.getTimestamp();
      VersionNode node = new VersionNodeImpl();
      node.setVersionInfo(new VersionInfoImpl(segmentOperation.getTargetVersion(), author, timestamp));
      node.setFromPreviousVersionOperation(segmentOperation);
      operationsSizeAfterSnapshot += segmentOperation.serialize().getData().length();
      operationsAfterSnapshot++;
      writeVersionNode(node);
      if (lastStreamSnapshotVersion == 0 || operationsAfterSnapshot >= writtingSnapshotAttemptInterval) {
        int size = lastSnapshot.getRawSnapshot().serialize().getData().length();
        float density = ((float)size) / (operationsSizeAfterSnapshot + size);
        if (lastStreamSnapshotVersion == 0 || density <= SNAPSHOT_RECORDING_DENSITY) {
          writeSnapshotToNode(node);
        } else {
          writtingSnapshotAttemptInterval *= 1.5;
        }
      }
      return new SegmentOperationImpl(ImmutableList.copyOf(reverseOperations));
    } finally {
      writeLock.unlock();
      Timing.stop(timer);
    }
  }

  @Override
  public void flush() {
    Timer timer = Timing.start("SegmentImpl.flush");
    writeLock.lock();
    try {
      if (lastModifiedVersion > savedVersion && lastFragment != null) {
        lastFragment.writeLastSnapshot(lastSnapshot);
        savedVersion = lastModifiedVersion;
      }
    } finally {
      writeLock.unlock();
      Timing.stop(timer);
    }
  }

  private void initFromLastFragment(Fragment fragment) throws OperationException {
    Preconditions.checkArgument(fragment.isLast(), "Fragment is not last");
    lastModifiedVersion = fragment.getLastModifiedVersion();
    lastModifiedTime = fragment.getTimestamp(lastModifiedVersion);
    ReadableSegmentSnapshot snapshot = fragment.getLastSnapshot();
    Preconditions.checkNotNull(snapshot, "Fragment has not last snapshot");
    lastSnapshot = snapshot.duplicate();
    lastStreamSnapshotVersion = fragment.getLastStreamSnapshotVersion();
    lastFragment = fragment;
    savedVersion = lastModifiedVersion;
  }

  private void writeVersionNode(final VersionNode versionNode) {
    Timer timer = Timing.start("SegmentImpl.writeVersionNode");
    try {
      boolean fragmentFirst = false;
      if (lastFragment == null) {
        fragmentFirst = fragments.asMapOfRanges().isEmpty();
      } else if (checkFinishCondition(lastFragment.getBlock())) {
        lastFragment.finish(versionNode.getVersion()-1);
        lastFragment = null;
      }
      if (lastFragment != null) {
        lastFragment.writeVersionNode(versionNode);
      } else {
        Block lastBlock = blockFactory.getOrCreateBlockToWriteNewFragment();
        lastBlock.getWriteLock().lock();
        try {
          lastFragment = lastBlock.createFragment(segmentId, fragmentFirst);
          lastFragment.writeVersionNode(versionNode);
          if (!fragmentFirst && versionNode.getSegmentSnapshot() == null) {
            writeSnapshotToNode(versionNode);
          }
        } finally {
          lastBlock.getWriteLock().unlock();
        }
        addFragment(lastFragment);
      }
    } finally {
      Timing.stop(timer);
    }
  }

  private void writeSnapshotToNode(VersionNode node) {
    node.setSegmentSnapshot(lastSnapshot.duplicate());
    lastFragment.writeSnapshot(node);
    lastStreamSnapshotVersion = node.getVersion();
    operationsAfterSnapshot = 0;
    operationsSizeAfterSnapshot = 0;
  }

  private boolean checkFinishCondition(Block block) {
    if (block.getSize() >= Block.LOW_WATER) {
      if (!segmentId.isBlip() || !IdUtil.isBlipId(segmentId.getBlipId())) {
        return true;
      }
      if (block.getSize() >= Block.HIGH_WATER) {
        return true;
      }
    }
    return false;
  }

  private void addFragment(final Fragment fragment) {
    ReadableRangeValue lower = RangeValue.of(fragment.getStartVersion());
    ReadableRangeValue upper = fragment.getEndRangeValue();
    fragments.put(Range.closed(lower, upper), new SoftReference<>(fragment));
    fragment.setSegment(this);
  }

  private Fragment getFragmentByVersion(RangeValue version) {
    SoftReference<Fragment> reference = fragments.get(version);
    if (reference != null) {
      Fragment fragment = reference.get();
      return fragment;
    }
    return null;
  }

  /**
   * Like applyWaveletOperations, but throws an {@link IllegalStateException}
   * when ops fail to apply. Is used for rolling back operations.
   *
   * @param ops to apply for rollback
   */
  private void rollbackOperations(List<WaveletOperation> ops) {
    for (int i = ops.size() - 1; i >= 0; i--) {
      try {
        lastSnapshot.applyAndReturnReverse(ops.get(i));
      } catch (OperationException e) {
        throw new IllegalStateException(
            "Failed to roll back operation with inverse " + ops.get(i), e);
      }
    }
  }
}
