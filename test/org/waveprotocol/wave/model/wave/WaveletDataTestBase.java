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

package org.waveprotocol.wave.model.wave;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;
import org.waveprotocol.wave.model.testing.ExtraAsserts;
import org.waveprotocol.wave.model.testing.ModelTestUtils;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.wave.data.BlipData;
import org.waveprotocol.wave.model.wave.data.WaveletData;

/**
 * Black-box test of the {@link WaveletData} interface.
 *
 * To create a concrete test case, subclass this with an appropriate factory
 * for creating an {@link WaveletData}.
 *
 * NOTE(user): These tests should not be too complex, because the intent
 * of the {@link WaveletData} interface is that a basic implementation be
 * Too Trivial To Test.  Any non-trivial aspects of a {@link WaveletData}
 * implementation (e.g., sending events, managing locks, ...) are
 * implementation aspects for which implementation-specific tests should be
 * written.
 *
 * @author zdwang@google.com (David Wang)
 * @author anorth@google.com (Alex North)
 */
public abstract class WaveletDataTestBase extends TestCase {

  private WaveletData target;

  /**
   * Creates a wavelet data instance for testing.
   */
  protected abstract WaveletData createWaveletData();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    target = createWaveletData();
  }

  public void testNewWaveHasCreator() {
    assertNotNull(target.getCreator());
  }

  public void testNewWaveHasNoParticipants() {
    // The creator is NOT automatically a participant.
    assertTrue(target.getParticipants().isEmpty());
  }

  public void testNewWaveHasNoDocuments() {
    assertTrue(target.getDocumentIds().isEmpty());
  }

  public void testCreateBlip() {
    target.createBlip("root", target.getCreator(), Collections.singletonList(target.getCreator()),
        ModelTestUtils.createContent("test"), 0L, 0L, 42L, 42L);

    BlipData blip = target.getBlip("root");
    assertNotNull(blip);

    // The root blip is retrievable by id.
    assertNotNull(target.getBlip("root"));
    Set<String> docIds = target.getDocumentIds();
    assertEquals(Collections.singleton("root"), docIds);

    assertEquals("root", blip.getId());
    assertEquals(target.getCreator(), blip.getAuthor());
    ExtraAsserts.checkContent("<body><line/>test</body>", blip);
    assertEquals(42L, blip.getLastModifiedTime());
    // Initial value for lastModifiedVersion is not defined.
  }

  public void testCanNotCreateMultipleBlipsWithSameId() {
    target.createBlip("DOCUMENT_NAME", target.getCreator(),
        Collections.singletonList(target.getCreator()), ModelTestUtils.createContent("test"),
        0L, 0L, 42L, 42L);
    try {
      target.createBlip("DOCUMENT_NAME", target.getCreator(),
          Collections.singletonList(target.getCreator()), ModelTestUtils.createContent("test"),
          0L, 0L, 42L, 42L);
      fail("Should not be able to create document 2nd time.");
    } catch (IllegalArgumentException ex) {
      // Test is fine.
    }
  }

  public void testGetNonExistentBlipFails() {
    assertNull(target.getBlip("not a real blip id"));
  }

  public void testAddParticipant() {
    ParticipantId fred = new ParticipantId("fred@gwave.com");
    ParticipantId jane = new ParticipantId("jane@gwave.com");
    assertTrue(target.addParticipant(fred, null));
    assertTrue(target.addParticipant(jane, null));
  }

  public void testAddParticipantAtPosition() {
    ParticipantId fred = new ParticipantId("fred@gwave.com");
    ParticipantId jane = new ParticipantId("jane@gwave.com");
    assertTrue(target.addParticipant(fred, 0, null));
    assertEquals(Collections.singleton(fred), target.getParticipants());
    assertFalse(target.addParticipant(fred, 0, null));
    assertTrue(target.addParticipant(jane, 0, null));
    assertEquals(
        CollectionUtils.newArrayList(jane, fred),
        CollectionUtils.newArrayList(target.getParticipants()));
    assertFalse(target.addParticipant(jane, 0, null));
  }

  public void testAddParticipantAtPositionPastEnd() {
    ParticipantId fred = new ParticipantId("fred@gwave.com");
    ParticipantId jane = new ParticipantId("jane@gwave.com");
    try {
      target.addParticipant(fred, 1, null);
      fail("Should not accept position beyond the end of the participant list");
    } catch (IndexOutOfBoundsException expected) {
      // Test is fine.
    }
    assertTrue(target.addParticipant(fred, 0, null));
    try {
      target.addParticipant(jane, 2, null);
      fail("Should not accept position beyond the end of the participant list");
    } catch (IndexOutOfBoundsException expected) {
      // Test is fine.
    }
    assertEquals(Collections.singleton(fred), target.getParticipants());
  }

  public void testRemoveParticipant() {
    ParticipantId fred = new ParticipantId("fred@gwave.com");
    ParticipantId jane = new ParticipantId("jane@gwave.com");
    target.addParticipant(fred, null);
    target.addParticipant(jane, null);
    assertTrue(target.removeParticipant(fred, null));
    assertEquals(Collections.singleton(jane), target.getParticipants());
  }

  public void testAddDuplicateParticipantReturnsFalse() {
    ParticipantId fred = new ParticipantId("fred@gwave.com");
    target.addParticipant(fred, null);
    assertFalse(target.addParticipant(fred, null));
    assertEquals(Collections.singleton(fred), target.getParticipants());
  }

  public void testRemoveInvalidParticipantReturnsFalse() {
    ParticipantId fred = new ParticipantId("fred@gwave.com");
    ParticipantId jane = new ParticipantId("jane@gwave.com");
    target.addParticipant(fred, null);
    assertFalse(target.removeParticipant(jane, null));
    assertEquals(Collections.singleton(fred), target.getParticipants());
  }

  public void testAddParticipantsRetrievedInOrder() {
    ParticipantId fred = new ParticipantId("fred@gwave.com");
    ParticipantId jane = new ParticipantId("jane@gwave.com");
    target.addParticipant(fred, null);
    target.addParticipant(jane, null);
    List<ParticipantId> result = new ArrayList<ParticipantId>();
    for (ParticipantId p : target.getParticipants()) {
      result.add(p);
    }
    assertEquals(Arrays.asList(fred, jane), result);
  }

  public void testRemovalOfMultiplyAddedParticipant() {
    ParticipantId fred = new ParticipantId("fred@gwave.com");
    ParticipantId jane = new ParticipantId("jane@gwave.com");
    target.addParticipant(fred, null);
    target.addParticipant(fred, null);
    target.addParticipant(jane, null);
    assertTrue(target.removeParticipant(fred, null));
    assertEquals(Collections.singleton(jane), target.getParticipants());
  }

  public void testSetTitle() {
    // TODO(anorth): re-enable this test when the title representation
    // has been confirmed
    //target.setTitle("A wavelet title");
    //assertEquals("A wavelet title", target.getTitle());
  }

  public void testSetLastModifiedTime() {
    target.setLastModifiedTime(4321L);
    assertEquals(4321L, target.getLastModifiedTime());
  }
}
