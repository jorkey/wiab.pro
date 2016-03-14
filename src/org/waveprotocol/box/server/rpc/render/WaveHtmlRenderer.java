package org.waveprotocol.box.server.rpc.render;

import org.waveprotocol.wave.model.id.WaveId;

import java.io.IOException;

/**
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public interface WaveHtmlRenderer {

  public void renderHtml(WaveId waveId) throws IOException;
}
