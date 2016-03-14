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

package org.waveprotocol.box.server.persistence.file;

import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.persistence.ContactStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.contact.Contact;
import org.waveprotocol.box.server.contact.ContactImpl;
import org.waveprotocol.box.server.persistence.protos.ProtoContactStore.ProtoContacts;
import org.waveprotocol.box.server.shutdown.LifeCycle;
import org.waveprotocol.box.server.shutdown.ShutdownPriority;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * A flat file based implementation of {@link ContactStore}
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class FileContactStore implements ContactStore {
  private static final Log LOG = Log.get(FileContactStore.class);

  private static final String CONTACT_FILE_EXTENSION = ".contact";

  private final String contactStoreBasePath;

  private final LifeCycle lifeCycle = new LifeCycle(FileContactStore.class.getSimpleName(), ShutdownPriority.Storage);

  @Override
  public synchronized void initializeContactStore() throws PersistenceException {
    FileUtils.performDirectoryChecks(contactStoreBasePath,
        CONTACT_FILE_EXTENSION, "contact store", LOG);
  }

  @Inject
  public FileContactStore(@Named(CoreSettings.CONTACT_STORE_DIRECTORY) String contactStoreBasePath) {
    Preconditions.checkNotNull(contactStoreBasePath, "Requested path is null");
    this.contactStoreBasePath = contactStoreBasePath;
    lifeCycle.start();
  }

  @Override
  public synchronized List<Contact> getContacts(ParticipantId participantId) throws PersistenceException {
    lifeCycle.enter();
    try {
      LOG.info("Get contacts for " + participantId.getAddress());
      File file = getContactsFile(participantId);
      if (!file.exists()) {
        return null;
      }
      FileInputStream in = null;
      try {
        in = new FileInputStream(file);
        ProtoContacts proto = ProtoContacts.newBuilder().mergeFrom(in).build();
        List<Contact> contacts = Lists.newArrayList();
        for (ProtoContacts.Contact contact : proto.getContactList()) {
          contacts.add(new ContactImpl(ParticipantId.ofUnsafe(contact.getParticipant()),
              contact.getLastContactTime(), contact.getScoreBonus()));
        }
        return contacts;
      } catch (IOException ex) {
        throw new PersistenceException(ex);
      } finally {
        FileUtils.closeAndIgnoreException(in, file, LOG);
      }
    } finally {
      lifeCycle.leave();
    }
  }

  @Override
  public synchronized void storeContacts(ParticipantId participantId, List<Contact> contacts)
      throws PersistenceException {
    lifeCycle.enter();
    try {
      LOG.info("Store contacts for " + participantId.getAddress());
      File file = getContactsFile(participantId);
      FileOutputStream out = null;
      try {
        ProtoContacts.Builder proto = ProtoContacts.newBuilder();
        for (Contact contact : contacts) {
          proto.addContact(
              ProtoContacts.Contact.newBuilder()
              .setParticipant(contact.getParticipantId().getAddress())
              .setLastContactTime(contact.getLastContactTime())
              .setScoreBonus(contact.getScoreBonus()));
        }
        byte bytes[] = proto.build().toByteArray();
        out = new FileOutputStream(file);
        out.write(bytes);
        out.flush();
      } catch (IOException ex) {
        throw new PersistenceException(ex);
      } finally {
        FileUtils.closeAndIgnoreException(out, file, LOG);
      }
    } finally {
      lifeCycle.leave();
    }
  }

  private File getContactsFile(ParticipantId participant) {
    return new File(contactStoreBasePath, participant.getAddress() + CONTACT_FILE_EXTENSION);
  }
}
