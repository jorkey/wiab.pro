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
import java.io.IOException;
import java.util.logging.Level;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.search.WaveIndexer;
import org.waveprotocol.box.server.waveletstate.IndexingInProcessException;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.util.logging.Log;

/**
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
@SuppressWarnings("serial")
@Singleton
public class RemakeIndexServlet extends HttpServlet {

  private static final Log LOG = Log.get(RemakeIndexServlet.class);

  private final WaveIndexer waveIndexer;
  private final WaveletProvider waveletProvider;

  @Inject
  private RemakeIndexServlet(WaveIndexer waveIndexer, WaveletProvider waveletProvider) {
    this.waveIndexer = waveIndexer;
    this.waveletProvider = waveletProvider;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    try {
      remakeAllWavesIndex();
      response.setStatus(HttpServletResponse.SC_OK);
      response.getOutputStream().write(("done").getBytes());
    } catch (Exception ex) {
      LOG.log(Level.SEVERE, null, ex);
      throw new IOException(ex);
    }
  }

  public synchronized void remakeAllWavesIndex() throws WaveletStateException, WaveServerException, InterruptedException {
    ExceptionalIterator<WaveId, WaveServerException> witr = waveletProvider.getWaveIds();
    int i=0;
    while (witr.hasNext()) {
      WaveId waveId = witr.next();
      for (;;) {
        try {
          waveIndexer.updateIndex(waveId);
          LOG.info("Index on " + ++i + " waves has been remade");
        } catch (IndexingInProcessException ex) {
          Thread.sleep(1000);
          continue;
        } catch (Exception ex) {
          LOG.log(Level.SEVERE, "Remaking index on wave " + waveId.serialise() + " error",  ex);
        }
        break;
      }
    }
  }

}
