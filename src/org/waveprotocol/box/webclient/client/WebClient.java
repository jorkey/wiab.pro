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

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.OptionElement;
import com.google.gwt.dom.client.SelectElement;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.http.client.UrlBuilder;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Window.ClosingEvent;
import com.google.gwt.user.client.Window.Location;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SplitLayoutPanel;
import com.google.gwt.user.client.ui.UIObject;
import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.webclient.client.events.SavingDataEvent;
import org.waveprotocol.box.webclient.client.events.SavingDataEventHandler;
import org.waveprotocol.box.webclient.client.events.SearchesModifyEvent;
import org.waveprotocol.box.webclient.client.events.WaveCreationEvent;
import org.waveprotocol.box.webclient.client.events.WaveCreationEventHandler;
import org.waveprotocol.box.webclient.client.events.WaveSelectionEvent;
import org.waveprotocol.box.webclient.client.events.WaveSelectionEventHandler;
import org.waveprotocol.box.webclient.client.i18n.WebClientMessages;
import org.waveprotocol.box.webclient.profile.RemoteProfileManagerImpl;
import org.waveprotocol.box.webclient.search.RemoteSearchService;
import org.waveprotocol.box.webclient.search.RemoteSearchesService;
import org.waveprotocol.box.webclient.search.Search;
import org.waveprotocol.box.webclient.search.SearchPanelRenderer;
import org.waveprotocol.box.webclient.search.SearchPanelWidget;
import org.waveprotocol.box.webclient.search.SearchPresenter;
import org.waveprotocol.box.webclient.search.SearchesService;
import org.waveprotocol.box.webclient.search.SimpleSearch;
import org.waveprotocol.box.webclient.search.WaveStore;
import org.waveprotocol.box.stat.Timing;
import org.waveprotocol.box.webclient.stat.SingleThreadedRequestScope;
import org.waveprotocol.box.webclient.stat.gwtevent.GwtStatisticsEventSystem;
import org.waveprotocol.box.webclient.stat.gwtevent.GwtStatisticsHandler;
import org.waveprotocol.box.webclient.widget.frame.FramedPanel;
import org.waveprotocol.box.webclient.widget.loading.LoadingIndicator;
import org.waveprotocol.box.webclient.widget.update.UpdateIndicatorWidget;
import org.waveprotocol.wave.client.StageThree;
import org.waveprotocol.wave.client.account.ProfileManager;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.client.debug.logger.LogLevel;
import org.waveprotocol.wave.client.scheduler.Scheduler.Task;
import org.waveprotocol.wave.client.scheduler.SchedulerInstance;
import org.waveprotocol.wave.client.wavepanel.event.EventDispatcherPanel;
import org.waveprotocol.wave.client.wavepanel.event.FocusManager;
import org.waveprotocol.wave.client.wavepanel.event.WaveChangeHandler;
import org.waveprotocol.wave.client.wavepanel.impl.edit.EditSession;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomUtil;
import org.waveprotocol.wave.client.widget.common.ImplPanel;
import org.waveprotocol.wave.client.widget.dialog.Dialog;
import org.waveprotocol.wave.client.widget.dialog.DialogActivator;
import org.waveprotocol.wave.client.widget.dialog.DialogBox;
import org.waveprotocol.wave.client.widget.dialog.DialogButton;
import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.RelativePopupPositioner;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.waveref.InvalidWaveRefException;
import org.waveprotocol.wave.model.waveref.WaveRef;
import org.waveprotocol.wave.util.escapers.GwtWaverefEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class WebClient implements EntryPoint {

  interface Binder extends UiBinder<DockLayoutPanel, WebClient> {}

  interface Style extends CssResource {}

/**
 * Data for opening the wave.
 */
  private class OpenWaveData {

    private final WaveRef waveRef;
    private final boolean newWave;
    private final Set<ParticipantId> participants;
    private final boolean fromLastRead;

    public OpenWaveData(WaveRef waveRef, boolean newWave, Set<ParticipantId> participants,
        boolean fromLastRead) {
      this.waveRef = waveRef;
      this.newWave = newWave;
      this.participants = participants;
      this.fromLastRead = fromLastRead;
    }

    public WaveRef getWaveRef() {
      return waveRef;
    }

    public boolean isNewWave() {
      return newWave;
    }

    public Set<ParticipantId> getParticipants() {
      return participants;
    }

    public boolean isFromLastRead() {
      return fromLastRead;
    }

    @Override
    public String toString() {
      return "waveRef: " + waveRef + ", " + "newWave: " + newWave + ", " +
          "participants: " + participants + ", " + "withDiffs: " + fromLastRead;
    }
  }

  private static final int SAVING_DATA_INDICATE_DELAY_MS = 5 * 1000;
  private static final int NOT_SAVED_DATA_INDICATE_DELAY_MS = 5 * 1000;

  /** Resources. */
  private static final Binder BINDER = GWT.create(Binder.class);
  private static final WebClientMessages messages = GWT.create(WebClientMessages.class);

  static LoggerBundle LOG = new DomLogger("client");

  private final ProfileManager profiles = new RemoteProfileManagerImpl();

  private LoadingIndicator loadingIndicator;

  private DialogActivator savingDataActivator;

  /** Not saved data indicator. */
  private DialogActivator notSavedDialogActivator;
  private final DialogButton notSavedDialogYes = new DialogButton(messages.yes());
  private final DialogButton notSavedDialogNo = new DialogButton(messages.no());

  private final SearchPanelRenderer searchPanelRenderer = new SearchPanelRenderer(profiles);

  /**
   * Inner structure.
   */

  @UiField
  SplitLayoutPanel splitPanel;

  @UiField
  Style style;

  @UiField
  FramedPanel waveFrame;

  @UiField
  ImplPanel waveHolder;

  @UiField(provided = true)
  final SearchPanelWidget searchPanel = new SearchPanelWidget(searchPanelRenderer);

  @UiField
  HTMLPanel logPanel;

  /** Element IDs. */
  public static final String APP_ELEMENT_ID = "app";
  public static final String SEARCH_ELEMENT_ID = "search";
  public static final String WAVE_ELEMENT_ID = "wave";
  public static final String LANG_ELEMENT_ID = "lang";
  public static final String NETSTATUS_ELEMENT_ID = "netstatus";
  public static final String SIGNOUT_ELEMENT_ID = "signout";
  public static final String ADD_PARTICIPANT_BUTTON_ID = "btnAddParticipant";
  public static final String ADD_TAG_BUTTON_ID = "btnAddTag";

  /** URL builder parameters. */
  private static final String LOCALE_URLBUILDER_PARAMETER = "locale";
  private static final String DEFAULT_LOCALE = "default";

  /** Net status element class names. */
  private static final String ONLINE_NETSTATUS_CLASSNAME = "online";
  private static final String OFFLINE_NETSTATUS_CLASSNAME = "offline";
  private static final String CONNECTING_NETSTATUS_CLASSNAME = "connecting";

  /** The glass panel, is shown above the whole page during waiting mode. */
  private Element glassPanel;

  /** Error popup panel. */
  private UniversalPopup errorPopup;

  /** The wave panel, if a wave is open. */
  private StagesProvider wave;
  private WaveRef waveRef;

  private final WaveStore waveStore = new SimpleWaveStore();

  /** A remote web socket to connect to the server. */
  private WaveWebSocketClient websocket;

  private ClientVersionChecker versionChecker;

  private IdGenerator idGenerator;
  private boolean savingInProcess = false;
  private final Set<WaveletId> savingDataWavelets = new HashSet<>();
  private final LocaleService localeService = new RemoteLocaleService();
  private SearchPresenter searchPresenter;

  /**
   * This is the entry point method.
   */
  @Override
  public void onModuleLoad() {
    if (LOG.trace().shouldLog()) {
      LOG.trace().log("WebClient.onModuleLoad() started");
    }

    ErrorHandler.install();

    setupWaveCreationHandler();

    setupLocaleSelect();
    setupSavingDataIndicator();
    setupWindowClosingHandler();

    HistorySupport.init(new HistoryProviderDefault());
    HistoryChangeListener.init();

    setupConnection();

    setupVersionChecker();
    setupRootPanel();
    setupLogPanel();
    setupSearchPanel();
    setupWavePanel();
    setupGlassPanel();
    setupErrorPopup();
    setupFocusManager();
    setupStatistics();

    History.fireCurrentHistoryState();

    if (LOG.trace().shouldLog()) {
      LOG.trace().log("WebClient.onModuleLoad() finished");
    }

  }

  private void setupWaveCreationHandler() {
    ClientEvents.get().addWaveCreationEventHandler(new WaveCreationEventHandler() {

      @Override
      public void onCreateRequest(WaveCreationEvent event, Set<ParticipantId> participants) {
        if (LOG.trace().shouldLog()) {
          LOG.trace().log("WaveCreationEvent received");
        }

        if (!Session.get().isLoggedIn()) {
          throw new RuntimeException("Spaghetti attack. Create occured before login");
        }

        closeAndOpenWave(new OpenWaveData(
            WaveRef.of(idGenerator.newWaveId()), true, participants, false));
      }
    });
  }

  private void setupLocaleSelect() {
    final SelectElement select = (SelectElement) Document.get().getElementById(LANG_ELEMENT_ID);
    String currentLocale = LocaleInfo.getCurrentLocale().getLocaleName();
    String[] localeNames = LocaleInfo.getAvailableLocaleNames();
    for (String locale : localeNames) {
      if (!DEFAULT_LOCALE.equals(locale)) {
        String displayName = LocaleInfo.getLocaleNativeDisplayName(locale);
        OptionElement option = Document.get().createOptionElement();
        option.setValue(locale);
        option.setText(displayName);
        select.add(option, null);
        if (locale.equals(currentLocale)) {
          select.setSelectedIndex(select.getLength() - 1);
        }
      }
    }

    EventDispatcherPanel.of(select).registerChangeHandler(null, new WaveChangeHandler() {

      @Override
      public boolean onChange(ChangeEvent event, Element context) {
        UrlBuilder builder = Location.createUrlBuilder().setParameter(
            LOCALE_URLBUILDER_PARAMETER, select.getValue());
        Window.Location.replace(builder.buildString());
        localeService.storeLocale(select.getValue());
        return true;
      }
    });
  }

  private void setupConnection() {
    final Element element = Document.get().getElementById(NETSTATUS_ELEMENT_ID);

    websocket = new WaveWebSocketClient(websocketNotAvailable(), getWebSocketBaseUrl());
    websocket.addListener(new WaveWebSocketClient.ConnectionListener() {

      @Override
      public void onConnecting() {
        element.setInnerText(messages.connecting());
        element.setClassName(CONNECTING_NETSTATUS_CLASSNAME);
      }

      @Override
      public void onConnected() {
        element.setInnerText(messages.online());
        element.setClassName(ONLINE_NETSTATUS_CLASSNAME);
        versionChecker.checkClientUpdated();
      }

      @Override
      public void onDisconnected() {
        element.setInnerText(messages.offline());
        element.setClassName(OFFLINE_NETSTATUS_CLASSNAME);
      }

      @Override
      public void onFinished(String error) {
        showErrorPopup(messages.fatalServerError());
      }
    });

    websocket.connect();
    idGenerator = ClientIdGenerator.create();
  }

  private void setupVersionChecker() {
    versionChecker = new ClientVersionChecker(new ClientVersionChecker.Listener() {

      @Override
      public void onClientUpdated() {
        UpdateIndicatorWidget.create(RootPanel.get("banner"), new UpdateIndicatorWidget.Listener() {

          @Override
          public void refresh() {
            completeWave(new Command() {

              @Override
              public void execute() {
                Location.assign(GWT.getHostPageBaseURL());
              }
            });
          }
        });
      }
    }, LOG);
  }

  private void setupSavingDataIndicator() {
    ClientEvents.get().addSavingDataEventHandler(new SavingDataEventHandler() {

      @Override
      public void onSavingData(SavingDataEvent event) {
        if (LOG.trace().shouldLog()) {
          LOG.trace().log("onSavingData(" + event + ")");
        }

        if (event.isInProcess()) {
          savingDataWavelets.add(event.getWaveletId());
        } else {
          savingDataWavelets.remove(event.getWaveletId());
        }

        if (!savingDataWavelets.isEmpty()) {
          if (!savingInProcess) {
            savingInProcess = true;
            getSavingDataActivator().start(SAVING_DATA_INDICATE_DELAY_MS);
          }
        } else {
          if (savingInProcess) {
            savingInProcess = false;
            getSavingDataActivator().submit();
            getNotSavedDialogActivator().submit(notSavedDialogYes);
          }
        }
      }
    });
  }

  private void setupWindowClosingHandler() {
    Window.addWindowClosingHandler(new Window.ClosingHandler() {

      @Override
      public void onWindowClosing(ClosingEvent event) {
        if (savingInProcess) {
          event.setMessage("Failed to save your last changes.");
        }
      }
    });
  }

  private void setupRootPanel() {
    DockLayoutPanel self = BINDER.createAndBindUi(this);
    RootPanel.get(APP_ELEMENT_ID).add(self);
    // DockLayoutPanel forcibly conflicts with sensible layout control, and
    // sticks inline styles on elements without permission. They must be
    // cleared.
    self.getElement().getStyle().clearPosition();
  }

  private void setupLogPanel() {
    if (LogLevel.showDebug()) {
      DomLogger.enable(logPanel.getElement());
    } else {
      logPanel.removeFromParent();
    }
  }

  private void setupSearchPanel() {
    // On wave action fire an event.
    SearchPresenter.WaveActionHandler actionHandler = new SearchPresenter.WaveActionHandler() {

      @Override
      public void onCreateWave() {
        ClientEvents.get().fireEvent(new WaveCreationEvent());
      }

      @Override
      public void onWaveSelected(WaveId id, boolean ctrlDown, boolean altDown) {
        ClientEvents.get().fireEvent(
            new WaveSelectionEvent(WaveRef.of(id), ctrlDown, altDown));
      }
    };

    SearchPresenter.SearchesModifyHandler searchesModifyHandler =
        new SearchPresenter.SearchesModifyHandler() {

      @Override
      public void onSearchesModify(List<SearchesItem> searches) {
        ClientEvents.get().fireEvent(new SearchesModifyEvent(searches));
      }
    };

    Search search = SimpleSearch.create(RemoteSearchService.create(), waveStore);
    search.addListener(searchPanelRenderer);
    SearchesService searchesService = new RemoteSearchesService();
    searchPresenter = SearchPresenter.create(search, searchesService,
        searchPanel, actionHandler, searchesModifyHandler);

    searchPanel.getElement().setId(SEARCH_ELEMENT_ID);
    splitPanel.setWidgetMinSize(searchPanel, 300);
  }

  private void setupWavePanel() {
    // Hide the frame until waves start getting opened
    UIObject.setVisible(waveFrame.getElement(), false);

    Document.get().getElementById(SIGNOUT_ELEMENT_ID).setInnerText(messages.signout());

    // Handles opening waves.
    ClientEvents.get().addWaveSelectionEventHandler(new WaveSelectionEventHandler() {

      @Override
      public void onSelection(WaveRef waveRef, boolean ctrlDown, boolean altDown) {
        // Ctrl or Alt isn't pressed => open wave with diffs
        closeAndOpenWave(new OpenWaveData(waveRef, false, null, !ctrlDown && !altDown));
      }
    });

    waveFrame.getElement().setId(WAVE_ELEMENT_ID);
  }

  private void setupGlassPanel() {
    glassPanel = Document.get().createDivElement();
    glassPanel.setClassName(Dialog.getCss().glassPanel());
  }

  private void setupErrorPopup() {
    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    errorPopup = PopupFactory.createPopup(waveHolder.getElement(),
        new CenterPopupPositioner(), chrome, true);
  }

  private void showErrorPopup(String message) {
    errorPopup.clear();
    errorPopup.add(new HTML(
        "<div style='color: red; padding: 5px; text-align: center;'>" + "<b>" + message +
        "</b></div>"));
    errorPopup.show();
  }

  private void setupFocusManager() {
    FocusManager.init();
  }

  private void setupStatistics() {
    Timing.setScope(new SingleThreadedRequestScope());
    Timing.setEnabled(true);
    GwtStatisticsEventSystem eventSystem = new GwtStatisticsEventSystem();
    eventSystem.addListener(new GwtStatisticsHandler(), true);
    eventSystem.enable(true);
  }

  private LoadingIndicator getLoadingIndicator() {
    if (loadingIndicator == null) {
      loadingIndicator = new LoadingIndicator(waveHolder.getElement());
    }
    return loadingIndicator;
  }

  /**
   * Creates a popup that informs about data saving.
   */
  private DialogActivator getSavingDataActivator() {
    if (savingDataActivator == null) {
      UniversalPopup popup = PopupFactory.createPopup(null, new RelativePopupPositioner() {

        @Override
        public void setPopupPositionAndMakeVisible(Element relative, Element popup) {
          com.google.gwt.dom.client.Style popupStyle = popup.getStyle();
          popupStyle.setTop(5, Unit.PX);
          popupStyle.setLeft((RootPanel.get().getOffsetWidth()-
              popup.getOffsetWidth())/2, Unit.PX);
          popupStyle.setPosition(Position.FIXED);
          popupStyle.setVisibility(Visibility.VISIBLE);
        }
      }, PopupChromeFactory.createPopupChrome(), false);

      Label savingLabel = new Label(messages.savingData());
      savingLabel.setStyleName(Dialog.getCss().warningLabel());
      popup.add(savingLabel);

      savingDataActivator = new DialogActivator(popup);
    }
    return savingDataActivator;
  }

  /**
   * Creates a dialogBox that informs about not saved data.
   */
  private DialogActivator getNotSavedDialogActivator() {
    if (notSavedDialogActivator == null) {
      PopupChrome chrome = PopupChromeFactory.createPopupChrome();
      UniversalPopup popup = PopupFactory.createPopup(Document.get().getElementById(APP_ELEMENT_ID),
          new CenterPopupPositioner(), chrome, false);

      Label messageLabel = new Label(messages.changesNotSavedMessage());
      messageLabel.setStyleName(Dialog.getCss().infoLabel());

      DialogBox.create(popup, messages.changesNotSavedTitle(), messageLabel,
          new DialogButton[] { notSavedDialogNo, notSavedDialogYes });

      notSavedDialogActivator = new DialogActivator(popup);
    }
    return notSavedDialogActivator;
  }

  /**
   * Returns <code>ws(s)://yourhost[:port]/</code>.
   */
  // XXX check formatting wrt GPE
  private native String getWebSocketBaseUrl()
      /*-{return ((window.location.protocol == "https:") ? "wss" : "ws") + "://" +  $wnd.__websocket_address + "/";}-*/;

  private native boolean websocketNotAvailable()
      /*-{ return !window.WebSocket }-*/;

  /**
   * Opening a new wave in a wave panel.
   * @param openWaveData - data for opening the new wave
   */
  private void openWave(OpenWaveData openWaveData) {
    if (LOG.trace().shouldLog()) {
      LOG.trace().log("WebClient.openWave()");
    }
    final Timer timer = Timing.startRequest("Open Wave");

    // Release the display:none.
    UIObject.setVisible(waveFrame.getElement(), true);
    Element waveHolderElement = waveHolder.getElement();
    getLoadingIndicator().showLoading();

    Element waveElement = waveHolderElement.appendChild(Document.get().createDivElement());
    this.wave = new StagesProvider(waveElement, waveFrame, waveHolder, openWaveData.getWaveRef(),
        websocket, idGenerator, profiles, waveStore, openWaveData.isNewWave(), Session.get().getDomain(),
        openWaveData.getParticipants(), openWaveData.isFromLastRead(), searchPresenter,
        getLoadingIndicator(), errorPopup);
    this.waveRef = openWaveData.getWaveRef();

    wave.load(new Command() {
      @Override
      public void execute() {
        Timing.stop(timer);
      }
    });

    String encodedToken = History.getToken();
    if (encodedToken != null && !encodedToken.isEmpty()) {
      WaveRef fromWaveRef;
      try {
        fromWaveRef = GwtWaverefEncoder.decodeWaveRefFromPath(encodedToken);
      } catch (InvalidWaveRefException e) {
        LOG.trace().log("History token contains invalid path: " + encodedToken);
        return;
      }
      if (fromWaveRef.getWaveId().equals(waveRef.getWaveId())) {
        // History change was caused by clicking on a link, it's already
        // updated by browser.
        return;
      }
    }
    History.newItem(GwtWaverefEncoder.encodeToUriPathSegment(waveRef), false);
  }

  private void closeWave(final Command whenReady) {
    if (LOG.trace().shouldLog()) {
      LOG.trace().log("WebClient.closeWave()");
    }

    errorPopup.hide();

    completeWave(new Command() {

      @Override
      public void execute() {
        destroyWave();
        whenReady.execute();
      }
    });
  }

  private void completeWave(final Command whenReady) {
    if (LOG.trace().shouldLog()) {
      LOG.trace().log("WebClient.completeWave()");
    }

    if (wave != null) {
      wave.complete();
    }

    if (savingInProcess) {
      notSavedDialogYes.setOnClick(new Command() {

        @Override
        public void execute() {
          SchedulerInstance.getLowPriorityTimer().schedule(new Task() {

            @Override
            public void execute() {
              getNotSavedDialogActivator().setVisible(false);
              destroyWave();
              if (whenReady != null) {
                whenReady.execute();
              }
            }
          });
        }
      });

      notSavedDialogNo.setOnClick(new Command() {

        @Override
        public void execute() {
          getNotSavedDialogActivator().setVisible(false);
        }
      });

      getNotSavedDialogActivator().start(NOT_SAVED_DATA_INDICATE_DELAY_MS);
    } else {
      if (whenReady != null) {
        whenReady.execute();
      }
    }
  }

  /**
   * Completion of the current wave, starting check for the end of the message processing
   * and opening the new wave.
   * @param openWaveData - data for opening the new wave
   */
  private void closeAndOpenWave(final OpenWaveData openWaveData) {
    final Command openWave = new Command() {

      @Override
      public void execute() {
        openWave(openWaveData);
      }
    };

    if (wave != null) {
      StageThree three = wave.getStageThree();

      if (three != null) {
        final EditSession session = three.getEditSession();

        if (session.isDraftModified()) {
          final EditSession.Finisher finisher = session.getFinisher();
          finisher.onWaveCompletion(new Command() {
            @Override
            public void execute() {
              if (finisher.isExitAllowed()) {
                session.leaveDraftMode(finisher.shouldDraftBeSaved());
                closeWave(openWave);
              } else {
                searchPresenter.onRestoreSelection();
              }
            }
          });
          return;
        }
      }
    }

    closeWave(openWave);
  }

  /**
   * Closing the current wave in a wave panel.
   */
  private void destroyWave() {
    if (LOG.trace().shouldLog()) {
      LOG.trace().log("WebClient.destroyWave()");
    }

    if (wave != null) {
      wave.destroy();
    }
  }

  /**
   * Sets visibility of the glass panel.
   * @param visible - visibility of the glass panel
   */
  private void setGlassPanelVisible(boolean visible) {
    if (visible) {
      Document.get().getElementById(WAVE_ELEMENT_ID).appendChild(glassPanel);
      DomUtil.setProgressCursor(glassPanel);
    } else {
      DomUtil.setDefaultCursor(glassPanel);
      glassPanel.removeFromParent();
    }
  }
}
