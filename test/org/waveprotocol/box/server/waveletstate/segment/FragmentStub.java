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

package org.waveprotocol.box.server.waveletstate.segment;

import java.util.List;
import org.waveprotocol.box.server.persistence.blocks.Block;
import org.waveprotocol.box.server.persistence.blocks.Fragment;
import org.waveprotocol.box.server.persistence.blocks.ReadableRangeValue;
import org.waveprotocol.box.server.persistence.blocks.ReadableSegmentSnapshot;
import org.waveprotocol.box.server.persistence.blocks.Segment;
import org.waveprotocol.box.server.persistence.blocks.SegmentOperation;
import org.waveprotocol.box.server.persistence.blocks.SegmentSnapshot;
import org.waveprotocol.box.server.persistence.blocks.VersionInfo;
import org.waveprotocol.box.server.persistence.blocks.VersionNode;
import org.waveprotocol.box.server.persistence.blocks.WritableSegmentSnapshot;
import org.waveprotocol.box.server.persistence.blocks.impl.BlipSnapshot;
import org.waveprotocol.box.server.persistence.blocks.impl.ParticipantsSnapshot;
import org.waveprotocol.box.server.persistence.blocks.impl.RangeValue;
import org.waveprotocol.box.server.persistence.protos.ProtoBlockStore;
import org.waveprotocol.box.server.persistence.protos.ProtoBlockStore.SegmentSnapshotRecord;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.raw.RawSnapshot;

/**
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
class FragmentStub implements Fragment {
  private final Block block;
  private final long startVersion;
  private final long endVersion;
  private final boolean last;
  private Segment segment;

  public FragmentStub(Block block, long startVersion, long endVersion, boolean last) {
    this.block = block;
    this.startVersion = startVersion;
    this.endVersion = endVersion;
    this.last = last;
  }

  @Override
  public void setSegment(Segment segment) {
    this.segment = segment;
  }

  @Override
  public SegmentId getSegmentId() {
    return segment.getSegmentId();
  }

  @Override
  public Block getBlock() {
    return block;
  }

  @Override
  public Fragment getPreviousFragment() {
    if (getStartVersion() == 0) {
      return null;
    }
    return segment.getFragment(getStartVersion()-1);
  }

  @Override
  public Fragment getNextFragment() {
    if (isLast()) {
      return null;
    }
    return segment.getFragment(getLastModifiedVersion()+1);
  }

  @Override
  public long getStartVersion() {
    return startVersion;
  }

  @Override
  public long getLastModifiedVersion() {
    return endVersion;
  }

  @Override
  public ReadableRangeValue getEndRangeValue() {
    return RangeValue.of(last ? Long.MAX_VALUE : endVersion);
  }

  @Override
  public long getLastStreamSnapshotVersion() {
    return 0;
  }

  @Override
  public VersionNode getNodeByTopMarkerOffset(int topMarkerOffset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VersionNode getNodeByMarkerOffset(int bottomMarkerOffset) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public boolean isFirst() {
    return startVersion == 0;
  }

  @Override
  public boolean isLast() {
    return last;
  }

  @Override
  public VersionNode getFirstNode() {
    throw new UnsupportedOperationException();
  }

  @Override
  public VersionNode getLastNode() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getTimestamp(long version) {
    return 0;
  }

  @Override
  public ReadableSegmentSnapshot getLastSnapshot() {
    SegmentSnapshotRecord.BlipSnapshot.Builder builder = SegmentSnapshotRecord.BlipSnapshot.newBuilder();
    builder.setRawBlipSnapshot("snapshot");
    return BlipSnapshot.deserialize(builder.build(), "blip");
  }

  @Override
  public ReadableSegmentSnapshot getSnapshot(long version) {
    return null;
  }

  @Override
  public VersionNode getNode(long version) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VersionNode getNearestNode(long version) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VersionNode getNearestSnapshotNode(long version) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VersionInfo deserializeVersionInfo(int offset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SegmentOperation deserializeFromPreviousVersionOperation(int offset, SegmentId segmentId, WaveletOperationContext context) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SegmentOperation deserializeFromPreviousFarVersionOperation(int offset, SegmentId segmentId, WaveletOperationContext context) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ReadableSegmentSnapshot deserializeSegmentSnapshot(int offset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void writeVersionNode(VersionNode versionNode) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void writeLastSnapshot(ReadableSegmentSnapshot snapshot) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void writeSnapshot(VersionNode versionNode) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void finish(long endVersion) {
    throw new UnsupportedOperationException();
  }
}
