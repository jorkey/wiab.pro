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

import org.waveprotocol.box.server.persistence.protos.ProtoBlockStore.BlockHeaderRecord;

import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;

import org.waveprotocol.box.server.persistence.blocks.DeserializationBlockException;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Map;
import java.util.List;
import java.util.Collections;

/**
 * Header of block.
 *
 * Contains:
 *  - blockId
 *  - fragments indexes
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
class BlockHeader {
  /** The block Id. */
  private final String blockId;

  /** Serialized segmentIds. */
  private final List<SegmentId> serializedSegmentIds;

  /** Serialized header. */
  private final BlockHeaderRecord serialized;

  /** Deserialized and new indexes of fragments. */
  private final Map<SegmentId, FragmentIndex> fragmentIndexes = CollectionUtils.newHashMap();

  /** Authors of versions. */
  private List<ParticipantId> authors;
  
  /** Last modified version. */
  private volatile long lastModifiedVersion;

  static BlockHeader deserialize(byte[] buffer) throws InvalidProtocolBufferException {
    return deserialize(BlockHeaderRecord.parseFrom(buffer));
  }

  static BlockHeader deserialize(BlockHeaderRecord record) {
    List<SegmentId> segmentIds = CollectionUtils.newLinkedList();
    for (int i=0; i < record.getSegmentIdsCount(); i++) {
      segmentIds.add(SegmentId.of(record.getSegmentIds(i)));
    }
    Preconditions.checkArgument(record.getRawMarkersCount() == segmentIds.size() &&
      record.getRawSnapshotIndexesCount() == segmentIds.size() &&
      record.getRawLastSnapshotsCount() == segmentIds.size() &&
      record.getFirstFragmentsCount() == segmentIds.size() &&
      record.getLastFragmentsCount() == segmentIds.size(),
      "Invalid block header");
    List<ParticipantId> authors = CollectionUtils.newLinkedList();
    try {
      for (int i=0; i < record.getAuthorsCount(); i++) {
        authors.add(ParticipantId.of(record.getAuthors(i)));
      }
    } catch (InvalidParticipantAddress ex) {
      throw new DeserializationBlockException(ex);
    }
    return new BlockHeader(record, segmentIds, authors);
  }

  BlockHeader(String blockId) {
    this.blockId = blockId;
    this.serialized = null;
    this.serializedSegmentIds = null;
    authors = CollectionUtils.newLinkedList();
  }

  BlockHeader(BlockHeaderRecord serialized, List<SegmentId> serializedSegmentIds, List<ParticipantId> authors) {
    this.blockId = serialized.getBlockId();
    this.serialized = serialized;
    this.serializedSegmentIds = serializedSegmentIds;
    this.authors = authors;
    this.lastModifiedVersion = serialized.getLastModifiedVersion();
  }

  String getBlockId() {
    return blockId;
  }

  boolean hasSegment(SegmentId segmentId) {
    if (serializedSegmentIds != null && serializedSegmentIds.contains(segmentId)) {
      return true;
    }
    return fragmentIndexes.containsKey(segmentId);
  }

  long getLastModifiedVersion() {
    return lastModifiedVersion;
  }

  FragmentIndex getFragmentIndex(SegmentId segmentId) {
    FragmentIndex fragmentIndex = fragmentIndexes.get(segmentId);
    if (fragmentIndex == null && serializedSegmentIds != null) {
      int index = serializedSegmentIds.indexOf(segmentId);
      if (index != -1) {
        fragmentIndex = new FragmentIndex(
            serialized.getRawMarkers(index).toByteArray(),
            serialized.getRawSnapshotIndexes(index).toByteArray(),
            serialized.getRawLastSnapshots(index).toByteArray(),
            serialized.getFirstFragments(index), 
            serialized.getLastFragments(index));
        fragmentIndexes.put(segmentId, fragmentIndex);
      }
    }
    Preconditions.checkNotNull(fragmentIndex, "No such fragment " + segmentId.toString());
    return fragmentIndex;
  }
  
  List<ParticipantId> getAuthors() {
    return Collections.unmodifiableList(authors);
  }

  FragmentIndex createFragmentIndex(SegmentId segmentId, boolean first) {
    Preconditions.checkArgument((serializedSegmentIds == null || !serializedSegmentIds.contains(segmentId)) &&
      !fragmentIndexes.containsKey(segmentId), "Block already has fragment " + segmentId.toString());
    FragmentIndex index = new FragmentIndex(first);
    fragmentIndexes.put(segmentId, index);
    return index;
  }
  
  void registryAuthor(ParticipantId author) {
    Preconditions.checkNotNull(author, "Author is null");
    if (!authors.contains(author)) {
      authors.add(author);
    }
  }

  void setLastModifiedVersion(long lastModifiedVersion) {
    this.lastModifiedVersion = lastModifiedVersion;
  }

  BlockHeaderRecord serialize() {
    BlockHeaderRecord.Builder header = BlockHeaderRecord.newBuilder();
    header.setBlockId(blockId);
    if (serialized != null) {
      for (int i = 0; i < serialized.getSegmentIdsCount(); i++) {
        SegmentId segmentId = SegmentId.of(serialized.getSegmentIds(i));
        if (!fragmentIndexes.containsKey(segmentId)) {
          header.addSegmentIds(serialized.getSegmentIds(i));
          header.addRawMarkers(serialized.getRawMarkers(i));
          header.addRawSnapshotIndexes(serialized.getRawSnapshotIndexes(i));
          header.addRawLastSnapshots(serialized.getRawLastSnapshots(i));
          header.addFirstFragments(serialized.getFirstFragments(i));
          header.addLastFragments(serialized.getLastFragments(i));
        }
      }
    }
    for (Map.Entry<SegmentId, FragmentIndex> entry : fragmentIndexes.entrySet()) {
      header.addSegmentIds(entry.getKey().toString());
      header.addRawMarkers(entry.getValue().serializeMarkers());
      header.addRawSnapshotIndexes(entry.getValue().serializeSnapshotsIndex());
      header.addRawLastSnapshots(entry.getValue().serializeLastSnapshot());
      header.addFirstFragments(entry.getValue().isFirst());
      header.addLastFragments(entry.getValue().isLast());
    }
    for (ParticipantId author : authors) {
      header.addAuthors(author.toString());
    }
    header.setLastModifiedVersion(lastModifiedVersion);
    return header.build();
  }
}
