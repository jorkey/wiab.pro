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
import junit.framework.TestCase;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class FragmentIndexTest extends TestCase {

  @Override
  protected void setUp() throws Exception {
  }

  public void testFragmentIndexNearestSnapshotVersion() throws Exception {
    FragmentIndex fi = new FragmentIndex(true);

    int snapshotIndex7 = fi.addSnapshot(7, 12);
    int snapshotIndex10 = fi.addSnapshot(10, 14);
    int snapshotIndex12 = fi.addSnapshot(12, 16);

    assertEquals(snapshotIndex10, fi.getNearestSnapshotIndex(10));
    assertEquals(snapshotIndex10, fi.getNearestSnapshotIndex(9));
    assertEquals(snapshotIndex10, fi.getNearestSnapshotIndex(11));

    assertEquals(snapshotIndex7, fi.getNearestSnapshotIndex(6));
    assertEquals(snapshotIndex12, fi.getNearestSnapshotIndex(13));
  }

  public void testFinishedFragmentDoesNotPermitWritting() throws Exception {
    FragmentIndex fi = new FragmentIndex(true);
    fi.finish();

    boolean exceptionThrown = false;
    try {
      fi.addSnapshot(7, 2);
    } catch (Exception ex) {
      exceptionThrown = true;
    }

    assertTrue(exceptionThrown);
  }
}
