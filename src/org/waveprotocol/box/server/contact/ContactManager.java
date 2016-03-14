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

package org.waveprotocol.box.server.contact;

import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.ParticipantId;
  
import java.util.List;

/**
 * Serves and caches reads and updates of contacts.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public interface ContactManager {
  public static final long BONUSES_EXPIRATION_MS = 365L*24*60*60*1000; // 1 year
  public static final long BONUSES_STEP_MS = 30L*24*60*60*1000; // 1 month
  
  public static final long INCOMING_INDIRECT_CALL_BONUS_MS = 0;
  public static final long OUTGOING_INDIRECT_CALL_BONUS_MS = BONUSES_EXPIRATION_MS; // 1 month
  public static final long INCOMING_DIRECT_CALL_BONUS_MS   = 12 * BONUSES_EXPIRATION_MS; // 1 year
  public static final long OUTGOING_DIRECT_CALL_BONUS_MS   = 120 * BONUSES_EXPIRATION_MS; // 10 years
  
  /**
   * Gets coontacts of participant from specified time.
   * Gets all, if fromTime is 0.
   * 
   * @param participant
   * @param fromTime
   * @return contacts
   * @throws PersistenceException 
   */
  public List<Contact> getContacts(ParticipantId participant, long fromTime) throws PersistenceException;
  
  /**
   * Appends call to contact, stores contact and updates interlucutrs info. 
   * 
   * @param caller participant id
   * @param receptor participant id
   * @param direct true if call is directly 
   * @param time the time of call
   * @throws PersistenceException 
   */
  public void newCall(ParticipantId caller, ParticipantId receptor, long time, boolean direct) throws PersistenceException;

  /**
   * Gets bonus of contact's score at the time.
   */
  public double getScoreBonusAtTime(Contact contact, long time);
}
