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

import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.operation.wave.AddParticipant;

import org.waveprotocol.box.server.persistence.blocks.impl.*;
import org.waveprotocol.box.server.persistence.blocks.SegmentOperation;
import org.waveprotocol.box.server.persistence.blocks.VersionNode;
import org.waveprotocol.box.server.persistence.blocks.FarBackwardLinks;
import org.waveprotocol.box.server.persistence.blocks.FarForwardLink;
import org.waveprotocol.box.server.persistence.blocks.FarForwardLinks;
import org.waveprotocol.box.server.persistence.blocks.FarBackwardLink;

import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class OperationAggregatorTest extends TestCase {
  private static final ParticipantId USER1 = new ParticipantId("user1@domain");
  private static final ParticipantId USER2 = new ParticipantId("user2@domain");

  final static int BASE = 10;
  final static int MAX_LEVELS = 2;

  private final List<VersionNode> wroteNodes = CollectionUtils.newLinkedList();
  private final Map<Long, Set<Long>> wroteFarBackwardLinks = CollectionUtils.newHashMap();
  private final Map<Long, Set<Long>> wroteFarForwardLinks = CollectionUtils.newHashMap();

  private OperationAggregator aggregator;

  private OperationAggregator createAggregator() {
    return new OperationAggregator(BASE, MAX_LEVELS,
      new OperationAggregator.Callback() {

        @Override
        public void writeNode(VersionNode node) {
          wroteNodes.add(node);
        }

        @Override
        public void writeFarBackwardLink(VersionNode node, FarBackwardLink farBackwardLink) {
          Set<Long> links = wroteFarBackwardLinks.get(node.getVersion());
          if (links == null) {
            wroteFarBackwardLinks.put(node.getVersion(), links = CollectionUtils.newHashSet());
          }
          links.add(farBackwardLink.getDistanceToPreviousFarVersion());
        }

        @Override
        public void writeFarForwardLink(VersionNode node, FarForwardLink farForwardLink) {
          Set<Long> links = wroteFarForwardLinks.get(node.getVersion());
          if (links == null) {
            wroteFarForwardLinks.put(node.getVersion(), links = CollectionUtils.newHashSet());
          }
          links.add(farForwardLink.getDistanceToNextFarVersion());
        }

        @Override
        public void rewriteFarForwardLink(VersionNode node, FarForwardLink oldFarForwardLink, FarForwardLink newFarForwardLink) {
          Set<Long> links = wroteFarForwardLinks.get(node.getVersion());
          assertNotNull("Far forward links for version " + node.getVersion() + " are not wrote", links);
          assertTrue("No far forward link for version " + node.getVersion() + " to version distance " +
            oldFarForwardLink.getDistanceToNextFarVersion(), links.remove(oldFarForwardLink.getDistanceToNextFarVersion()));
          links.add(newFarForwardLink.getDistanceToNextFarVersion());
        }
      });
   }

  private VersionNode lastNode = null;

  @Override
  protected void setUp() throws Exception {
    aggregator = createAggregator();
  }

  public void testDoAggregation() throws Exception {
    // Try to aggregate BASE operations.
    for (int i=0; i <= BASE; i++) {
      aggregator.addNode(makeVersionNode(USER1, 0));
    }

    // Check that all nodes are wrote.
    assertEquals(BASE+1, wroteNodes.size());

    // Check that aggregation is complete.
    checkAggregationHasBeenDone(0, BASE);

    // Try to aggregate BASE^2 operations.
    for (int i=0; i < Math.pow(BASE, 2) - BASE; i++) {
      aggregator.addNode(makeVersionNode(USER1, 0));
    }

    // Check that aggregation is complete.
    checkAggregationHasBeenDone((int)Math.pow(BASE, 2) - BASE, (int)Math.pow(BASE, 2));
    checkAggregationHasBeenDone(0, (int)Math.pow(BASE, 2));
  }

  public void testComplete() {
    // Create unfinished aggregation state.
    for (int i=0; i < Math.pow(BASE, 2); i++) {
      aggregator.addNode(makeVersionNode(USER1, 0));
    }

    // Check that aggregation is not complete.
    checkAggregationHasNotBeenDone(0, (int)Math.pow(BASE, 2) - 1);
    checkAggregationHasNotBeenDone((int)Math.pow(BASE, 2) - BASE, (int)Math.pow(BASE, 2) - 1);
    
    // This must complete aggregation.
    aggregator.complete();

    // Check that aggregation is complete.
    checkAggregationHasBeenDone(0, (int)Math.pow(BASE, 2) - 1);
    checkAggregationHasBeenDone((int)Math.pow(BASE, 2) - BASE, (int)Math.pow(BASE, 2) - 1);
  }
  
  public void testDoAggregationWhenUserChanged() {
    aggregator.setAggregateByAuthor(true);
    
    // Create unfinished aggregation state.
    for (int i=0; i < Math.pow(BASE, 2); i++) {
      aggregator.addNode(makeVersionNode(USER1, 0));
    }

    // This operation must complete aggregation.
    aggregator.addNode(makeVersionNode(USER2, 0));

    // Check that aggregation is complete.
    checkAggregationHasBeenDone(0, (int)Math.pow(BASE, 2) - 1);
    checkAggregationHasBeenDone((int)Math.pow(BASE, 2) - BASE, (int)Math.pow(BASE, 2) - 1);
  }

  public void testDoAggregationWhenTimeIntervalPassed() {
    aggregator.setTimeInterval((int)Math.pow(BASE, 2));

    // Create unfinished aggregation state.
    long ts=0;
    while (ts < Math.pow(BASE, 2)) {
      aggregator.addNode(makeVersionNode(USER1, ts++));
    }

    // This operation must complete aggregation.
    aggregator.addNode(makeVersionNode(USER1, ts));
    
    // Check that aggregation is complete.
    checkAggregationHasBeenDone(0, (int)Math.pow(BASE, 2) - 1);
    checkAggregationHasBeenDone((int)Math.pow(BASE, 2) - BASE, (int)Math.pow(BASE, 2) - 1);
  }

  public void testRestoreState() throws Exception {
    // Create unfinished aggregation state.
    for (int i=0; i < Math.pow(BASE, 2); i++) {
      aggregator.addNode(makeVersionNode(USER1, 0));
    }
    
    // Remake aggregator.
    aggregator = createAggregator();

    // Finish aggregation.
    aggregator.addNode(makeVersionNode(USER1, 0));

    // Check that aggregation is complete.
    checkAggregationHasBeenDone((int)Math.pow(BASE, 2) - BASE, (int)Math.pow(BASE, 2));
    checkAggregationHasBeenDone(0, (int)Math.pow(BASE, 2));
  }

  public void testRestoreStateError() throws Exception {
    // Create unfinished aggregation state.
    for (int i=0; i < Math.pow(BASE, 2); i++) {
      aggregator.addNode(makeVersionNode(USER1, 0));
    }
    
    // Break link to first node.
    wroteNodes.get(1).setPreviousNode(null);
    
    // Remake aggregator.
    aggregator = createAggregator();

    // Try to finish aggregation.
    aggregator.addNode(makeVersionNode(USER1, 0));
    
    // Check that aggregation is not complete.
    checkAggregationHasNotBeenDone((int)Math.pow(BASE, 2) - BASE, (int)Math.pow(BASE, 2));
    checkAggregationHasNotBeenDone(0, (int)Math.pow(BASE, 2));
    
    // Try new aggregation.
    for (int i=0; i < Math.pow(BASE, 2); i++) {
      aggregator.addNode(makeVersionNode(USER1, 0));
    }
    
    // Check that aggregation is complete.
    checkAggregationHasBeenDone((int)Math.pow(BASE, 2)*2 - (int)Math.pow(BASE, 2), (int)Math.pow(BASE, 2)*2);
  }

  private VersionNode makeVersionNode(ParticipantId author, long timestamp) {
    VersionNode node = new VersionNodeStub(lastNode == null ? 0 : lastNode.getVersion()+1, author, timestamp);
    WaveletOperationContext context = new WaveletOperationContext(author, timestamp, 1);
    SegmentOperation op = new SegmentOperationImpl(
        new AddParticipant(context, ParticipantId.ofUnsafe(Long.toString(node.getVersion()), author.getDomain())));
    node.setFromPreviousVersionOperation(op);
    if (lastNode != null) {
      lastNode.setNextNode(node);
      node.setPreviousNode(lastNode);
    }
    lastNode = node;
    return node;
  }

  private void checkAggregationHasBeenDone(int start, int end) {
    int distance = end-start;
    FarBackwardLinks backwardLinks = wroteNodes.get(end).getFarBackwardLinks();
    assertNotNull("No far backward links from version " + end, backwardLinks);
    FarBackwardLink backwardLink = backwardLinks.getLinkByVersionDistance(distance);
    assertNotNull("No far backward link from version " + end + " to distance " + distance, backwardLink);
    assertEquals("Unexpected far backward link node", wroteNodes.get(start), backwardLink.getPreviousFarNode());
    assertNotNull("No far link operation", backwardLink.getFromPreviousFarVersionOperation());
    FarForwardLinks forwardLinks = wroteNodes.get(start).getFarForwardLinks();
    assertNotNull("No far forward links from version " + start, forwardLinks);
    FarForwardLink forwardLink = forwardLinks.getLinkByVersionDistance(distance);
    assertNotNull("No far forward link from version " + start + " to distance " + distance, forwardLink);
    assertEquals("Unexpected far forward link node", wroteNodes.get(end), forwardLink.getNextFarNode());
    assertNotNull("Backward links from version " + end + " are not wrote", wroteFarBackwardLinks.get((long)end));
    assertTrue("Backward link from version " + end + " to version " + start + " is not valid",
        wroteFarBackwardLinks.get((long)end).contains(backwardLink.getDistanceToPreviousFarVersion()));
    assertNotNull("Forward links from version " + start + " are not wrote", wroteFarForwardLinks.get((long)start));
    assertTrue("Forward link from version " + start+ " to version " + end + " is not valid",
        wroteFarForwardLinks.get((long)start).contains(forwardLink.getDistanceToNextFarVersion()));
  }
  
  private void checkAggregationHasNotBeenDone(int start, int end) {
    int distance = end-start;
    FarBackwardLinks backwardLinks = wroteNodes.get(end).getFarBackwardLinks();
    assertNull("Unexpected far backward links from version " + end, backwardLinks);
    FarForwardLinks forwardLinks = wroteNodes.get(start).getFarForwardLinks();
    if (forwardLinks != null) {
      FarForwardLink forwardLink = forwardLinks.getLinkByVersionDistance(distance);
      if (forwardLink != null) {
        assertTrue("Unexpected far forward link from version " + start, forwardLink.isEmpty());
      }
    }
  }
}
