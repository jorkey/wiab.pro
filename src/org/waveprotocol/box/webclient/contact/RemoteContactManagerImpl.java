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

package org.waveprotocol.box.webclient.contact;

import org.waveprotocol.box.contact.ContactResponse;
import org.waveprotocol.wave.client.account.ContactManager;
import org.waveprotocol.wave.client.account.impl.AbstractContactManager;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.client.scheduler.Scheduler.IncrementalTask;
import org.waveprotocol.wave.client.scheduler.SchedulerInstance;
import org.waveprotocol.wave.client.scheduler.TimerService;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link ContactManager} that returns contacts fetched from the server.
 *
 * @author akaplanov@apache.org (Andrew Kaplanov)
 */
public final class RemoteContactManagerImpl extends AbstractContactManager {

  private final static LoggerBundle LOG = new DomLogger("fetchContacts");

  private final FetchContactsServiceImpl fetchContactsService;

  private List<ParticipantId> contacts = new ArrayList<ParticipantId>();
  private Map<ParticipantId, Double> contactScores = new HashMap<ParticipantId, Double>();

  protected long timestamp = -1;

  private final IncrementalTask contactsUpdater = new IncrementalTask() {
    @Override
    public boolean execute() {
      fetchContacts();
      return false;
    }
  };

  private final TimerService scheduler;

  public RemoteContactManagerImpl() {
    fetchContactsService = FetchContactsServiceImpl.create();
    scheduler = SchedulerInstance.getHighPriorityTimer();
  }

  @Override
  public List<ParticipantId> getContacts() {
    return contacts;
  }

  @Override
  public void update() {
    scheduler.schedule(contactsUpdater);
  }

  private void fetchContacts() {
    fetchContactsService.fetch(timestamp, new FetchContactsService.Callback() {

      @Override
      public void onFailure(String message) {
        LOG.error().log(message);
      }

      @Override
      public void onSuccess(ContactResponse contactResponse) {
        deserializeResponseAndUpdateContacts(contactResponse);
      }
    });
  }

  private void deserializeResponseAndUpdateContacts(ContactResponse contactResponse) {
    timestamp = contactResponse.getTimestamp();
    if (contactResponse.getContactSize() != 0) {
      for (ContactResponse.Contact contact : contactResponse.getContact()) {
        ParticipantId participant = ParticipantId.ofUnsafe(contact.getParticipant());
        contactScores.put(participant, contact.getScore());
        if (!contacts.contains(participant)) {
          contacts.add(participant);
        }
      }
      Collections.sort(contacts, new Comparator<ParticipantId>(){

        @Override
        public int compare(ParticipantId p1, ParticipantId p2) {
          Double score1 = contactScores.get(p1);
          Double score2 = contactScores.get(p2);
          return score2.compareTo(score1);
        }
      });
      fireOnUpdated();
    }
  }
}
