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

package org.waveprotocol.box.server.persistence.blocks.impl.aggregator;

import org.waveprotocol.box.server.persistence.blocks.VersionNode;
import org.waveprotocol.box.server.persistence.blocks.SegmentOperation;
import org.waveprotocol.box.server.persistence.blocks.impl.SegmentOperationImpl;

import org.waveprotocol.wave.model.operation.wave.WaveletOperation;

import com.google.common.collect.ImmutableList;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
class AggregationJump {
  private final VersionNode sourceNode;
  private final VersionNode targetNode;
  private final ImmutableList<? extends WaveletOperation> operations;
  private final int level;
  private final boolean aggregated;

  AggregationJump(VersionNode sourceNode, VersionNode targetNode, ImmutableList<? extends WaveletOperation> operations,
      int level, boolean aggregated) {
    this.sourceNode = sourceNode;
    this.targetNode = targetNode;
    this.operations = operations;
    this.level = level;
    this.aggregated = aggregated;
  }

  AggregationJump(VersionNode sourceNode, VersionNode targetNode, SegmentOperation segmentOperation) {
    this.sourceNode = sourceNode;
    this.targetNode = targetNode;
    this.operations = segmentOperation != null ? segmentOperation.getOperations() : null;
    this.level = -1;
    this.aggregated = false;
  }

  AggregationJump(VersionNode targetNode, int level) {
    this.targetNode = targetNode;
    this.level = level;
    this.sourceNode = null;
    this.operations = null;
    this.aggregated = false;
  }

  VersionNode getSourceNode() {
    return sourceNode;
  }

  VersionNode getTargetNode() {
    return targetNode;
  }

  ImmutableList<? extends WaveletOperation> getOperations() {
    return operations;
  }

  public int getLevel() {
    return level;
  }

  boolean isAggregated() {
    return aggregated;
  }

  SegmentOperation toSegmentOperation() {
    return operations != null && !operations.isEmpty() ? new SegmentOperationImpl(operations) : null;
  }

  @Override
  public String toString() {
    if (sourceNode != null) {
      return sourceNode.getVersion() + "->" + targetNode.getVersion();
    }
    return Long.toString(targetNode.getVersion());
  }
}
