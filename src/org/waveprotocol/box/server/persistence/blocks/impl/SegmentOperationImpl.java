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

import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.ReversibleOperation;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.raw.RawOperation;
import org.waveprotocol.wave.model.raw.serialization.GsonSerializer;
import org.waveprotocol.wave.communication.Blob;

import org.waveprotocol.box.server.persistence.blocks.SegmentOperation;

import com.google.common.collect.ImmutableList;

/**
 * Wavelet operations on segment that change version.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class SegmentOperationImpl implements SegmentOperation {

  private final RawOperation rawOperation;

  static SegmentOperationImpl deserialize(String buffer, SegmentId segmentId,
      WaveletOperationContext context) {
    RawOperation rawOperation = new RawOperation(GsonSerializer.OPERATION_SERIALIZER,
        new Blob(buffer), segmentId, context);
    return new SegmentOperationImpl(rawOperation);
  }

  public SegmentOperationImpl(RawOperation rawOperation) {
    this.rawOperation = rawOperation;
  }

  public SegmentOperationImpl(WaveletOperation... operations) {
    ImmutableList.Builder<WaveletOperation> operationsListBuilder = ImmutableList.builder();
    Preconditions.checkArgument(operations.length != 0, "No operations");
    for (WaveletOperation operation : operations) {
      operationsListBuilder.add(operation);
    }
    ImmutableList<WaveletOperation> operationsList = operationsListBuilder.build();
    rawOperation = new RawOperation(GsonSerializer.OPERATION_SERIALIZER, operationsList,
      operationsList.get(operationsList.size()-1).getContext());
  }

  public SegmentOperationImpl(ImmutableList<? extends WaveletOperation> operations) {
    Preconditions.checkArgument(!operations.isEmpty(), "No operations");
    rawOperation = new RawOperation(GsonSerializer.OPERATION_SERIALIZER, operations,
      operations.get(operations.size()-1).getContext());
  }

  @Override
  public ImmutableList<? extends WaveletOperation> getOperations() {
    return rawOperation.getOperations();
  }

  @Override
  public boolean isWorthy() {
    return rawOperation.isWorthy();
  }

  @Override
  public long getTargetVersion() {
    ImmutableList<? extends WaveletOperation> ops = rawOperation.getOperations();
    return ops.get(ops.size()-1).getContext().getSegmentVersion();
  }

  @Override
  public long getTimestamp() {
    ImmutableList<? extends WaveletOperation> ops = rawOperation.getOperations();
    return ops.get(ops.size()-1).getContext().getTimestamp();
  }

  /**
   * Returns reverted {@link SegmentOperation}.
   *
   * @param context the reversed context.
   */
  @Override
  public SegmentOperationImpl revert(WaveletOperationContext context) {
    ImmutableList<? extends WaveletOperation> ops = getOperations();
    ImmutableList.Builder<WaveletOperation> reverseOps = ImmutableList.builder();
    for (int i=ops.size()-1; i >= 0; i--) {
      WaveletOperation operation = ops.get(i);
      try {
        Preconditions.checkArgument(operation instanceof ReversibleOperation, "Bad operation type");
        reverseOps.addAll(((ReversibleOperation)operation).reverse(context));
      } catch (OperationException ex) {
        throw new RuntimeException(ex);
      }
    }
    return new SegmentOperationImpl(reverseOps.build());
  }

  @Override
  public RawOperation getRawOperation() {
    return rawOperation;
  }

  @Override
  public Blob serialize() {
    return rawOperation.serialize();
  }

  @Override
  public String toString() {
    return rawOperation.toString();
  }
}
