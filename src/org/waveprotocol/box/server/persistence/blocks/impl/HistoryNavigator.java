/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.waveprotocol.box.server.persistence.blocks.impl;

import org.waveprotocol.box.server.persistence.blocks.*;
import org.waveprotocol.box.server.persistence.blocks.SegmentOperation;
import org.waveprotocol.box.server.persistence.blocks.VersionNode;
import org.waveprotocol.box.common.ListReceiver;
import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.Pair;

import java.util.List;

/**
 * Navigator of the VersionNode's.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class HistoryNavigator {

  /**
   * Finds fragment node with version as requested or near less.
   *
   * @param fromNode source node from which find.
   * @param version of sought-for node.
   * @return found node or null.
   */
  public static VersionNode findFragmentNode(VersionNode fromNode, long version) {
    if (fromNode.getVersion() < version) {
      return forwardToFragmentVersion(fromNode, version, null);
    } else if (fromNode.getVersion() > version) {
      VersionNode node = backwardToFragmentVersion(fromNode, version, null);
      return node.getVersion() <= version ? node : null;
    } else {
      return fromNode;
    }
  }

  /**
   * Gets history operations from specified node to version of segment.
   *
   * @param sourceFragment the source fragment.
   * @param sourceNode the source node.
   * @param toVersion target version.
   * @return list of operations.
   */
  public static List<SegmentOperation> getHistory(ReadableFragment sourceFragment, VersionNode sourceNode, long toVersion) {
    ListReceiver<SegmentOperation> operations = new ListReceiver<>();
    if (sourceNode.getVersion() < toVersion) {
      forwardToVersion(sourceFragment, sourceNode, toVersion, operations);
    } else if (sourceNode.getVersion() > toVersion) {
      backwardToVersion(sourceFragment, sourceNode, toVersion, operations);
    }
    return operations;
  }

  /**
   * Gets history operations from specified node to version of same fragment.
   *
   * @param sourceNode the source node.
   * @param toVersion target version.
   * @return list of operations.
   */
  public static List<SegmentOperation> getFragmentHistory(VersionNode sourceNode, long toVersion) {
    ListReceiver<SegmentOperation> operations = new ListReceiver<>();
    if (sourceNode.getVersion() < toVersion) {
      forwardToFragmentVersion(sourceNode, toVersion, operations);
    } else if (sourceNode.getVersion() > toVersion) {
      backwardToFragmentVersion(sourceNode, toVersion, operations);
    }
    return operations;
  }

  private static VersionNode forwardToVersion(ReadableFragment fragment, VersionNode node, long toVersion,
      Receiver<SegmentOperation> operationReceiver) {
    for (;;) {
      node = forwardToFragmentVersion(node, toVersion, operationReceiver);
      if (fragment.getLastNode().getVersion() >= toVersion) {
        return node;
      }
      Preconditions.checkArgument(node == fragment.getLastNode(), "Node is not last of fragment");
      fragment = fragment.getNextFragment();
      if (fragment == null) {
        return node;
      }
      if (operationReceiver != null && fragment.getFirstNode().getFromPreviousVersionOperation() != null) {
        operationReceiver.put(fragment.getFirstNode().getFromPreviousVersionOperation());
      }
      if (fragment.getFirstNode().getVersion() == toVersion) {
        return fragment.getFirstNode();
      }
      if (fragment.getFirstNode().getVersion() > toVersion) {
        return node;
      }
      node = fragment.getFirstNode();
    }
  }

  private static VersionNode forwardToFragmentVersion(VersionNode node, long toVersion,
      Receiver<SegmentOperation> operationReceiver) {
    Preconditions.checkArgument(toVersion > node.getVersion(), "Destination version " + toVersion +
      " is not more than source version " + node.getVersion());
    for (;;) {
      VersionNode nextNode;
      Pair<VersionNode, SegmentOperation> jump = tryForwardJump(node, toVersion);
      if (jump != null) {
        nextNode = jump.first;
        if (operationReceiver != null && jump.second != null) {
          operationReceiver.put(jump.second);
        }
      } else {
        nextNode = node.getNextNode();
        if (operationReceiver != null && nextNode != null && nextNode.getVersion() <= toVersion) {
          if (nextNode.getFromPreviousVersionOperation() != null) {
            operationReceiver.put(nextNode.getFromPreviousVersionOperation());
          }
        }
      }
      if (nextNode == null || nextNode.getVersion() > toVersion) {
        return node;
      }
      if (nextNode.getVersion() == toVersion) {
        return nextNode;
      }
      node = nextNode;
    }
  }

  private static VersionNode backwardToVersion(ReadableFragment fragment, VersionNode node, long toVersion,
      Receiver<SegmentOperation> operationReceiver) {
    for (;;) {
      node = backwardToFragmentVersion(node, toVersion, operationReceiver);
      if (node.getVersion() <= toVersion) {
        return node;
      }
      Preconditions.checkArgument(node == fragment.getFirstNode(), "Node is not first of fragment");
      fragment = fragment.getPreviousFragment();
      Preconditions.checkNotNull(fragment,
          "Not found fragment for version " + toVersion + ", first known version is " + node.getVersion());
      if (operationReceiver != null && node.getToPreviousVersionOperation(fragment.getLastNode()) != null) {
        operationReceiver.put(node.getToPreviousVersionOperation(fragment.getLastNode()));
      }
      node = fragment.getLastNode();
      if (node.getVersion() <= toVersion) {
        return node;
      }
    }
  }

  private static VersionNode backwardToFragmentVersion(VersionNode node, long toVersion,
      Receiver<SegmentOperation> operationReceiver) {
    Preconditions.checkArgument(toVersion < node.getVersion(), "Destination version " + toVersion +
      " is not less than source version " + node.getVersion());
    for (;;) {
      VersionNode previousNode;
      Pair<VersionNode, SegmentOperation> jump = tryBackwardJump(node, toVersion);
      if (jump != null) {
        previousNode = jump.first;
        if (operationReceiver != null && jump.second != null) {
          operationReceiver.put(jump.second);
        }
      } else {
        previousNode = node.getPreviousNode();
        if (previousNode == null) {
          return node;
        }
        if (operationReceiver != null && node.getToPreviousVersionOperation(previousNode) != null) {
          operationReceiver.put(node.getToPreviousVersionOperation(previousNode));
        }
      }
      if (previousNode.getVersion() <= toVersion) {
        return previousNode;
      }
      node = previousNode;
    }
  }

  private static Pair<VersionNode, SegmentOperation> tryForwardJump(VersionNode sourceNode, long targetVersion) {
    if (sourceNode.getFarForwardLinks() != null) {
      FarForwardLink farForwardLink = sourceNode.getFarForwardLinks().getNearestLinkByVersionDistance(targetVersion - sourceNode.getVersion());
      if (farForwardLink != null) {
        VersionNode node = farForwardLink.getNextFarNode();
        Preconditions.checkArgument(node.getVersion() <= targetVersion, "Returned invalid far forward link");
        long traveledDistance = node.getVersion() - sourceNode.getVersion();
        FarBackwardLink farBackwardLink = node.getFarBackwardLinks().getLinkByVersionDistance(traveledDistance);
        Preconditions.checkNotNull(farBackwardLink, "No far backward link as pair to forward link");
        SegmentOperation operation = farBackwardLink.getFromPreviousFarVersionOperation();
        return Pair.of(node, operation);
      }
    }
    return null;
  }

  private static Pair<VersionNode, SegmentOperation> tryBackwardJump(VersionNode sourceNode, long targetVersion) {
    if (sourceNode.getFarBackwardLinks() != null) {
      FarBackwardLink farBackwardLink = sourceNode.getFarBackwardLinks().getNearestLinkByVersionDistance(sourceNode.getVersion() - targetVersion);
      if (farBackwardLink != null) {
        VersionNode node = farBackwardLink.getPreviousFarNode();
        Preconditions.checkArgument(node.getVersion() >= targetVersion, "Returned invalid far backward link");
        SegmentOperation operation = farBackwardLink.getToPreviousFarVersionOperation();
        return Pair.of(node, operation);
      }
    }
    return null;
  }
}
