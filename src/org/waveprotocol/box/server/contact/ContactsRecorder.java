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

import com.google.common.collect.Sets;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.AddParticipant;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.operation.wave.RemoveParticipant;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.util.logging.Log;

import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.waveserver.WaveletProvider;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.waveprotocol.box.server.waveserver.WaveServerException;

/**
 * Listen events on WaveBus and updates contact store.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class ContactsRecorder implements ContactsBusSubscriber {
  private static final Log LOG = Log.get(ContactsRecorder.class);

  private final ContactManager contactManager;
  private final String waveDomain;
  private final WaveletProvider waveletProvider;

  @Inject
  public ContactsRecorder(ContactManager contactManager,
        @Named(CoreSettings.WAVE_SERVER_DOMAIN) final String waveDomain,
        WaveletProvider waveletProvider) {
    this.contactManager = contactManager;
    this.waveDomain = waveDomain;
    this.waveletProvider = waveletProvider;
  }

  @Override
  public void waveletUpdate(WaveletName waveletName, DeltaSequence deltas) {
    HashSet<ParticipantId> participants = Sets.newHashSet();
    try {
      participants.addAll(waveletProvider.getParticipants(waveletName));
    } catch (WaveServerException ex) {
      LOG.log(Level.SEVERE, "Updating contacts error", ex);
    }
    for (int i=0; i < deltas.size(); i++) {
      updateContacts(participants, deltas.get(i), contactManager, waveDomain);
    }
  }

  @Override
  public void waveletCommitted(WaveletName waveletName, HashedVersion version) {
  }

  public static void updateContacts(Set<ParticipantId> participants, TransformedWaveletDelta delta,
      ContactManager contactManager, String waveDomain) {
    ParticipantId sharedParticipant =  ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(waveDomain);
    ParticipantId caller = delta.getAuthor();
    if (!caller.equals(sharedParticipant)) {
      for (WaveletOperation op : delta) {
        if (op instanceof AddParticipant) {
          ParticipantId receptor = ((AddParticipant) op).getParticipantId();
          if (!receptor.equals(sharedParticipant)) {
            try {
              if (!caller.equals(receptor)) {
                contactManager.newCall(caller, receptor, delta.getApplicationTimestamp(), true);
              }
            } catch (PersistenceException ex) {
              LOG.severe("Update contact " + delta.getAuthor().getAddress() + "-" + receptor.getAddress(), ex);
            }
            for (ParticipantId participant : participants) {
              if (!participant.equals(sharedParticipant)
                  && !participant.equals(caller)
                  && !participant.equals(receptor)) {
                try {
                  contactManager.newCall(participant, receptor, delta.getApplicationTimestamp(), false);
                } catch (PersistenceException ex) {
                  LOG.severe("Update contact " + participant.getAddress() + "-" + receptor.getAddress(), ex);
                }
              }
            }
          }
          participants.add(receptor);
        } else if (op instanceof RemoveParticipant) {
          ParticipantId participant = ((RemoveParticipant) op).getParticipantId();
          participants.remove(participant);
        }
      }
    }
  }
}