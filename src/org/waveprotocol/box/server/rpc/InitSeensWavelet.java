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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.wave.api.InvalidRequestException;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.data.converter.EventDataConverterManager;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.robots.OperationContextImpl;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.robots.util.LoggingRequestListener;
import org.waveprotocol.box.server.robots.util.OperationUtil;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.supplement.PrimitiveSupplement;
import org.waveprotocol.wave.model.supplement.WaveletBasedSupplement;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.util.logging.Log;

/**
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
@SuppressWarnings("serial")
@Singleton
public class InitSeensWavelet extends HttpServlet {

  private static final Log LOG = Log.get(InitSeensWavelet.class);
  private static final WaveletProvider.SubmitRequestCallback LOGGING_REQUEST_LISTENER =
      new LoggingRequestListener(LOG);

  private final WaveletProvider waveletProvider;
  private final ConversationUtil conversationUtil;
  private final EventDataConverterManager converterManager;

  @Inject
  private InitSeensWavelet(WaveletProvider waveletProvider,
      ConversationUtil conversationUtil, EventDataConverterManager converterManager) {
    this.waveletProvider = waveletProvider;
    this.conversationUtil = conversationUtil;
    this.converterManager = converterManager;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    try {
      initializeAllWavesSeens();
      response.setStatus(HttpServletResponse.SC_OK);
      response.getOutputStream().write(("done").getBytes());
    } catch (Exception ex) {
      LOG.log(Level.SEVERE, null, ex);
      throw new IOException(ex);
    }
  }

  public synchronized void initializeAllWavesSeens() throws WaveletStateException, WaveServerException {
    ExceptionalIterator<WaveId, WaveServerException> witr = waveletProvider.getWaveIds();
    int i=0;
    while (witr.hasNext()) {
      WaveId waveId = witr.next();
      LOG.info("Initialize seens on wave " + waveId.serialise() + " ...");
      for (WaveletId waveletId : waveletProvider.getWaveletIds(waveId)) {
        try {
          if (IdUtil.isUserDataWavelet(waveletId)) {
            ParticipantId participant = ParticipantId.of(IdUtil.getUserDataWaveletAddress(waveletId));
            LOG.info("Initialize seen on wavelet " + waveletId.serialise() + " ...");
            setSeenVersion(WaveletName.of(waveId, waveletId), participant);
          }
        } catch (Exception ex) {
          LOG.log(Level.SEVERE, "Initialize seen on wavelet " + waveletId.serialise() + " error", ex);
        }
      }
      LOG.info("Seens on " + ++i + " waves has been initialized");
    }
  }

  public void setSeenVersion(WaveletName waveletName, ParticipantId participant)
    throws WaveletStateException, InterruptedException, ExecutionException,
    InvalidRequestException, WaveServerException, OperationException {

    OperationContextImpl context = new OperationContextImpl(waveletProvider,        converterManager.getEventDataConverter(ProtocolVersion.DEFAULT), conversationUtil);

    OpBasedWavelet udw = context.openWavelet(waveletName.waveId, waveletName.waveletId, participant);
    PrimitiveSupplement udwState = WaveletBasedSupplement.create(udw);

    WaveletId rootWavelet = WaveletId.of(waveletName.waveId.getDomain(), IdConstants.CONVERSATION_ROOT_WAVELET);
    HashedVersion seenVersion = udwState.getSeenVersion(rootWavelet);
    if (seenVersion.getVersion() == 0) {
      long lastReadVersion = PrimitiveSupplement.NO_VERSION;
      for (String blipId : udwState.getReadBlips(rootWavelet)) {
        long version = udwState.getLastReadBlipVersion(rootWavelet, blipId);
        if (version > lastReadVersion) {
          lastReadVersion = version;
        }
      }
      if (lastReadVersion != PrimitiveSupplement.NO_VERSION) {
        seenVersion = waveletProvider.getNearestHashedVersion(WaveletName.of(
            waveletName.waveId, rootWavelet), lastReadVersion);
        if (seenVersion.getVersion() != 0) {
          LOG.info("Set initial seen on " + waveletName.toString() + " to " + seenVersion.toString());
          udwState.setSeenVersion(rootWavelet, seenVersion);
        }
      }
    }

    OperationUtil.submitDeltas(context, waveletProvider, LOGGING_REQUEST_LISTENER);
  }

}
