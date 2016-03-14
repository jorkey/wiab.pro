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

package org.waveprotocol.wave.model.wave.undo;

import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.algorithm.DocOpInverter;
import org.waveprotocol.wave.model.document.operation.algorithm.Transformer;
import org.waveprotocol.wave.model.operation.OperationPair;
import org.waveprotocol.wave.model.operation.TransformException;
import org.waveprotocol.wave.model.operation.core.CoreAddParticipant;
import org.waveprotocol.wave.model.operation.core.CoreNoOp;
import org.waveprotocol.wave.model.operation.core.CoreRemoveParticipant;
import org.waveprotocol.wave.model.operation.core.CoreWaveletDocumentOperation;
import org.waveprotocol.wave.model.operation.core.CoreWaveletOperation;
import org.waveprotocol.wave.model.operation.core.CoreAddSegment;
import org.waveprotocol.wave.model.operation.core.CoreEndModifyingSegment;
import org.waveprotocol.wave.model.operation.core.CoreRemoveSegment;
import org.waveprotocol.wave.model.operation.core.CoreStartModifyingSegment;
import org.waveprotocol.wave.model.operation.wave.AddParticipant;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.NoOp;
import org.waveprotocol.wave.model.operation.wave.RemoveParticipant;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.AddSegment;
import org.waveprotocol.wave.model.operation.wave.EndModifyingSegment;
import org.waveprotocol.wave.model.operation.wave.RemoveSegment;
import org.waveprotocol.wave.model.operation.wave.StartModifyingSegment;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.id.SegmentId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * An aggregate operation, built up from wavelet operations.
 *
 */
final class AggregateOperation {

  private static final class DocumentOperations {

    final String id;
    final DocOpList operations;

    DocumentOperations(String id, DocOpList operations) {
      this.id = id;
      this.operations = operations;
    }

  }

  private static final Comparator<SegmentId> segmentComparator =
      new Comparator<SegmentId>() {

    @Override
    public int compare(SegmentId o1, SegmentId o2) {
      if (o1 == o2) {
        return 0;
      }
      if (o1 == null) {
        return 1;
      }
      if (o2 == null) {
        return -1;
      }
      return o1.compareTo(o2);
    }

  };

  private static final Comparator<ParticipantId> participantComparator =
      new Comparator<ParticipantId>() {

    @Override
    public int compare(ParticipantId o1, ParticipantId o2) {
      return o1.getAddress().compareTo(o2.getAddress());
    }

  };

  /**
   * Creates an aggregate operation from a <code>CoreWaveletOperation</code>.
   *
   * @param operation The wavelet operation whose behaviour the aggregate
   *        operation should have.
   * @return The aggregate operation.
   */
  static AggregateOperation createAggregate(CoreWaveletOperation operation) {
    if (operation instanceof CoreWaveletDocumentOperation) {
      return new AggregateOperation((CoreWaveletDocumentOperation) operation);
    } else if (operation instanceof CoreRemoveParticipant) {
      return new AggregateOperation((CoreRemoveParticipant) operation);
    } else if (operation instanceof CoreAddParticipant) {
      return new AggregateOperation((CoreAddParticipant) operation);
    } else if (operation instanceof CoreAddSegment) {
      return new AggregateOperation((CoreAddSegment) operation);
    } else if (operation instanceof CoreRemoveSegment) {
      return new AggregateOperation((CoreRemoveSegment) operation);
    } else if (operation instanceof CoreStartModifyingSegment) {
      return new AggregateOperation((CoreStartModifyingSegment) operation);
    } else if (operation instanceof CoreEndModifyingSegment) {
      return new AggregateOperation((CoreEndModifyingSegment) operation);
    }
    assert operation instanceof CoreNoOp;
    return new AggregateOperation();
  }

  /**
   * Creates an aggregate operation from a <code>WaveletOperation</code>.
   *
   * @param operation The wavelet operation whose behaviour the aggregate
   *        operation should have.
   * @return The aggregate operation.
   */
  static AggregateOperation createAggregate(WaveletOperation operation) {
    if (operation instanceof WaveletBlipOperation) {
      return new AggregateOperation((WaveletBlipOperation) operation);
    } else if (operation instanceof RemoveParticipant) {
      return new AggregateOperation((RemoveParticipant) operation);
    } else if (operation instanceof AddParticipant) {
      return new AggregateOperation((AddParticipant) operation);
    } else if (operation instanceof AddSegment) {
      return new AggregateOperation((AddSegment) operation);
    } else if (operation instanceof RemoveSegment) {
      return new AggregateOperation((RemoveSegment) operation);
    } else if (operation instanceof StartModifyingSegment) {
      return new AggregateOperation((StartModifyingSegment) operation);
    } else if (operation instanceof EndModifyingSegment) {
      return new AggregateOperation((EndModifyingSegment) operation);
    }
    assert operation instanceof NoOp : "Operation is an unhandled type: " + operation.getClass();
    return new AggregateOperation();
  }

  private static DocOpList invert(DocOpList docOpList) {
    return new DocOpList.Singleton(DocOpInverter.invert(docOpList.composeAll()));
  }

  /**
   * Composes the given aggregate operations.
   *
   * @param operations The aggregate operations to compose.
   * @return The composition of the given operations.
   */
  static AggregateOperation compose(Iterable<AggregateOperation> operations) {
    // NOTE(user): It's possible to replace the following two sets with a single map.
    Set<SegmentId> segmentsToRemove = new TreeSet<SegmentId>(segmentComparator);
    Set<SegmentId> segmentsToAdd = new TreeSet<SegmentId>(segmentComparator);
    Set<SegmentId> segmentsToEndModifying = new TreeSet<SegmentId>(segmentComparator);
    Set<SegmentId> segmentsToStartModifying = new TreeSet<SegmentId>(segmentComparator);
    Set<ParticipantId> participantsToRemove = new TreeSet<ParticipantId>(participantComparator);
    Set<ParticipantId> participantsToAdd = new TreeSet<ParticipantId>(participantComparator);
    Map<String, DocOpList> docOps = new TreeMap<String, DocOpList>();
    for (AggregateOperation operation : operations) {
      composeIds(operation.segmentsToRemove, segmentsToRemove, segmentsToAdd);
      composeIds(operation.segmentsToAdd, segmentsToAdd, segmentsToRemove);
      composeIds(operation.segmentsToEndModifying, segmentsToEndModifying, segmentsToStartModifying);
      composeIds(operation.segmentsToStartModifying, segmentsToStartModifying, segmentsToEndModifying);
      composeIds(operation.participantsToRemove, participantsToRemove, participantsToAdd);
      composeIds(operation.participantsToAdd, participantsToAdd, participantsToRemove);
      for (DocumentOperations documentOps : operation.docOps) {
        DocOpList docOpList = docOps.get(documentOps.id);
        if (docOpList != null) {
          docOps.put(documentOps.id, docOpList.concatenateWith(documentOps.operations));
        } else {
          docOps.put(documentOps.id, documentOps.operations);
        }
      }
    }
    return new AggregateOperation(
        new ArrayList<SegmentId>(segmentsToRemove),
        new ArrayList<SegmentId>(segmentsToAdd),
        new ArrayList<SegmentId>(segmentsToEndModifying),
        new ArrayList<SegmentId>(segmentsToStartModifying),
        new ArrayList<ParticipantId>(participantsToRemove),
        new ArrayList<ParticipantId>(participantsToAdd),
        mapToList(docOps));
  }
  
  static private <T> void composeIds(List<T> operationIds, Set<T> idsToAdd, Set<T> idsToRemove) {
    for (T id : operationIds) {
      if (idsToRemove.contains(id)) {
        idsToRemove.remove(id);
      } else {
        idsToAdd.add(id);
      }
    }
  }

  /**
   * Transforms the given aggregate operations.
   *
   * @param clientOp The client operation to transform.
   * @param serverOp The server operation to transform.
   *
   * @return The transform of the two operations.
   * @throws TransformException
   */
  static OperationPair<AggregateOperation> transform(AggregateOperation clientOp,
      AggregateOperation serverOp) throws TransformException {
    List<SegmentId> clientSegmentsToRemove = new ArrayList<SegmentId>();
    List<SegmentId> serverSegmentsToRemove = new ArrayList<SegmentId>();
    List<SegmentId> clientSegmentsToAdd = new ArrayList<SegmentId>();
    List<SegmentId> serverSegmentsToAdd = new ArrayList<SegmentId>();
    removeCommonIds(clientOp.segmentsToRemove, serverOp.segmentsToRemove,
        clientSegmentsToRemove, serverSegmentsToRemove, segmentComparator);
    removeCommonIds(clientOp.segmentsToAdd, serverOp.segmentsToAdd,
        clientSegmentsToAdd, serverSegmentsToAdd, segmentComparator);
    List<SegmentId> clientSegmentsToEndModifying = new ArrayList<SegmentId>();
    List<SegmentId> serverSegmentsToEndModifying = new ArrayList<SegmentId>();
    List<SegmentId> clientSegmentsToStartModifying = new ArrayList<SegmentId>();
    List<SegmentId> serverSegmentsToStartModifying = new ArrayList<SegmentId>();
    removeCommonIds(clientOp.segmentsToEndModifying, serverOp.segmentsToEndModifying,
        clientSegmentsToEndModifying, serverSegmentsToEndModifying, segmentComparator);
    removeCommonIds(clientOp.segmentsToStartModifying, serverOp.segmentsToStartModifying,
        clientSegmentsToStartModifying, serverSegmentsToStartModifying, segmentComparator);
    List<ParticipantId> clientParticipantsToRemove = new ArrayList<ParticipantId>();
    List<ParticipantId> serverParticipantsToRemove = new ArrayList<ParticipantId>();
    List<ParticipantId> clientParticipantsToAdd = new ArrayList<ParticipantId>();
    List<ParticipantId> serverParticipantsToAdd = new ArrayList<ParticipantId>();
    List<DocumentOperations> clientDocOps = new ArrayList<DocumentOperations>();
    List<DocumentOperations> serverDocOps = new ArrayList<DocumentOperations>();
    removeCommonIds(clientOp.participantsToRemove, serverOp.participantsToRemove,
        clientParticipantsToRemove, serverParticipantsToRemove, participantComparator);
    removeCommonIds(clientOp.participantsToAdd, serverOp.participantsToAdd,
        clientParticipantsToAdd, serverParticipantsToAdd, participantComparator);
    transformDocumentOperations(clientOp.docOps, serverOp.docOps,
        clientDocOps, serverDocOps);
    AggregateOperation transformedClientOp = new AggregateOperation(
        clientSegmentsToRemove, clientSegmentsToAdd,
        clientSegmentsToEndModifying, clientSegmentsToStartModifying,
        clientParticipantsToRemove, clientParticipantsToAdd, clientDocOps);
    AggregateOperation transformedServerOp = new AggregateOperation(
        serverSegmentsToRemove, serverSegmentsToAdd,
        serverSegmentsToEndModifying, serverSegmentsToStartModifying,
        serverParticipantsToRemove, serverParticipantsToAdd, serverDocOps);
    return new OperationPair<AggregateOperation>(transformedClientOp, transformedServerOp);
  }

  private static List<DocumentOperations> mapToList(Map<String, DocOpList> map) {
    List<DocumentOperations> list = new ArrayList<DocumentOperations>();
    for (Map.Entry<String, DocOpList> entry : map.entrySet()) {
      list.add(new DocumentOperations(entry.getKey(), entry.getValue()));
    }
    return list;
  }

  static private <T> void removeCommonIds(List<T> ids1, List<T> ids2,
      List<T> outputIds1, List<T> outputIds2, Comparator<T> comparator) {
    int index = 0;
    outerLoop:
    for (T id1 : ids1) {
      while (index < ids2.size()) {
        T id2 = ids2.get(index);
        int comparison = comparator.compare(id1, id2);
        if (comparison < 0) {
          break;
        }
        ++index;
        if (comparison > 0) {
          outputIds2.add(id2);
        } else {
          continue outerLoop;
        }
      }
      outputIds1.add(id1);
    }
    for (; index < ids2.size(); ++index) {
      outputIds2.add(ids2.get(index));
    }
  }

  static private void transformDocumentOperations(
      List<DocumentOperations> clientOps,
      List<DocumentOperations> serverOps,
      List<DocumentOperations> transformedClientOps,
      List<DocumentOperations> transformedServerOps) throws TransformException {
    int index = 0;
    outerLoop:
    for (DocumentOperations fromClient : clientOps) {
      while (index < serverOps.size()) {
        DocumentOperations fromServer = serverOps.get(index);
        int comparison = fromClient.id.compareTo(fromServer.id);
        if (comparison < 0) {
          break;
        }
        ++index;
        if (comparison > 0) {
          transformedServerOps.add(fromServer);
        } else {
          DocOp clientOp = fromClient.operations.composeAll();
          DocOp serverOp = fromServer.operations.composeAll();
          OperationPair<DocOp> transformedOps = Transformer.transform(clientOp, serverOp);
          transformedClientOps.add(new DocumentOperations(fromClient.id,
              new DocOpList.Singleton(transformedOps.clientOp())));
          transformedServerOps.add(new DocumentOperations(fromClient.id,
              new DocOpList.Singleton(transformedOps.serverOp())));
          continue outerLoop;
        }
      }
      transformedClientOps.add(fromClient);
    }
    for (; index < serverOps.size(); ++index) {
      transformedServerOps.add(serverOps.get(index));
    }
  }

  private final List<SegmentId> segmentsToRemove;
  private final List<SegmentId> segmentsToAdd;
  private final List<SegmentId> segmentsToEndModifying;
  private final List<SegmentId> segmentsToStartModifying;
  private final List<ParticipantId> participantsToRemove;
  private final List<ParticipantId> participantsToAdd;
  private final List<DocumentOperations> docOps;

  private AggregateOperation(List<SegmentId> segmentsToRemove, List<SegmentId> segmentsToAdd,
      List<SegmentId> segmentsToEndModifying, List<SegmentId> segmentsToStartModifying,
      List<ParticipantId> participantsToRemove, List<ParticipantId> participantsToAdd,
      List<DocumentOperations> docOps) {
    this.segmentsToRemove = segmentsToRemove;
    this.segmentsToAdd = segmentsToAdd;
    this.segmentsToEndModifying = segmentsToEndModifying;
    this.segmentsToStartModifying = segmentsToStartModifying;
    this.participantsToRemove = participantsToRemove;
    this.participantsToAdd = participantsToAdd;
    this.docOps = docOps;
  }

  /**
   * Constructs an aggregate operation that does nothing.
   */
  AggregateOperation() {
    segmentsToRemove = Collections.emptyList();
    segmentsToAdd = Collections.emptyList();
    segmentsToEndModifying = Collections.emptyList();
    segmentsToStartModifying = Collections.emptyList();
    participantsToRemove = Collections.emptyList();
    participantsToAdd = Collections.emptyList();
    docOps = Collections.emptyList();
  }

  // The "Core" operations are simpler variants of the regular operations,
  // used in the open source org.waveprotocol federation implementation.

  /**
   * Constructs an aggregate operation that has the same behaviour as a
   * <code>CoreWaveletDocumentOperation</code>.
   *
   * @param waveletDocumentOperation The wavelet document operation.
   */
  AggregateOperation(CoreWaveletDocumentOperation waveletDocumentOperation) {
    segmentsToRemove = Collections.emptyList();
    segmentsToAdd = Collections.emptyList();
    segmentsToEndModifying = Collections.emptyList();
    segmentsToStartModifying = Collections.emptyList();
    participantsToRemove = Collections.emptyList();
    participantsToAdd = Collections.emptyList();
    docOps = Collections.singletonList(
        new DocumentOperations(
            waveletDocumentOperation.getDocumentId(),
            new DocOpList.Singleton(waveletDocumentOperation.getOperation())));
  }

  /**
   * Constructs an aggregate operation that has the same behaviour as a
   * <code>CoreRemoveParticipant</code>.
   *
   * @param removeParticipant
   */
  AggregateOperation(CoreRemoveParticipant removeParticipant) {
    segmentsToRemove = Collections.emptyList();
    segmentsToAdd = Collections.emptyList();
    segmentsToEndModifying = Collections.emptyList();
    segmentsToStartModifying = Collections.emptyList();
    participantsToRemove = Collections.singletonList(removeParticipant.getParticipantId());
    participantsToAdd = Collections.emptyList();
    docOps = Collections.emptyList();
  }

  /**
   * Constructs an aggregate operation that has the same behaviour as an
   * <code>CoreAddParticipant</code>.
   *
   * @param addParticipant
   */
  AggregateOperation(CoreAddParticipant addParticipant) {
    segmentsToRemove = Collections.emptyList();
    segmentsToAdd = Collections.emptyList();
    segmentsToEndModifying = Collections.emptyList();
    segmentsToStartModifying = Collections.emptyList();
    participantsToRemove = Collections.emptyList();
    participantsToAdd = Collections.singletonList(addParticipant.getParticipantId());
    docOps = Collections.emptyList();
  }

  /**
   * Constructs an aggregate operation that has the same behaviour as an
   * <code>CoreRemoveSegment</code>.
   *
   * @param removeSegment
   */
  AggregateOperation(CoreRemoveSegment removeSegment) {
    segmentsToRemove = Collections.singletonList(removeSegment.getSegmentId());
    segmentsToAdd = Collections.emptyList();
    segmentsToEndModifying = Collections.emptyList();
    segmentsToStartModifying = Collections.emptyList();
    participantsToRemove = Collections.emptyList();
    participantsToAdd = Collections.emptyList();
    docOps = Collections.emptyList();
  }
  
  /**
   * Constructs an aggregate operation that has the same behaviour as an
   * <code>CoreAddSegment</code>.
   *
   * @param addSegment
   */
  AggregateOperation(CoreAddSegment addSegment) {
    segmentsToRemove = Collections.emptyList();
    segmentsToAdd = Collections.singletonList(addSegment.getSegmentId());
    segmentsToEndModifying = Collections.emptyList();
    segmentsToStartModifying = Collections.emptyList();
    participantsToRemove = Collections.emptyList();
    participantsToAdd = Collections.emptyList();
    docOps = Collections.emptyList();
  }
  
  /**
   * Constructs an aggregate operation that has the same behaviour as an
   * <code>CoreEndModifyingSegment</code>.
   *
   * @param endModifyingSegment
   */
  AggregateOperation(CoreEndModifyingSegment endModifyingSegment) {
    segmentsToRemove = Collections.emptyList();
    segmentsToAdd = Collections.emptyList();
    segmentsToEndModifying = Collections.singletonList(endModifyingSegment.getSegmentId());
    segmentsToStartModifying = Collections.emptyList();
    participantsToRemove = Collections.emptyList();
    participantsToAdd = Collections.emptyList();
    docOps = Collections.emptyList();
  }
  
  /**
   * Constructs an aggregate operation that has the same behaviour as an
   * <code>CoreStartModifyingSegment</code>.
   *
   * @param startModifyingSegment
   */
  AggregateOperation(CoreStartModifyingSegment startModifyingSegment) {
    segmentsToRemove = Collections.emptyList();
    segmentsToAdd = Collections.emptyList();
    segmentsToEndModifying = Collections.emptyList();
    segmentsToStartModifying = Collections.singletonList(startModifyingSegment.getSegmentId());
    participantsToRemove = Collections.emptyList();
    participantsToAdd = Collections.emptyList();
    docOps = Collections.emptyList();
  }
  
  /**
   * Constructs an aggregate operation that has the same behaviour as a
   * <code>WaveletBlipOperation</code>.
   *
   * @param op The wavelet blip operation.
   */
  AggregateOperation(WaveletBlipOperation op) {
    segmentsToRemove = Collections.emptyList();
    segmentsToAdd = Collections.emptyList();
    segmentsToEndModifying = Collections.emptyList();
    segmentsToStartModifying = Collections.emptyList();
    participantsToRemove = Collections.emptyList();
    participantsToAdd = Collections.emptyList();
    if (op.getBlipOp() instanceof BlipContentOperation) {
    docOps = Collections.singletonList(
        new DocumentOperations(
            op.getBlipId(),
            new DocOpList.Singleton(((BlipContentOperation) op.getBlipOp()).getContentOp())));
    } else {
      docOps = Collections.emptyList();
    }
  }

  /**
   * Constructs an aggregate operation that has the same behaviour as a
   * <code>RemoveParticipant</code>.
   *
   * @param removeParticipant
   */
  AggregateOperation(RemoveParticipant removeParticipant) {
    ParticipantId participant = new ParticipantId(
        removeParticipant.getParticipantId().getAddress());
    segmentsToRemove = Collections.emptyList();
    segmentsToAdd = Collections.emptyList();
    segmentsToEndModifying = Collections.emptyList();
    segmentsToStartModifying = Collections.emptyList();
    participantsToRemove = Collections.singletonList(participant);
    participantsToAdd = Collections.emptyList();
    docOps = Collections.emptyList();
  }

  /**
   * Constructs an aggregate operation that has the same behaviour as an
   * <code>AddParticipant</code>.
   *
   * @param addParticipant
   */
  AggregateOperation(AddParticipant addParticipant) {
    ParticipantId participant = new ParticipantId(addParticipant.getParticipantId().getAddress());
    segmentsToRemove = Collections.emptyList();
    segmentsToAdd = Collections.emptyList();
    segmentsToEndModifying = Collections.emptyList();
    segmentsToStartModifying = Collections.emptyList();
    participantsToRemove = Collections.emptyList();
    participantsToAdd = Collections.singletonList(participant);
    docOps = Collections.emptyList();
  }

  /**
   * Constructs an aggregate operation that has the same behaviour as an
   * <code>RemoveSegment</code>.
   *
   * @param removeSegment
   */
  AggregateOperation(RemoveSegment removeSegment) {
    segmentsToRemove = Collections.singletonList(removeSegment.getSegmentId());
    segmentsToAdd = Collections.emptyList();
    segmentsToEndModifying = Collections.emptyList();
    segmentsToStartModifying = Collections.emptyList();
    participantsToRemove = Collections.emptyList();
    participantsToAdd = Collections.emptyList();
    docOps = Collections.emptyList();
  }
  
  /**
   * Constructs an aggregate operation that has the same behaviour as an
   * <code>AddSegment</code>.
   *
   * @param addSegment
   */
  AggregateOperation(AddSegment addSegment) {
    segmentsToRemove = Collections.emptyList();
    segmentsToAdd = Collections.singletonList(addSegment.getSegmentId());
    segmentsToEndModifying = Collections.emptyList();
    segmentsToStartModifying = Collections.emptyList();
    participantsToRemove = Collections.emptyList();
    participantsToAdd = Collections.emptyList();
    docOps = Collections.emptyList();
  }
  
  /**
   * Constructs an aggregate operation that has the same behaviour as an
   * <code>EndModifyingSegment</code>.
   *
   * @param endModifyingSegment
   */
  AggregateOperation(EndModifyingSegment endModifyingSegment) {
    segmentsToRemove = Collections.emptyList();
    segmentsToAdd = Collections.emptyList();
    segmentsToEndModifying = Collections.singletonList(endModifyingSegment.getSegmentId());
    segmentsToStartModifying = Collections.emptyList();
    participantsToRemove = Collections.emptyList();
    participantsToAdd = Collections.emptyList();
    docOps = Collections.emptyList();
  }
  
  /**
   * Constructs an aggregate operation that has the same behaviour as an
   * <code>StartModifyingSegment</code>.
   *
   * @param startModifyingSegment
   */
  AggregateOperation(StartModifyingSegment startModifyingSegment) {
    segmentsToRemove = Collections.emptyList();
    segmentsToAdd = Collections.emptyList();
    segmentsToEndModifying = Collections.emptyList();
    segmentsToStartModifying = Collections.singletonList(startModifyingSegment.getSegmentId());
    participantsToRemove = Collections.emptyList();
    participantsToAdd = Collections.emptyList();
    docOps = Collections.emptyList();
  }
  
  /**
   * Inverts this aggregate operation.
   *
   * @return this aggregate operation.
   */
  AggregateOperation invert() {
    List<DocumentOperations> invertedDocOps = new ArrayList<DocumentOperations>(docOps.size());
    for (DocumentOperations operations : docOps) {
      invertedDocOps.add(new DocumentOperations(operations.id, invert(operations.operations)));
    }
    return new AggregateOperation(segmentsToAdd, segmentsToRemove, segmentsToStartModifying, segmentsToEndModifying,
      participantsToAdd, participantsToRemove, invertedDocOps);
  }

  /**
   * Creates a list of wavelet operations representing the behaviour of this
   * aggregate operation.
   *
   * @return The list of wavelet operations representing the behaviour of this
   *         aggregate operation.
   */
  List<CoreWaveletOperation> toCoreWaveletOperations() {
    List<CoreWaveletOperation> operations = new ArrayList<CoreWaveletOperation>();
    for (SegmentId segment : segmentsToRemove) {
      operations.add(new CoreRemoveSegment(segment));
    }
    for (SegmentId segment : segmentsToAdd) {
      operations.add(new CoreAddSegment(segment));
    }
    for (SegmentId segment : segmentsToEndModifying) {
      operations.add(new CoreEndModifyingSegment(segment));
    }
    for (SegmentId segment : segmentsToStartModifying) {
      operations.add(new CoreStartModifyingSegment(segment));
    }
    for (ParticipantId participant : participantsToRemove) {
      operations.add(new CoreRemoveParticipant(participant));
    }
    for (ParticipantId participant : participantsToAdd) {
      operations.add(new CoreAddParticipant(participant));
    }
    for (DocumentOperations documentOps : docOps) {
      operations.add(new CoreWaveletDocumentOperation(documentOps.id,
          documentOps.operations.composeAll()));
    }
    return operations;
  }
}
