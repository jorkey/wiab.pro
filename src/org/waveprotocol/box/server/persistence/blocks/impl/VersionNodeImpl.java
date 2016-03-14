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

import org.waveprotocol.box.server.persistence.blocks.FarBackwardMarker;
import org.waveprotocol.box.server.persistence.blocks.FarForwardMarker;
import org.waveprotocol.box.server.persistence.blocks.TopMarker;
import org.waveprotocol.box.server.persistence.blocks.Fragment;
import org.waveprotocol.box.server.persistence.blocks.ReadableSegmentSnapshot;
import org.waveprotocol.box.server.persistence.blocks.ReadableVersionNode;
import org.waveprotocol.box.server.persistence.blocks.VersionNode;
import org.waveprotocol.box.server.persistence.blocks.VersionInfo;
import org.waveprotocol.box.server.persistence.blocks.SegmentOperation;
import org.waveprotocol.box.server.persistence.blocks.FarBackwardLinks;
import org.waveprotocol.box.server.persistence.blocks.FarForwardLinks;
import org.waveprotocol.box.server.persistence.blocks.SnapshotMarker;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.List;

/**
 * Version node implementation.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class VersionNodeImpl implements VersionNode {
  /** Fragment of version node. */
  private final Fragment fragment;

  /** Top marker of the version. */
  private final TopMarker topMarker;

  /** Marker of far backward versions. */
  private final List<FarBackwardMarker> farBackwardMarkers;

  /** Marker of far forward versions. */
  private final List<FarForwardMarker> farForwardMarkers;

  /** Marker of snapshot. */
  private final SnapshotMarker snapshotMarker;
  
  /** Offset of next marker. */
  private final int nextMarkerOffset;

  /** Version info. */
  private VersionInfo versionInfo;

  /** Segment snapshot. */
  private ReadableSegmentSnapshot segmentSnapshot;

  /** Operation to this version from previous version. */
  private SegmentOperation fromPreviousVersionOperation;

  /** Rollback operation to previous version. */
  private SegmentOperation toPreviousVersionOperation;

  /** Next version node. */
  private VersionNode nextNode;

  /** Previous version node. */
  private VersionNode previousNode;

  /** Links of far backward nodes. */
  private FarBackwardLinks farBackwardLinks;

  /** Links of far forward nodes. */
  private FarForwardLinks farForwardLinks;

  public VersionNodeImpl() {
    fragment = null;
    topMarker = null;
    farBackwardMarkers = null;
    farForwardMarkers = null;
    nextMarkerOffset = -1;
    snapshotMarker = null;
  }

  VersionNodeImpl(Fragment fragment, TopMarker topMarker,
      List<FarBackwardMarker> farBackwardMarkers, List<FarForwardMarker> farForwardMarkers,
      SnapshotMarker snapshotMarker,
      int nextMarkerOffset) {
    this.fragment = fragment;
    this.topMarker = topMarker;
    this.farBackwardMarkers = farBackwardMarkers;
    this.farForwardMarkers = farForwardMarkers;
    this.snapshotMarker = snapshotMarker;
    this.nextMarkerOffset = nextMarkerOffset;
  }

  @Override
  public synchronized long getVersion() {
    return getVersionInfo().getVersion();
  }

  @Override
  public synchronized ParticipantId getAuthor() {
    return getVersionInfo().getAuthor();
  }

  @Override
  public synchronized long getTimestamp() {
    return getVersionInfo().getTimestamp();
  }

  @Override
  public synchronized VersionInfo getVersionInfo() {
    if (versionInfo == null && fragment != null) {
      versionInfo = fragment.deserializeVersionInfo(topMarker.getVersionInfoOffset());
    }
    return versionInfo;
  }

  @Override
  public synchronized VersionNode getNextNode() {
    if (nextNode == null && fragment != null && nextMarkerOffset != -1) {
      nextNode = fragment.getNodeByTopMarkerOffset(nextMarkerOffset);
    }
    return nextNode;
  }

  @Override
  public synchronized VersionNode getPreviousNode() {
    if (previousNode == null && fragment != null) {
      previousNode = fragment.getNodeByMarkerOffset(topMarker.getPreviousMarkerOffset());
    }
    return previousNode;
  }

  @Override
  public synchronized ReadableSegmentSnapshot getSegmentSnapshot() {
    if (segmentSnapshot == null && fragment != null && snapshotMarker != null) {
      segmentSnapshot = fragment.deserializeSegmentSnapshot(snapshotMarker.getSnapshotOffset());
    }
    return segmentSnapshot;
  }

  @Override
  public synchronized SegmentOperation getFromPreviousVersionOperation() {
    if (fromPreviousVersionOperation == null && fragment != null) {
      int offset = topMarker.getFromPreviousVersionOperationOffset();
      if (offset != -1) {
        WaveletOperationContext context = new WaveletOperationContext(getAuthor(),
          getTimestamp(), getVersion());
        fromPreviousVersionOperation = fragment.deserializeFromPreviousVersionOperation(
          offset, fragment.getSegmentId(), context);
      }
    }
    return fromPreviousVersionOperation;
  }

  @Override
  public synchronized SegmentOperation getToPreviousVersionOperation(ReadableVersionNode previousNode) {
    if (toPreviousVersionOperation == null && previousNode != null) {
      WaveletOperationContext context = new WaveletOperationContext(previousNode.getAuthor(),
        previousNode.getTimestamp(), previousNode.getVersion());
      SegmentOperation forwardOp = getFromPreviousVersionOperation();
      if (forwardOp != null) {
        toPreviousVersionOperation = forwardOp.revert(context);
      }
    }
    return toPreviousVersionOperation;
  }

  @Override
  public synchronized FarBackwardLinks getFarBackwardLinks() {
    if (farBackwardLinks == null && farBackwardMarkers != null) {
      farBackwardLinks = new FarBackwardLinksImpl();
      for (FarBackwardMarker marker : farBackwardMarkers) {
        WaveletOperationContext context = new WaveletOperationContext(getAuthor(), getTimestamp(), getVersion());
        SegmentOperation operation = null;
        if (marker.getFromPreviousFarVersionOperationOffset() != -1) {
          operation = fragment.deserializeFromPreviousFarVersionOperation(
            marker.getFromPreviousFarVersionOperationOffset(), fragment.getSegmentId(), context);
        }
        farBackwardLinks.addLink(new FarBackwardLinkImpl(marker.getDistanceToPreviousFarVersion(),
            fragment.getNodeByTopMarkerOffset(marker.getPreviousFarMarkerOffset()), operation));
      }
    }
    return farBackwardLinks;
  }

  @Override
  public synchronized FarForwardLinks getFarForwardLinks() {
    if (farForwardLinks == null && farForwardMarkers != null) {
      farForwardLinks = new FarForwardLinksImpl();
      for (FarForwardMarker marker : farForwardMarkers) {
        FarForwardLinkImpl link = new FarForwardLinkImpl(marker.getDistanceToNextFarVersion(),
          fragment.getNodeByTopMarkerOffset(marker.getNextFarMarkerOffset()));
        farForwardLinks.addLink(link);
      }
    }
    return farForwardLinks;
  }

  @Override
  public synchronized void setVersionInfo(VersionInfo versionInfo) {
    this.versionInfo = versionInfo;
  }

  @Override
  public synchronized void setSegmentSnapshot(ReadableSegmentSnapshot segmentSnapshot) {
    this.segmentSnapshot = segmentSnapshot;
  }

  @Override
  public synchronized void setNextNode(VersionNode nextNode) {
    this.nextNode = nextNode;
  }

  @Override
  public synchronized void setPreviousNode(VersionNode previousNode) {
    this.previousNode = previousNode;
  }

  @Override
  public synchronized void setFromPreviousVersionOperation(SegmentOperation operation) {
    this.fromPreviousVersionOperation = operation;
  }

  @Override
  public synchronized void addFarBackwardLinks(FarBackwardLinks farBackwardLinks) {
    Preconditions.checkArgument(this.farBackwardLinks == null && farBackwardMarkers == null,
      "Far backward links are already created");
    this.farBackwardLinks = farBackwardLinks;
  }

  @Override
  public synchronized void addFarForwardLinks(FarForwardLinks farForwardLinks) {
    Preconditions.checkArgument(this.farForwardLinks == null && farForwardMarkers == null,
      "Far forward links are already created");
    this.farForwardLinks = farForwardLinks;
  }

  @Override
  public String toString() {
    return Long.toString(getVersion());
  }
}