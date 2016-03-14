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

package org.waveprotocol.wave.model.raw;

import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.communication.Blob;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableMap;

import com.google.common.collect.ImmutableList;

import java.util.Map;

/**
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class RawIndexSnapshot implements RawSnapshot {

  public interface Serializer {
    public interface Writer {
      void setCreationTime(long creationTime);
      void setExistingSegments(ImmutableSet<SegmentId> segments);
      void setCreationVersions(ImmutableMap<SegmentId, Long> creationVersions);
      void setLastModifiedVersions(ImmutableMap<SegmentId, Long> lastModifiedVersions);
      void setBeingModifiedSegments(ImmutableSet<SegmentId> lastModifiedSegments);
      void setLastModifiedSegmentId(SegmentId segmentId);
    }

    Blob serializeIndex(RawIndexSnapshot snapshot);
    void deserializeIndex(Blob serialized, RawIndexSnapshot.Serializer.Writer writer);
  };

  private final Serializer serializer;
  private Blob serialized;

  private long creationTime;
  private ImmutableSet<SegmentId> existingSegments;
  private ImmutableMap<SegmentId, Long> creationVersions;
  private ImmutableMap<SegmentId, Long> lastModifiedVersions;
  private ImmutableSet<SegmentId> beingModifiedSegments;
  private SegmentId lastModifiedSegmentId;

  public RawIndexSnapshot(Serializer serializer, Blob serialized) {
    this.serializer = serializer;
    this.serialized = serialized;
  }

  public RawIndexSnapshot(Serializer serializer, long creationTime, ImmutableSet<SegmentId> segments,
      ImmutableMap<SegmentId, Long> creationVersions, ImmutableMap<SegmentId, Long> lastModifiedVersions,
      ImmutableSet<SegmentId> lastModifiedSegments, SegmentId lastModifiedSegmentId) {
    this.serializer = serializer;
    this.creationTime = creationTime;
    this.existingSegments = segments;
    this.creationVersions = creationVersions;
    this.lastModifiedVersions = lastModifiedVersions;
    this.beingModifiedSegments = lastModifiedSegments;
    this.lastModifiedSegmentId = lastModifiedSegmentId;
  }

  public synchronized long getCreationTime() {
    deserialize();
    return creationTime;
  }

  public synchronized ImmutableSet<SegmentId> getExistingSegments() {
    deserialize();
    return existingSegments;
  }

  public synchronized ImmutableMap<SegmentId, Long> getCreationVersions() {
    deserialize();
    return creationVersions;
  }

  public synchronized ImmutableMap<SegmentId, Long> getLastModifiedVersions() {
    deserialize();
    return lastModifiedVersions;
  }

  public synchronized ImmutableSet<SegmentId> getBeingModifiedSegments() {
    deserialize();
    return beingModifiedSegments;
  }
  
  public synchronized SegmentId getLastModifiedSegmentId() {
    deserialize();
    return lastModifiedSegmentId;
  }

  private void deserialize() {
    if (existingSegments == null) {
      serializer.deserializeIndex(serialized, new Serializer.Writer() {

        @Override
        public void setCreationTime(long creationTime) {
          RawIndexSnapshot.this.creationTime = creationTime;
        }

        @Override
        public void setExistingSegments(ImmutableSet<SegmentId> segments) {
          RawIndexSnapshot.this.existingSegments = segments;
        }

        @Override
        public void setCreationVersions(ImmutableMap<SegmentId, Long> creationVersions) {
          RawIndexSnapshot.this.creationVersions = creationVersions;
        }

        @Override
        public void setLastModifiedVersions(ImmutableMap<SegmentId, Long> lastModifiedVersions) {
          RawIndexSnapshot.this.lastModifiedVersions = lastModifiedVersions;
        }

        @Override
        public void setBeingModifiedSegments(ImmutableSet<SegmentId> beingModifiedSegments) {
          RawIndexSnapshot.this.beingModifiedSegments = beingModifiedSegments;
        }
        
        @Override
        public void setLastModifiedSegmentId(SegmentId lastModifiedSegmentId) {
          RawIndexSnapshot.this.lastModifiedSegmentId = lastModifiedSegmentId;
        }
      });
    }
  }

  @Override
  public Blob serialize() {
    if (serialized == null) {
      serialized = serializer.serializeIndex(this);
    }
    return serialized;
  }

  @Override
  public String toString() {
    deserialize();
    StringBuilder sb = new StringBuilder();
    if (existingSegments != null) {
      sb.append("existingSegments:");
      for (SegmentId segment : existingSegments) {
        sb.append(" ").append(segment.toString());
      }
    }
    if (creationVersions != null) {
      sb.append("\ncreationVersions:");
      for (Map.Entry<SegmentId, Long> entry : creationVersions.entrySet()) {
        sb.append(" ").append(entry.getKey()).append(":").append(entry.getValue());
      }
    }
    if (lastModifiedVersions != null) {
      sb.append("\nlastModifiedVersions:");
      for (Map.Entry<SegmentId, Long> entry : lastModifiedVersions.entrySet()) {
        sb.append(" ").append(entry.getKey()).append(":").append(entry.getValue());
      }
    }
    if (beingModifiedSegments != null) {
      sb.append("\nbeingModifiedSegments:");
      for (SegmentId segment : beingModifiedSegments) {
        sb.append(" ").append(segment.toString());
      }
    }
    sb.append("\nlastModifiedSegment: " + lastModifiedSegmentId.toString());
    sb.append("\n").append(creationTime);
    return sb.toString();
  }
}
