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

import org.waveprotocol.wave.model.raw.RawParseException;
import org.waveprotocol.wave.model.raw.ProtoBlipSnapshot;
import org.waveprotocol.wave.model.raw.ProtoDocumentSnapshot;
import org.waveprotocol.wave.model.raw.ProtoParticipantsSnapshot;
import org.waveprotocol.wave.model.raw.ProtoWaveletOperation;
import org.waveprotocol.wave.model.raw.ProtoWaveletOperations;
import org.waveprotocol.wave.model.raw.ProtoSegmentIndexSnapshot;
import org.waveprotocol.wave.model.raw.gson.ProtoBlipSnapshotGsonImpl;
import org.waveprotocol.wave.model.raw.gson.ProtoDocumentSnapshotGsonImpl;
import org.waveprotocol.wave.model.raw.gson.ProtoParticipantsSnapshotGsonImpl;
import org.waveprotocol.wave.model.raw.gson.ProtoWaveletOperationGsonImpl;
import org.waveprotocol.wave.model.raw.gson.ProtoWaveletOperationsGsonImpl;
import org.waveprotocol.wave.model.raw.gson.ProtoSegmentIndexSnapshotGsonImpl;

import org.waveprotocol.wave.federation.gson.ProtocolDocumentOperationGsonImpl;
import org.waveprotocol.wave.federation.gson.ProtocolHashedVersionGsonImpl;
import org.waveprotocol.wave.federation.gson.ProtocolWaveletOperationGsonImpl;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation;
import org.waveprotocol.wave.federation.ProtocolHashedVersion;
import org.waveprotocol.wave.federation.ProtocolWaveletOperation;

import org.waveprotocol.wave.communication.gson.GsonException;
import org.waveprotocol.wave.communication.Blob;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class GsonSerializerAdaptor  implements
    RawOperationSerializer.Adaptor,
    RawBlipSerializer.Adaptor,
    WaveletOperationSerializer.Adaptor,
    RawParticipantsSerializer.Adaptor,
    RawIndexSerializer.Adaptor {

  public static final GsonSerializerAdaptor INSTANCE = new GsonSerializerAdaptor();

  private static final JsonParser jsonParser = new JsonParser();
  private static final Gson gson = new Gson();

  @Override
  public ProtoWaveletOperationsGsonImpl createWaveletOperations() {
    return new ProtoWaveletOperationsGsonImpl();
  }

  @Override
  public ProtoWaveletOperationGsonImpl createWaveletOperation() {
    return new ProtoWaveletOperationGsonImpl();
  }

  @Override
  public ProtoDocumentSnapshotGsonImpl createDocumentSnapshot() {
    return new ProtoDocumentSnapshotGsonImpl();
  }

  @Override
  public ProtoBlipSnapshot createBlipSnapshot() {
    return new ProtoBlipSnapshotGsonImpl();
  }

  @Override
  public ProtoParticipantsSnapshot createParticipantsSnapshot() {
    return new ProtoParticipantsSnapshotGsonImpl();
  }

  @Override
  public ProtoSegmentIndexSnapshot createIndexSnapshot() {
    return new ProtoSegmentIndexSnapshotGsonImpl();
  }

  @Override
  public ProtoSegmentIndexSnapshot.SegmentInfo createSegmentInfo() {
    return new ProtoSegmentIndexSnapshotGsonImpl.SegmentInfoGsonImpl();
  }

  @Override
  public ProtocolWaveletOperation createProtocolWaveletOperation() {
    return new ProtocolWaveletOperationGsonImpl();
  }

  @Override
  public ProtocolWaveletOperation.MutateDocument createProtocolWaveletDocumentOperation() {
    return new ProtocolWaveletOperationGsonImpl.MutateDocumentGsonImpl();
  }

  @Override
  public ProtocolDocumentOperation createProtocolDocumentOperation() {
    return new ProtocolDocumentOperationGsonImpl();
  }

  @Override
  public ProtocolDocumentOperation.Component createComponent() {
    return new ProtocolDocumentOperationGsonImpl.ComponentGsonImpl();
  }

  @Override
  public ProtocolDocumentOperation.Component.ReplaceAttributes createReplaceAttributes() {
    return new ProtocolDocumentOperationGsonImpl.ComponentGsonImpl.ReplaceAttributesGsonImpl();
  }

  @Override
  public ProtocolDocumentOperation.Component.UpdateAttributes createUpdateAttributes() {
    return new ProtocolDocumentOperationGsonImpl.ComponentGsonImpl.UpdateAttributesGsonImpl();
  }

  @Override
  public ProtocolDocumentOperation.Component.AnnotationBoundary createAnnotationBoundary() {
    return new ProtocolDocumentOperationGsonImpl.ComponentGsonImpl.AnnotationBoundaryGsonImpl();
  }

  @Override
  public ProtocolDocumentOperation.Component.ElementStart createElementStart() {
    return new ProtocolDocumentOperationGsonImpl.ComponentGsonImpl.ElementStartGsonImpl();
  }

  @Override
  public ProtocolDocumentOperation.Component.KeyValuePair createKeyValuePair() {
    return new ProtocolDocumentOperationGsonImpl.ComponentGsonImpl.KeyValuePairGsonImpl();
  }

  @Override
  public ProtocolDocumentOperation.Component.KeyValueUpdate createKeyValueUpdate() {
    return new ProtocolDocumentOperationGsonImpl.ComponentGsonImpl.KeyValueUpdateGsonImpl();
  }

  @Override
  public ProtocolHashedVersion createProtocolHashedVersion() {
    return new ProtocolHashedVersionGsonImpl();
  }

  @Override
  public ProtoWaveletOperations createWaveletOperations(Blob serialized) {
    try {
      ProtoWaveletOperationsGsonImpl operations = new ProtoWaveletOperationsGsonImpl();
      operations.fromGson(jsonParser.parse(serialized.getData()), gson, null);
      return operations;
    } catch (GsonException ex) {
      throw new RawParseException("Parsing wavelet operations", ex);
    }
  }

  @Override
  public ProtoWaveletOperation createWaveletOperation(Blob serialized) {
    try {
      ProtoWaveletOperationGsonImpl operation = new ProtoWaveletOperationGsonImpl();
      operation.fromGson(jsonParser.parse(serialized.getData()), gson, null);
      return operation;
    } catch (GsonException ex) {
      throw new RawParseException("Parsing wavelet operation", ex);
    }
  }

  @Override
  public ProtoDocumentSnapshot createDocumentSnapshot(Blob serialized) {
    try {
      ProtoDocumentSnapshotGsonImpl snapshot = new ProtoDocumentSnapshotGsonImpl();
      snapshot.fromGson(jsonParser.parse(serialized.getData()), gson, null);
      return snapshot;
    } catch (GsonException ex) {
      throw new RawParseException("Parsing document snapshot", ex);
    }
  }

  @Override
  public ProtoBlipSnapshot createBlipSnapshot(Blob serialized) {
    try {
      ProtoBlipSnapshotGsonImpl snapshot = new ProtoBlipSnapshotGsonImpl();
      snapshot.fromGson(jsonParser.parse(serialized.getData()), gson, null);
      return snapshot;
    } catch (GsonException ex) {
      throw new RawParseException("Parsing blip snapshot", ex);
    }
  }

  @Override
  public ProtoParticipantsSnapshot createParticipantsSnapshot(Blob serialized) {
    try {
      ProtoParticipantsSnapshotGsonImpl snapshot = new ProtoParticipantsSnapshotGsonImpl();
      snapshot.fromGson(jsonParser.parse(serialized.getData()), gson, null);
      return snapshot;
    } catch (GsonException ex) {
      throw new RawParseException("Parsing participants snapshot", ex);
    }
  }

  @Override
  public ProtoSegmentIndexSnapshot createIndexSnapshot(Blob serialized) {
    try {
      ProtoSegmentIndexSnapshotGsonImpl snapshot = new ProtoSegmentIndexSnapshotGsonImpl();
      snapshot.fromGson(jsonParser.parse(serialized.getData()), gson, null);
      return snapshot;
    } catch (GsonException ex) {
      throw new RawParseException("Parsing index snapshot", ex);
    }
  }

  @Override
  public String toJson(ProtoWaveletOperations serialized) {
    return ((ProtoWaveletOperationsGsonImpl)serialized).toGson(null, gson).toString();
  }

  @Override
  public String toJson(ProtoWaveletOperation serialized) {
    return ((ProtoWaveletOperationGsonImpl)serialized).toGson(null, gson).toString();
  }

  @Override
  public String toJson(ProtoDocumentSnapshot serialized) {
    return ((ProtoDocumentSnapshotGsonImpl)serialized).toGson(null, gson).toString();
  }

  @Override
  public String toJson(ProtoBlipSnapshot serialized) {
    return ((ProtoBlipSnapshotGsonImpl)serialized).toGson(null, gson).toString();
  }

  @Override
  public String toJson(ProtoParticipantsSnapshot serialized) {
    return ((ProtoParticipantsSnapshotGsonImpl)serialized).toGson(null, gson).toString();
  }

  @Override
  public String toJson(ProtoSegmentIndexSnapshot serialized) {
    return ((ProtoSegmentIndexSnapshotGsonImpl)serialized).toGson(null, gson).toString();
  }
}
