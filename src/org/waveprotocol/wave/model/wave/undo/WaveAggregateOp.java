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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.operation.OperationPair;
import org.waveprotocol.wave.model.operation.TransformException;
import org.waveprotocol.wave.model.operation.core.CoreAddParticipant;
import org.waveprotocol.wave.model.operation.core.CoreRemoveParticipant;
import org.waveprotocol.wave.model.operation.core.CoreWaveletDocumentOperation;
import org.waveprotocol.wave.model.operation.core.CoreWaveletOperation;
import org.waveprotocol.wave.model.operation.core.CoreAddSegment;
import org.waveprotocol.wave.model.operation.core.CoreEndModifyingSegment;
import org.waveprotocol.wave.model.operation.core.CoreRemoveSegment;
import org.waveprotocol.wave.model.operation.core.CoreStartModifyingSegment;
import org.waveprotocol.wave.model.operation.wave.AddSegment;
import org.waveprotocol.wave.model.operation.wave.EndModifyingSegment;
import org.waveprotocol.wave.model.operation.wave.RemoveSegment;
import org.waveprotocol.wave.model.operation.wave.StartModifyingSegment;
import org.waveprotocol.wave.model.operation.wave.AddParticipant;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.RemoveParticipant;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An aggregate operation similar to @see AggregateOperation, but with an
 * additional field to specify the creator of its component ops.
 *
 */
public class WaveAggregateOp {
  /**  List of op, creator pairs */
  private final List<OpCreatorPair> opPairs;

  private static class OpCreatorPair {
    final AggregateOperation op;
    final ParticipantId creator;
    final long timestamp;

    OpCreatorPair(AggregateOperation op, ParticipantId creator, long timestamp) {
      Preconditions.checkNotNull(op, "op must be non-null");
      Preconditions.checkNotNull(creator, "creator must be non-null");
      this.op = op;
      this.creator = creator;
      this.timestamp = timestamp;
    }
  }

  /**
   * Constructs a WaveAggregateOp from a Wavelet op.
   * @param op
   */
  public static WaveAggregateOp createAggregate(WaveletOperation op) {
    Preconditions.checkNotNull(op, "op must be non-null");
    Preconditions.checkNotNull(op.getContext(), "context must be non-null");

    ParticipantId creator = op.getContext().getCreator();
    long timestamp = op.getContext().getTimestamp();
    AggregateOperation aggOp = AggregateOperation.createAggregate(op);

    return new WaveAggregateOp(aggOp, creator, timestamp);
  }

  /**
   * Compose a list of WaveAggregateOps.
   *
   * NOTE(user): Consider adding some checks for operations that span different
   * creators, i.e. a compose of addParticipant(personA) by creator1 and
   * creator2 should be invalid.
   *
   * @param operations
   */
  public static WaveAggregateOp compose(List<WaveAggregateOp> operations) {
    return new WaveAggregateOp(composeDocumentOps(flatten(operations)));
  }

  /**
   * Transform the given operations.
   *
   * @param clientOp
   * @param serverOp
   *
   * @throws TransformException
   */
  static OperationPair<WaveAggregateOp> transform(WaveAggregateOp clientOp,
      WaveAggregateOp serverOp) throws TransformException {
    // This gets filled with transformed server ops.
    List<OpCreatorPair> transformedServerOps = new ArrayList<OpCreatorPair>();
    // This starts with the original client ops, and gets transformed with each server op.
    List<OpCreatorPair> transformedClientOps = new ArrayList<OpCreatorPair>(clientOp.opPairs);

    for (OpCreatorPair sPair : serverOp.opPairs) {
      transformedServerOps.add(transformAndUpdate(transformedClientOps, sPair));
    }

    return new OperationPair<WaveAggregateOp>(new WaveAggregateOp(transformedClientOps),
        new WaveAggregateOp(transformedServerOps));
  }

  private static void maybeCollectOps(List<AggregateOperation> ops, ParticipantId creator,
      long timestamp, List<OpCreatorPair> dest) {
    if (ops != null && ops.size() > 0) {
      assert creator != null;
      dest.add(new OpCreatorPair(AggregateOperation.compose(ops), creator, timestamp));
    }
  }

  private static List<OpCreatorPair> composeDocumentOps(List<OpCreatorPair> ops) {
    Timer timer = Timing.start("WaveAggregateOp.composeDocumentOps");
    try {
      List<OpCreatorPair> ret = new ArrayList<>();
      ParticipantId currentCreator = null;
      long currentTimestamp = 0;
      List<AggregateOperation> currentOps = null;

      // Group sequences of ops.
      for (OpCreatorPair op : ops) {
        if (!op.creator.equals(currentCreator)) {
          // If the creator is different, compose and finish with the current
          // group, and start the next group.
          maybeCollectOps(currentOps, currentCreator, currentTimestamp, ret);
          currentOps = null;
          currentCreator = op.creator;
          currentTimestamp = op.timestamp;
        }

        if (currentOps == null) {
          currentOps = new ArrayList<>();
        }
        currentOps.add(op.op);
      }
      // Collect the last batch of ops.
      maybeCollectOps(currentOps, currentCreator, currentTimestamp, ret);

      return ret;
    } finally {
      Timing.stop(timer);
    }
  }

  /**
   * Flatten a sequence of WaveAggregate operations into a list of
   * OpCreatorPairs.
   *
   * @param operations
   */
  private static List<OpCreatorPair> flatten(List<WaveAggregateOp> operations) {
    List<OpCreatorPair> ret = new ArrayList<OpCreatorPair>();
    for (WaveAggregateOp aggOp : operations) {
      ret.addAll(aggOp.opPairs);
    }
    return ret;
  }

  /**
   * Transform stream S against streamC, updating streamC and returning the
   * transform of s.
   *
   * @param streamC
   * @param s
   * @throws TransformException
   */
  private static OpCreatorPair transformAndUpdate(List<OpCreatorPair> streamC, OpCreatorPair s)
      throws TransformException {
    // Makes a copy of streamC and clear the original, so that it can be filled with the
    // transformed version.
    List<OpCreatorPair> streamCCopy = new ArrayList<OpCreatorPair>(streamC);
    streamC.clear();

    for (OpCreatorPair c : streamCCopy) {
      OperationPair<OpCreatorPair> transformed = transform(c, s);
      streamC.add(transformed.clientOp());
      s = transformed.serverOp();
    }

    return s;
  }

  private static OperationPair<OpCreatorPair> transform(OpCreatorPair c, OpCreatorPair s)
      throws TransformException {
    OperationPair<AggregateOperation> transformed = AggregateOperation.transform(c.op, s.op);
    return new OperationPair<OpCreatorPair>(new OpCreatorPair(transformed.clientOp(), c.creator, c.timestamp),
        new OpCreatorPair(transformed.serverOp(), s.creator, s.timestamp));
  }

  @VisibleForTesting
  WaveAggregateOp(AggregateOperation op, ParticipantId creator, long timestamp) {
    opPairs = Collections.singletonList(new OpCreatorPair(op, creator, timestamp));
  }

  private WaveAggregateOp(List<OpCreatorPair> pairs) {
    Preconditions.checkNotNull(pairs, "pairs must be non-null");
    this.opPairs = pairs;
  }

  /**
   * @return wavelet operations corresponding to this WaveAggregateOp.
   */
  public ImmutableList<WaveletOperation> toWaveletOperations() {
    return toWaveletOperationsWithVersions(0, null);
  }

  /**
   * Special case where we populate the last op in the list with the given versions.
   * This is necessary to preserve the WaveletOperationContext from the server.
   *
   * @param segmentVersion
   * @param hashedVersion
   */
  public ImmutableList<WaveletOperation> toWaveletOperationsWithVersions(long segmentVersion,
      HashedVersion hashedVersion) {
    Timer timer = Timing.start("WaveAggregateOp.toWaveletOperationsWithVersions");
    try {
      ImmutableList.Builder<WaveletOperation> ret = ImmutableList.builder();
      for (int i = 0; i < opPairs.size(); ++i) {
        OpCreatorPair pair = opPairs.get(i);
        boolean isLastOfOuter = (i == opPairs.size() - 1);

        List<CoreWaveletOperation> coreWaveletOperations = pair.op.toCoreWaveletOperations();

        for (int j = 0; j < coreWaveletOperations.size(); ++j) {
          boolean isLast = isLastOfOuter && (j == coreWaveletOperations.size() - 1);
          WaveletOperationContext opContext;
          if (isLast) {
            opContext = new WaveletOperationContext(pair.creator, pair.timestamp, segmentVersion, hashedVersion);
          } else {
            opContext = new WaveletOperationContext(pair.creator, pair.timestamp, segmentVersion);
          }
          WaveletOperation waveletOps =
              coreWaveletOpsToWaveletOps(coreWaveletOperations.get(j), opContext);
          ret.add(waveletOps);
        }
      }
      return ret.build();
    } finally {
      Timing.stop(timer);
    }
  }

  WaveAggregateOp invert() {
    List<OpCreatorPair> invertedPairs = new ArrayList<OpCreatorPair>();
    for (OpCreatorPair pair : opPairs) {
      invertedPairs.add(new OpCreatorPair(pair.op.invert(), pair.creator, pair.timestamp));
    }
    Collections.reverse(invertedPairs);
    return new WaveAggregateOp(invertedPairs);
  }

  private WaveletOperation coreWaveletOpsToWaveletOps(CoreWaveletOperation op,
      WaveletOperationContext context) {
    if (op instanceof CoreRemoveSegment) {
      SegmentId segmentId = ((CoreRemoveSegment) op).getSegmentId();
      return new RemoveSegment(context, segmentId);
    } else if (op instanceof CoreAddSegment) {
      SegmentId segmentId = ((CoreAddSegment) op).getSegmentId();
      return new AddSegment(context, segmentId);
    } else if (op instanceof CoreEndModifyingSegment) {
      SegmentId segmentId = ((CoreEndModifyingSegment) op).getSegmentId();
      return new EndModifyingSegment(context, segmentId);
    } else if (op instanceof CoreStartModifyingSegment) {
      SegmentId segmentId = ((CoreStartModifyingSegment) op).getSegmentId();
      return new StartModifyingSegment(context, segmentId);
    } else if (op instanceof CoreRemoveParticipant) {
      ParticipantId participantId = ((CoreRemoveParticipant) op).getParticipantId();
      return new RemoveParticipant(context, participantId);
    } else if (op instanceof CoreAddParticipant) {
      ParticipantId participantId = ((CoreAddParticipant) op).getParticipantId();
      return new AddParticipant(context, participantId);
    } else if (op instanceof CoreWaveletDocumentOperation) {
      CoreWaveletDocumentOperation waveletDocOp = (CoreWaveletDocumentOperation) op;
      String documentId = waveletDocOp.getDocumentId();
      DocOp operation = waveletDocOp.getOperation();
      return new WaveletBlipOperation(documentId, new BlipContentOperation(context, operation));
    }

    throw new RuntimeException("unhandled operation type");
  }
}
