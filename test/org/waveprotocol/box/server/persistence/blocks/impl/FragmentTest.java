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

import org.waveprotocol.box.server.persistence.blocks.impl.FragmentIndex;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import junit.framework.TestCase;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.waveprotocol.box.server.persistence.blocks.Block;
import org.waveprotocol.box.server.persistence.blocks.Fragment;
import org.waveprotocol.box.server.persistence.blocks.VersionNode;
import org.waveprotocol.box.server.persistence.blocks.impl.FragmentImpl;
import org.waveprotocol.wave.model.id.SegmentId;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class FragmentTest extends TestCase {
  static final SegmentId SEGMENT_ID = SegmentId.ofBlipId("blip");

  @Override
  protected void setUp() throws Exception {
  }

  public void testFragmentFinishedOnBlockBecameLowWater() throws Exception {
    Block block = mock(Block.class);

    when(block.getSize()).thenAnswer(new Answer<Integer>(){
      boolean blockSizePrechecked = false;

      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        if (!blockSizePrechecked) {
          blockSizePrechecked = true;
          return 0;
        }
        return Block.LOW_WATER;
      }
    });

    Fragment fragment = new FragmentImpl(block, SEGMENT_ID, new FragmentIndex(true));

    VersionNode versionNode = mock(VersionNode.class);
    fragment.writeVersionNode(versionNode);

    assertTrue(!fragment.isLast());
  }

  public void testFragmentFinishedOnBlockHightWater() throws Exception {
    Block block = mock(Block.class);

    when(block.getSize()).thenReturn(Block.HIGH_WATER);

    Fragment fragment = new FragmentImpl(block, SEGMENT_ID, new FragmentIndex(true));

    VersionNode versionNode = mock(VersionNode.class);
    fragment.writeVersionNode(versionNode);

    assertTrue(!fragment.isLast());
  }
}
