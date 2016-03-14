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

package org.waveprotocol.wave.model.raw.serialization;

import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.raw.ProtoSegmentIndexSnapshot;
import org.waveprotocol.wave.model.raw.RawIndexSnapshot;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableList;

import java.util.Map;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class RawIndexSerializer implements RawIndexSnapshot.Serializer {

  public interface Adaptor {
    ProtoSegmentIndexSnapshot createIndexSnapshot();
    ProtoSegmentIndexSnapshot createIndexSnapshot(Blob serialized);
    ProtoSegmentIndexSnapshot.SegmentInfo createSegmentInfo();
    String toJson(ProtoSegmentIndexSnapshot serialized);
  };

  private final Adaptor adaptor;

  public RawIndexSerializer(Adaptor adaptor) {
    this.adaptor = adaptor;
  }

  @Override
  public Blob serializeIndex(RawIndexSnapshot snapshot) {
    Timer timer = Timing.start("RawIndexSerializer.serializeIndex");
    try {
      ProtoSegmentIndexSnapshot serialized = adaptor.createIndexSnapshot();
      serialized.setCreationTime(snapshot.getCreationTime());
      for (Map.Entry<SegmentId, Long> entry : snapshot.getCreationVersions().entrySet()) {
        ProtoSegmentIndexSnapshot.SegmentInfo segmentInfo = adaptor.createSegmentInfo();
        segmentInfo.setSegmentId(entry.getKey().serialize());
        segmentInfo.setCreationTime(entry.getValue());
        segmentInfo.setLastModificationVersion(snapshot.getLastModifiedVersions().get(entry.getKey()));
        if (!snapshot.getExistingSegments().contains(entry.getKey())) {
          segmentInfo.setRemoved(true);
        }
        serialized.addSegments(segmentInfo);
      }
      for (SegmentId segment : snapshot.getBeingModifiedSegments()) {
        serialized.addBeingModifiedSegments(segment.serialize());
      }
      if (snapshot.getLastModifiedSegmentId() != null) {
        serialized.setLastModifiedSegmentId(snapshot.getLastModifiedSegmentId().serialize());
      }
      return new Blob(adaptor.toJson(serialized));
    } finally {
      Timing.stop(timer);
    }
  }

  @Override
  public void deserializeIndex(Blob serialized, Writer writer) {
    Timer timer = Timing.start("RawIndexSerializer.deserializeIndex");
    try {
      ProtoSegmentIndexSnapshot serializedIndex = adaptor.createIndexSnapshot(serialized);
      writer.setCreationTime(serializedIndex.getCreationTime());
      ImmutableSet.Builder<SegmentId> existingSegments = ImmutableSet.builder();
      ImmutableMap.Builder<SegmentId, Long> creationVersions = ImmutableMap.builder();
      ImmutableMap.Builder<SegmentId, Long> lastModifiedVersions = ImmutableMap.builder();
      for (ProtoSegmentIndexSnapshot.SegmentInfo segmentInfo : serializedIndex.getSegments()) {
        SegmentId segmentId = SegmentId.of(segmentInfo.getSegmentId());
        creationVersions.put(segmentId, segmentInfo.getCreationTime());
        lastModifiedVersions.put(segmentId, segmentInfo.getLastModificationVersion());
        if (!segmentInfo.hasRemoved() || !segmentInfo.getRemoved()) {
          existingSegments.add(segmentId);
        }
      }
      writer.setExistingSegments(existingSegments.build());
      writer.setCreationVersions(creationVersions.build());
      writer.setLastModifiedVersions(lastModifiedVersions.build());
      ImmutableSet.Builder<SegmentId> beingModifiedSegments = ImmutableSet.builder();
      for (String segment : serializedIndex.getBeingModifiedSegments()) {
        beingModifiedSegments.add(SegmentId.of(segment));
      }
      writer.setBeingModifiedSegments(beingModifiedSegments.build());
      writer.setLastModifiedSegmentId(SegmentId.of(serializedIndex.getLastModifiedSegmentId()));
    } finally {
      Timing.stop(timer);
    }
  }
}
