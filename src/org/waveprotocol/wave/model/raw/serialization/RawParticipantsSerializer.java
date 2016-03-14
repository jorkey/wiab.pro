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

import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.raw.ProtoParticipantsSnapshot;
import org.waveprotocol.wave.model.raw.RawParseException;
import org.waveprotocol.wave.model.raw.RawParticipantsSnapshot;

import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;

import org.waveprotocol.wave.communication.Blob;

import com.google.common.collect.ImmutableSet;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class RawParticipantsSerializer implements RawParticipantsSnapshot.Serializer {

  public interface Adaptor {
    ProtoParticipantsSnapshot createParticipantsSnapshot();
    ProtoParticipantsSnapshot createParticipantsSnapshot(Blob serialized);
    String toJson(ProtoParticipantsSnapshot serialized);
  };

  private final Adaptor adaptor;

  public RawParticipantsSerializer(Adaptor adaptor) {
    this.adaptor = adaptor;
  }

  @Override
  public Blob serializeParticipants(RawParticipantsSnapshot snapshot) {
    Timer timer = Timing.start("RawBlipSerializer.serializeParticipants");
    try {
      ProtoParticipantsSnapshot serialized = adaptor.createParticipantsSnapshot();
      serialized.setCreator(snapshot.getCreator().getAddress());
      for (ParticipantId participant : snapshot.getParticipants()) {
        serialized.addParticipants(participant.getAddress());
      }
      return new Blob(adaptor.toJson(serialized));
    } finally {
      Timing.stop(timer);
    }
  }

  @Override
  public void deserializeParticipants(Blob serialized, RawParticipantsSnapshot.Serializer.Writer writer) {
    Timer timer = Timing.start("RawBlipSerializer.deserializeParticipants");
    try {
      ProtoParticipantsSnapshot serializedParticipants = adaptor.createParticipantsSnapshot(serialized);
      writer.setCreator(ParticipantId.of(serializedParticipants.getCreator()));
      ImmutableSet.Builder<ParticipantId> participants = ImmutableSet.builder();
      for (String participant : serializedParticipants.getParticipants()) {
        participants.add(ParticipantId.of(participant));
      }
      writer.setParticipants(participants.build());
    } catch (InvalidParticipantAddress ex) {
      throw new RawParseException("Participants deserializing error", ex);
    } finally {
      Timing.stop(timer);
    }
  }
}
