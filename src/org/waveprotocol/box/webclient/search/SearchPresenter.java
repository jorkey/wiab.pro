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

package org.waveprotocol.box.webclient.search;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Timer;

import org.waveprotocol.box.search.query.QueryCondition;
import org.waveprotocol.box.search.query.QueryParser;
import org.waveprotocol.box.search.query.SearchQuery;
import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.searches.impl.SearchesItemImpl;
import org.waveprotocol.box.webclient.client.ClientEvents;
import org.waveprotocol.box.webclient.client.events.SearchesModifyEventHandler;
import org.waveprotocol.box.webclient.folder.FolderOperationBuilder;
import org.waveprotocol.box.webclient.folder.FolderOperationBuilderImpl;
import org.waveprotocol.box.webclient.folder.FolderOperationService;
import org.waveprotocol.box.webclient.folder.FolderOperationServiceImpl;
import org.waveprotocol.box.webclient.search.Search.State;
import org.waveprotocol.box.webclient.search.i18n.SearchPresenterMessages;
import org.waveprotocol.box.webclient.search.i18n.SearchWidgetMessages;
import org.waveprotocol.wave.client.common.regexp.RegExpWrapFactoryImpl;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.client.scheduler.Scheduler.IncrementalTask;
import org.waveprotocol.wave.client.scheduler.Scheduler.Task;
import org.waveprotocol.wave.client.scheduler.SchedulerInstance;
import org.waveprotocol.wave.client.scheduler.TimerService;
import org.waveprotocol.wave.client.widget.toolbar.GroupingToolbar;
import org.waveprotocol.wave.client.widget.toolbar.ToolbarButtonViewBuilder;
import org.waveprotocol.wave.client.widget.toolbar.buttons.ToolbarClickButton;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.conversation.InboxState;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;
import org.waveprotocol.wave.model.util.IdentityMap;
import org.waveprotocol.wave.model.wave.SourcesEvents;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Presents a searchesItem model into a searchesItem view.
 * <p>
 * This class invokes rendering, and controls the life cycle of digest views. It
 * also handles all UI gesture events sourced from views in the searchesItem panel.
 *
 * @author hearnden@google.com (David Hearnden)
 */
public final class SearchPresenter implements Search.Listener, SearchPanelView.Listener,
    SearchView.Listener, SourcesEvents<SearchPresenter.WaveTitleListener> {

  /**
   * Handles wave actions.
   */
  public interface WaveActionHandler {

    /** Handles the wave creation action. */
    void onCreateWave();

    /** Handles a wave selection action. */
    void onWaveSelected(WaveId id, boolean ctrlDown, boolean altDown);
  }

  /**
   * Handles wave title changes.
   */
  public interface WaveTitleListener {

    void onMaybeWaveTitleChanged();
  }

  public interface SearchesModifyHandler {

    void onSearchesModify(List<SearchesItem> searches);
  }

  private static final LoggerBundle LOG = new DomLogger("SearchPresenter");

  private static final SearchWidgetMessages searchWidgetMessages =
      GWT.create(SearchWidgetMessages.class);

  private static final SearchPresenterMessages searchPresenterMessages =
      GWT.create(SearchPresenterMessages.class);

  /** How often to repeat the searchesItem searchQuery. */
  private final static int POLLING_INTERVAL_MS = 15000; // 15s

  private final static String DEFAULT_SEARCH = QueryCondition.INBOX.toString();
  private final static String DEFAULT_SEARCH_NAME = searchWidgetMessages.inbox();
  private final static int DEFAULT_PAGE_SIZE = 20;

  private final static long REINDEXING_TIMEOUT = 30000; // 30s
  private final static int DIGESTS_UPDATE_TIMEOUT = 1;

  /**
   * Creates a searchesItem presenter.
   *
   * @param model model to present
   * @param searchPatternsService search patterns service
   * @param view view to render into
   * @param actionHandler handler for actions
   * @param searchesModifyHandler searches modify handler
   * @return new search presenter
   */
  public static SearchPresenter create(Search model, SearchesService searchPatternsService,
      SearchPanelView view, WaveActionHandler actionHandler,
      SearchesModifyHandler searchesModifyHandler) {
    SearchPresenter presenter = new SearchPresenter(SchedulerInstance.getHighPriorityTimer(),
        model, searchPatternsService, view, actionHandler, searchesModifyHandler);
    presenter.init();
    return presenter;
  }

  // External references
  private final TimerService scheduler;
  private final Search search;
  private final SearchesService searchesService;
  private final SearchPanelView searchUi;
  private final WaveActionHandler actionHandler;
  private final SearchesModifyHandler searchesModifyHandler;

  private boolean searchStopped = false;

  // Digests to render
  private final Set<Digest> digestsToRender = new HashSet<>();

  // Internal state
  private final IdentityMap<DigestView, Digest> digestUis = CollectionUtils.createIdentityMap();

  private final IncrementalTask searchUpdater = new IncrementalTask() {

    @Override
    public boolean execute() {
      doSearch();
      return true;
    }
  };

  private final IncrementalTask searchPatternsGetter = new IncrementalTask() {

    @Override
    public boolean execute() {
      doQuerySearches();
      return false;
    }
  };

  private final IncrementalTask renderer = new IncrementalTask() {

    @Override
    public boolean execute() {
      if (search.getState() == State.READY) {
        render();
        return false;
      } else {
        return true;
      }
    }
  };

  private final Task digestsUpdater = new Task() {

    @Override
    public void execute() {
      if (search.getState() == State.READY && !isRenderingInProgress) {
        for (Digest digest : digestsToRender) {
          renderDigest(digest);
        }
        digestsToRender.clear();
        fireMaybeWaveTitleChanged();
      } else {
        // Try again later.
        scheduler.schedule(this);
      }
    }
  };

  /** Current searchesItem searchQuery. */
  private String queryText = DEFAULT_SEARCH;

  private String queryName = DEFAULT_SEARCH_NAME;

  /** Number of results to searchQuery for. */
  private int querySize = DEFAULT_PAGE_SIZE;

  /** Current checked digests. */
  private final Set<DigestView> checkedViews = new HashSet<>();

  /** Current selected digest. */
  private DigestView selectedView;
  
  /** Digest was selected before current. To be able to restore old selection
   * if user cancels close wave action
   */
  private DigestView oldSelection;

  /** Current touched digest. */
  private DigestView touchedView;

  private boolean isRenderingInProgress = false;

  /** Parser for searchesItem searchQuery */
  private final QueryParser queryParser = new QueryParser(new RegExpWrapFactoryImpl());

  /** Parsed searchQuery */
  private SearchQuery query = queryParser.parseQuery(queryText);

  /** Searches list */
  private final List<SearchesItem> searches = new ArrayList<SearchesItem>();

  private final SearchesEditorPopup searchesEditorPopup = new SearchesEditorPopup();

  private final Map<WaveId, Long> goneInboxWaves = new HashMap<WaveId, Long>();
  private final Map<WaveId, Long> goneArchiveWaves = new HashMap<WaveId, Long>();

  private final Timer digestsUpdateTimer;

  CopyOnWriteSet<WaveTitleListener> listeners = CopyOnWriteSet.create();

  SearchPresenter(TimerService scheduler, Search search,
      SearchesService searchPatternsService,
      SearchPanelView searchUi, WaveActionHandler actionHandler,
      SearchesModifyHandler searchesModifyHandler) {
    this.search = search;
    this.searchesService = searchPatternsService;
    this.searchUi = searchUi;
    this.scheduler = scheduler;
    this.actionHandler = actionHandler;
    this.searchesModifyHandler = searchesModifyHandler;

    digestsUpdateTimer = new Timer() {

      @Override
      public void run() {
        digestUis.each(new IdentityMap.ProcV<DigestView, Digest>() {
          
          @Override
          public void apply(DigestView digestView, Digest digest) {
            digestView.update();
          }
        });
      }
    };
  }

  /**
   * Releases resources and detaches listeners.
   */
  public void destroy() {
    scheduler.cancel(searchUpdater);
    scheduler.cancel(renderer);
    searchUi.getSearch().reset();
    searchUi.reset();
    search.removeListener(this);
  }

  @Override
  public void onChecked(DigestView digestUi, boolean checked) {
    setChecked(digestUi, checked);
  }

  @Override
  public void onSelected(DigestView digestUi, boolean ctrlDown, boolean altDown) {
    setSelected(digestUi);
    openSelected(ctrlDown, altDown);
  }

  @Override
  public void onRestoreSelection() {
    setSelected(oldSelection);
  }

  @Override
  public void onTouched(DigestView digestUi) {
    setTouched(digestUi);
  }

  @Override
  public void onResized() {
    digestsUpdateTimer.cancel();
    digestsUpdateTimer.schedule(DIGESTS_UPDATE_TIMEOUT);
  }

  @Override
  public void onQueryEntered() {
    queryText = searchUi.getSearch().getQuery();
    query = queryParser.parseQuery(queryText);
    queryName = getConstantSearchName(query);
    if (queryName == null) {
      queryName = searchUi.getSearch().getQueryName();
    }
    querySize = DEFAULT_PAGE_SIZE;
    searchUi.setTitleText(searchPresenterMessages.searching());
    doSearch();
  }

  @Override
  public void onWaveCreate() {
    updateDigests(500);
  }

  @Override
  public void onShowMoreClicked() {
    querySize += DEFAULT_PAGE_SIZE;
    doSearch();
  }

  //
  // Search events. For now, dumbly re-render the whole list.
  //

  @Override
  public void onStateChanged() {
    //
    // If the state switches to searching, then do nothing. A manual title-bar
    // update is performed in onQueryEntered(), and the title-bar should not be
    // updated when a polling searchesItem fires.
    //
    // If the state switches to ready, then just update the title. Do not
    // necessarily re-render, since that is only necessary if a change occurred,
    // which would have fired one of the other methods below.
    //
    if (search.getState() == State.READY) {
      renderTitle();
    }
  }

  @Override
  public void onDigestAdded(int index, Digest digest) {
    renderLater();
  }

  @Override
  public void onDigestRemoved(int index, Digest digest) {
    renderLater();
  }

  @Override
  public void onDigestReady(int index, Digest digest) {
    renderDigestLater(digest);
  }

  @Override
  public void onTotalChanged(int total) {
    renderLater();
  }

  @Override
  public void addListener(WaveTitleListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(WaveTitleListener listener) {
    listeners.remove(listener);
  }

  /**
   * @return title of the selected digest (it's used in window title)
   */
  public String getSelectedTitle() {
    Digest selectedDigest = digestUis.get(selectedView);
    return selectedDigest != null ? selectedDigest.getTitle() : null;
  }
  
  /**
   * Initializes toolbar.
   */
  private void initToolbar() {
    GroupingToolbar.View rightToolbarUi = searchUi.getRightToolbar();

    final ToolbarClickButton button = rightToolbarUi.addClickButton();
    button.setShowDivider(true);
    new ToolbarButtonViewBuilder()
        .setIcon(SearchPanelResourceLoader.getSearch().css().tools())
        .setTooltip(searchPresenterMessages.modify())
        .applyTo(button, new ToolbarClickButton.Listener() {

      @Override
      public void onClicked() {
        searchesEditorPopup.init(searches, new SearchesEditorPopup.Listener() {

          @Override
          public void onShow() {
          }

          @Override
          public void onHide() {
          }

          @Override
          public void onDone(List<SearchesItem> searches) {
            searchesModifyHandler.onSearchesModify(searches);
          }
        });
        searchesEditorPopup.show();
      }
    });
  }

  /**
   * Performs initial presentation, and attaches listeners to live objects.
   */
  private void init() {
    initToolbar();
    initSearchBox();
    render();
    search.addListener(this);
    searchUi.init(this);
    searchUi.getSearch().init(actionHandler, searchesModifyHandler, this);

    // Fire a polling searchesItem.
    scheduler.scheduleRepeating(searchUpdater, 0, POLLING_INTERVAL_MS);

    // Fire searchQuery of searchesItem patterns.
    scheduler.schedule(searchPatternsGetter);

    ClientEvents.get().addSearchesModifyEventHandler(new SearchesModifyEventHandler() {

      @Override
      public void onSearchesListModify(List<SearchesItem> sourceSearches) {
        searches.clear();
        for (SearchesItem sourceSearch : sourceSearches) {
          SearchesItem search = new SearchesItemImpl();
          search.copyFrom(sourceSearch);
          searches.add(search);
        }
        if (checkedViews.isEmpty()) {
          initToolbarInViewMode();
        }
      }
    });
  }

  /**
   * Put searchesItem buttons to the toolbar.
   */
  private void initToolbarInViewMode() {
    GroupingToolbar.View leftToolbarUi = searchUi.getLeftToolbar();
    leftToolbarUi.clearItems();

    for (final SearchesItem searchesItem : searches) {
      ToolbarClickButton button = leftToolbarUi.addClickButton();
      ToolbarButtonViewBuilder buttonBuilder = new ToolbarButtonViewBuilder();
      SearchQuery searchQuery = queryParser.parseQuery(searchesItem.getQuery());

      String name = getConstantSearchName(searchQuery);
      if (name == null) {
        name = searchesItem.getName();
      }
      buttonBuilder.setText(name);

      String icon = getSearchIcon(searchQuery);
      if (icon != null) {
        buttonBuilder.setIcon(icon);
      }

      String tooltip = getConstantSearchTooltip(searchQuery);
      if (tooltip != null) {
        buttonBuilder.setTooltip(tooltip);
      }

      buttonBuilder.applyTo(button, new ToolbarClickButton.Listener() {

        @Override
        public void onClicked() {
          searchUi.getSearch().setQuery(searchesItem.getQuery());
          onQueryEntered();
        }
      });
    }
}

  /**
   * Put move buttons to the toolbar.
   */
  private void initToolbarInManageMode() {
    GroupingToolbar.View leftToolbarUi = searchUi.getLeftToolbar();
    leftToolbarUi.clearItems();

    if (query.isInbox()) {
      ToolbarClickButton moveButton = leftToolbarUi.addClickButton();
      new ToolbarButtonViewBuilder()
          .setText(searchPresenterMessages.toArchive())
          .setIcon(SearchPanelResourceLoader.getSearch().css().archive())
          .applyTo(moveButton, new ToolbarClickButton.Listener() {

        @Override
        public void onClicked() {
          folderOperation(FolderOperationBuilder.OPERATION_MOVE, FolderOperationBuilder.FOLDER_ARCHIVE);
        }
      });

    } else {
      ToolbarClickButton moveButton = leftToolbarUi.addClickButton();
      new ToolbarButtonViewBuilder()
          .setText(searchPresenterMessages.toInbox())
          .setIcon(SearchPanelResourceLoader.getSearch().css().inbox())
          .applyTo(moveButton, new ToolbarClickButton.Listener() {

        @Override
        public void onClicked() {
          folderOperation(FolderOperationBuilder.OPERATION_MOVE, FolderOperationBuilder.FOLDER_INBOX);
        }
      });

    }
  }

  private void updateDigests(int delay) {
    // HACK(hearnden): To mimic live searchesItem, fire a searchesItem poll
    // reasonably soon after creating a wave. This will be unnecessary
    // with a real live searchesItem implementation. The delay is to give
    // enough time for the wave state to propagate to the server.
    scheduler.cancel(searchUpdater);
    scheduler.scheduleRepeating(searchUpdater, delay, POLLING_INTERVAL_MS);
  }

  private void folderOperation(String operation, String folder) {
    Set<DigestView> views = new HashSet<>();
    views.addAll(checkedViews);
    if (!views.isEmpty()) {
      FolderOperationBuilder op = new FolderOperationBuilderImpl();
      op.addParameter(FolderOperationBuilder.PARAM_OPERATION, operation);
      op.addParameter(FolderOperationBuilder.PARAM_FOLDER, folder);
      for (DigestView view : views) {
        op.addParameter(FolderOperationBuilder.PARAM_WAVE_ID, digestUis.get(view).getWaveId().serialise());
      }
      FolderOperationService service = new FolderOperationServiceImpl();
      service.execute(op.getUrl(), new FolderOperationService.Callback() {

        @Override
        public void onFailure(String message) {
          LOG.error().log("Folder operation failed: ", message);
        }

        @Override
        public void onSuccess() {
        }
      });
      boolean moved = FolderOperationBuilder.OPERATION_MOVE.equals(operation);
      for (DigestView view : views) {
        setChecked(view, false);
        if (moved) {
          search.cancel();
          Digest digest = digestUis.get(view);
          if (digest != null) {
            search.removeDigest(digest.getWaveId());
            gone(digest.getWaveId());
          }
        }
      }
      if (moved) {
        updateDigests(POLLING_INTERVAL_MS);
      }
    }
  }

  /**
   * Initializes the searchesItem box.
   */
  private void initSearchBox() {
    searchUi.getSearch().setQuery(queryText);
  }

  /**
   * Executes the current searchesItem.
   */
  private void doSearch() {
    search.find(queryText, querySize);
  }

  /**
   * Query searchesItem patterns
   */
  private void doQuerySearches() {
    searchesService.getSearches(new SearchesService.GetCallback() {

      @Override
      public void onFailure(String message) {
        LOG.error().log("Query search patterns failed", message);
      }

      @Override
      public void onSuccess(List<SearchesItem> searches) {
        searchesModifyHandler.onSearchesModify(searches);
      }
    });
  }

  /**
   * Renders the current state of the searchesItem result into the panel.
   */
  private void render() {
    renderTitle();
    renderDigests();
    renderShowMore();
  }

  /**
   * Renders the paging information into the title bar.
   */
  private void renderTitle() {
    int resultEnd = querySize;
    String totalStr;
    if (search.getTotal() != Search.UNKNOWN_SIZE) {
      resultEnd = Math.min(resultEnd, search.getTotal());
      totalStr = searchPresenterMessages.of(search.getTotal());
    } else {
      totalStr = searchPresenterMessages.ofUnknown();
    }
    searchUi.setTitleText((queryName != null ? queryName : queryText) +
        " (0-" + resultEnd + " " + totalStr + ")");
  }

  private void renderDigests() {
    WaveId toSelect = selectedView != null ? digestUis.get(selectedView).getWaveId() : null;
    Set<WaveId> toCheck = new HashSet<>();

    for (DigestView digestUi : checkedViews) {
      Digest digest = digestUis.get(digestUi);
      if (digest != null) {
        toCheck.add(digest.getWaveId());
      }
    }

    digestUis.clear();
    checkedViews.clear();
    isRenderingInProgress = true;
    // Preserve selection on re-rendering.
    searchUi.clearDigests();
    setSelected(null);
    updateGoneCache();
    for (int i = 0, size = search.getMinimumTotal(); i < size; i++) {
      Digest digest = search.getDigest(i);
      if (digest == null) {
        continue;
      }
      if (digest.getInboxState() != null) {
        if (query.isInbox() && digest.getInboxState() != InboxState.INBOX) {
          gone(digest.getWaveId());
          continue;
        }
        if (query.isArchive() && digest.getInboxState() != InboxState.ARCHIVE) {
          gone(digest.getWaveId());
          continue;
        }
      }
      if (isGone(digest.getWaveId())) {
        continue;
      }
      DigestView digestUi = searchUi.insertBefore(null, digest);
      digestUis.put(digestUi, digest);
      if (digest.getWaveId().equals(toSelect)) {
        setSelected(digestUi);
      }
      for (WaveId waveId : toCheck) {
        if (digest.getWaveId().equals(waveId)) {
          setChecked(digestUi, true);
        }
      }
    }
    isRenderingInProgress = false;
  }

  private void renderShowMore() {
    searchUi.setShowMoreVisible(
        search.getTotal() == Search.UNKNOWN_SIZE || querySize < search.getTotal());
  }

  private void renderDigest(Digest digest) {
    setSelected(null);

    DigestView digestToRemove = findDigestView(digest);
    if (digestToRemove == null) {
      return;
    }

    DigestView insertRef = searchUi.getNext(digestToRemove);
    digestToRemove.remove();
    if (digest.getInboxState() != null) {
      if (query.isInbox() && digest.getInboxState() != InboxState.INBOX) {
        gone(digest.getWaveId());
        return;
      }
      if (query.isArchive() && digest.getInboxState() != InboxState.ARCHIVE) {
        gone(digest.getWaveId());
        return;
      }
    }

    DigestView newDigestView = insertDigest(insertRef, digest);
    setSelected(newDigestView);
  }

  //
  // UI gesture events.
  //

  private void setChecked(DigestView digestUi, boolean checked) {
    if (checked) {
      digestUi.setChecked(true);
      checkedViews.add(digestUi);
      if (checkedViews.size() == 1) {
        initToolbarInManageMode();
      }
    } else {
      digestUi.setChecked(false);
      checkedViews.remove(digestUi);
      if (checkedViews.isEmpty()) {
        initToolbarInViewMode();      }
    }
  }

  private void setSelected(DigestView digestUi) {
    if (selectedView != null) {
      selectedView.setSelected(false);
    }
    oldSelection = selectedView;
    selectedView = digestUi;
    if (selectedView != null) {
      selectedView.setSelected(true);
    }
  }

  private void setTouched(DigestView digestUi) {
    if (touchedView == digestUi) {
      return;
    }

    if (touchedView != null) {
      touchedView.setTouched(false);
    }
    touchedView = digestUi;
    if (touchedView != null) {
      touchedView.setTouched(true);
    }
  }

  /**
   * Invokes the wave-select action on the currently selected digest.
   */
  private void openSelected(boolean ctrlDown, boolean altDown) {
    actionHandler.onWaveSelected(
        digestUis.get(selectedView).getWaveId(), ctrlDown, altDown);
  }

  /**
   * Find the DigestView that contains a certain digest
   *
   * @param digest the digest the DigestView should contain.
   * @return the DigestView containing the digest. {@null} if the digest is
   *            not found.
   */
  private DigestView findDigestView(Digest digest) {
    DigestView digestUi = searchUi.getFirst();
    while(digestUi != null) {
      if (digestUis.get(digestUi).equals(digest)) {
        return digestUi;
      }
      digestUi = searchUi.getNext(digestUi);
    }
    return null;
  }

  /**
   * Insert a digest before amongst the currently shown digests
   *
   * @param insertRef the DigestView to insert the new digest before. The new digest
   *                    is inserted last if insertRef is {@null}.
   * @param digest the digest to insert.
   * @return the newly inserted DigestView.
   */
  private DigestView insertDigest(DigestView insertRef, Digest digest) {
    DigestView newDigestUi;
    if (insertRef != null) {
      newDigestUi = searchUi.insertBefore(insertRef, digest);
      digestUis.put(newDigestUi, digest);
    } else {
      insertRef = searchUi.getLast();
      newDigestUi = searchUi.insertAfter(insertRef, digest);
      digestUis.put(newDigestUi, digest);
    }
    return newDigestUi;
  }

  private void renderLater() {
    if (searchStopped) {
      return;
    }

    if (!scheduler.isScheduled(renderer)) {
      scheduler.schedule(renderer);
    }
  }

  private void renderDigestLater(Digest digest) {
    if (searchStopped) {
      return;
    }

    digestsToRender.add(digest);
    if (!scheduler.isScheduled(digestsUpdater)) {
      scheduler.schedule(digestsUpdater);
    }
  }

  private String getConstantSearchName(SearchQuery query) {
    if (query.getConditions().size() == 1) {
      if (query.isInbox()) {
        return searchWidgetMessages.inbox();
      } else if (query.isArchive()) {
        return searchWidgetMessages.archive();
      } else if (query.isPublic()) {
        return searchWidgetMessages.pub();
      }
    }
    return null;
  }

  private String getConstantSearchTooltip(SearchQuery query) {
    if (query.getConditions().size() == 1) {
      if (query.isInbox()) {
        return searchWidgetMessages.inboxHint();
      } else if (query.isArchive()) {
        return searchWidgetMessages.archiveHint();
      } else if (query.isPublic()) {
        return searchWidgetMessages.pubHint();
      }
    }
    return null;
  }

  private String getSearchIcon(SearchQuery query) {
    if (query.getConditions().size() == 1) {
      if (query.isInbox()) {
        return SearchPanelResourceLoader.getSearch().css().inbox();
      } else if (query.isArchive()) {
        return SearchPanelResourceLoader.getSearch().css().archive();
      } else if (query.isPublic()) {
        return SearchPanelResourceLoader.getSearch().css().pub();
      }
    }
    return null;
  }

  private void updateGoneCache(Map<WaveId, Long> cache) {
    long time = new Date().getTime();
    List<WaveId> toRemove = new ArrayList<WaveId>();
    for (Map.Entry<WaveId, Long> entry : cache.entrySet()) {
      if (entry.getValue() + REINDEXING_TIMEOUT < time) {
        toRemove.add(entry.getKey());
      }
    }
    for (WaveId id : toRemove) {
      cache.remove(id);
    }
  }

  private void updateGoneCache() {
    updateGoneCache(goneInboxWaves);
    updateGoneCache(goneArchiveWaves);
  }

  private void gone(WaveId waveId) {
    long time = new Date().getTime();
    if (query.isInbox()) {
      if (!goneInboxWaves.containsKey(waveId)) {
        goneArchiveWaves.remove(waveId);
        goneInboxWaves.put(waveId, time);
      }
    } else if (query.isArchive()) {
      if (!goneArchiveWaves.containsKey(waveId)) {
        goneInboxWaves.remove(waveId);
        goneArchiveWaves.put(waveId, time);
      }
    }
  }

  private boolean isGone(WaveId waveId) {
    return (query.isInbox() && goneInboxWaves.containsKey(waveId)) ||
           (query.isArchive() && goneArchiveWaves.containsKey(waveId));
  }

  private void fireMaybeWaveTitleChanged() {
    for (WaveTitleListener listener : listeners) {
      listener.onMaybeWaveTitleChanged();
    }
  }
}
