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
import org.waveprotocol.box.server.rpc.render.WaveHtmlRenderer;
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
public class RemakeHtmlServlet extends HttpServlet {

  private static final Log LOG = Log.get(RemakeHtmlServlet.class);

  private final WaveHtmlRenderer waveHtmlRenderer;
  private final WaveletProvider waveletProvider;

  @Inject
  private RemakeHtmlServlet(WaveHtmlRenderer waveHtmlRenderer, WaveletProvider waveletProvider) {
    this.waveHtmlRenderer = waveHtmlRenderer;
    this.waveletProvider = waveletProvider;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    try {
      remakeAllWavesHtml();
      response.setStatus(HttpServletResponse.SC_OK);
      response.getOutputStream().write(("done").getBytes());
    } catch (Exception ex) {
      LOG.log(Level.SEVERE, null, ex);
      throw new IOException(ex);
    }
  }

  public synchronized void remakeAllWavesHtml() throws WaveletStateException, WaveServerException {
    ExceptionalIterator<WaveId, WaveServerException> witr = waveletProvider.getWaveIds();
    int index = 0;
    while (witr.hasNext()) {
      WaveId waveId = witr.next();
      try {
        waveHtmlRenderer.renderHtml(waveId);
        LOG.info("HTML on " + ++index + " waves has been remade");
      } catch (Exception ex) {
        LOG.log(Level.SEVERE, "Remaking HTML on wave " + waveId.serialise() + " error",  ex);
      }
    }
  }

}
