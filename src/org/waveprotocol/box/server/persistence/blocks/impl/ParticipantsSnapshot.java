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

import org.waveprotocol.box.server.persistence.protos.ProtoBlockStore;
import org.waveprotocol.box.server.persistence.blocks.ReadableParticipantsSnapshot;

import org.waveprotocol.wave.model.operation.wave.AddParticipant;
import org.waveprotocol.wave.model.operation.wave.NoOp;
import org.waveprotocol.wave.model.operation.wave.RemoveParticipant;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.raw.RawParticipantsSnapshot;
import org.waveprotocol.wave.model.raw.serialization.GsonSerializer;
import org.waveprotocol.wave.communication.Blob;

import com.google.common.collect.ImmutableSet;
import java.util.LinkedHashSet;

import java.util.List;
import java.util.Set;

/**
 * Snapshot of participants segment.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class ParticipantsSnapshot extends SegmentSnapshotImpl implements ReadableParticipantsSnapshot {
  private RawParticipantsSnapshot rawSnapshot;

  private ParticipantId creator;
  private Set<ParticipantId> participants;

  static ParticipantsSnapshot deserialize(ProtoBlockStore.SegmentSnapshotRecord.ParticipantsSnapshot serializedSnapshot) {
    ParticipantsSnapshot snapshot = new ParticipantsSnapshot();
    snapshot.rawSnapshot = new RawParticipantsSnapshot(GsonSerializer.PARTICIPANTS_SERIALIZER,
        new Blob(serializedSnapshot.getRawParticipantsSnapshot()));
    return snapshot;
  }

  ParticipantsSnapshot() {
  }

  @Override
  public ParticipantId getCreator() {
    if (creator != null) {
      return creator;
    }
    Preconditions.checkNotNull(rawSnapshot, "Not initializated");
    return rawSnapshot.getCreator();
  }

  @Override
  public Set<ParticipantId> getParticipants() {
    if (creator != null) {
      return ImmutableSet.copyOf(participants);
    }
    Preconditions.checkNotNull(rawSnapshot, "Not initializated");
    return ImmutableSet.copyOf(rawSnapshot.getParticipants());
  }

  @Override
  public List<? extends WaveletOperation> applyAndReturnReverse(WaveletOperation operation) throws OperationException {
    nofifyBeforeUpdate();
    WaveletOperationContext context = operation.getContext();
    Preconditions.checkNotNull(context, "Operation has no context");
    if (creator == null) {
      if (rawSnapshot != null) {
        creator = rawSnapshot.getCreator();
        participants = new LinkedHashSet<>(rawSnapshot.getParticipants());
        rawSnapshot = null;
      } else {
        creator = context.getCreator();
        participants = new LinkedHashSet<>();
      }
    } else {
      rawSnapshot = null;
    }
    if (operation instanceof AddParticipant) {
      if (!participants.add(((AddParticipant)operation).getParticipantId())) {
        throw new OperationException("Participant " + (((AddParticipant)operation).getParticipantId()).toString() + " already exists");
      }
    } else if (operation instanceof RemoveParticipant) {
      if (!participants.remove(((RemoveParticipant)operation).getParticipantId())) {
        throw new OperationException("No participant " + (((RemoveParticipant)operation).getParticipantId()).toString());
      }
    } else if (!(operation instanceof NoOp)) {
      throw new RuntimeException("Invalid operation for apply to participants snapshot: " + operation);
    }
   return operation.reverse(context);
  }

  @Override
  public boolean hasContent() {
    return true;
  }

  @Override
  public RawParticipantsSnapshot getRawSnapshot() {
    if (rawSnapshot == null) {
      rawSnapshot = new RawParticipantsSnapshot(GsonSerializer.PARTICIPANTS_SERIALIZER,
          creator, ImmutableSet.copyOf(participants));
    }
    return rawSnapshot;
  }

  @Override
  public ParticipantsSnapshot duplicate() {
    ParticipantsSnapshot snapshot = new ParticipantsSnapshot();
    snapshot.rawSnapshot = rawSnapshot;
    if (creator != null) {
      snapshot.creator = creator;
      snapshot.participants = CollectionUtils.newHashSet(participants);
    }
    return snapshot;
  }

  @Override
  protected void serialize(ProtoBlockStore.SegmentSnapshotRecord.Builder builder) {
    ProtoBlockStore.SegmentSnapshotRecord.ParticipantsSnapshot.Builder participantsBuilder = builder.getParticipantsSnapshotBuilder();
    participantsBuilder.setRawParticipantsSnapshot(getRawSnapshot().serialize().getData());
  }
}
