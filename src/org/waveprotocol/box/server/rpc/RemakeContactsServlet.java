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

package org.waveprotocol.box.server.rpc;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.common.ThrowableReceiver;
import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.contact.ContactManager;
import org.waveprotocol.box.server.contact.ContactsRecorder;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionFactoryImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;
import org.waveprotocol.wave.util.logging.Log;

/**
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
@SuppressWarnings("serial")
@Singleton
public class RemakeContactsServlet extends HttpServlet {

  private static final Log LOG = Log.get(RemakeContactsServlet.class);

  private static final IdURIEncoderDecoder URI_CODEC =
      new IdURIEncoderDecoder(new JavaUrlCodec());

  private static final HashedVersionFactory HASH_FACTORY = new HashedVersionFactoryImpl(URI_CODEC);

  private final ContactManager contactManager;
  private final WaveletProvider waveletProvider;
  private final String waveDomain;

  @Inject
  private RemakeContactsServlet(ContactManager contactManager, WaveletProvider waveletProvider,
      @Named(CoreSettings.WAVE_SERVER_DOMAIN) final String waveDomain) {
    this.contactManager = contactManager;
    this.waveletProvider = waveletProvider;
    this.waveDomain = waveDomain;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    try {
      remakeContacts();
      response.setStatus(HttpServletResponse.SC_OK);
      response.getOutputStream().write(("done").getBytes());
    } catch (Exception ex) {
      LOG.log(Level.SEVERE, null, ex);
      throw new IOException(ex);
    }
  }

  public synchronized void remakeContacts() throws WaveletStateException, WaveServerException {
    ExceptionalIterator<WaveId, WaveServerException> witr = waveletProvider.getWaveIds();
    int i=0;
    while (witr.hasNext()) {
      WaveId waveId = witr.next();
      try {
        ImmutableSet<WaveletId> wavelets = waveletProvider.getWaveletIds(waveId);
        for (WaveletId wavelet : wavelets) {
          WaveletName waveletName = WaveletName.of(waveId, wavelet);
          HashedVersion committedVersion = waveletProvider.getLastCommittedVersion(waveletName);
          final Set<ParticipantId> participants = Sets.newHashSet();
          waveletProvider.getDeltaHistory(WaveletName.of(waveId, wavelet),
              HASH_FACTORY.createVersionZero(waveletName), committedVersion,
              new ThrowableReceiver<WaveletDeltaRecord, WaveServerException>() {

            @Override
            public boolean put(WaveletDeltaRecord delta) {
              ContactsRecorder.updateContacts(participants, delta.getTransformedDelta(), contactManager, waveDomain);
              return true;
            }
          });
        }
        LOG.info("Contacts on " + ++i + " waves has been remade");
      } catch (Exception ex) {
        LOG.log(Level.SEVERE, "Remaking contacts on wave " + waveId.serialise() + " error",  ex);
      }
    }
  }
}
