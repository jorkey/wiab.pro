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

import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.wave.undo.WaveAggregateOp;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
class AggregationLevel {
  private final int base;
  private final int level;
  private final List<AggregationJump> toCompose = CollectionUtils.newLinkedList();

  /**
   * Creates aggregation on fragment.
   */
  AggregationLevel(int base, int level, VersionNode startNode, VersionNode endNode) {
    this.base = base;
    this.level = level;
    initState(startNode, endNode);
  }

  /**
   * Gets source aggregation node.
   */
  VersionNode getSourceNode() {
    if (toCompose.isEmpty()) {
      return null;
    }
    return toCompose.get(0).getSourceNode();
  }

  /**
   * Adds composed operation from previous node.
   *
   * @param jump aggregation jump from previous level.
   * @return aggregation jump from this level or null.
   */
  AggregationJump addJump(AggregationJump jump) {
    toCompose.add(jump);
    AggregationJump composeJump = null;
    if (toCompose.size() == base) {
      composeJump = compose(toCompose, level);
      toCompose.clear();
    }
    return composeJump;
  }

  /**
   * Completes aggregation operations.
   */
  AggregationJump complete() {
    AggregationJump jump = null;
    if (!toCompose.isEmpty()) {
      jump = aggregate(toCompose, level);
      toCompose.clear();
    }
    return jump;
  }

  private void initState(VersionNode startNode, VersionNode endNode) {
    int pecursiveComposeStep = (int) Math.pow(base, level);
    List<AggregationJump> pecursiveCompose = CollectionUtils.newLinkedList();
    for (VersionNode node = startNode; node != endNode; node = node.getNextNode()) {
      AggregationJump op = new AggregationJump(node, node.getNextNode(),
          node.getNextNode().getFromPreviousVersionOperation());
      pecursiveCompose.add(op);
      if (pecursiveCompose.size() == pecursiveComposeStep) {
        AggregationJump jump = aggregate(pecursiveCompose, level-1);
        toCompose.add(jump);
        pecursiveCompose.clear();
      }
    }
  }

  private static AggregationJump aggregate(List<AggregationJump> toAggregate, int level) {
    if (toAggregate.size() > 1) {
      return compose(toAggregate, level);
    } else {
      return concatenate(toAggregate, level);
    }
  }

  private static AggregationJump compose(List<AggregationJump> toCompose, int level) {
    Preconditions.checkArgument(toCompose.size() > 1, "Operations list to compose size < 2");
    VersionNode sourceVersion = toCompose.get(0).getSourceNode();
    VersionNode targetVersion = toCompose.get(toCompose.size()-1).getTargetNode();
    List<WaveAggregateOp> operations = CollectionUtils.newLinkedList();
    for (AggregationJump segmentOperation : toCompose) {
      if (segmentOperation.getOperations() != null) {
        for (WaveletOperation operation : segmentOperation.getOperations()) {
          operations.add(WaveAggregateOp.createAggregate(operation));
        }
      }
    }
    ImmutableList<WaveletOperation> composed = ImmutableList.of();
    if (!operations.isEmpty()) {
      composed = WaveAggregateOp.compose(operations).
        toWaveletOperationsWithVersions(targetVersion.getVersion(), null);
    }
    return new AggregationJump(sourceVersion, targetVersion, composed, level, true);
  }

  private static AggregationJump concatenate(List<AggregationJump> toConcatenate, int level) {
    Preconditions.checkArgument(!toConcatenate.isEmpty(), "Operations list to concatenate size is empty");
    VersionNode sourceVersion = toConcatenate.get(0).getSourceNode();
    VersionNode targetVersion = toConcatenate.get(toConcatenate.size()-1).getTargetNode();
    ImmutableList.Builder<WaveletOperation> concatenated = ImmutableList.builder();
    for (AggregationJump jump : toConcatenate) {
      if (jump.getOperations() != null) {
        concatenated.addAll(jump.getOperations());
      }
    }
    return new AggregationJump(sourceVersion, targetVersion, concatenated.build(), level, false);
  }
}
