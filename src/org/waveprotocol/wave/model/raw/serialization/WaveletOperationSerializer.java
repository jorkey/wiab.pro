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
import org.waveprotocol.wave.communication.Codec;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.AnnotationBoundary;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.ElementStart;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.KeyValuePair;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.KeyValueUpdate;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.ReplaceAttributes;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.UpdateAttributes;
import org.waveprotocol.wave.federation.ProtocolHashedVersion;
import org.waveprotocol.wave.federation.ProtocolWaveletDelta;
import org.waveprotocol.wave.federation.ProtocolWaveletOperation;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.AttributesUpdate;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.DocOpCursor;
import org.waveprotocol.wave.model.document.operation.impl.AnnotationBoundaryMapImpl;
import org.waveprotocol.wave.model.document.operation.impl.AttributesImpl;
import org.waveprotocol.wave.model.document.operation.impl.AttributesUpdateImpl;
import org.waveprotocol.wave.model.document.operation.impl.DocOpBuilder;
import org.waveprotocol.wave.model.operation.wave.AddParticipant;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.BlipOperation;
import org.waveprotocol.wave.model.operation.wave.NoOp;
import org.waveprotocol.wave.model.operation.wave.RemoveParticipant;
import org.waveprotocol.wave.model.operation.wave.SubmitBlip;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for serializing/deserializing wavelet operations (and their
 * components) to/from their protocol buffer representations (and their
 * components).
 */
public class WaveletOperationSerializer {
  
  public interface Adaptor {
    ProtocolWaveletOperation createProtocolWaveletOperation();
    ProtocolWaveletOperation.MutateDocument createProtocolWaveletDocumentOperation();
    ProtocolDocumentOperation createProtocolDocumentOperation();
    Component createComponent();
    ReplaceAttributes createReplaceAttributes();
    UpdateAttributes createUpdateAttributes();
    AnnotationBoundary createAnnotationBoundary();
    ElementStart createElementStart();
    KeyValuePair createKeyValuePair();
    KeyValueUpdate createKeyValueUpdate();
    ProtocolHashedVersion createProtocolHashedVersion();
  };

  private final Adaptor adaptor;
  
  public WaveletOperationSerializer(Adaptor adaptor) {
    this.adaptor = adaptor;
  }

  /**
   * Serialize a {@link WaveletOperation} as a {@link ProtocolWaveletOperation}.
   *
   * @param waveletOp wavelet operation to serialize
   * @return serialized protocol buffer wavelet operation
   */
  public ProtocolWaveletOperation serialize(WaveletOperation waveletOp) {
    ProtocolWaveletOperation protobufOp = adaptor.createProtocolWaveletOperation();

    if (waveletOp instanceof NoOp) {
      protobufOp.setNoOp(true);
    } else if (waveletOp instanceof AddParticipant) {
      protobufOp.setAddParticipant(((AddParticipant) waveletOp).getParticipantId().getAddress());
    } else if (waveletOp instanceof RemoveParticipant) {
      protobufOp.setRemoveParticipant(((RemoveParticipant) waveletOp).getParticipantId()
          .getAddress());
    } else if (waveletOp instanceof WaveletBlipOperation) {
      ProtocolWaveletOperation.MutateDocument mutation = adaptor.createProtocolWaveletDocumentOperation();
      mutation.setDocumentId(((WaveletBlipOperation) waveletOp).getBlipId());
      mutation.setDocumentOperation(serialize(((WaveletBlipOperation) waveletOp).getBlipOp()));
      protobufOp.setMutateDocument(mutation);
    } else {
      throw new IllegalArgumentException("Unsupported operation type: " + waveletOp);
    }

    return protobufOp;
  }


  /**
   * Serialize a {@link DocOp} as a {@link ProtocolDocumentOperation}.
   *
   * @param blipOp document operation to serialize
   * @return serialized protocol buffer document operation
   */
  public ProtocolDocumentOperation serialize(BlipOperation blipOp) {
    ProtocolDocumentOperation output;

    if (blipOp instanceof BlipContentOperation) {
      output = serialize(((BlipContentOperation) blipOp).getContentOp());
    } else if (blipOp instanceof SubmitBlip) {
      // we don't support this operation here.
      output = adaptor.createProtocolDocumentOperation();
    } else {
      throw new IllegalArgumentException("Unsupported operation type: " + blipOp);
    }
    return output;
  }

  /**
   * Deserializes a {@link ProtocolWaveletDelta} as a
   * {@link TransformedWaveletDelta}
   *
   * @param protocolDelta protocol buffer wavelet delta to deserialize
   * @return deserialized wavelet delta and version
   */
  public TransformedWaveletDelta deserialize(final ProtocolWaveletDelta protocolDelta,
      HashedVersion postVersion, long applicationTimestamp) {
    WaveletOperationContext ctx = new WaveletOperationContext(
        ParticipantId.ofUnsafe(protocolDelta.getAuthor()), applicationTimestamp);
    List<WaveletOperation> ops = new ArrayList<WaveletOperation>();
    for (ProtocolWaveletOperation protocolOp : protocolDelta.getOperation()) {
      ops.add(deserialize(protocolOp, ctx));
    }
    // This involves an unnecessary copy of the ops, but avoids repeating
    // error-prone context calculations.
    return TransformedWaveletDelta.cloneOperations(
        ParticipantId.ofUnsafe(protocolDelta.getAuthor()), postVersion, applicationTimestamp, ops);
  }

  /**
   * Deserialize a {@link ProtocolWaveletOperation} as a
   * {@link WaveletOperation}.
   *
   * @param protobufOp protocol buffer wavelet operation to deserialize
   * @return deserialized wavelet operation
   */
  public static WaveletOperation deserialize(ProtocolWaveletOperation protobufOp,
      WaveletOperationContext ctx) {
    if (protobufOp.hasNoOp()) {
      return new NoOp(ctx);
    } else if (protobufOp.hasAddParticipant()) {
      return new AddParticipant(ctx, new ParticipantId(protobufOp.getAddParticipant()));
    } else if (protobufOp.hasRemoveParticipant()) {
      return new RemoveParticipant(ctx, new ParticipantId(protobufOp.getRemoveParticipant()));
    } else if (protobufOp.hasMutateDocument()) {
      return new WaveletBlipOperation(protobufOp.getMutateDocument().getDocumentId(),
          new BlipContentOperation(ctx, deserialize(protobufOp.getMutateDocument()
              .getDocumentOperation())));
    } else {
      throw new IllegalArgumentException("Unsupported operation: " + protobufOp);
    }
  }

  /**
   * Deserialize a {@link ProtocolDocumentOperation} into a {@link DocOp}.
   *
   * @param op protocol buffer document operation to deserialize
   * @return deserialized DocOp
   */
  public static DocOp deserialize(ProtocolDocumentOperation op) {
    DocOpBuilder output = new DocOpBuilder();

    for (ProtocolDocumentOperation.Component c : op.getComponent()) {
      if (c.hasAnnotationBoundary()) {
        AnnotationBoundary boundary = c.getAnnotationBoundary();
        if (boundary.hasEmpty() && boundary.getEmpty()) {
          output.annotationBoundary(AnnotationBoundaryMapImpl.EMPTY_MAP);
        } else {
          String[] ends = boundary.getEnd().toArray(new String[boundary.getEnd().size()]);
          int changes = boundary.getChange().size();
          String[] changeKeys = new String[changes];
          String[] oldValues = new String[changes];
          String[] newValues = new String[changes];
          for (int i = 0; i < changes; i++) {
            KeyValueUpdate kvu = boundary.getChange(i);
            changeKeys[i] = kvu.getKey();
            oldValues[i] = kvu.hasOldValue() ? kvu.getOldValue() : null;
            newValues[i] = kvu.hasNewValue() ? kvu.getNewValue() : null;
          }
          output.annotationBoundary(new AnnotationBoundaryMapImpl(ends, changeKeys, oldValues,
              newValues));
        }
      } else if (c.hasCharacters()) {
        output.characters(c.getCharacters());
      } else if (c.hasElementStart()) {
        output.elementStart(c.getElementStart().getType(), new AttributesImpl(deserialize(c
            .getElementStart().getAttribute())));
      } else if (c.hasElementEnd()) {
        output.elementEnd();
      } else if (c.hasRetainItemCount()) {
        output.retain(c.getRetainItemCount());
      } else if (c.hasDeleteCharacters()) {
        output.deleteCharacters(c.getDeleteCharacters());
      } else if (c.hasDeleteElementStart()) {
        output.deleteElementStart(c.getDeleteElementStart().getType(), new AttributesImpl(
            deserialize(c.getDeleteElementStart().getAttribute())));
      } else if (c.hasDeleteElementEnd()) {
        output.deleteElementEnd();
      } else if (c.hasReplaceAttributes()) {
        ReplaceAttributes r = c.getReplaceAttributes();
        if (r.hasEmpty() && r.getEmpty()) {
          output.replaceAttributes(AttributesImpl.EMPTY_MAP, AttributesImpl.EMPTY_MAP);
        } else {
          output.replaceAttributes(new AttributesImpl(deserialize(r.getOldAttribute())),
              new AttributesImpl(deserialize(r.getNewAttribute())));
        }
      } else if (c.hasUpdateAttributes()) {
        UpdateAttributes u = c.getUpdateAttributes();
        if (u.hasEmpty() && u.getEmpty()) {
          output.updateAttributes(AttributesUpdateImpl.EMPTY_MAP);
        } else {
          String[] triplets = new String[u.getAttributeUpdate().size() * 3];
          int i = 0;
          for (KeyValueUpdate kvu : u.getAttributeUpdate()) {
            triplets[i++] = kvu.getKey();
            triplets[i++] = kvu.hasOldValue() ? kvu.getOldValue() : null;
            triplets[i++] = kvu.hasNewValue() ? kvu.getNewValue() : null;
          }
          output.updateAttributes(new AttributesUpdateImpl(triplets));
        }
      } else {
        throw new IllegalArgumentException("Unsupported operation component: " + c);
      }
    }

    return output.build();
  }

  private static Map<String, String> deserialize(Collection<? extends KeyValuePair> pairs) {
    if (pairs.isEmpty()) {
      return Collections.emptyMap();
    } else {
      Map<String, String> map = new HashMap<String, String>();
      for (KeyValuePair pair : pairs) {
        map.put(pair.getKey(), pair.getValue());
      }
      return map;
    }
  }

  /**
   * Deserializes a {@link ProtocolHashedVersion} to a {@link HashedVersion}
   * POJO.
   */
  public static HashedVersion deserialize(ProtocolHashedVersion hashedVersion) {
    byte[] hash = Codec.decode(hashedVersion.getHistoryHash().getData());
    return HashedVersion.of((long) hashedVersion.getVersion(), hash);
  }

  /**
   * Serializes a {@link DocOp} as a {@link ProtocolDocumentOperation}.
   *
   * @param inputOp document operation to serialize
   * @return serialized protocol buffer document operation
   */
  public ProtocolDocumentOperation serialize(DocOp inputOp) {
    final ProtocolDocumentOperation output = adaptor.createProtocolDocumentOperation();
    inputOp.apply(new DocOpCursor() {

      @Override
      public void retain(int itemCount) {
        Component component = createComponent();
        component.setRetainItemCount(itemCount);
        addComponent(component);
      }

      @Override
      public void characters(String characters) {
        Component component = createComponent();
        component.setCharacters(characters);
        addComponent(component);
      }

      @Override
      public void deleteCharacters(String characters) {
        Component component = createComponent();
        component.setDeleteCharacters(characters);
        addComponent(component);
      }

      @Override
      public void elementStart(String type, Attributes attributes) {
        Component component = createComponent();
        component.setElementStart(makeElementStart(type, attributes));
        addComponent(component);
      }

      @Override
      public void deleteElementStart(String type, Attributes attributes) {
        Component component = createComponent();
        component.setDeleteElementStart(makeElementStart(type, attributes));
        addComponent(component);
      }

      @Override
      public void elementEnd() {
        Component component = createComponent();
        component.setElementEnd(true);
        addComponent(component);
      }

      @Override
      public void deleteElementEnd() {
        Component component = createComponent();
        component.setDeleteElementEnd(true);
        addComponent(component);
      }

      @Override
      public void replaceAttributes(Attributes oldAttributes, Attributes newAttributes) {
        ReplaceAttributes r = adaptor.createReplaceAttributes();
        if (oldAttributes.isEmpty() && newAttributes.isEmpty()) {
          r.setEmpty(true);
        } else {
          for (String name : oldAttributes.keySet()) {
            r.addOldAttribute(keyValuePair(name, oldAttributes.get(name)));
          }

          for (String name : newAttributes.keySet()) {
            r.addNewAttribute(keyValuePair(name, newAttributes.get(name)));
          }
        }
        Component component = createComponent();
        component.setReplaceAttributes(r);
        addComponent(component);
      }

      @Override
      public void updateAttributes(AttributesUpdate attributes) {
        UpdateAttributes u = adaptor.createUpdateAttributes();
        if (attributes.changeSize() == 0) {
          u.setEmpty(true);
        } else {
          for (int i = 0; i < attributes.changeSize(); i++) {
            u.addAttributeUpdate(keyValueUpdate(attributes.getChangeKey(i),
                attributes.getOldValue(i), attributes.getNewValue(i)));
          }
        }
        Component component = createComponent();
        component.setUpdateAttributes(u);
        addComponent(component);
      }

      @Override
      public void annotationBoundary(AnnotationBoundaryMap map) {
        AnnotationBoundary a = adaptor.createAnnotationBoundary();
        if (map.endSize() == 0 && map.changeSize() == 0) {
          a.setEmpty(true);
        } else {
          for (int i = 0; i < map.endSize(); i++) {
            a.addEnd(map.getEndKey(i));
          }
          for (int i = 0; i < map.changeSize(); i++) {
            a.addChange(
                keyValueUpdate(map.getChangeKey(i), map.getOldValue(i), map.getNewValue(i)));
          }
        }
        Component component = createComponent();
        component.setAnnotationBoundary(a);
        addComponent(component);
      }

      private Component createComponent() {
        return adaptor.createComponent();
      }

      private void addComponent(Component component) {
        output.addComponent(component);
      }

      private ElementStart makeElementStart(String type, Attributes attributes) {
        ElementStart e = adaptor.createElementStart();
        e.setType(type);
        for (String name : attributes.keySet()) {
          e.addAttribute(keyValuePair(name, attributes.get(name)));
        }
        return e;
      }

      private KeyValuePair keyValuePair(String key, String value) {
        KeyValuePair pair = adaptor.createKeyValuePair();
        pair.setKey(key);
        pair.setValue(value);
        return pair;
      }

      private KeyValueUpdate keyValueUpdate(String key, String oldValue, String newValue) {
        KeyValueUpdate kvu = adaptor.createKeyValueUpdate();
        kvu.setKey(key);
        if (oldValue != null) {
          kvu.setOldValue(oldValue);
        }
        if (newValue != null) {
          kvu.setNewValue(newValue);
        }
        return kvu;
      }
    });
    return output;
  }

  /**
   * Serializes a {@link HashedVersion} POJO to a {@link ProtocolHashedVersion}.
   */
  public ProtocolHashedVersion serialize(HashedVersion hashedVersion) {
    Blob b64Hash = new Blob(Codec.encode(hashedVersion.getHistoryHash()));
    ProtocolHashedVersion version = adaptor.createProtocolHashedVersion();
    version.setVersion(hashedVersion.getVersion());
    version.setHistoryHash(b64Hash);
    return version;
  }
}
