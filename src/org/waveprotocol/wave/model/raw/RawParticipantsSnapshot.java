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

import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.communication.Blob;

import com.google.common.collect.ImmutableSet;

/**
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class RawParticipantsSnapshot implements RawSnapshot {

  public interface Serializer {
    public interface Writer {
      void setCreator(ParticipantId creator);

      void setParticipants(ImmutableSet<ParticipantId> participants);
    }

    Blob serializeParticipants(RawParticipantsSnapshot snapshot);
    void deserializeParticipants(Blob serialized, Writer writer);
  };

  private final Serializer serializer;
  private Blob serialized;

  private ParticipantId creator;
  private ImmutableSet<ParticipantId> participants;

  public RawParticipantsSnapshot(Serializer serializer, Blob serialized) {
    this.serializer = serializer;
    this.serialized = serialized;
  }

  public RawParticipantsSnapshot(Serializer serializer, ParticipantId creator,
      ImmutableSet<ParticipantId> participants) {
    this.serializer = serializer;
    this.creator = creator;
    this.participants = participants;
  }

  public synchronized ParticipantId getCreator() {
    deserialize();
    return creator;
  }

  public synchronized ImmutableSet<ParticipantId> getParticipants() {
    deserialize();
    return participants;
  }

  private void deserialize() {
    if (creator == null) {
      serializer.deserializeParticipants(serialized, new Serializer.Writer() {

        @Override
        public void setCreator(ParticipantId creator) {
          RawParticipantsSnapshot.this.creator = creator;
        }

        @Override
        public void setParticipants(ImmutableSet<ParticipantId> participants) {
          RawParticipantsSnapshot.this.participants = participants;
        }
      });
    }
  }

  @Override
  public Blob serialize() {
    if (serialized == null) {
      serialized = serializer.serializeParticipants(this);
    }
    return serialized;
  }

  @Override
  public String toString() {
    deserialize();
    StringBuilder sb = new StringBuilder();
    sb.append("creator").append(creator);
    if (participants != null) {
      sb.append("participants:");
      for (ParticipantId participant : participants) {
        sb.append(" ").append(participant.toString());
      }
    }
    return sb.toString();
  }
}
