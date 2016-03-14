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

package org.waveprotocol.box.server.persistence.protos;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import org.waveprotocol.wave.model.wave.ParticipantId;

import org.waveprotocol.box.server.contact.Contact;
import org.waveprotocol.box.server.contact.ContactImpl;
import org.waveprotocol.box.server.persistence.protos.ProtoContactStore.ProtoContacts;

import java.util.List;

/**
 * This class is used to serialize and deserialize {@link Account} and {@link ProtoAccount}
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class ProtoContactsDataSerializer {
  /**
   * Serialize {@link Contact} into {@link ProtoContact}.
   */
  public static ProtoContacts serialize(List<Contact> contacts) {
    Preconditions.checkNotNull(contacts, "contacts is null");
    ProtoContacts.Builder protoContacts = ProtoContacts.newBuilder();
    for (Contact contact : contacts) {
      ProtoContacts.Contact.Builder protoContact = ProtoContacts.Contact.newBuilder();
      protoContact.setParticipant(contact.getParticipantId().getAddress());
      protoContact.setLastContactTime(contact.getLastContactTime());
      protoContact.setScoreBonus(contact.getScoreBonus());
    }
    return protoContacts.build();
  }

  /**
   * Deserialize {@link ProtoContact} into {@link Contact}.
   */
  public static List<Contact> deserialize(ProtoContacts protoContacts) {
    List<Contact> contacts = Lists.newArrayList();
    for (ProtoContacts.Contact protoContact : protoContacts.getContactList()) {
      Contact contact = new ContactImpl(ParticipantId.ofUnsafe(protoContact.getParticipant()),
          protoContact.getLastContactTime(), protoContact.getScoreBonus());
      contacts.add(contact);
    }
    return contacts;
  }
}