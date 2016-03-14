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

import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.util.logging.Log;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.logging.Level;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
@SuppressWarnings("serial")
@Singleton
public class RemakeStoreIndexServlet extends HttpServlet {

  private static final Log LOG = Log.get(RemakeStoreIndexServlet.class);

  private final WaveletProvider waveletProvider;

  @Inject
  private RemakeStoreIndexServlet(WaveletProvider waveletProvider) {
    this.waveletProvider = waveletProvider;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    try {
      String pathInfo = request.getPathInfo();
      if ("/".equals(pathInfo)) {
        remakeAllWavesStoreIndex();
      } else {
        WaveId waveId = ModernIdSerialiser.INSTANCE.deserialiseWaveId(pathInfo.substring(1));
        remakeWaveStoreIndex(waveId);
      }
      response.setStatus(HttpServletResponse.SC_OK);
      response.getOutputStream().write(("done").getBytes());
    } catch (Exception ex) {
      LOG.log(Level.SEVERE, null, ex);
      throw new IOException(ex);
    }
  }

  public synchronized void remakeAllWavesStoreIndex() throws WaveletStateException, WaveServerException {
    ExceptionalIterator<WaveId, WaveServerException> it = waveletProvider.getWaveIds();
    int i=0;
    while (it.hasNext()) {
      remakeWaveStoreIndex(it.next());
      LOG.info("Store index on " + ++i + " waves has been remade");
    }
  }
  
  public synchronized void remakeWaveStoreIndex(WaveId waveId) throws WaveServerException {
    LOG.info("Remaking store index on wave " + waveId.serialise() + " ...");
    for (WaveletId waveletId : waveletProvider.getWaveletIds(waveId)) {
      LOG.info("Remaking store index on wavelet " + waveletId.serialise() + " ...");
      try {
        waveletProvider.remakeIndex(WaveletName.of(waveId, waveletId));
      } catch (Exception ex) {
        LOG.log(Level.SEVERE, "Remaking store index on wavelet " + waveletId.serialise() + " error",  ex);
      }
    }
  }
}
