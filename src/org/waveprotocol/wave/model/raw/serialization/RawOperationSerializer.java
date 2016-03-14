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

import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.operation.wave.AddParticipant;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.BlipOperation;
import org.waveprotocol.wave.model.operation.wave.NoOp;
import org.waveprotocol.wave.model.operation.wave.RemoveParticipant;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.operation.wave.AddSegment;
import org.waveprotocol.wave.model.operation.wave.RemoveSegment;
import org.waveprotocol.wave.model.operation.wave.StartModifyingSegment;
import org.waveprotocol.wave.model.operation.wave.EndModifyingSegment;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.raw.ProtoWaveletOperation;
import org.waveprotocol.wave.model.raw.ProtoWaveletOperations;
import org.waveprotocol.wave.model.raw.RawOperation;
import org.waveprotocol.wave.model.raw.RawParseException;

import org.waveprotocol.wave.communication.Blob;

import com.google.common.collect.ImmutableList;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class RawOperationSerializer implements RawOperation.Serializer {

  public interface Adaptor extends WaveletOperationSerializer.Adaptor {
    ProtoWaveletOperations createWaveletOperations();
    ProtoWaveletOperation createWaveletOperation();
    ProtoWaveletOperations createWaveletOperations(Blob serialized);
    ProtoWaveletOperation createWaveletOperation(Blob serialized);
    String toJson(ProtoWaveletOperations serialized);
    String toJson(ProtoWaveletOperation serialized);
  }

  private final Adaptor adaptor;
  private final WaveletOperationSerializer operationSerializer;

  public RawOperationSerializer(Adaptor adaptor) {
    this.adaptor = adaptor;
    operationSerializer = new WaveletOperationSerializer(adaptor);
  }

  @Override
  public Blob serializeOperation(RawOperation operation) {
    Timer timer = Timing.start("RawOperationSerializer.serializeOperation");
    try {
      ProtoWaveletOperations serialized = adaptor.createWaveletOperations();
      for (WaveletOperation op : operation.getOperations()) {
        ProtoWaveletOperation serializedOp = adaptor.createWaveletOperation();
        serializeOperation(op, serializedOp);
        serialized.addOperation(serializedOp);
      }
      return new Blob(adaptor.toJson(serialized));
    } finally {
      Timing.stop(timer);
    }
  }

  @Override
  public void deserializeOperation(Blob serialized, SegmentId segmentId, WaveletOperationContext context,
      RawOperation.Serializer.Writer writer) {
    Timer timer = Timing.start("RawOperationSerializer.deserializeOperation");
    try {
      ProtoWaveletOperations serializedOperations = adaptor.createWaveletOperations(serialized);
      ImmutableList.Builder<WaveletOperation> operations = ImmutableList.builder();
      for (ProtoWaveletOperation rawOp : serializedOperations.getOperation()) {
        try {
          operations.add(deserializeOperation(rawOp, segmentId, context));
        } catch (InvalidParticipantAddress ex) {
          throw new RawParseException("Operation deserializing error", ex);
        }
      }
      writer.setOperations(operations.build());
    } finally {
      Timing.stop(timer);
    }
  }

  private void serializeOperation(WaveletOperation operation, ProtoWaveletOperation serialized) {
    if (operation instanceof AddSegment) {
      serialized.setAddSegment(
        serializeOptionalSegmentId(((AddSegment)operation).getSegmentId()));
    } else if (operation instanceof RemoveSegment) {
      serialized.setRemoveSegment(
        serializeOptionalSegmentId(((RemoveSegment)operation).getSegmentId()));
    } else if (operation instanceof StartModifyingSegment) {
      serialized.setStartModifyingSegment(
        serializeOptionalSegmentId(((StartModifyingSegment)operation).getSegmentId()));
    } else if (operation instanceof EndModifyingSegment) {
      serialized.setEndModifyingSegment(
        serializeOptionalSegmentId(((EndModifyingSegment)operation).getSegmentId()));
    } else if (operation instanceof AddParticipant) {
      serialized.setAddParticipant(((AddParticipant)operation).getParticipantId().getAddress());
    } else if (operation instanceof RemoveParticipant) {
      serialized.setRemoveParticipant(((RemoveParticipant)operation).getParticipantId().getAddress());
    } else if (operation instanceof WaveletBlipOperation) {
      WaveletBlipOperation waveletOp = (WaveletBlipOperation)operation;
      BlipContentOperation blipOp = (BlipContentOperation)waveletOp.getBlipOp();
      switch (blipOp.getContributorMethod()) {
      case ADD:
        serialized.setAddDocumentContributor(true);
        break;
      case REMOVE:
        serialized.setRemoveDocumentContributor(true);
        break;
      }
      serialized.setDocumentOperation(operationSerializer.serialize(blipOp.getContentOp()));
    } else if (operation instanceof NoOp) {
      serialized.setNoOp(true);
    } else {
      throw new RuntimeException("Bad operation type");
    }
  }
  
  private WaveletOperation deserializeOperation(ProtoWaveletOperation operation,
      SegmentId segmentId, WaveletOperationContext context) throws InvalidParticipantAddress {
    if (operation.hasAddSegment()) {
      return new AddSegment(context, deserializeOptionalSegmentId(operation.getAddSegment()));
    }
    if (operation.hasRemoveSegment()) {
      return new RemoveSegment(context, deserializeOptionalSegmentId(operation.getRemoveSegment()));
    }
    if (operation.hasStartModifyingSegment()) {
      return new StartModifyingSegment(context, deserializeOptionalSegmentId(operation.getStartModifyingSegment()));
    }
    if (operation.hasEndModifyingSegment()) {
      return new EndModifyingSegment(context, deserializeOptionalSegmentId(operation.getEndModifyingSegment()));
    }
    if (operation.hasAddParticipant()) {
      return new AddParticipant(context, ParticipantId.of(operation.getAddParticipant()));
    }
    if (operation.hasRemoveParticipant()) {
      return new RemoveParticipant(context, ParticipantId.of(operation.getRemoveParticipant()));
    }
    if (operation.hasDocumentOperation()) {
      BlipOperation.UpdateContributorMethod method = BlipOperation.UpdateContributorMethod.NONE;
      if (operation.hasAddDocumentContributor()) {
        method = BlipOperation.UpdateContributorMethod.ADD;
      } else if (operation.hasRemoveDocumentContributor()) {
        method = BlipOperation.UpdateContributorMethod.REMOVE;
      }
      DocOp docOp = WaveletOperationSerializer.deserialize(operation.getDocumentOperation());
      BlipContentOperation blipOp = new BlipContentOperation(context, docOp, method);
      return new WaveletBlipOperation(segmentId.getBlipId(), blipOp);
    }
    if (operation.hasNoOp()) {
      return new NoOp(context);
    }
    Preconditions.nullPointer("Invalid operation " + operation);
    return null; // unreachable
  }
  
  private String serializeOptionalSegmentId(SegmentId segmentId) {
    if (segmentId == null) {
      return "";
    }
    return segmentId.toString();
  }

  private SegmentId deserializeOptionalSegmentId(String segmentId) {
    if (segmentId.isEmpty()) {
      return null;
    }
    return SegmentId.of(segmentId);
  }
}
