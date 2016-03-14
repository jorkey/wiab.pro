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

import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.AddSegment;
import org.waveprotocol.wave.model.operation.wave.RemoveSegment;
import org.waveprotocol.wave.model.operation.wave.EndModifyingSegment;
import org.waveprotocol.wave.model.operation.wave.StartModifyingSegment;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.raw.RawIndexSnapshot;
import org.waveprotocol.wave.model.raw.serialization.GsonSerializer;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.communication.Blob;

import org.waveprotocol.box.server.persistence.protos.ProtoBlockStore;
import org.waveprotocol.box.server.persistence.blocks.ReadableIndexSnapshot;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Snapshot of index segment.
 * Contains set of wavelet segments.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class IndexSnapshot extends SegmentSnapshotImpl implements ReadableIndexSnapshot {
  private long creationTime = -1;
  private Set<SegmentId> existingSegmentIds = null;
  private Map<SegmentId, Long> creationVersions = null;
  private Map<SegmentId, Long> lastModifiedVersions = null;
  private Set<SegmentId> beingModifiedSegmentIds = null;
  private SegmentId lastModifiedSegmentId = null;

  /** Raw snapshot. */
  private RawIndexSnapshot rawSnapshot;

  static IndexSnapshot deserialize(ProtoBlockStore.SegmentSnapshotRecord.IndexSnapshot serializedSnapshot) {
    IndexSnapshot snapshot = new IndexSnapshot();
    snapshot.rawSnapshot = new RawIndexSnapshot(GsonSerializer.INDEX_SERIALIZER,
      new Blob(serializedSnapshot.getRawIndexSnapshot()));
    return snapshot;
  }

  IndexSnapshot() {
  }

  @Override
  public long getCreationTime() {
    if (existingSegmentIds != null) {
      return creationTime;
    }
    return rawSnapshot.getCreationTime();
  }

  @Override
  public Set<SegmentId> getExistingSegmentIds() {
    if (existingSegmentIds != null) {
      return ImmutableSet.copyOf(existingSegmentIds);
    }
    return ImmutableSet.copyOf(rawSnapshot.getExistingSegments());
  }

  @Override
  public SegmentId getLastModifiedSegmentId() {
    if (existingSegmentIds != null) {
      return lastModifiedSegmentId;
    }
    return rawSnapshot.getLastModifiedSegmentId();
  }

  @Override
  public long getCreationVersion(SegmentId segmentId) {
    if (existingSegmentIds != null) {
      return creationVersions.get(segmentId);
    }
    Long version = rawSnapshot.getCreationVersions().get(segmentId);
    Preconditions.checkNotNull(version, "No segment " + segmentId.toString());
    return version;
  }

  @Override
  public long getLastModifiedVersion(SegmentId segmentId) {
    if (existingSegmentIds != null) {
      return lastModifiedVersions.get(segmentId);
    }
    Long version = rawSnapshot.getLastModifiedVersions().get(segmentId);
    Preconditions.checkNotNull(version, "No segment " + segmentId.toString());
    return version;
  }

  @Override
  public Set<SegmentId> getBeingModifiedSegmentIds() {
    if (existingSegmentIds != null) {
      return ImmutableSet.copyOf(beingModifiedSegmentIds);
    }
    return ImmutableSet.copyOf(rawSnapshot.getBeingModifiedSegments());
  }

  @Override
  public boolean hasSegment(SegmentId segmentId) {
    if (existingSegmentIds != null) {
      return existingSegmentIds.contains(segmentId);
    }
    return rawSnapshot.getExistingSegments().contains(segmentId);
  }

  @Override
  public List<? extends WaveletOperation> applyAndReturnReverse(WaveletOperation operation) throws OperationException {
    nofifyBeforeUpdate();
    WaveletOperationContext context = operation.getContext();
    Preconditions.checkNotNull(context, "Operation has no context");
    if (existingSegmentIds == null) {
      if (rawSnapshot != null) {
        existingSegmentIds = CollectionUtils.newHashSet(rawSnapshot.getExistingSegments());
        creationVersions = CollectionUtils.newHashMap(rawSnapshot.getCreationVersions());
        lastModifiedVersions = CollectionUtils.newHashMap(rawSnapshot.getLastModifiedVersions());
        beingModifiedSegmentIds = CollectionUtils.newHashSet(rawSnapshot.getBeingModifiedSegments());
        lastModifiedSegmentId = rawSnapshot.getLastModifiedSegmentId();
        creationTime = rawSnapshot.getCreationTime();
        rawSnapshot = null;
      } else {
        existingSegmentIds = CollectionUtils.newHashSet();
        creationVersions = CollectionUtils.newHashMap();
        lastModifiedVersions = CollectionUtils.newHashMap();
        beingModifiedSegmentIds = CollectionUtils.newHashSet();
        lastModifiedSegmentId = null;
        creationTime = context.getTimestamp();
      }
    } else {
      rawSnapshot = null;
    }
    SegmentId segmentId;
    if (operation instanceof AddSegment) {
      segmentId = getOperationSegmentId(((AddSegment)operation).getSegmentId());
      existingSegmentIds.add(segmentId);
      creationVersions.put(segmentId, context.getSegmentVersion());
      beingModifiedSegmentIds.add(segmentId);
    } else if (operation instanceof RemoveSegment) {
      segmentId = getOperationSegmentId(((RemoveSegment)operation).getSegmentId());
      existingSegmentIds.remove(segmentId);
      beingModifiedSegmentIds.add(segmentId);
    } else if (operation instanceof StartModifyingSegment) {
      segmentId = getOperationSegmentId(((StartModifyingSegment)operation).getSegmentId());
      beingModifiedSegmentIds.add(segmentId);
    } else if (operation instanceof EndModifyingSegment) {
      segmentId = getOperationSegmentId(((EndModifyingSegment)operation).getSegmentId());
      beingModifiedSegmentIds.remove(segmentId);
    } else {
      throw new OperationException("Invalid operation for apply to index snapshot: " + operation);
    }
    for (SegmentId id : beingModifiedSegmentIds) {
      lastModifiedVersions.put(id, context.getSegmentVersion());
    }
    lastModifiedSegmentId = segmentId;
    return operation.reverse(context);
  }

  @Override
  public boolean hasContent() {
    return (existingSegmentIds != null && !existingSegmentIds.isEmpty()) ||
      (rawSnapshot != null && !rawSnapshot.getExistingSegments().isEmpty());
  }

  @Override
  public IndexSnapshot duplicate() {
    IndexSnapshot snapshot = new IndexSnapshot();
    snapshot.rawSnapshot = rawSnapshot;
    if (existingSegmentIds != null) {
      snapshot.creationTime = creationTime;
      snapshot.existingSegmentIds = CollectionUtils.newHashSet(existingSegmentIds);
      snapshot.creationVersions = CollectionUtils.newHashMap(creationVersions);
      snapshot.lastModifiedVersions = CollectionUtils.newHashMap(lastModifiedVersions);
      snapshot.beingModifiedSegmentIds =  CollectionUtils.newHashSet(beingModifiedSegmentIds);
      snapshot.lastModifiedSegmentId = lastModifiedSegmentId;
    }
    return snapshot;
  }

  @Override
  public RawIndexSnapshot getRawSnapshot() {
    if (rawSnapshot == null) {
      rawSnapshot = new RawIndexSnapshot(GsonSerializer.INDEX_SERIALIZER, creationTime,
        ImmutableSet.copyOf(existingSegmentIds), ImmutableMap.copyOf(creationVersions),
        ImmutableMap.copyOf(lastModifiedVersions), ImmutableSet.copyOf(beingModifiedSegmentIds),
        lastModifiedSegmentId);
    }
    return rawSnapshot;
  }

  @Override
  protected void serialize(ProtoBlockStore.SegmentSnapshotRecord.Builder builder) {
    ProtoBlockStore.SegmentSnapshotRecord.IndexSnapshot.Builder waveletBuilder = builder.getIndexSnapshotBuilder();
    waveletBuilder.setRawIndexSnapshot(getRawSnapshot().serialize().getData());
  }

  private SegmentId getOperationSegmentId(SegmentId operationSegmentId) {
    if (operationSegmentId == null) {
      Preconditions.checkNotNull(lastModifiedSegmentId, "Last modified segment Id is undefined");
      return lastModifiedSegmentId;
    }
    return operationSegmentId;
  }
}
