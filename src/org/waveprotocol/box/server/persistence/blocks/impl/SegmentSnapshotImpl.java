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
import org.waveprotocol.box.server.persistence.blocks.DeserializationBlockException;
import org.waveprotocol.box.server.persistence.protos.ProtoBlockStore;
import org.waveprotocol.box.server.persistence.protos.ProtoBlockStore.SegmentSnapshotRecord;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.id.SegmentId;

import com.google.protobuf.InvalidProtocolBufferException;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Base class of segment snapshot.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public abstract class SegmentSnapshotImpl implements SegmentSnapshot {

  private final List<WeakReference<UpdateListener>> updateListeners = CollectionUtils.newLinkedList();

  static SegmentSnapshot createSnapshot(SegmentId segmentId) {
    if (SegmentId.INDEX_ID.equals(segmentId)) {
      return new IndexSnapshot();
    } else if (SegmentId.PARTICIPANTS_ID.equals(segmentId)) {
      return new ParticipantsSnapshot();
    } else {
      Preconditions.checkArgument(segmentId.isBlip(), "Can't create snapshot for segment " + segmentId.toString());
      return new BlipSnapshot(segmentId.getBlipId());
    }
  }

  static SegmentSnapshotImpl deserialize(byte[] buffer, SegmentId segmentId) {
    SegmentSnapshotRecord serializedSnapshot;
    try {
      serializedSnapshot = SegmentSnapshotRecord.parseFrom(buffer);
    } catch (InvalidProtocolBufferException ex) {
      throw new DeserializationBlockException(ex);
    }
    return deserialize(serializedSnapshot, segmentId);
  }

  static SegmentSnapshotImpl deserialize(SegmentSnapshotRecord serializedSnapshot, SegmentId segmentId) {
    if (serializedSnapshot.hasIndexSnapshot()) {
      return IndexSnapshot.deserialize(serializedSnapshot.getIndexSnapshot());
    } else if (serializedSnapshot.hasParticipantsSnapshot()) {
      return ParticipantsSnapshot.deserialize(serializedSnapshot.getParticipantsSnapshot());
    } else if (serializedSnapshot.hasBlipSnapshot()) {
      return BlipSnapshot.deserialize(serializedSnapshot.getBlipSnapshot(), segmentId.getBlipId());
    }
    throw new RuntimeException("Neither the participants nor the blip snapshot is defined");
  }

  SegmentSnapshotImpl() {
  }

  @Override
  public abstract boolean hasContent();

  @Override
  public abstract SegmentSnapshot duplicate();

  @Override
  public SegmentSnapshotRecord serialize() {
    ProtoBlockStore.SegmentSnapshotRecord.Builder builder = ProtoBlockStore.SegmentSnapshotRecord.newBuilder();
    serialize(builder);
    return builder.build();
  }

  protected abstract void serialize(ProtoBlockStore.SegmentSnapshotRecord.Builder builder);

  @Override
  public void addUpdateListener(UpdateListener listener) {
    synchronized (updateListeners) {
      updateListeners.add(new WeakReference(listener));
    }
  }

  protected void nofifyBeforeUpdate() {
    synchronized (updateListeners) {
      for (WeakReference<UpdateListener> ref : updateListeners) {
        UpdateListener listener = ref.get();
        if (listener != null) {
          listener.onBeforeUpdate();
        }
      }
      updateListeners.clear();
    }
  }
}
