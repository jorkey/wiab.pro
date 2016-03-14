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

import org.waveprotocol.box.server.persistence.blocks.FarBackwardLink;
import org.waveprotocol.box.server.persistence.blocks.FarForwardLink;
import org.waveprotocol.box.server.persistence.blocks.VersionNode;
import org.waveprotocol.box.server.persistence.blocks.SegmentOperation;
import org.waveprotocol.box.server.persistence.blocks.impl.FarForwardLinksImpl;
import org.waveprotocol.box.server.persistence.blocks.FarBackwardLinks;
import org.waveprotocol.box.server.persistence.blocks.FarForwardLinks;
import org.waveprotocol.box.server.persistence.blocks.impl.FarBackwardLinkImpl;
import org.waveprotocol.box.server.persistence.blocks.impl.FarBackwardLinksImpl;
import org.waveprotocol.box.server.persistence.blocks.impl.FarForwardLinkImpl;

import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;

import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.Preconditions;

import org.waveprotocol.wave.util.logging.Log;

import java.util.List;
import java.util.Objects;

/**
 * Aggregates operations.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */

public class OperationAggregator {
  static final Log LOG = Log.get(OperationAggregator.class);

  /** Length of aggregation step. */
  private final int base;

  /** Maximum degree to calculate length of aggregation step. */
  private final int maxLevels;

  /** Callback to write nodes to fragment. */
  public interface Callback {
    /**
     * Writes new version node.
     */
    void writeNode(VersionNode node);

    /**
     * Writes far backward link of node.
     */
    void writeFarBackwardLink(VersionNode node, FarBackwardLink farBackwardLink);

    /**
     * Writes far forward link of node.
     */
    void writeFarForwardLink(VersionNode node, FarForwardLink farForwardLink);

    /**
     * Rewrites far forward link of node.
     */
    void rewriteFarForwardLink(VersionNode node, FarForwardLink oldFarForwardLink, FarForwardLink newFarForwardLink);
  }

  /** Aggregations data. */
  private AggregationLevel levels[];

  /** Aggregate operations by author. */
  private boolean aggregateByAuthor = false;

  /** Aggregate operations by time interval. */
  private int timeInterval = -1;

  /** Callback to write nodes to fragment. */
  private final Callback callback;

  /** Creates aggregator on fragment. */
  public OperationAggregator(int base, int maxLevels, Callback callback) {
    this.base = base;
    this.maxLevels = maxLevels;
    this.callback = callback;
  }

  public void setAggregateByAuthor(boolean aggregateByAuthor) {
    this.aggregateByAuthor = aggregateByAuthor;
  }

  public void setTimeInterval(int timeInterval) {
    this.timeInterval = timeInterval;
  }

  /**
   * Adds version node to aggregate.
   */
  public void addNode(VersionNode node) {
    Timer timer = Timing.start("OperationAggregator.addNode");
    try {
      List<AggregationJump> jumps = CollectionUtils.newLinkedList();
      if (levels == null) {
        if (!initLevels(node.getPreviousNode() != null ? node.getPreviousNode() : node)) {
          for (int level=0; level < maxLevels; level++) {
            jumps.add(new AggregationJump(node, level));
          }
        }
      }
      if (jumps.isEmpty()) {
        if (isTimeToCancelAggregation(node)) {
          complete();
          for (int level=0; level < maxLevels; level++) {
            jumps.add(new AggregationJump(node, level));
          }
        } else {
          AggregationJump jump = new AggregationJump(node.getPreviousNode(), node,
              node.getFromPreviousVersionOperation());
          for (int level=0; level < maxLevels && jump != null; level++) {
            jump = levels[level].addJump(jump);
            if (jump != null) {
              jumps.add(jump);
              jumps.add(new AggregationJump(node, level));
            }
          }
        }
      }
      callback.writeNode(node);
      if (!jumps.isEmpty()) {
        processJumps(jumps);
      }
    } finally {
      Timing.stop(timer);
    }
  }

  /**
   * Completes aggregation.
   */
  public void complete() {
    Timer timer = Timing.start("OperationAggregator.complete");
    try {
      List<AggregationJump> jumps = CollectionUtils.newLinkedList();
      AggregationJump jump = null;
      for (int level=0; level < maxLevels; level++) {
        if (jump != null) {
          jump = levels[level].addJump(jump);
        }
        if (jump == null) {
          jump = levels[level].complete();
        }
        if (jump != null) {
          jumps.add(jump);
        }
      }
      if (!jumps.isEmpty()) {
        processJumps(jumps);
      }
    } finally {
      Timing.stop(timer);
    }
  }

  private boolean initLevels(VersionNode lastNode) {
    Preconditions.checkArgument(levels == null, "Levels is already initialized");
    levels = new AggregationLevel[maxLevels];
    int currentLevel = 0;
    boolean exit = false;
    for (VersionNode node = lastNode; !exit && node != null && currentLevel < maxLevels; node = node.getPreviousNode()) {
      if (node.getFarForwardLinks() != null) {
        for (FarForwardLink farForwardLink : node.getFarForwardLinks().getList()) {
          if (farForwardLink.isEmpty()) {
            long distance = farForwardLink.getDistanceToNextFarVersion();
            if (distance == Math.pow(base, currentLevel+1)) {
              levels[currentLevel] = new AggregationLevel(base, currentLevel, node, lastNode);
              currentLevel++;
            } else {
              exit = true;
              break;
            }
          }
        }
      }
    }
    if (currentLevel < maxLevels) {
      for (int level=0; level < maxLevels; level++) {
        levels[level] = new AggregationLevel(base, level, lastNode, lastNode);
      }
      return false;
    }
    return true;
  }

  private boolean isTimeToCancelAggregation(VersionNode targetNode) {
    VersionNode sourceNode = levels[maxLevels-1].getSourceNode();
    if (sourceNode == null) {
      return false;
    }
    if (aggregateByAuthor && !Objects.equals(targetNode.getAuthor(), sourceNode.getAuthor())) {
      return true;
    }
    return timeInterval != -1 && targetNode.getTimestamp() - sourceNode.getTimestamp() >= timeInterval;
  }

  private void processJumps(List<AggregationJump> jumps) {
    for (AggregationJump jump : jumps) {
      if (jump.isAggregated()) {
        FarBackwardLinks farBackwardLinks = jump.getTargetNode().getFarBackwardLinks();
        if (farBackwardLinks == null) {
          jump.getTargetNode().addFarBackwardLinks(farBackwardLinks = new FarBackwardLinksImpl());
        }
        FarBackwardLink backwardLink = new FarBackwardLinkImpl(
            jump.getTargetNode().getVersion()-jump.getSourceNode().getVersion(), jump.getSourceNode(), jump.toSegmentOperation());
        if (farBackwardLinks.addLink(backwardLink)) {
          callback.writeFarBackwardLink(jump.getTargetNode(), backwardLink);
        }
        FarForwardLinks farForwardLinks = jump.getSourceNode().getFarForwardLinks();
        Preconditions.checkNotNull(farForwardLinks, "Far forward links are not reserved for version "
            + jump.getSourceNode().getVersion());
        FarForwardLink forwardLink = new FarForwardLinkImpl(
            jump.getTargetNode().getVersion()-jump.getSourceNode().getVersion(), jump.getTargetNode());
        long oldDistance = (long)Math.pow(base, jump.getLevel()+1);
        FarForwardLink oldForwardLink = farForwardLinks.getLinkByVersionDistance(oldDistance);
        Preconditions.checkNotNull(oldForwardLink, "Far forward link are not reserved for version "
            + jump.getSourceNode().getVersion() + " for version distance " + oldDistance);
        farForwardLinks.replaceLink(oldForwardLink, forwardLink);
        callback.rewriteFarForwardLink(jump.getSourceNode(), oldForwardLink, forwardLink);
      } else if (jump.getSourceNode() == null) {
        FarForwardLinks farForwardLinks = jump.getTargetNode().getFarForwardLinks();
        if (farForwardLinks == null) {
          jump.getTargetNode().addFarForwardLinks(farForwardLinks = new FarForwardLinksImpl());
        }
        FarForwardLink forwardLink = new FarForwardLinkImpl((long)Math.pow(base, jump.getLevel()+1), null);
        if (farForwardLinks.addLink(forwardLink)) {
          callback.writeFarForwardLink(jump.getTargetNode(), forwardLink);
        }
      }
    }
  }
}
