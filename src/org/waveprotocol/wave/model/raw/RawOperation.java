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

import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.communication.Blob;

import com.google.common.collect.ImmutableList;

/**
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class RawOperation {

  public interface Serializer {
    public interface Writer {
      void setOperations(ImmutableList<? extends WaveletOperation> operations);
    }

    Blob serializeOperation(RawOperation operation);
    void deserializeOperation(Blob serialized, SegmentId segmentId, WaveletOperationContext context,
      RawOperation.Serializer.Writer writer);
  };

  private final Serializer serializer;
  private Blob serialized;
  private boolean worthy;
  private final WaveletOperationContext context;
  private final SegmentId segmentId;

  private ImmutableList<? extends WaveletOperation> operations;

  public RawOperation(Serializer serializer, Blob serialized,
      SegmentId segmentId, WaveletOperationContext context) {
    this.serializer = serializer;
    this.serialized = serialized;
    this.segmentId = segmentId;
    this.context = context;
  }

  public RawOperation(Serializer serializer, ImmutableList<? extends WaveletOperation> operations,
      WaveletOperationContext context) {
    Preconditions.checkArgument(!operations.isEmpty(), "No operations");
    this.serializer = serializer;
    this.segmentId = null;
    this.context = context;
    this.operations = operations;
    this.worthy = isWorthy(operations);
  }

  public WaveletOperationContext getContext() {
    return context;
  }

  public boolean isWorthy() {
    deserialize();
    return isWorthy(operations);
  }

  public ImmutableList<? extends WaveletOperation> getOperations() throws RawParseException {
    deserialize();
    return operations;
  }

  public Blob serialize() {
    if (serialized == null) {
      serialized = serializer.serializeOperation(this);
    }
    return serialized;
  }

  @Override
  public String toString() {
    deserialize();
    StringBuilder sb = new StringBuilder();
    if (operations != null) {
      for (int i=0; i < operations.size(); i++) {
        if (i != 0) {
          sb.append("\n");
        }
        sb.append(operations.get(i).toString());
      }
    }
    return sb.toString();
  }

  private synchronized void deserialize() {
    if (operations == null) {
      serializer.deserializeOperation(serialized, segmentId, context, new Serializer.Writer() {

        @Override
        public void setOperations(ImmutableList<? extends WaveletOperation> operations) {
          RawOperation.this.operations = operations;
        }
      });
    }
  }

  private static boolean isWorthy(ImmutableList<? extends WaveletOperation> operations) {
    for (WaveletOperation op : operations) {
      if (op.isWorthyOfAttribution()) {
        return true;
      }
    }
    return false;
  }
}
