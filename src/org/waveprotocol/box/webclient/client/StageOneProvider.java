/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.webclient.client;

import org.waveprotocol.wave.client.StageOne;
import org.waveprotocol.wave.client.StageZero;
import org.waveprotocol.wave.client.common.util.LogicalPanel;
import org.waveprotocol.wave.client.scroll.SmartScroller;
import org.waveprotocol.wave.client.wavepanel.event.FocusManager;
import org.waveprotocol.wave.client.wavepanel.impl.WavePanelImpl;
import org.waveprotocol.wave.client.wavepanel.impl.collapse.CollapsePresenter;
import org.waveprotocol.wave.client.wavepanel.impl.focus.FocusFrameController;
import org.waveprotocol.wave.client.wavepanel.impl.focus.FocusFramePresenterImpl;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.dom.CssProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.FullStructure;
import org.waveprotocol.wave.client.wavepanel.view.dom.UpgradeableDomAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.WavePanelResourceLoader;
import org.waveprotocol.wave.client.account.ContactManager;
import org.waveprotocol.wave.client.account.ProfileManager;
import org.waveprotocol.wave.client.common.util.AsyncHolder;
import org.waveprotocol.wave.client.common.util.ClientPercentEncoderDecoder;
import org.waveprotocol.wave.client.common.util.CountdownLatch;
import org.waveprotocol.wave.client.concurrencycontrol.WaveletOperationalizer;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.client.doodad.DoodadInstallers;
import org.waveprotocol.wave.client.doodad.attachment.AttachmentManagerImpl;
import org.waveprotocol.wave.client.doodad.attachment.ImageThumbnail;
import org.waveprotocol.wave.client.doodad.attachment.render.ImageThumbnailWrapper;
import org.waveprotocol.wave.client.doodad.diff.DiffAnnotationHandler;
import org.waveprotocol.wave.client.doodad.diff.DiffDeleteRenderer;
import org.waveprotocol.wave.client.doodad.link.LinkAnnotationHandler;
import org.waveprotocol.wave.client.doodad.selection.SelectionAnnotationHandler;
import org.waveprotocol.wave.client.editor.content.ContentDocument;
import org.waveprotocol.wave.client.editor.content.Registries;
import org.waveprotocol.wave.client.editor.content.misc.StyleAnnotationHandler;
import org.waveprotocol.wave.client.gadget.Gadget;
import org.waveprotocol.wave.client.i18n.ErrorMessages;
import org.waveprotocol.wave.client.render.ReductionBasedRenderer;
import org.waveprotocol.wave.client.render.RenderingRules;
import org.waveprotocol.wave.client.scheduler.Scheduler;
import org.waveprotocol.wave.client.scheduler.SchedulerInstance;
import org.waveprotocol.wave.client.state.BlipReadStateMonitor;
import org.waveprotocol.wave.client.state.BlipReadStateMonitorImpl;
import org.waveprotocol.wave.client.state.InboxStateMonitor;
import org.waveprotocol.wave.client.state.InboxStateMonitorImpl;
import org.waveprotocol.wave.client.state.ThreadReadStateMonitor;
import org.waveprotocol.wave.client.state.ThreadReadStateMonitorImpl;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.wave.DiffContentDocument;
import org.waveprotocol.wave.client.wave.LocalSupplementedWave;
import org.waveprotocol.wave.client.wave.LocalSupplementedWaveImpl;
import org.waveprotocol.wave.client.wave.RegistriesHolder;
import org.waveprotocol.wave.client.wave.WaveDocuments;
import org.waveprotocol.wave.client.wavepanel.impl.blipreader.BlipReader;
import org.waveprotocol.wave.client.wavepanel.render.BlipDocumentRendererImpl;
import org.waveprotocol.wave.client.wavepanel.render.DocumentRegistries;
import org.waveprotocol.wave.client.wavepanel.render.DomScrollerImpl;
import org.waveprotocol.wave.client.wavepanel.render.DynamicDomRenderer;
import org.waveprotocol.wave.client.wavepanel.render.ElementDomMeasurer;
import org.waveprotocol.wave.client.wavepanel.render.ElementDomRenderer;
import org.waveprotocol.wave.client.wavepanel.render.FullDomRenderer;
import org.waveprotocol.wave.client.wavepanel.render.HtmlDomRenderer;
import org.waveprotocol.wave.client.wavepanel.render.InlineAnchorLiveRenderer;
import org.waveprotocol.wave.client.wavepanel.render.LiveProfileRenderer;
import org.waveprotocol.wave.client.wavepanel.render.LiveSupplementRenderer;
import org.waveprotocol.wave.client.wavepanel.render.ParticipantUpdateRenderer;
import org.waveprotocol.wave.client.wavepanel.render.ReplyManager;
import org.waveprotocol.wave.client.wavepanel.render.ScreenPositionDomScroller;
import org.waveprotocol.wave.client.wavepanel.render.ShallowBlipRenderer;
import org.waveprotocol.wave.client.wavepanel.render.TagUpdateRenderer;
import org.waveprotocol.wave.client.wavepanel.render.UndercurrentShallowBlipRenderer;
import org.waveprotocol.wave.client.wavepanel.render.ObservableDynamicRenderer;
import org.waveprotocol.wave.client.wavepanel.view.ModelIdMapper;
import org.waveprotocol.wave.client.wavepanel.view.ModelIdMapperImpl;
import org.waveprotocol.wave.client.wavepanel.view.ViewIdMapper;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProviderImpl;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.DomRenderer;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.ViewFactories;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.ViewFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;
import org.waveprotocol.wave.clientserver.ReturnCode;
import org.waveprotocol.wave.clientserver.ReturnStatus;
import org.waveprotocol.wave.common.logging.LoggerBundle;

import org.waveprotocol.wave.concurrencycontrol.channel.ViewChannel;
import org.waveprotocol.wave.concurrencycontrol.channel.ViewChannelImpl;
import org.waveprotocol.wave.concurrencycontrol.channel.WaveViewService;

import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.conversation.WaveBasedConversationView;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationView;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationViewImpl;
import org.waveprotocol.wave.model.document.indexed.IndexedDocumentImpl;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.automaton.DocumentSchema;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.OperationRuntimeException;
import org.waveprotocol.wave.model.operation.wave.BasicWaveletOperationContextFactory;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.schema.SchemaProvider;
import org.waveprotocol.wave.model.schema.conversation.ConversationSchemas;
import org.waveprotocol.wave.model.supplement.LiveSupplementedWaveImpl;
import org.waveprotocol.wave.model.supplement.ObservablePrimitiveSupplement;
import org.waveprotocol.wave.model.supplement.ObservableSupplementedWave;
import org.waveprotocol.wave.model.supplement.ScreenPosition;
import org.waveprotocol.wave.model.supplement.SupplementImpl;
import org.waveprotocol.wave.model.supplement.SupplementedWaveImpl;
import org.waveprotocol.wave.model.supplement.WaveletBasedSupplement;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionZeroFactoryImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.Wavelet;
import org.waveprotocol.wave.model.wave.data.DocumentFactory;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletFragmentData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.data.impl.LazyContentBlipDataImpl;
import org.waveprotocol.wave.model.wave.data.impl.ObservablePluggableMutableDocument;
import org.waveprotocol.wave.model.wave.data.impl.StartVersionHelper;
import org.waveprotocol.wave.model.wave.data.impl.WaveViewDataImpl;
import org.waveprotocol.wave.model.wave.data.impl.WaveletFragmentDataImpl;
import org.waveprotocol.wave.model.wave.opbased.ObservableWaveView;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.model.wave.opbased.WaveViewImpl;
import org.waveprotocol.wave.model.waveref.WaveRef;

import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;
import org.waveprotocol.box.webclient.contact.RemoteContactManagerImpl;
import org.waveprotocol.box.webclient.widget.loading.LoadingIndicator;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.HTML;

import com.google.common.base.Preconditions;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of the stage one configuration. Each component is
 * defined by a factory method, any of which may be overridden in order to
 * stub out some dependencies. Circular dependencies are not detected.
 */
public final class StageOneProvider extends StageProvider<StageOne> {
  // Initial values.
  private final LogicalPanel waveHolder;
  private final Element waveElement;
  private final String sessionId;
  private final ParticipantId signedInUser;
  private final boolean fromLastRead;
  private final boolean newWave;
  private final Set<ParticipantId> participants;
  private final WaveWebSocketClient webSocket;
  private final WaveRef waveRef;
  private final IdGenerator idGenerator;
  private final ProfileManager profileManager;
  private final LoadingIndicator loadingIndicator;
  private final UniversalPopup errorPopup;
  private final ErrorMessages errorMessages;

  // Clien-Server protocol.
  private ViewChannel viewChannel;
  private FragmentRequesterImpl fragmentRequester;

  // Data model.
  private WaveViewData waveViewData;
  private WaveViewImpl<OpBasedWavelet> waveView;
  private WaveDocuments<DiffContentDocument> waveDocuments;

  // Rendering objects.
  private WavePanelImpl wavePanel;
  private FocusFramePresenterImpl focusFramePresenter;
  private FocusFrameController focusFrameController;
  private UpgradeableDomAsViewProvider domAsViewProvider;
  private SmartScroller<? super BlipView> smartScroller;
  private CssProvider cssProvider;
  private Scheduler.Task installStaticsTask;
  private ViewIdMapper viewIdMapper;
  private ShallowBlipRenderer shallowBlipRenderer;
  private RenderingRules<UiBuilder> renderingRules;
  private DomRenderer domRenderer;
  private BlipDocumentRendererImpl blipDocumentRenderer;
  private ModelAsViewProvider modelAsViewProvider;
  private ReplyManager replyManager;
  private ConversationNavigator navigator;
  private LiveProfileRenderer liveProfileRenderer;
  private ParticipantUpdateRenderer participantUpdateRenderer;
  private TagUpdateRenderer tagUpdateRenderer;
  private DynamicDomRenderer dynamicRenderer;
  private DocumentRegistries documentRegistries;
  private ScreenPositionDomScroller screenPositionScroller;
  private BlipReader blipReader;
  private ElementDomRenderer elementRenderer;
  private ElementDomMeasurer elementMeasurer;
  private WaveViewService waveViewService;
  private SchemaProvider schemaProvider;

  // Misc.
  private StartVersionHelper lookVersionHelper;
  private ContactManager contactManager;
  private ObservableConversationView conversationView;
  private ObservableQuasiConversationView quasiConversationView;
  private ObservablePrimitiveSupplement supplement;
  private LocalSupplementedWave localSupplementedWave;
  private BlipReadStateMonitor blipReadStateMonitor;
  private InboxStateMonitor inboxStateMonitor;
  private ThreadReadStateMonitor threadReadStateMonitor;
  private WaveletOperationContext.Factory operationContextFactory;
  private WaveletOperationalizer waveletOperationalizer;

  public StageOneProvider(StageZero previous, LogicalPanel waveHolder, Element waveElement,
      String sessionId, ParticipantId signedInUser,
      boolean fromLastRead, boolean newWave, Set<ParticipantId> participants,
      WaveWebSocketClient webSocket, WaveRef waveRef, IdGenerator idGenerator,
      ProfileManager profileManager, LoadingIndicator loadingIndicator,
      UniversalPopup errorPopup, ErrorMessages errorMessages) {
    // Nothing in stage one depends on anything in stage zero currently, but
    // the dependency is wired up so that it is simple to add such
    // dependencies should they be necessary in the future.
    this.waveHolder = waveHolder;
    this.waveElement = waveElement;
    this.sessionId = sessionId;
    this.signedInUser = signedInUser;
    this.fromLastRead = fromLastRead;
    this.newWave = newWave;
    this.participants = participants;
    this.webSocket = webSocket;
    this.waveRef = waveRef;
    this.idGenerator = idGenerator;
    this.profileManager = profileManager;
    this.loadingIndicator = loadingIndicator;
    this.errorPopup = errorPopup;
    this.errorMessages = errorMessages;
  }

  @Override
  public void destroy() {
    if (dynamicRenderer != null) {
      dynamicRenderer.destroy();
      dynamicRenderer = null;
    }
    if (fragmentRequester != null) {
      fragmentRequester.close();
      fragmentRequester = null;
    }
    if (viewChannel != null) {
      viewChannel.close();
      viewChannel = null;
    }
    if (wavePanel != null) {
      wavePanel.destroy();
      wavePanel = null;
    }
    if (installStaticsTask != null) {
      SchedulerInstance.getMediumPriorityTimer().cancel(installStaticsTask);
      installStaticsTask = null;
    }
  }

  //
  // Stage loading methods
  //

  @Override
  public void create(final Accessor<StageOne> whenReady) {
    final CountdownLatch waveViewLatch = CountdownLatch.create(2, new Command() {

      @Override
      public void execute() {
        onWaveViewDataCreated(whenReady);
      }
    });

    if (!newWave) {
      fetchWave(new AsyncHolder.Accessor<WaveViewData>() {

        @Override
        public void use(WaveViewData x) {
          waveViewData = x;
          waveViewLatch.tick();
        }
      },
      new AsyncHolder.Accessor<ReturnStatus>() {

        @Override
        public void use(ReturnStatus error) {
          whenReady.use(null);
          showFetchError(error);
        }
      });
    } else {
      waveViewData = WaveViewDataImpl.create(waveRef.getWaveId());
      waveViewLatch.tick();
    }

    // Defer everything else, to let the RPC go out.
    SchedulerInstance.getMediumPriorityTimer().scheduleDelayed(installStaticsTask = new Scheduler.Task() {

      @Override
      public void execute() {
        install();
        waveViewLatch.tick();
      }
    }, 20);
  }

  /**
   * Installs parts of stage one that have dependencies.
   * <p>
   * This method is only called once all asynchronously loaded components of
   * stage one are ready.
   * <p>
   * Subclasses may override this to change the set of installed features.
   */
  private void install() {
    Timer timer = Timing.start("StageOneProvider.install");
    try {
      // Statics.
      WavePanelResourceLoader.loadCss();
      RegistriesHolder.initialize();

      // Eagerly install some features.
      getFocusFrame();

      // Install wave panel into focusManager framework.
      FocusManager focusManager = FocusManager.getRoot();
      focusManager.add(getWavePanel());
      focusManager.select(getWavePanel());
    } finally {
      Timing.stop(timer);
    }
  }

  private void onWaveViewDataCreated(Accessor<StageOne> whenReady) {
    // Initialize base segments.
    initializeBlip(IdConstants.MANIFEST_DOCUMENT_ID);
    initializeBlip(IdConstants.TAGS_DOCUMENT_ID);

    // Initialize the conversation.
    getQuasiConversationView().initialize(getConversationView(), getSupplementWave());
    if (newWave) {
      Conversation conversation = getQuasiConversationView().createRoot();

      // For a new wave, initial state comes from local initialization.
      conversation.getRootThread().appendBlip();

      // Adds any initial participant to the new wave.
      conversation.addParticipantIds(participants);
    }

    // Process diff operations on manifest.
    processDiffsOnBlip(IdConstants.MANIFEST_DOCUMENT_ID);

    // Look unlooked blips.
    getBlipReader().look(getQuasiConversationView());

    // Start rendering.
    startRendering();

    // Process diff operations on participants and tags.
    processDiffsOnParticipants();
    processDiffsOnBlip(IdConstants.TAGS_DOCUMENT_ID);

    // Wait for complete of rendering.
    waitForRenderingFinished(whenReady);
  }

  private void waitForRenderingFinished(final Accessor<StageOne> whenReady) {
    getDynamicRenderer().addListener(new ObservableDynamicRenderer.ListenerImpl() {
      @Override
      public void onRenderingFinished(ObservableDynamicRenderer.RenderResult result) {
        loadingIndicator.hide();
        getDynamicRenderer().removeListener(this);
        whenReady.use(createStage());
      }
    });
  }

  private StageOne createStage() {
    return new StageOne() {

      @Override
      public WavePanelImpl getWavePanel() {
        return StageOneProvider.this.getWavePanel();
      }

      @Override
      public WaveViewImpl<OpBasedWavelet> getWaveView() {
        return StageOneProvider.this.getWaveView();
      }

      @Override
      public WaveletOperationalizer getWaveletOperationalizer() {
        return StageOneProvider.this.getWaveletOperationalizer();
      }

      @Override
      public WaveDocuments getWaveDocuments() {
        return StageOneProvider.this.getWaveDocuments();
      }

      @Override
      public ViewChannel getViewChannel() {
        return StageOneProvider.this.getViewChannel();
      }

      @Override
      public FragmentRequesterImpl getFragmentRequester() {
        return StageOneProvider.this.getFragmentRequester();
      }

      @Override
      public FocusFramePresenterImpl getFocusFrame() {
        return StageOneProvider.this.getFocusFrame();
      }

      @Override
      public ObservableQuasiConversationView getConversations() {
        return StageOneProvider.this.getQuasiConversationView();
      }

      @Override
      public ObservableWaveView getWave() {
        return StageOneProvider.this.getWaveView();
      }

      @Override
      public LocalSupplementedWave getSupplement() {
        return StageOneProvider.this.getSupplementWave();
      }

      @Override
      public BlipReadStateMonitor getReadMonitor() {
        return StageOneProvider.this.getBlipReadStateMonitor();
      }

      @Override
      public InboxStateMonitor getInboxStateMonitor() {
        return StageOneProvider.this.getInboxStateMonitor();
      }

      @Override
      public ModelAsViewProvider getModelAsViewProvider() {
        return StageOneProvider.this.getModelAsViewProvider();
      }

      @Override
      public ContactManager getContactManager() {
        return StageOneProvider.this.getContactManager();
      }

      @Override
      public BlipReader getBlipReader() {
        return StageOneProvider.this.getBlipReader();
      }

      @Override
      public ConversationNavigator getNavigator() {
        return StageOneProvider.this.getNavigator();
      }

      @Override
      public DynamicDomRenderer getDynamicRenderer() {
        return StageOneProvider.this.getDynamicRenderer();
      }

      @Override
      public ScreenPositionDomScroller getScreenPositionScroller() {
        return StageOneProvider.this.getScreenPositionScroller();
      }
    };
  }

  private DocumentRegistries.Builder installDoodads(DocumentRegistries.Builder doodads) {
    return doodads.use(new DoodadInstallers.GlobalInstaller() {

      @Override
      public void install(Registries r) {
        DiffAnnotationHandler.register(r.getAnnotationHandlerRegistry(), r.getPaintRegistry());
        DiffDeleteRenderer.register(r.getElementHandlerRegistry());
        StyleAnnotationHandler.register(r);
        LinkAnnotationHandler.register(r, createLinkAttributeAugmenter());
        SelectionAnnotationHandler.register(r, sessionId, profileManager);
        ImageThumbnail.register(r.getElementHandlerRegistry(), AttachmentManagerImpl.getInstance(),
            new ImageThumbnail.ThumbnailActionHandler() {

          @Override
          public boolean onClick(ImageThumbnailWrapper thumbnail) {
            return false;
          }
        });
      }
    });
  }

  /**
   * Starts the wave view rendering.
   * <p>
   * Subclasses may override (e.g., to use server-side rendering).
   */
  private void startRendering() {
    getDomAsViewProvider().setRenderer(getDomRenderer());

    // Initialize focus controller.
    getFocusController().upgrade(getScreenPositionScroller(), getModelAsViewProvider());

    // Render empty wave panel.
    getWavePanel().init(getDomRenderer().render(getQuasiConversationView()));

    // Create participant and tags updates renderer.
    getParticipantUpdateRenderer();
    getTagUpdateRenderer();

    // Create blip reader.
    getBlipReader();

    // Start dynamic renderer.
    if (getQuasiConversationView().getRoot() != null) {
      ScreenPosition startScreenPosition = getStartScreenPosition();
      String startBlipId = startScreenPosition != null ? startScreenPosition.getBlipId() : null;
      getDynamicRenderer().startRendering(startBlipId);
    } else {  // No conversation.
      // TODO(akaplanov) notify listener to complete stage.
    }
  }

  /**
   * Fetches and builds the core waveViewImpl state.
   *
   * @param whenReady command to execute when the waveViewImpl is built
   * @param whenError command to execute when error is happened.
   */
  private void fetchWave(final Accessor<WaveViewData> whenReady, final Accessor<ReturnStatus> whenError) {
    final Timer timer = Timing.start("StageOneProvider.fetchWave");
    WaveletId udwId = idGenerator.newUserDataWaveletId(signedInUser.getAddress());
    IdFilter filter = IdFilter.of(Collections.singleton(udwId), Collections.singleton(IdConstants.CONVERSATION_WAVELET_PREFIX));
    getViewChannel().fetchWaveView(filter, fromLastRead,
      FragmentRequester.MIN_FETCH_REPLY_SIZE, FragmentRequester.MAX_FETCH_REPLY_SIZE, FragmentRequester.MAX_FETCH_BLIPS_COUNT,
      new ViewChannel.FetchWaveViewCallback() {

      @Override
      public void onWaveViewFetch(WaveViewData waveView) {
        Timing.stop(timer);
        whenReady.use(waveView);
      }

      @Override
      public void onFailure(ReturnStatus status) {
        Timing.stop(timer);
        whenError.use(status);
      }
    });
  }

  private ScreenPosition getStartScreenPosition() {
    String startBlipId = waveRef != null ? waveRef.getDocumentId() : null;
    if (startBlipId != null) {
      return new ScreenPosition(startBlipId);
    }
    Wavelet wavelet = getWaveView().getRoot();
    WaveletId waveletId = wavelet != null ? wavelet.getId() : null;
    return getSupplementWave().getScreenPosition(waveletId);
  }

  private void initializeBlip(String blipId) {
    Iterator<? extends OpBasedWavelet> it = getWaveView().getWavelets().iterator();
    while (it.hasNext()) {
      WaveletFragmentDataImpl wavelet = (WaveletFragmentDataImpl)it.next().getWaveletData();
      if (IdUtil.isConversationalId(wavelet.getWaveletId())) {
        LazyContentBlipDataImpl blip = wavelet.getBlip(blipId);
        if (blip != null && blip.hasContent()) {
          blip.initalizeSnapshot();
        }
      }
    }
  }

  private void processDiffsOnParticipants() {
    Iterator<? extends OpBasedWavelet> it = getWaveView().getWavelets().iterator();
    while (it.hasNext()) {
      WaveletFragmentDataImpl wavelet = (WaveletFragmentDataImpl)it.next().getWaveletData();
      if (IdUtil.isConversationalId(wavelet.getWaveletId())) {
        try {
          wavelet.processParticipantsDiffs();
        } catch (OperationException ex) {
          throw new OperationRuntimeException("Participant operation applying error, wavelet "
            + wavelet.getWaveletId(), ex);
        }
      }
    }
  }

  private void processDiffsOnBlip(String blipId) {
    Iterator<? extends OpBasedWavelet> it = getWaveView().getWavelets().iterator();
    while (it.hasNext()) {
      WaveletFragmentDataImpl wavelet = (WaveletFragmentDataImpl)it.next().getWaveletData();
      if (IdUtil.isConversationalId(wavelet.getWaveletId())) {
        try {
          LazyContentBlipDataImpl blip = wavelet.getBlip(blipId);
          if (blip != null && blip.hasContent()) {
            blip.processDiffs();
          }
        } catch (OperationException ex) {
          throw new OperationRuntimeException("Blip operation applying error on " + blipId + ", wavelet "
            + wavelet.getWaveletId(), ex);
        }
      }
    }
  }

  private void showFetchError(ReturnStatus error) {
    if (error.getCode() == ReturnCode.NOT_LOGGED_IN) {
      showErrorPopup(errorMessages.notLoggedIn());
    } else if (error.getCode() == ReturnCode.NOT_AUTHORIZED) {
      showErrorPopup(errorMessages.notAuthorized());
    } else if (error.getCode() == ReturnCode.NOT_EXISTS) {
      showErrorPopup(errorMessages.noSuchWave());
    } else if (error.getCode() == ReturnCode.NOT_AUTHORIZED) {
      showErrorPopup(errorMessages.notAuthorized());
    } else {
      showErrorPopup(error.toString());
    }
  }

  private void showErrorPopup(String message) {
    loadingIndicator.hide();
    errorPopup.clear();
    errorPopup.add(new HTML(
        "<div style='color: red; padding: 5px; text-align: center;'>" + "<b>" + message +
        "</b></div>"));
    errorPopup.show();
  }

  // Getters.

  private WavePanelImpl getWavePanel() {
    if (wavePanel == null) {
      wavePanel = createWavePanel();
    }
    return wavePanel;
  }

  private FocusFramePresenterImpl getFocusFrame() {
    if (focusFramePresenter == null) {
      focusFramePresenter = createFocusFrame();
    }
    return focusFramePresenter;
  }

  private FocusFrameController getFocusController() {
    if (focusFrameController == null) {
      focusFrameController = createFocusController();
    }
    return focusFrameController;
  }

  private UpgradeableDomAsViewProvider getDomAsViewProvider() {
    if (domAsViewProvider == null) {
      domAsViewProvider = createDomAsViewProvider();
    }
    return domAsViewProvider;
  }

  private SmartScroller<? super BlipView> getSmartScroller() {
    if (smartScroller == null) {
      smartScroller = createSmartScroller();
    }
    return smartScroller;
  }

  private CssProvider getCssProvider() {
    if (cssProvider == null) {
      cssProvider = createCssProvider();
    }
    return cssProvider;
  }

  private ObservableConversationView getConversationView() {
    if (conversationView == null) {
      conversationView = createConversationView();
    }
    return conversationView;
  }

  private ViewIdMapper getViewIdMapper() {
    if (viewIdMapper == null) {
      viewIdMapper = createViewIdMapper();
    }
    return viewIdMapper;
  }

  private ShallowBlipRenderer getShallowBlipRenderer() {
    if (shallowBlipRenderer == null) {
      shallowBlipRenderer = createShallowBlipRenderer();
    }
    return shallowBlipRenderer;
  }

  private RenderingRules<UiBuilder> getRenderingRules() {
    if (renderingRules == null) {
      renderingRules = createRenderingRules();
    }
    return renderingRules;
  }

  private DomRenderer getDomRenderer() {
    if (domRenderer == null) {
      domRenderer = createDomRenderer();
    }
    return domRenderer;
  }

  private ThreadReadStateMonitor getThreadReadStateMonitor() {
    if (threadReadStateMonitor == null) {
      threadReadStateMonitor = createThreadReadStateMonitor();
    }
    return threadReadStateMonitor;
  }

  private WaveletOperationContext.Factory getOperationContextFactory() {
    if (operationContextFactory == null) {
      operationContextFactory = createOperationContextFactory();
    }
    return operationContextFactory;
  }

  private WaveletOperationalizer getWaveletOperationalizer() {
    if (waveletOperationalizer == null) {
      waveletOperationalizer = createWaveletOperationalizer();
    }
    return waveletOperationalizer;
  }

  private WaveViewData getWaveData() {
    Preconditions.checkState(waveViewData != null, "wave not ready");
    return waveViewData;
  }

  private ObservablePrimitiveSupplement getSupplement() {
    if (supplement == null) {
      supplement = createSupplement();
    }
    return supplement;
  }

  private StartVersionHelper getLookVersionHelper() {
    if (lookVersionHelper == null) {
      lookVersionHelper = createLookVersionHelper();
    }
    return lookVersionHelper;
  }

  private FragmentRequesterImpl getFragmentRequester() {
    if (fragmentRequester == null) {
      fragmentRequester = createFragmentRequester();
    }
    return fragmentRequester;
  }

  private ReplyManager getReplyManager() {
    if (replyManager == null) {
      replyManager = createReplyManager();
    }
    return replyManager;
  }

  private ElementDomRenderer getElementRenderer() {
    if (elementRenderer == null) {
      elementRenderer = createElementRenderer();
    }
    return elementRenderer;
  }

  private ElementDomMeasurer getElementMeasurer() {
    if (elementMeasurer == null) {
      elementMeasurer = createElementMeasurer();
    }
    return elementMeasurer;
  }

  private WaveViewService getWaveViewService() {
    if (waveViewService == null) {
      waveViewService = createWaveViewService();
    }
    return waveViewService;
  }

  private SchemaProvider getSchemaProvider() {
    if (schemaProvider == null) {
      schemaProvider = createSchemaProvider();
    }
    return schemaProvider;
  }

  private BlipDocumentRendererImpl getBlipDocumentRenderer() {
    if (blipDocumentRenderer == null) {
      blipDocumentRenderer = createBlipDocumentRenderer();
    }
    return blipDocumentRenderer;
  }

  private final ModelAsViewProvider getModelAsViewProvider() {
    if (modelAsViewProvider == null) {
      modelAsViewProvider = createModelAsViewProvider();
    }
    return modelAsViewProvider;
  }

  private ContactManager getContactManager() {
    if (contactManager == null) {
      contactManager = createContactManager();
    }
    return contactManager;
  }

  private ViewChannel getViewChannel() {
    if (viewChannel == null) {
      viewChannel = createViewChannel();
    }
    return viewChannel;
  }

  private final WaveViewImpl<OpBasedWavelet> getWaveView() {
    if (waveView == null) {
      waveView = createWaveView();
    }
    return waveView;
  }

  private final ObservableQuasiConversationView getQuasiConversationView() {
    if (quasiConversationView == null) {
      quasiConversationView = createQuasiConversationView();
    }
    return quasiConversationView;
  }

  private final LocalSupplementedWave getSupplementWave() {
    if (localSupplementedWave == null) {
      localSupplementedWave = createSupplementWave();
    }
    return localSupplementedWave;
  }

  private final BlipReadStateMonitor getBlipReadStateMonitor() {
    if (blipReadStateMonitor == null) {
      blipReadStateMonitor = createBlipReadStateMonitor();
    }
    return blipReadStateMonitor;
  }

  private final InboxStateMonitor getInboxStateMonitor() {
    if (inboxStateMonitor == null) {
      inboxStateMonitor = createInboxStateMonitor();
    }
    return inboxStateMonitor;
  }

  private final WaveDocuments<DiffContentDocument> getWaveDocuments() {
    if (waveDocuments == null) {
      waveDocuments = createWaveDocuments();
    }
    return waveDocuments;
  }

  private final BlipReader getBlipReader() {
    if (blipReader == null) {
      blipReader = createBlipReader();
    }
    return blipReader;
  }

  private ConversationNavigator getNavigator() {
    if (navigator == null) {
      navigator = createNavigator();
    }
    return navigator;
  }

  private LiveProfileRenderer getLiveProfileRenderer() {
    if (liveProfileRenderer == null) {
      liveProfileRenderer = createLiveProfileRenderer();
    }
    return liveProfileRenderer;
  }

  private ParticipantUpdateRenderer getParticipantUpdateRenderer() {
    if (participantUpdateRenderer == null) {
      participantUpdateRenderer = createParticipantUpdateRenderer();
    }
    return participantUpdateRenderer;
  }

  private TagUpdateRenderer getTagUpdateRenderer() {
    if (tagUpdateRenderer == null) {
      tagUpdateRenderer = createTagUpdateRenderer();
    }
    return tagUpdateRenderer;
  }

  private DynamicDomRenderer getDynamicRenderer() {
    if (dynamicRenderer == null) {
      dynamicRenderer = createDynamicRenderer();
    }
    return dynamicRenderer;
  }

  private DocumentRegistries getDocumentRegistries() {
    if (documentRegistries == null) {
      documentRegistries = createDocumentRegistries();
    }
    return documentRegistries;
  }

  private ScreenPositionDomScroller getScreenPositionScroller() {
    if (screenPositionScroller == null) {
      screenPositionScroller = createScreenPositionScroller();
    }
    return screenPositionScroller;
  }

  // Creaters.

  private LinkAnnotationHandler.LinkAttributeAugmenter createLinkAttributeAugmenter() {
    return new LinkAnnotationHandler.LinkAttributeAugmenter() {

      @Override
      public Map<String, String> augment(Map<String, Object> annotations, boolean isEditing,
          Map<String, String> current) {
        return current;
      }
    };
  }

  private ModelAsViewProvider createModelAsViewProvider() {
    return new ModelAsViewProviderImpl(getViewIdMapper(), getDomAsViewProvider());
  }

  /** @return the id mangler for model objects. Subclasses may override. */
  private ModelIdMapper createModelIdMapper() {
    return ModelIdMapperImpl.create(getQuasiConversationView(), "UC");
  }

  /** @return the id mangler for view objects. Subclasses may override. */
  private ViewIdMapper createViewIdMapper() {
    return new ViewIdMapper(createModelIdMapper());
  }

  private WaveletOperationContext.Factory createOperationContextFactory() {
    return new BasicWaveletOperationContextFactory(signedInUser);
  }

  private WaveletOperationalizer createWaveletOperationalizer() {
    return WaveletOperationalizer.create(getWaveData().getWaveId(), getOperationContextFactory());
  }

  private WaveViewImpl<OpBasedWavelet> createWaveView() {
    WaveViewData snapshot = getWaveData();
    // The operationalizer makes the waveletOperationalizer function via operation control.
    // The hookup with concurrency-control and remote operation streams occurs
    // later in createUpgrader().
    final WaveletOperationalizer operationalizer = getWaveletOperationalizer();
    final IdURIEncoderDecoder uriCodec = new IdURIEncoderDecoder(new ClientPercentEncoderDecoder());

    final HashedVersionFactory hashFactory = new HashedVersionZeroFactoryImpl(uriCodec);
    WaveViewImpl.WaveletFactory<OpBasedWavelet> waveletFactory =
        new WaveViewImpl.WaveletFactory<OpBasedWavelet>() {

      @Override
      public OpBasedWavelet create(WaveId waveId, WaveletId id, ParticipantId creator) {
        HashedVersion v0 = hashFactory.createVersionZero(WaveletName.of(waveId, id));
        long now = System.currentTimeMillis();
        ObservableWaveletFragmentData data = new WaveletFragmentDataImpl(id, creator, now, v0, now, waveId,
            getWaveDocuments());
        return operationalizer.operationalize(data);
      }
    };

    WaveViewImpl<OpBasedWavelet> waveViewImpl = WaveViewImpl.create(
        waveletFactory, getWaveData().getWaveId(), idGenerator, signedInUser,
        WaveViewImpl.WaveletConfigurator.ADD_CREATOR);

    // Populate the initial state.
    for (ObservableWaveletData waveletData : snapshot.getWavelets()) {
      waveViewImpl.addWavelet(operationalizer.operationalize(
          (ObservableWaveletFragmentData) waveletData));
    }
    return waveViewImpl;
  }

  /** @return the base conversation view. Subclasses may override. */
  private ObservableConversationView createConversationView() {
    return WaveBasedConversationView.create(getWaveView(), idGenerator);
  }

  /** @return the quasi conversation view. Subclasses may override. */
  private ObservableQuasiConversationView createQuasiConversationView() {
    return QuasiConversationViewImpl.create(getFocusFrame());
  }

  /** @return the user ObservablePrimitiveSupplement. Subclasses may override. */
  private ObservablePrimitiveSupplement createSupplement() {
    Wavelet udw = getWaveView().getUserData();
    if (udw == null) {
      udw = getWaveView().createUserData();
    }
    return WaveletBasedSupplement.create(udw);
  }

  /** @return the user localSupplementedWave createDocument the waveViewImpl. Subclasses may override. */
  private LocalSupplementedWave createSupplementWave() {
    ObservablePrimitiveSupplement state = getSupplement();
    ObservableSupplementedWave live = new LiveSupplementedWaveImpl(state, getWaveView(),
        signedInUser, SupplementedWaveImpl.DefaultFollow.ALWAYS, getQuasiConversationView());
    return LocalSupplementedWaveImpl.create(getWaveView(), live);
  }

  /** @return a localSupplementedWave to the localSupplementedWave, to getDocument exact read/unread counts. */
  private BlipReadStateMonitor createBlipReadStateMonitor() {
    return BlipReadStateMonitorImpl.create(getSupplementWave(), getQuasiConversationView());
  }

  /** @return a localSupplementedWave to the localSupplementedWave, to getDocument inbox state. */
  private InboxStateMonitor createInboxStateMonitor() {
    return InboxStateMonitorImpl.create(getWaveView().getWaveId(), getSupplementWave());
  }

  /** @return the registry createDocument documents in the waveViewImpl.
   * Subclasses may override.
   */
  private WaveDocuments<DiffContentDocument> createWaveDocuments() {
    IndexedDocumentImpl.performValidation = false;

    DocumentFactory<DiffContentDocument> blipDocumentFactory =
        new DocumentFactory<DiffContentDocument>() {

      @Override
      public DiffContentDocument create(final WaveletId waveletId, final String documentId,
          DocInitialization content) {
        Timer timer = Timing.start("create DiffContentDocument");
        try {
          ContentDocument core = new ContentDocument(DocumentSchema.NO_SCHEMA_CONSTRAINTS);
          core.setRegistries(RegistriesHolder.get());
          core.consume(content);
          return DiffContentDocument.create(core, getQuasiConversationView().getRoot());
        } finally {
          Timing.stop(timer);
        }
      }
    };

    DocumentFactory<?> dataDocumentFactory =
        ObservablePluggableMutableDocument.createFactory(getSchemaProvider());

    return WaveDocuments.create(blipDocumentFactory, dataDocumentFactory);
  }

  /** @return the wave panel. Subclasses may override. */
  private WavePanelImpl createWavePanel() {
    return WavePanelImpl.create(getDomAsViewProvider(), waveElement, waveHolder);
  }

  /** @return the focusManager feature. Subclasses may override. */
  private FocusFramePresenterImpl createFocusFrame() {
    return FocusFramePresenterImpl.install(getWavePanel(), getSmartScroller());
  }

  private FocusFrameController createFocusController() {
    return FocusFrameController.create(getFocusFrame(), getWavePanel());
  }

  /** @return the collapse feature. Subclasses may override. */
  private CollapsePresenter createCollapsePresenter() {
    return new CollapsePresenter();
  }

  /** @return the interpreter of DOM elements as semantic domAsViewProvider. */
  private UpgradeableDomAsViewProvider createDomAsViewProvider() {
    return FullStructure.create(getCssProvider());
  }

  /** @return smart scroller. May be overridden. */
  private SmartScroller<? super BlipView> createSmartScroller() {
    return SmartScroller.install(getWavePanel());
  }

  /** @return the source of CSS rules to apply in domAsViewProvider. */
  private CssProvider createCssProvider() {
    return WavePanelResourceLoader.createCssProvider();
  }

  /** @return look version helper. Subclasses may override. */
  private StartVersionHelper createLookVersionHelper() {
    Set<WaveletId> waveletIds = CollectionUtils.newHashSet();
    for (OpBasedWavelet wavelet : getWaveView().getWavelets()) {
      waveletIds.add(wavelet.getId());
    }
    return new StartVersionHelper(new SupplementImpl(getSupplement()));
  }

  /** @return fragment requester. Subclasses may override. */
  private FragmentRequesterImpl createFragmentRequester() {
    fragmentRequester = new FragmentRequesterImpl(getViewChannel(), getWaveView(), getLookVersionHelper());
    webSocket.addListener(fragmentRequester.getConnectionListener());
    return fragmentRequester;
  }

  /** @return view channel provider. Subclasses may override. */
  private ViewChannel createViewChannel() {
    LoggerBundle viewLogger = new DomLogger("view");
    return new ViewChannelImpl(waveRef.getWaveId(), getWaveViewService(), new ViewChannel.IndexingCallback() {

      @Override
      public void onIndexing(long totalVersions, long indexedVersions) {
        int percent = (int)(indexedVersions*100/totalVersions);
        loadingIndicator.showIndexing(percent);
      }

      @Override
      public void onIndexingComplete() {
        loadingIndicator.hideIndexing();
      }
    }, viewLogger);
  }

  /** @return the manager createDocument user contacts. Subclasses may override. */
  private ContactManager createContactManager() {
    return new RemoteContactManagerImpl();
  }

  /** @return the domRenderer createDocument intrinsic blip state. Subclasses may override. */
  private ShallowBlipRenderer createShallowBlipRenderer() {
    return new UndercurrentShallowBlipRenderer(profileManager, getSupplementWave(), getNavigator());
  }

  /** @return the thread state monitor. Subclasses may override. */
  private ThreadReadStateMonitor createThreadReadStateMonitor() {
    return ThreadReadStateMonitorImpl.create(getSupplementWave(), getQuasiConversationView());
  }

  /** @return the blip document renderer. */
  private BlipDocumentRendererImpl createBlipDocumentRenderer() {
    return BlipDocumentRendererImpl.create(getDomAsViewProvider(), getModelAsViewProvider(),
      getDocumentRegistries(), getWavePanel().getGwtPanel());
  }

  private DocumentRegistries createDocumentRegistries() {
    // Add all doodads here.
    return installDoodads(DocumentRegistries.builder()) // \u2620
        .use(InlineAnchorLiveRenderer.installer(getViewIdMapper(), getReplyManager(),
            getDomAsViewProvider()))
        .use(Gadget.install(profileManager, getSupplementWave(), signedInUser) )
        .build();
  }

  private ViewFactory createViewFactories() {
    return ViewFactories.FIXED;
  }

  private RenderingRules<UiBuilder> createRenderingRules() {
    return new FullDomRenderer(profileManager, getViewIdMapper(), createViewFactories());
  }

  private DomRenderer createDomRenderer() {
    ReductionBasedRenderer reductionBasedRenderer = ReductionBasedRenderer.of(getRenderingRules(),
        getQuasiConversationView());
    return HtmlDomRenderer.create(reductionBasedRenderer);
  }

  private BlipReader createBlipReader() {
    return BlipReader.create(getSupplementWave(), getNavigator(), getFocusFrame(),
        getDynamicRenderer(), getQuasiConversationView());
  }

  private ReplyManager createReplyManager() {
    return new ReplyManager(getModelAsViewProvider());
  }

  private ConversationNavigator createNavigator() {
    return ConversationNavigator.create(getQuasiConversationView());
  }

  private LiveProfileRenderer createLiveProfileRenderer() {
    return LiveProfileRenderer.create(profileManager,
        getModelAsViewProvider(), getShallowBlipRenderer());
  }

  private ParticipantUpdateRenderer createParticipantUpdateRenderer() {
    return ParticipantUpdateRenderer.create(getModelAsViewProvider(),
      getLiveProfileRenderer(), getQuasiConversationView());
  }

  private TagUpdateRenderer createTagUpdateRenderer() {
    TagUpdateRenderer.TagReader tagReader = new TagUpdateRenderer.TagReader() {

      @Override
      public boolean wasTagsEverRead() {
        return getSupplementWave().wasTagsEverRead(getWaveView().getRoot().getId());
      }
    };
    return TagUpdateRenderer.create(getModelAsViewProvider(), tagReader, getQuasiConversationView());
  }

  private DynamicDomRenderer createDynamicRenderer() {
    LiveSupplementRenderer supplementRenderer = LiveSupplementRenderer.create(getSupplementWave(),
        getModelAsViewProvider(), getThreadReadStateMonitor(), getNavigator());

    DynamicDomRenderer renderer = DynamicDomRenderer.create(getModelAsViewProvider(),
        getShallowBlipRenderer(), getFragmentRequester(), getBlipDocumentRenderer(),
        getNavigator(), getLiveProfileRenderer(), supplementRenderer, getElementRenderer(),
        getElementMeasurer());

    renderer.init(getQuasiConversationView());

    Wavelet wavelet = getWaveView().getRoot();
    WaveletId waveletId = wavelet != null ? wavelet.getId() : null;

    FocusFramePresenterImpl focus = getFocusFrame();
    focus.upgrade(getModelAsViewProvider(), getQuasiConversationView(), getNavigator(),
        getSupplementWave(), waveletId, getSupplementWave().getFocusedBlipId(waveletId), renderer);
    renderer.addListener(focus.getRendererListener());

    return renderer;
  }

  private ScreenPositionDomScroller createScreenPositionScroller() {
    ScreenPositionDomScroller spds = ScreenPositionDomScroller.create(getDynamicRenderer(),
        getSmartScroller(), getModelAsViewProvider(), new DomScrollerImpl(),
        getElementMeasurer());
    spds.initialize(getStartScreenPosition(), getQuasiConversationView());
    return spds;
  }

  private ElementDomRenderer createElementRenderer() {
    return new ElementDomRenderer(getModelAsViewProvider(), getDomAsViewProvider(),
        getReplyManager(), getElementMeasurer());
  }

  private ElementDomMeasurer createElementMeasurer() {
    return new ElementDomMeasurer();
  }

  private SchemaProvider createSchemaProvider() {
    return new ConversationSchemas();
  }

  private WaveViewService createWaveViewService() {
    return new RemoteWaveViewService(
        waveRef.getWaveId(), webSocket, getWaveDocuments());
  }
}

