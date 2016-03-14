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

package org.waveprotocol.box.server.persistence;

import org.waveprotocol.wave.model.wave.ParticipantId;

import org.waveprotocol.box.server.contact.ContactImpl;
import org.waveprotocol.box.server.contact.Contact;

import com.google.common.collect.Lists;
import java.util.Calendar;
import java.util.List;
import junit.framework.TestCase;

/**
 * Testcases for the {@link ContactStore}. Implementors of these testcases are
 * responsible for cleanup.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public abstract class ContactStoreTestBase extends TestCase {

  private static final String DOMAIN = "example.com";

  private static final ParticipantId USER1 = ParticipantId.ofUnsafe("user1", DOMAIN);
  private static final ParticipantId USER2 = ParticipantId.ofUnsafe("user2", DOMAIN);
  private static final ParticipantId USER3 = ParticipantId.ofUnsafe("user3", DOMAIN);

  private long currentTime;

  @Override
  protected void setUp() throws Exception {
    currentTime = Calendar.getInstance().getTimeInMillis();
  }

  /**
   * Returns a new empty {@link ContactStore}.
   */
  protected abstract ContactStore newContactStore() throws PersistenceException;

  public final void testRoundtripContact() throws Exception {
    ContactStore contactStore = newContactStore();

    List<Contact> contacts = Lists.newArrayList();
    contacts.add(new ContactImpl(USER2, currentTime-1, 1));
    contacts.add(new ContactImpl(USER3, currentTime, 2));

    contactStore.storeContacts(USER1, contacts);

    List<Contact> retrievedContacts = contactStore.getContacts(USER1);
    assertEquals(2, retrievedContacts.size());
    assertEquals(USER2, retrievedContacts.get(0).getParticipantId());
    assertEquals(currentTime-1, retrievedContacts.get(0).getLastContactTime());
    assertEquals(1, retrievedContacts.get(0).getScoreBonus());
    assertEquals(USER3, retrievedContacts.get(1).getParticipantId());
    assertEquals(currentTime, retrievedContacts.get(1).getLastContactTime());
    assertEquals(2, retrievedContacts.get(1).getScoreBonus());
  }
}