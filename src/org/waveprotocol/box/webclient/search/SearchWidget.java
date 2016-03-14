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

import com.google.common.base.Preconditions;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;

import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.searches.impl.SearchesItemImpl;
import org.waveprotocol.box.webclient.client.ClientEvents;
import org.waveprotocol.box.webclient.client.events.SearchesModifyEventHandler;
import org.waveprotocol.box.webclient.search.SearchPresenter.SearchesModifyHandler;
import org.waveprotocol.box.webclient.search.SearchPresenter.WaveActionHandler;
import org.waveprotocol.box.webclient.search.i18n.SearchPresenterMessages;
import org.waveprotocol.wave.client.common.util.QuirksConstants;
import org.waveprotocol.wave.client.widget.button.ButtonFactory;
import org.waveprotocol.wave.client.widget.button.ClickButton.ClickButtonListener;
import org.waveprotocol.wave.client.widget.button.text.TextButton.TextButtonStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Widget implementation of the search area.
 *
 * @author hearnden@google.com (David Hearnden)
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class SearchWidget extends Composite implements SearchView, ChangeHandler {

  /** Resources used by this widget. */
  interface Resources extends ClientBundle {
    
    @Source("images/archive.png")
    ImageResource archive();
    
    @Source("images/inbox.png")
    ImageResource inbox();
    
    @Source("images/public.png")
    ImageResource pub();
    
    @Source("images/tools.png")
    ImageResource tools();

    /** CSS */
    @Source("Search.css")
    Css css();
  }

  interface Css extends CssResource {
    String self();
    String search();
    String query();
    String searchBoxContainer();
    String newWaveButton();
    String archive();
    String inbox();
    String pub();
    String tools();
  }

  interface Binder extends UiBinder<HTMLPanel, SearchWidget> {}  
  
  @UiField(provided = true)
  final static Css css = SearchPanelResourceLoader.getSearch().css();

  private final static Binder BINDER = GWT.create(Binder.class);

  private final static String DEFAULT_QUERY = "";  
  
  private final SearchPresenterMessages messages = GWT.create(SearchPresenterMessages.class);

  private WaveActionHandler actionHandler;
  private SearchesModifyHandler searchesModifyHandler;
  private Listener listener;

  private final List<SearchesItem> searches = new ArrayList<>();
  
  @UiField
  TextBox queryTextBox;
  
  @UiField
  SimplePanel newWaveButtonPanel;

  /**
   *
   */
  public SearchWidget() {
    initWidget(BINDER.createAndBindUi(this));    
    
    if (QuirksConstants.SUPPORTS_SEARCH_INPUT) {
      Element e = queryTextBox.getElement();
      e.setAttribute("type", "search");
      e.setAttribute("results", "10");
      e.setAttribute("autosave", "QUERY_AUTO_SAVE");
    }
    queryTextBox.addChangeHandler(this);
    
    newWaveButtonPanel.add(ButtonFactory.createTextClickButton(messages.newWave(),
        TextButtonStyle.REGULAR_BUTTON, messages.newWaveHint(),
        new ClickButtonListener() {
          
          @Override
          public void onClick() {
            actionHandler.onCreateWave();
            listener.onWaveCreate();
          }
        }));    
    
    ClientEvents.get().addSearchesModifyEventHandler(new SearchesModifyEventHandler() {
          
      @Override
      public void onSearchesListModify(List<SearchesItem> searches) {
        setSearches(searches);
      }
    });
  }

  @Override
  public void init(WaveActionHandler actionHandler, SearchesModifyHandler searchesModifyHandler,
      Listener listener) {
    Preconditions.checkState(this.actionHandler == null);
    Preconditions.checkArgument(actionHandler != null);
    Preconditions.checkState(this.searchesModifyHandler == null);
    Preconditions.checkArgument(searchesModifyHandler != null);
    Preconditions.checkState(this.listener == null);
    Preconditions.checkArgument(listener != null);
    
    this.actionHandler = actionHandler;
    this.searchesModifyHandler = searchesModifyHandler;
    this.listener = listener;
  }

  @Override
  public void reset() {
    Preconditions.checkState(listener != null);
    listener = null;
  }

  @Override
  public String getQuery() {
    return queryTextBox.getValue();
  }

  @Override
  public void setQuery(String text) {
    queryTextBox.setValue(text);
  }

  @Override
  public String getQueryName() {
    String query = getQuery();
    if (query != null) {
      for (SearchesItem item : searches) {
        if (query.equals(item.getQuery())) {
          return item.getName();
        }
      }
    }
    return null;
  }

  @Override
  public void onChange(ChangeEvent event) {
    if (queryTextBox.getValue() == null || queryTextBox.getValue().isEmpty()) {
      queryTextBox.setText(DEFAULT_QUERY);
    }
    onQuery();
  }

  private void setSearches(List<SearchesItem> sourceSearches) {
    searches.clear();
    for (SearchesItem sourceSearch : sourceSearches) {
      SearchesItem search = new SearchesItemImpl();
      search.copyFrom(sourceSearch);
      searches.add(search);
    }
  }

  private void onQuery() {
    if (listener != null) {
      listener.onQueryEntered();
    }
  }
}
