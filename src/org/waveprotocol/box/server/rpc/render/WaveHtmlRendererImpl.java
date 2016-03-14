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
package org.waveprotocol.box.server.rpc.render;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import com.google.wave.api.InvalidRequestException;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.data.converter.EventDataConverterManager;
import java.io.Closeable;
import java.io.IOException;
import java.util.Set;
import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.waveprotocol.box.server.persistence.html.WaveHtmlStore;
import org.waveprotocol.box.server.robots.OperationContextImpl;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;

/**
 * HTML based implementation of {@link PerUserWaveViewHandler}.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
@Singleton
public class WaveHtmlRendererImpl implements WaveHtmlRenderer, WaveHtmlRendererBusSubscriber, Closeable {

  private static final Logger LOG = Logger.getLogger(WaveHtmlRendererImpl.class
      .getName());

  static public final String HTML_FILE_SUFFIX = ".html";

  private final EventDataConverterManager converterManager;
  private final WaveletProvider waveletProvider;
  private final ConversationUtil conversationUtil;
  private final WaveHtmlStore waveHtmlStore;
  private final String waveDomain;
  private final ParticipantId sharedParticipant;

  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  /** Current indexing waves **/
  private static ConcurrentHashMap<WaveId, ListenableFutureTask<Void>> processingWaves =
      new ConcurrentHashMap<WaveId, ListenableFutureTask<Void>>();

  /** Delay between HTML updates **/
  private static final long WAVE_UPDATE_HTML_DELAY_MIN = 1;

  @Inject
  public WaveHtmlRendererImpl(EventDataConverterManager converterManager,
      WaveletProvider waveletProvider, ConversationUtil conversationUtil,
      WaveHtmlStore waveHtmlStore,
      @Named(CoreSettings.WAVE_SERVER_DOMAIN) String waveDomain) {
    this.converterManager = converterManager;
    this.waveletProvider = waveletProvider;
    this.conversationUtil = conversationUtil;
    this.waveHtmlStore = waveHtmlStore;
    this.waveDomain = waveDomain;
    this.sharedParticipant = ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(waveDomain);
  }

  @Override
  public void renderHtml(WaveId waveId) throws IOException {
    updateHtml(waveId);
  }

  @Override
  public void waveletUpdate(WaveletName waveletName, DeltaSequence deltas) {
    try {
      if (waveletProvider.hasParticipant(waveletName, sharedParticipant)) {
        sheduleUpdateHtml(waveletName.waveId);
      }
    } catch (WaveServerException ex) {
      LOG.log(Level.WARNING, "Rendering HTML error", ex);
    }
  }

  @Override
  public void waveletCommitted(WaveletName waveletName, HashedVersion version) {
  }

  private ListenableFuture<Void> sheduleUpdateHtml(final WaveId waveId) {
    ListenableFutureTask<Void> task = processingWaves.get(waveId);
    if (task == null) {
      task = ListenableFutureTask.create(new Callable<Void>() {

        @Override
        public Void call() throws Exception {
          processingWaves.remove(waveId);
          try {
            updateHtml(waveId);
          } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to make HTML for " + waveId.serialise(), e);
            throw e;
          }
          return null;
        }
      });
      processingWaves.put(waveId, task);
      executor.schedule(task, WAVE_UPDATE_HTML_DELAY_MIN, TimeUnit.MINUTES);
    }
    return task;
  }

  @Override
  public void close() throws IOException {
  }

  private void updateHtml(WaveId waveId) throws IOException {
    LOG.info("Rendering HTML of wave " + waveId.serialise());
    String html = fetchRenderedWavelet(waveId, WaveletId.of(waveDomain, IdConstants.CONVERSATION_ROOT_WAVELET),
        null, sharedParticipant);
    if (html == null) {
      html = HtmlRenderer.NO_CONVERSATIONS;
    }
    waveHtmlStore.writeHtml(waveId, html);
    LOG.info("HTML of wave " + waveId.serialise() + " was rendered");
  }

  private String fetchRenderedWavelet(WaveId waveId, WaveletId waveletId, String blipId,
      ParticipantId viewer) {
    OperationContextImpl context =
        new OperationContextImpl(waveletProvider,
        converterManager.getEventDataConverter(ProtocolVersion.DEFAULT), conversationUtil);
    LOG.fine("Fetching wavelet: waveId: " + waveId.serialise() + ", waveletId: "
        + waveletId != null ? waveletId.serialise() : "");
    String html = null;
    try {
      html = RenderWaveService.create().exec(waveId, waveletId, blipId, viewer, context);
    } catch (InvalidRequestException ex) {
      LOG.log(Level.SEVERE, "Render to HTML error", ex);
    }
    return html;
  }
}
