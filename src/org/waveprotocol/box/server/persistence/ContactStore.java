/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.waveprotocol.box.server.persistence;

import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.box.server.contact.Contact;

import java.util.List;

/**
 * Interface for the storage and retrieval of {@link Contact}s.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public interface ContactStore {
  /**
   * Initialize the contact store.
   *
   * @throws PersistenceException
   */
  void initializeContactStore() throws PersistenceException;

  /**
   * Gets contacts of specified participant.
   *
   * @param participant the participant id.
   * @return contacts of specified participant.
   */
  List<Contact> getContacts(ParticipantId participant) throws PersistenceException;

  /**
   * Store contacts of specified participant.
   *
   * @param participant the participant id.
   * @param contacts the contacts.
   */
  void storeContacts(ParticipantId participant, List<Contact> contacts) throws PersistenceException;
}
