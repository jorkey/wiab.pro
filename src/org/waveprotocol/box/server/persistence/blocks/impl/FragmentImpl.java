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

import org.waveprotocol.box.server.persistence.blocks.TopMarker;
import org.waveprotocol.box.server.persistence.blocks.*;
import org.waveprotocol.box.server.persistence.blocks.impl.aggregator.OperationAggregator;

import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;

import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.OperationRuntimeException;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.util.logging.Log;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;

import java.util.List;

/**
 * Fragment of segment in the block.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class FragmentImpl implements Fragment {
  private static final Log LOG = Log.get(FragmentImpl.class);

  /** Length of aggregation step. */
  private final int AGGREGATION_STEP_LENGTH = 10;

  /** Maximum degree to calculate length of aggregation step. */
  private final int AGGREGATION_MAX_LEVELS = 4;

  /** Time interval to aggregating blips. */
  static final int AGGREGATION_BLIP_TIME_INTERVAL_MS = 60*60*1000;

  /** Block of fragment. */
  private final Block block;

  /** Id of segment. */
  private final SegmentId segmentId;

  /** Markers and snapshots index. */
  private final FragmentIndex fragmentIndex;

  /** Nodes and its top marker offsets. */
  private final BiMap<Integer, VersionNode> nodeTopOffsetMap =
    Maps.<Integer, VersionNode>synchronizedBiMap(HashBiMap.<Integer, VersionNode>create());

  /** First node of fragment. */
  private volatile VersionNode firstNode = null;

  /** Last node of fragment. */
  private volatile VersionNode lastNode = null;

  /** Segment of fragment. */
  private Segment segment;

  /** End range value. */
  private final RangeValue endRangeValue = RangeValue.of(0);

  /**
   * Operation aggregator.
   * Writes nodes of processed operations to block.
   */
  private OperationAggregator aggregator;

  public FragmentImpl(Block block, SegmentId segmentId, FragmentIndex fragmentIndex) {
    this.block = block;
    this.segmentId = segmentId;
    this.fragmentIndex = fragmentIndex;
    if (fragmentIndex.getLastMarkerOffset() != -1) {
      firstNode = getNodeByTopMarkerOffset(0);
      lastNode = getNodeByMarkerOffset(fragmentIndex.getLastMarkerOffset());
    }
  }

  @Override
  public void setSegment(Segment segment) {
    this.segment = segment;
  }

  @Override
  public SegmentId getSegmentId() {
    return segmentId;
  }

  @Override
  public ReadableRangeValue getEndRangeValue() {
    endRangeValue.set(isLast() ? Long.MAX_VALUE : getLastModifiedVersion());
    return endRangeValue;
  }

  @Override
  public Block getBlock() {
    return block;
  }

  @Override
  public Fragment getPreviousFragment() {
    Preconditions.checkNotNull(segment, "Segment is null");
    if (getStartVersion() == 0) {
      return null;
    }
    return segment.getFragment(getStartVersion()-1);
  }

  @Override
  public Fragment getNextFragment() {
    Preconditions.checkNotNull(segment, "Segment is null");
    if (fragmentIndex.isLast()) {
      return null;
    }
    return segment.getFragment(getLastModifiedVersion()+1);
  }

  @Override
  public long getStartVersion() {
    return firstNode.getVersion();
  }

  @Override
  public long getLastModifiedVersion() {
    return lastNode.getVersion();
  }

  @Override
  public long getLastStreamSnapshotVersion() {
    return fragmentIndex.getLastStreamSnapshotVersion();
  }

  @Override
  public boolean isFirst() {
    return fragmentIndex.isFirst();
  }

  @Override
  public boolean isLast() {
    return fragmentIndex.isLast();
  }

  @Override
  public VersionNode getFirstNode() {
    return firstNode;
  }

  @Override
  public VersionNode getLastNode() {
    return lastNode;
  }

  @Override
  public long getTimestamp(long version) {
    return getNode(version).getTimestamp();
  }

  @Override
  public ReadableSegmentSnapshot getLastSnapshot() {
    byte[] snapshot = fragmentIndex.getLastSnapshot();
    if (snapshot != null) {
      return SegmentSnapshotImpl.deserialize(snapshot, segmentId);
    }
    return null;
  }
  
  @Override
  public ReadableSegmentSnapshot getSnapshot(long version) {
    Timer timer = Timing.start("FragmentImpl.getSnapshot");
    try {
      SegmentSnapshot snapshot;
      VersionNode nearestNode = getNearestNode(version);
      ReadableSegmentSnapshot nearestNodeSnapshot = nearestNode.getSegmentSnapshot();
      if (nearestNodeSnapshot != null) {
        if (nearestNode.getVersion() == version) {
          return nearestNodeSnapshot;
        }
        snapshot = nearestNodeSnapshot.duplicate();
      } else {
        Preconditions.checkArgument(nearestNode.getVersion() == 0, "Nearest node has not snapshot and is not first");
        snapshot = SegmentSnapshotImpl.createSnapshot(segmentId);
      }
      if (nearestNode.getVersion() != version) {
        List<SegmentOperation> operations = HistoryNavigator.getFragmentHistory(nearestNode, version);
        try {
          for (SegmentOperation operation : operations) {
            for (WaveletOperation op : operation.getOperations()) {
              snapshot.applyAndReturnReverse(op);
            }
          }
        } catch (OperationException ex) {
          throw new OperationRuntimeException("Making snapshot", ex);
        }
      }
      return snapshot;
    } finally {
      Timing.stop(timer);
    }
  }

  @Override
  public VersionNode getNode(long version) {
    VersionNode nearestNode = getNearestNode(version);
    VersionNode node = HistoryNavigator.findFragmentNode(nearestNode, version);
    Preconditions.checkNotNull(node, "Can't find version " + version);
    return node;
  }

  @Override
  public VersionNode getNearestNode(long version) {
    VersionNode node = getNearestSnapshotNode(version);
    if (node == null) {
      node = firstNode;
    }
    return node;
  }

  @Override
  public VersionNode getNearestSnapshotNode(long version) {
    int index = fragmentIndex.getNearestSnapshotIndex(version);
    if (index == -1) {
      return null;
    }
    int snapshotMarkerOffset = fragmentIndex.getSnapshotMarkerOffset(index);
    return getVersionNodeByMarkerOffset(snapshotMarkerOffset);
  }

  @Override
  public VersionNode getNodeByTopMarkerOffset(int topMarkerOffset) {
    if (topMarkerOffset == -1) {
      return null;
    }
    return getVersionNodeByTopMarkerOffset(topMarkerOffset);
  }

  @Override
  public VersionNode getNodeByMarkerOffset(int bottomMarkerOffset) {
    if (bottomMarkerOffset == -1) {
      return null;
    }
    return getVersionNodeByMarkerOffset(bottomMarkerOffset);
  }

  @Override
  public VersionInfo deserializeVersionInfo(int offset) {
    return block.deserializeVersionInfo(offset);
  }

  @Override
  public SegmentOperation deserializeFromPreviousVersionOperation(int offset, SegmentId segmentId, WaveletOperationContext context) {
    return block.deserializeSegmentOperation(offset, segmentId, context);
  }

  @Override
  public SegmentOperation deserializeFromPreviousFarVersionOperation(int offset, SegmentId segmentId, WaveletOperationContext context) {
    return block.deserializeSegmentOperation(offset, segmentId, context);
  }

  @Override
  public ReadableSegmentSnapshot deserializeSegmentSnapshot(int offset) {
    return block.deserializeSegmentSnapshot(offset, segmentId);
  }

  @Override
  public void writeVersionNode(VersionNode node) {
    Timer timer = Timing.start("FragmentImpl.writeVersionNode");
    block.getWriteLock().lock();
    try {
      if (firstNode == null) {
        firstNode = node;
      }
      if (lastNode != null) {
        lastNode.setNextNode(node);
        node.setPreviousNode(lastNode);
      }
      if (aggregator == null) {
        aggregator = new OperationAggregator(AGGREGATION_STEP_LENGTH, AGGREGATION_MAX_LEVELS,
            new OperationAggregator.Callback() {

              @Override
              public void writeNode(VersionNode node) {
                block.getWriteLock().lock();
                try {
                  lastNode = node;
                  writeVersionToBlock(node);
                } finally {
                  block.getWriteLock().unlock();
                }
              }

              @Override
              public void writeFarBackwardLink(VersionNode node, FarBackwardLink farBackwardLink) {
                block.getWriteLock().lock();
                try {
                  writeFarBackwardLinkToBlock(node, farBackwardLink);
                } finally {
                  block.getWriteLock().unlock();
                }
              }

              @Override
              public void writeFarForwardLink(VersionNode node, FarForwardLink farForwardLink) {
                block.getWriteLock().lock();
                try {
                  writeFarForwardLinkToBlock(node, farForwardLink);
                } finally {
                  block.getWriteLock().unlock();
                }
              }

              @Override
              public void rewriteFarForwardLink(VersionNode node, FarForwardLink oldFarForwardLink, FarForwardLink newFarForwardLink) {
                block.getWriteLock().lock();
                try {
                  rewriteFarForwardLinkInBlock(node, oldFarForwardLink, newFarForwardLink);
                } finally {
                  block.getWriteLock().unlock();
                }
              }
            });
        aggregator.setAggregateByAuthor(true);
        aggregator.setTimeInterval(AGGREGATION_BLIP_TIME_INTERVAL_MS);
      }
      aggregator.addNode(node);
    } finally {
      block.getWriteLock().unlock();
      Timing.stop(timer);
    }
    block.writeAccessNotify(this);
  }

  @Override
  public void writeLastSnapshot(ReadableSegmentSnapshot snapshot) {
    Timer timer = Timing.start("FragmentImpl.writeLastSnapshot");
    block.getWriteLock().lock();
    try {
      fragmentIndex.setLastSnapshot(snapshot.serialize().toByteArray());
    } finally {
      block.getWriteLock().unlock();
      Timing.stop(timer);
    }
    block.writeAccessNotify(this);
  }
  
  @Override
  public void writeSnapshot(VersionNode node) {
    Timer timer = Timing.start("FragmentImpl.writeSnapshot");
    block.getWriteLock().lock();
    try {
      writeSnapshotToBlock(node);
    } finally {
      block.getWriteLock().unlock();
      Timing.stop(timer);
    }
    block.writeAccessNotify(this);
  }

  @Override
  public void finish(long endVersion) {
    Timer timer = Timing.start("FragmentImpl.finish");
    block.getWriteLock().lock();
    try {
      if (aggregator != null) {
        aggregator.complete();
      }
      if (lastNode.getVersion() < endVersion) {
        VersionNodeImpl node = new VersionNodeImpl();
        node.setVersionInfo(new VersionInfoImpl(endVersion));
        writeVersionNode(node);
      }
      fragmentIndex.finish();
      endRangeValue.set(getLastNode().getVersion());
    } finally {
      block.getWriteLock().unlock();
      Timing.stop(timer);
    }
    block.writeAccessNotify(this);
  }

  @Override
  protected void finalize() throws Throwable {
    LOG.fine("FragmentImpl.finalize");
    if (isLast() && segment != null) {
      segment.flush();
    }
    super.finalize();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("segmentId ").append(segmentId.toString());
    sb.append(", blockId ").append(block.getBlockId());
    return sb.toString();
  }

  private VersionNode getVersionNodeByTopMarkerOffset(int topMarkerOffset) {
    VersionNode node = nodeTopOffsetMap.get(topMarkerOffset);
    if (node == null) {
      TopMarker topMarker = fragmentIndex.deserializeTopMarker(topMarkerOffset);
      if (topMarker == null) {
        return null;
      }
      List<FarBackwardMarker> farBackwardMarkers = CollectionUtils.newLinkedList();
      List<FarForwardMarker> farForwardMarkers = CollectionUtils.newLinkedList();
      SnapshotMarker snapshotMarker = null;
      int offset = topMarker.getNextMarkerOffset();
      for (;;) {
        Marker marker = fragmentIndex.deserializeAdditionalMarker(offset);
        if (marker == null) {
          break;
        }
        if (marker instanceof FarBackwardMarker) {
          farBackwardMarkers.add((FarBackwardMarker)marker);
        } else if (marker instanceof FarForwardMarker) {
          farForwardMarkers.add((FarForwardMarker)marker);
        } else if (marker instanceof SnapshotMarker) {
          snapshotMarker = (SnapshotMarker)marker;
        } else {
          Preconditions.illegalState("Invalid marker type " + marker);
        }
        offset = marker.getNextMarkerOffset();
      }
      node = new VersionNodeImpl(FragmentImpl.this, topMarker,
        !farBackwardMarkers.isEmpty() ? farBackwardMarkers : null,
        !farForwardMarkers.isEmpty() ? farForwardMarkers : null,
        snapshotMarker, offset);
      nodeTopOffsetMap.forcePut(topMarkerOffset, node);
    }
    return node;
  }

  private VersionNode getVersionNodeByMarkerOffset(int additionalMarkerOffset) {
    int topMarkerOffset = fragmentIndex.findTopMarkerOffset(additionalMarkerOffset);
    Preconditions.checkArgument(topMarkerOffset != -1, "Can't find top marker");
    return getVersionNodeByTopMarkerOffset(topMarkerOffset);
  }

  private void writeVersionToBlock(VersionNode node) {
    Timer timer = Timing.start("FragmentImpl.writeVersionToBlock");
    try {
      TopMarker topMarker = createTopMarker(node);
      int topMarkerOffset = fragmentIndex.addMarker(topMarker);
      nodeTopOffsetMap.forcePut(topMarkerOffset, node);
    } finally {
      Timing.stop(timer);
    }
  }

  private void writeFarBackwardLinkToBlock(VersionNode node, FarBackwardLink farBackwardLink) {
    Timer timer = Timing.start("FragmentImpl.writeFarBackwardLinksToBlock");
    try {
      Preconditions.checkArgument(node == lastNode, "Far backward link can be added only to last node, " +
          "last node version " + lastNode.getVersion() + ", link version " + node.getVersion());
      fragmentIndex.addMarker(createFarBackwardMarker(farBackwardLink));
    } finally {
      Timing.stop(timer);
    }
  }

  private void writeFarForwardLinkToBlock(VersionNode node, FarForwardLink farForwardLink) {
    Timer timer = Timing.start("FragmentImpl.writeFarForwardLinksToBlock");
    try {
      Preconditions.checkArgument(node == lastNode, "Far forward link can be added only to last node, " +
          "last node version " + lastNode.getVersion() + ", link version " + node.getVersion());
      fragmentIndex.addMarker(createFarForwardMarker(farForwardLink));
    } finally {
      Timing.stop(timer);
    }
  }

  private void writeSnapshotToBlock(VersionNode node) {
    Timer timer = Timing.start("FragmentImpl.writeSnapshotToBlock");
    try {
      Preconditions.checkArgument(node == lastNode, "Snapshot can be added only to last node, " +
          "last node version " + lastNode.getVersion() + ", link version " + node.getVersion());
      ReadableSegmentSnapshot snapshot = node.getSegmentSnapshot();
      Preconditions.checkNotNull(snapshot, "No snapshot");
      int markerOffset = fragmentIndex.addMarker(createSnapshotMarker(snapshot));
      fragmentIndex.addSnapshot(node.getVersion(), markerOffset);
    } finally {
      Timing.stop(timer);
    }
  }

  private void rewriteFarForwardLinkInBlock(VersionNode node, FarForwardLink oldFarForwardLink, FarForwardLink newFarForwardLink) {
    Timer timer = Timing.start("FragmentImpl.rewriteFarForwardLinksToBlock");
    try {
      Integer topMarkerOffset = nodeTopOffsetMap.inverse().get(node);
      Preconditions.checkNotNull(topMarkerOffset, "Node of version " + node.getVersion() + " is not registered");
      TopMarker topMarker = fragmentIndex.deserializeTopMarker(topMarkerOffset);
      Preconditions.checkNotNull(topMarker, "No top marker at offset " + topMarkerOffset);
      int offset = topMarker.getNextMarkerOffset();
      for (;;) {
        Marker marker = fragmentIndex.deserializeAdditionalMarker(offset);
        if (marker == null) {
          break;
        }
        if (marker instanceof FarForwardMarker &&
            ((FarForwardMarker)marker).getDistanceToNextFarVersion() == oldFarForwardLink.getDistanceToNextFarVersion()) {
          fragmentIndex.writeMarker(offset, createFarForwardMarker(newFarForwardLink));
          return;
        }
        offset = marker.getNextMarkerOffset();
      }
      Preconditions.illegalState("Node has not reserved far forward marker");
    } finally {
      Timing.stop(timer);
    }
  }

  private TopMarker createTopMarker(VersionNode node) {
    int versionInfoOffset = -1, fromPreviousVersionOperationOffset = -1;
    versionInfoOffset = block.writeVersionInfo(node.getVersionInfo());
    if (node.getFromPreviousVersionOperation() != null) {
      fromPreviousVersionOperationOffset = block.writeSegmentOperation(node.getFromPreviousVersionOperation());
    }
    return new TopMarkerImpl(versionInfoOffset, fromPreviousVersionOperationOffset);
  }

  private FarBackwardMarker createFarBackwardMarker(FarBackwardLink farBackwardLink) {
    VersionNode farNode = farBackwardLink.getPreviousFarNode();
    Integer farNodeOffset = nodeTopOffsetMap.inverse().get(farNode);
    Preconditions.checkNotNull(farNodeOffset, "Node of version " + farNode.getVersion() + " is not registered");
    int operationOffset = -1;
    SegmentOperation operation = farBackwardLink.getFromPreviousFarVersionOperation();
    if (operation != null) {
      operationOffset = block.writeSegmentOperation(operation);
    }
    return new FarBackwardMarkerImpl(farBackwardLink.getDistanceToPreviousFarVersion(),
      farNodeOffset, operationOffset);
  }

  private FarForwardMarker createFarForwardMarker(FarForwardLink farForwardLink) {
    VersionNode farNode = farForwardLink.getNextFarNode();
    if (farNode == null) {
      return new FarForwardMarkerImpl(farForwardLink.getDistanceToNextFarVersion());
    }
    Integer farNodeOffset = nodeTopOffsetMap.inverse().get(farNode);
    Preconditions.checkNotNull(farNodeOffset, "Node of version " + farNode.getVersion() + " is not registered");
    return new FarForwardMarkerImpl(farForwardLink.getDistanceToNextFarVersion(), farNodeOffset);
  }

  private SnapshotMarker createSnapshotMarker(ReadableSegmentSnapshot snapshot) {
    int snapshotOffset = block.writeSegmentSnapshot(snapshot);
    return new SnapshotMarkerImpl(snapshotOffset);
  }
}
