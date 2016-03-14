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

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import junit.framework.TestCase;
import org.waveprotocol.box.server.persistence.blocks.Fragment;
import org.waveprotocol.box.server.persistence.blocks.Segment;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class SegmentTest extends TestCase {

  static final private SegmentId SEGMENT_ID = SegmentId.of("123");

  private Segment segment;

  @Override
  protected void setUp() throws Exception {
    segment = new SegmentImpl(SEGMENT_ID, null);
  }

  public void testFragmentRegistrationAndSearchByRange() throws Exception {
    Fragment fragment = makeFragment(0, 5, false);
    Fragment fragment1 = makeFragment(5, 12, false);
    Fragment fragment2 = makeFragment(15, 17, true);

    segment.registryFragment(fragment);
    segment.registryFragment(fragment1);
    segment.registryFragment(fragment2);

    assertEquals(fragment, segment.getFragment(0));
    assertEquals(fragment, segment.getFragment(5));
    assertEquals(fragment, segment.getFragment(3));

    assertEquals(fragment, segment.getFragment(5));
    assertEquals(fragment1, segment.getFragment(10));
    assertEquals(fragment1, segment.getFragment(12));

    assertNull(segment.getFragment(13));

    assertEquals(fragment2, segment.getFragment(16));
    assertEquals(fragment2, segment.getFragment(30));
  }

  public void testFinishOfFragmentChangesItsRange() throws Exception {
    Fragment fragment = makeFragment(0, 5, false);
    segment.registryFragment(fragment);

    assertNull(segment.getFragment(6));

    when(fragment.isLast()).thenReturn(true);

    assertEquals(fragment, segment.getFragment(6));
  }

  static private Fragment makeFragment(long startVersion, long endVersion, boolean last) {
    Fragment fragment = mock(Fragment.class);
    when(fragment.getStartVersion()).thenReturn(startVersion);
    when(fragment.getLastModifiedVersion()).thenReturn(endVersion);
    when(fragment.isLast()).thenReturn(last);
    return fragment;
  }

}
