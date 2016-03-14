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

import org.waveprotocol.box.server.persistence.blocks.VersionNode;
import org.waveprotocol.box.server.persistence.blocks.SegmentOperation;

import org.waveprotocol.wave.model.operation.wave.NoOp;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.util.CollectionUtils;

import junit.framework.TestCase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class HistoryNavigatorTest extends TestCase {

  final int stepLength = 10;
  final int maxLevels = 2;

  final Map<Integer, VersionNode> nodes = CollectionUtils.newHashMap();

  class SegmentOperationStub extends SegmentOperationImpl {
    private final long sourceVersion;
    private final long targetVersion;

    public SegmentOperationStub(long sourceVersion, long targetVersion) {
      super(new NoOp(null));
      this.sourceVersion = sourceVersion;
      this.targetVersion = targetVersion;
    }

    @Override
    public SegmentOperationImpl revert(WaveletOperationContext context) {
      return new SegmentOperationStub(targetVersion, sourceVersion);
    }

    @Override
    public String toString() {
      return sourceVersion + "->" + targetVersion;
    }
  }

  @Override
  protected void setUp() throws Exception {
  }

  public void testFindExistingNode() {
    buildNodes(200, 400, new int[0]);

    assertEquals(nodes.get(302), HistoryNavigator.findFragmentNode(nodes.get(302), 302));
    assertEquals(nodes.get(302), HistoryNavigator.findFragmentNode(nodes.get(201), 302));
    assertEquals(nodes.get(302), HistoryNavigator.findFragmentNode(nodes.get(390), 302));
  }

  public void testFindNotExistingNode() {
    int skipedVersion[] = { 231, 232 };
    buildNodes(200, 250, skipedVersion);

    // Find not existing node must return previous existing one ...
    assertEquals(nodes.get(230), HistoryNavigator.findFragmentNode(nodes.get(201), 232));
    assertEquals(nodes.get(230), HistoryNavigator.findFragmentNode(nodes.get(241), 232));

    // Or nulll when previous node is not exists.
    assertNull(HistoryNavigator.findFragmentNode(nodes.get(202), 198));
  }

  public void testGettingOfHistory() throws Exception {
    buildNodes(100, 500, new int[0]);

    // Forward operations
    List<SegmentOperation> operations = HistoryNavigator.getFragmentHistory(nodes.get(201), 491);
    assertEquals(9+9+1+9+1, operations.size());

    // Backward operations
    operations = HistoryNavigator.getFragmentHistory(nodes.get(495), 162);
    assertEquals(5+9+2+3+8, operations.size());
  }

  private void buildNodes(int startVersion, int endVersion, int versionsToSkip[]) {
    VersionNode previousNode = null;
    VersionNode previousFarNodes[] = new VersionNode[maxLevels];
    int version = startVersion;
    for (int i=0; version <= endVersion; i++) {
      VersionNode node = mock(VersionNode.class);
      when(node.getVersion()).thenReturn((long)version);
      if (previousNode != null) {
        long previousVersion = previousNode.getVersion();
        when(node.getPreviousNode()).thenReturn(previousNode);
        when(previousNode.getNextNode()).thenReturn(node);
        when(node.getFromPreviousVersionOperation()).thenReturn(new SegmentOperationStub(previousVersion, version));
        when(node.getToPreviousVersionOperation(previousNode)).thenReturn(new SegmentOperationStub(version, previousVersion));
        if (i % stepLength == 0) {
          when(node.getFarBackwardLinks()).thenReturn(new FarBackwardLinksImpl());
          when(node.getFarForwardLinks()).thenReturn(new FarForwardLinksImpl());
          for (int level=0; level < maxLevels; level++) {
            int levelBound = (int)Math.pow(stepLength, level+1);
            if (i % levelBound == 0) {
              long distance = node.getVersion()-previousFarNodes[level].getVersion();
              node.getFarBackwardLinks().addLink(new FarBackwardLinkImpl(distance,
                previousFarNodes[level],
                new SegmentOperationStub(previousFarNodes[level].getVersion(), version)));
              previousFarNodes[level].getFarForwardLinks().addLink(new FarForwardLinkImpl(distance, node));
              previousFarNodes[level] = node;
            } else {
              break;
            }
          }
        }
      } else {
        for (int level=0; level < maxLevels; level++) {
          previousFarNodes[level] = node;
        }
        when(node.getFarForwardLinks()).thenReturn(new FarForwardLinksImpl());
      }
      nodes.put(version, node);
      version++;
      for (long versionToSkip : versionsToSkip) {
        if (version < versionToSkip) {
          break;
        }
        if (version == versionToSkip) {
          version++;
        }
      }
      previousNode = node;
    }
  }
}
