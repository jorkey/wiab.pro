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
package org.waveprotocol.wave.client.wavepanel.impl.contact;

import org.waveprotocol.wave.client.account.ContactListener;
import org.waveprotocol.wave.client.account.ContactManager;
import org.waveprotocol.wave.client.account.Profile;
import org.waveprotocol.wave.client.account.ProfileListener;
import org.waveprotocol.wave.client.account.ProfileManager;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;

/**
 * A contacts view list.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class ContactModelList extends ArrayList<ContactModel> implements ContactListener, ProfileListener {
  interface Listener {
    void onContactsUpdated();
    void onContactUpdated(ContactModel contact);
  }

  private final ContactManager contactManager;
  private final ProfileManager profileManager;
  private Listener listener;

  public ContactModelList(ContactManager contactManager, ProfileManager profileManager) {
    this.contactManager = contactManager;
    this.profileManager = profileManager;
    contactManager.addListener(this);
    profileManager.addListener(this);
    update();
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  @Override
  public void onContactsUpdated() {
    update();
    if (listener != null) {
      listener.onContactsUpdated();
    }
  }

  @Override
  public void onProfileUpdated(Profile profile) {
    if (listener != null) {
      for (ContactModel contact : this) {
        if (contact.getParticipantId().equals(profile.getParticipantId())) {
          listener.onContactUpdated(contact);
          break;
        }
      }
    }
  }

  private void update() {
    clear();
    for (ParticipantId participantId : contactManager.getContacts()) {
      add(new ContactModelImpl(participantId, profileManager.getProfile(participantId)));
    }
  }
}