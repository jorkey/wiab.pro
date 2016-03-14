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

package org.waveprotocol.box.server.contact;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;
import junit.framework.TestCase;

import org.waveprotocol.wave.model.wave.ParticipantId;

import org.waveprotocol.box.server.persistence.memory.MemoryStore;

/**
 * Testcases for the {@link ContactManager}.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class ContactManagerTest extends TestCase {

  private static final String DOMAIN = "example.com";

  private static final ParticipantId USER1 = ParticipantId.ofUnsafe("user1", DOMAIN);
  private static final ParticipantId USER2 = ParticipantId.ofUnsafe("user2", DOMAIN);
  private static final ParticipantId USER3 = ParticipantId.ofUnsafe("user3", DOMAIN);
  private static final ParticipantId USER4 = ParticipantId.ofUnsafe("user4", DOMAIN);

  private long currentTime;

  @Override
  protected void setUp() throws Exception {
    currentTime = Calendar.getInstance().getTimeInMillis();
  }

  public final void testContactAddedWhenCallIsStored() throws Exception {
    ContactManager contactManager = new ContactManagerImpl(new MemoryStore(),
        Executors.newSingleThreadScheduledExecutor());

    contactManager.newCall(USER1, USER2, currentTime, true);

    List<Contact> retrievedContacts = contactManager.getContacts(USER1, currentTime-1);
    assertEquals(1, retrievedContacts.size());
    Contact retrievedContact = retrievedContacts.iterator().next();
    assertEquals(USER2, retrievedContact.getParticipantId());
    assertEquals(currentTime, retrievedContact.getLastContactTime());
  }

  public final void testContactsGettedCorrectlyByTime() throws Exception {
    ContactManager contactManager = new ContactManagerImpl(new MemoryStore(),
        Executors.newSingleThreadScheduledExecutor());

    contactManager.newCall(USER1, USER2, currentTime-1, true);
    contactManager.newCall(USER1, USER3, currentTime, true);

    List<Contact> retrievedContacts = contactManager.getContacts(USER1, currentTime-1);
    assertEquals(1, retrievedContacts.size());
    Contact retrievedContact = retrievedContacts.iterator().next();
    assertEquals(USER3, retrievedContact.getParticipantId());
  }

  public final void testDirectCallScoreBonus() throws Exception {
    ContactManager contactManager = new ContactManagerImpl(new MemoryStore(),
        Executors.newSingleThreadScheduledExecutor());

    contactManager.newCall(USER1, USER2, currentTime, true);

    List<Contact> retrievedContacts = contactManager.getContacts(USER1, currentTime - 1);
    Contact contact1 = retrievedContacts.iterator().next();

    assertEquals(contact1.getScoreBonus(), ContactManager.OUTGOING_DIRECT_CALL_BONUS_MS);

    retrievedContacts = contactManager.getContacts(USER2, currentTime - 1);
    Contact contact2 = retrievedContacts.iterator().next();

    assertEquals(contact2.getScoreBonus(), ContactManager.INCOMING_DIRECT_CALL_BONUS_MS);
  }

  public final void testIndirectCallScoreBonus() throws Exception {
    ContactManager contactManager = new ContactManagerImpl(new MemoryStore(),
        Executors.newSingleThreadScheduledExecutor());

    contactManager.newCall(USER1, USER2, currentTime, false);

    List<Contact> retrievedContacts = contactManager.getContacts(USER1, currentTime - 1);
    Contact contact1 = retrievedContacts.iterator().next();

    assertEquals(contact1.getScoreBonus(), ContactManager.OUTGOING_INDIRECT_CALL_BONUS_MS);

    retrievedContacts = contactManager.getContacts(USER2, currentTime - 1);
    Contact contact2 = retrievedContacts.iterator().next();

    assertEquals(contact2.getScoreBonus(), ContactManager.INCOMING_INDIRECT_CALL_BONUS_MS);
  }

  public final void testBonusesExpiration() throws Exception {
    ContactManager contactManager = new ContactManagerImpl(new MemoryStore(),
        Executors.newSingleThreadScheduledExecutor());

    for (int i=0; i < 1000; i++) {
      contactManager.newCall(USER1, USER2, currentTime-999+i, true);
    }

    List<Contact> retrievedContacts = contactManager.getContacts(USER1, currentTime-1);
    Contact contact1 = retrievedContacts.iterator().next();

    assertEquals(0.0,
      contactManager.getScoreBonusAtTime(contact1, currentTime + ContactManager.BONUSES_EXPIRATION_MS));
  }

  public final void testOrderIndependence() throws Exception {
    ContactManager contactManager = new ContactManagerImpl(new MemoryStore(),
        Executors.newSingleThreadScheduledExecutor());

    contactManager.newCall(USER1, USER2, currentTime-1, true);
    contactManager.newCall(USER1, USER2, currentTime, true);

    List<Contact> retrievedContacts = contactManager.getContacts(USER1, currentTime-1);
    Contact contact1 = retrievedContacts.iterator().next();

    contactManager.newCall(USER3, USER2, currentTime, true);
    contactManager.newCall(USER3, USER2, currentTime-1, true);

    retrievedContacts = contactManager.getContacts(USER3, currentTime-1);
    Contact contact2 = retrievedContacts.iterator().next();

    assertEquals(contact1.getScoreBonus(), contact2.getScoreBonus());
  }
}
