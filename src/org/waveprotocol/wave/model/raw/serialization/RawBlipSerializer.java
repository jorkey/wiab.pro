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

import com.google.common.collect.ImmutableSet;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.impl.DocOpUtil;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.raw.ProtoBlipSnapshot;
import org.waveprotocol.wave.model.raw.ProtoDocumentSnapshot;
import org.waveprotocol.wave.model.raw.RawBlipSnapshot;
import org.waveprotocol.wave.model.raw.RawParseException;

import org.waveprotocol.wave.communication.Blob;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class RawBlipSerializer implements RawBlipSnapshot.Serializer {

  public interface Adaptor extends WaveletOperationSerializer.Adaptor {
    ProtoBlipSnapshot createBlipSnapshot();
    ProtoBlipSnapshot createBlipSnapshot(Blob serialized);
    ProtoDocumentSnapshot createDocumentSnapshot();
    ProtoDocumentSnapshot createDocumentSnapshot(Blob serialized);
    String toJson(ProtoBlipSnapshot serialized);
    String toJson(ProtoDocumentSnapshot serialized);
  }

  private final Adaptor adaptor;
  private final WaveletOperationSerializer operationSerializer;

  public RawBlipSerializer(Adaptor adaptor) {
    this.adaptor = adaptor;
    operationSerializer = new WaveletOperationSerializer(adaptor);
  }

  @Override
  public Blob serializeBlip(RawBlipSnapshot snapshot) {
    Timer timer = Timing.start("RawBlipSerializer.serializeBlip");
    try {
      ProtoBlipSnapshot serialized = adaptor.createBlipSnapshot();
      serialized.setAuthor(snapshot.getAuthor().toString());
      for (ParticipantId contributor : snapshot.getContributors()) {
        serialized.addContributor(contributor.getAddress());
      }
      serialized.setCreationTime(snapshot.getCreationTime());
      serialized.setCreationVersion(snapshot.getCreationVersion());
      serialized.setLastModifiedTime(snapshot.getLastModifiedTime());
      serialized.setLastModifiedVersion(snapshot.getLastModifiedVersion());
      ProtoDocumentSnapshot docSnapshot = adaptor.createDocumentSnapshot();
      docSnapshot.setDocumentSnapshot(operationSerializer.serialize(snapshot.getContent()));
      serialized.setRawDocumentSnapshot(adaptor.toJson(docSnapshot));
      return new Blob(adaptor.toJson(serialized));
    } finally {
      Timing.stop(timer);
    }
  }

  @Override
  public void deserializeBlip(Blob serialized, RawBlipSnapshot.Serializer.Writer writer) {
    Timer timer = Timing.start("RawBlipSerializer.deserializeBlip");
    try {
      ProtoBlipSnapshot serializedBlip = adaptor.createBlipSnapshot(serialized);
      ImmutableSet.Builder<ParticipantId> contributors = ImmutableSet.builder();
      try {
        writer.setAuthor(ParticipantId.of(serializedBlip.getAuthor()));
        for (String contributor : serializedBlip.getContributor()) {
          contributors.add(ParticipantId.of(contributor));
        }
      } catch (InvalidParticipantAddress ex) {
        throw new RawParseException("Blip deserializing error", ex);
      }
      writer.setContributors(contributors.build());
      writer.setCreationTime(serializedBlip.getCreationTime());
      writer.setCreationVersion(serializedBlip.getCreationVersion());
      writer.setLastModifiedTime(serializedBlip.getLastModifiedTime());
      writer.setLastModifiedVersion(serializedBlip.getLastModifiedVersion());
      ProtoDocumentSnapshot doc = adaptor.createDocumentSnapshot(
          new Blob(serializedBlip.getRawDocumentSnapshot()));
      DocOp docOp = WaveletOperationSerializer.deserialize(doc.getDocumentSnapshot());
      DocInitialization docInit = DocOpUtil.asInitialization(docOp);
      writer.setContent(docInit);
    } finally {
      Timing.stop(timer);
    }
  }
}
