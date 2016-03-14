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
import org.waveprotocol.wave.communication.gwt.JsonMessage;
import org.waveprotocol.wave.model.raw.RawParseException;
import org.waveprotocol.wave.model.raw.ProtoBlipSnapshot;
import org.waveprotocol.wave.model.raw.ProtoDocumentSnapshot;
import org.waveprotocol.wave.model.raw.ProtoParticipantsSnapshot;
import org.waveprotocol.wave.model.raw.ProtoWaveletOperation;
import org.waveprotocol.wave.model.raw.ProtoWaveletOperations;
import org.waveprotocol.wave.model.raw.ProtoSegmentIndexSnapshot;
import org.waveprotocol.wave.model.raw.jso.ProtoWaveletOperationsJsoImpl;
import org.waveprotocol.wave.model.raw.jso.ProtoDocumentSnapshotJsoImpl;
import org.waveprotocol.wave.model.raw.jso.ProtoWaveletOperationJsoImpl;
import org.waveprotocol.wave.model.raw.jso.ProtoBlipSnapshotJsoImpl;
import org.waveprotocol.wave.model.raw.jso.ProtoParticipantsSnapshotJsoImpl;
import org.waveprotocol.wave.model.raw.jso.ProtoSegmentIndexSnapshotJsoImpl;

import org.waveprotocol.wave.communication.json.JsonException;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation;
import org.waveprotocol.wave.federation.ProtocolHashedVersion;
import org.waveprotocol.wave.federation.ProtocolWaveletOperation;
import org.waveprotocol.wave.federation.jso.ProtocolDocumentOperationJsoImpl;
import org.waveprotocol.wave.federation.jso.ProtocolHashedVersionJsoImpl;
import org.waveprotocol.wave.federation.jso.ProtocolWaveletOperationJsoImpl;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class JsoSerializerAdaptor implements
    RawOperationSerializer.Adaptor,
    RawBlipSerializer.Adaptor,
    WaveletOperationSerializer.Adaptor,
    RawParticipantsSerializer.Adaptor,
    RawIndexSerializer.Adaptor {

  public static final JsoSerializerAdaptor INSTANCE = new JsoSerializerAdaptor();

  @Override
  public ProtoWaveletOperationsJsoImpl createWaveletOperations() {
    return ProtoWaveletOperationsJsoImpl.create();
  }

  @Override
  public ProtoWaveletOperation createWaveletOperation() {
    return ProtoWaveletOperationJsoImpl.create();
  }

  @Override
  public ProtocolWaveletOperation createProtocolWaveletOperation() {
    return ProtocolWaveletOperationJsoImpl.create();
  }

  @Override
  public ProtocolWaveletOperation.MutateDocument createProtocolWaveletDocumentOperation() {
    return ProtocolWaveletOperationJsoImpl.MutateDocumentJsoImpl.create();
  }

  @Override
  public ProtocolDocumentOperation createProtocolDocumentOperation() {
    return ProtocolDocumentOperationJsoImpl.create();
  }

  @Override
  public ProtocolDocumentOperation.Component createComponent() {
    return ProtocolDocumentOperationJsoImpl.ComponentJsoImpl.create();
  }

  @Override
  public ProtocolDocumentOperation.Component.ReplaceAttributes createReplaceAttributes() {
    return ProtocolDocumentOperationJsoImpl.ComponentJsoImpl.ReplaceAttributesJsoImpl.create();
  }

  @Override
  public ProtocolDocumentOperation.Component.UpdateAttributes createUpdateAttributes() {
    return ProtocolDocumentOperationJsoImpl.ComponentJsoImpl.UpdateAttributesJsoImpl.create();
  }

  @Override
  public ProtocolDocumentOperation.Component.AnnotationBoundary createAnnotationBoundary() {
    return ProtocolDocumentOperationJsoImpl.ComponentJsoImpl.AnnotationBoundaryJsoImpl.create();
  }

  @Override
  public ProtocolDocumentOperation.Component.ElementStart createElementStart() {
    return ProtocolDocumentOperationJsoImpl.ComponentJsoImpl.ElementStartJsoImpl.create();
  }

  @Override
  public ProtocolDocumentOperation.Component.KeyValuePair createKeyValuePair() {
    return ProtocolDocumentOperationJsoImpl.ComponentJsoImpl.KeyValuePairJsoImpl.create();
  }

  @Override
  public ProtocolDocumentOperation.Component.KeyValueUpdate createKeyValueUpdate() {
    return ProtocolDocumentOperationJsoImpl.ComponentJsoImpl.KeyValueUpdateJsoImpl.create();
  }

  @Override
  public ProtocolHashedVersion createProtocolHashedVersion() {
    return ProtocolHashedVersionJsoImpl.create();
  }

  @Override
  public ProtoDocumentSnapshotJsoImpl createDocumentSnapshot() {
    return ProtoDocumentSnapshotJsoImpl.create();
  }

  @Override
  public ProtoBlipSnapshot createBlipSnapshot() {
    return ProtoBlipSnapshotJsoImpl.create();
  }

  @Override
  public ProtoParticipantsSnapshot createParticipantsSnapshot() {
    return ProtoParticipantsSnapshotJsoImpl.create();
  }

  @Override
  public ProtoSegmentIndexSnapshot createIndexSnapshot() {
    return ProtoSegmentIndexSnapshotJsoImpl.create();
  }

  @Override
  public ProtoSegmentIndexSnapshot.SegmentInfo createSegmentInfo() {
    return ProtoSegmentIndexSnapshotJsoImpl.SegmentInfoJsoImpl.create();
  }

  @Override
  public ProtoWaveletOperationsJsoImpl createWaveletOperations(Blob serialized) {
    try {
      return JsonMessage.parse(serialized.getData());
    } catch (JsonException ex) {
      throw new RawParseException("Parsing wavelet operations", ex);
    }
  }

  @Override
  public ProtoWaveletOperationJsoImpl createWaveletOperation(Blob serialized) {
    try {
      return JsonMessage.parse(serialized.getData());
    } catch (JsonException ex) {
      throw new RawParseException("Parsing wavelet operation", ex);
    }
  }

  @Override
  public ProtoDocumentSnapshotJsoImpl createDocumentSnapshot(Blob serialized) {
    try {
      return JsonMessage.parse(serialized.getData());
    } catch (JsonException ex) {
      throw new RawParseException("Parsing document snapshot", ex);
    }
  }

  @Override
  public ProtoBlipSnapshotJsoImpl createBlipSnapshot(Blob serialized) {
    try {
      return JsonMessage.parse(serialized.getData());
    } catch (JsonException ex) {
      throw new RawParseException("Parsing blip snapshot", ex);
    }
  }

  @Override
  public ProtoParticipantsSnapshotJsoImpl createParticipantsSnapshot(Blob serialized) {
    try {
      return JsonMessage.parse(serialized.getData());
    } catch (JsonException ex) {
      throw new RawParseException("Parsing participants snapshot", ex);
    }
  }

  @Override
  public ProtoSegmentIndexSnapshotJsoImpl createIndexSnapshot(Blob serialized) {
    try {
      return JsonMessage.parse(serialized.getData());
    } catch (JsonException ex) {
      throw new RawParseException("Parsing index snapshot", ex);
    }
  }

  @Override
  public String toJson(ProtoWaveletOperations serialized) {
    return ((ProtoWaveletOperationsJsoImpl)serialized).toJson();
  }

  @Override
  public String toJson(ProtoWaveletOperation serialized) {
    return ((ProtoWaveletOperationJsoImpl)serialized).toJson();
  }

  @Override
  public String toJson(ProtoDocumentSnapshot serialized) {
    return ((ProtoDocumentSnapshotJsoImpl)serialized).toJson();
  }

  @Override
  public String toJson(ProtoBlipSnapshot serialized) {
    return ((ProtoBlipSnapshotJsoImpl)serialized).toJson();
  }

  @Override
  public String toJson(ProtoParticipantsSnapshot serialized) {
    return ((ProtoParticipantsSnapshotJsoImpl)serialized).toJson();
  }

  @Override
  public String toJson(ProtoSegmentIndexSnapshot serialized) {
    return ((ProtoSegmentIndexSnapshotJsoImpl)serialized).toJson();
  }
}
