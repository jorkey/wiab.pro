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
 */
package org.waveprotocol.box.webclient.search;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.RootPanel;
import java.util.ArrayList;
import java.util.List;
import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.searches.impl.SearchesItemImpl;
import org.waveprotocol.box.webclient.search.i18n.SearchesEditorMessages;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.client.widget.dialog.DialogBox;
import org.waveprotocol.wave.client.widget.dialog.DialogButton;
import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupEventListener;
import org.waveprotocol.wave.client.widget.popup.PopupEventSourcer;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;
import org.waveprotocol.wave.common.logging.LoggerBundle;

/**
 * Searches editor.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public final class SearchesEditorPopup extends Composite {

  private static final LoggerBundle LOG = new DomLogger(SearchWidget.class.getName());

  interface Binder extends UiBinder<HTMLPanel, SearchesEditorPopup> {
  }

  public interface Listener {

    void onShow();

    void onHide();

    void onDone(List<SearchesItem> searches);
  }

  public interface Resources extends ClientBundle {

    @Source("SearchesEditorPopup.css")
    Css style();
  }

  interface Css extends CssResource {

    String self();

    String centerPanel();

    String searchesPanel();

    String searchesListBox();

    String controlButtonsPanel();

    String controlButton();

    String commitButtonsPanel();

    String cancelButton();

    String okButton();
  }

  private final static Binder BINDER = GWT.create(Binder.class);

  @UiField(provided = true)
  final static Css css = GWT.<Resources>create(Resources.class).style();

  static {
    // StyleInjector's default behaviour of deferred injection messes up
    // popups, which do synchronous layout queries for positioning. Therefore,
    // we force synchronous injection.
    StyleInjector.inject(css.getText(), true);
  }

  private final static SearchesEditorMessages messages = GWT.create(SearchesEditorMessages.class);

  @UiField
  ListBox searchesListBox;
  @UiField
  Button addButton;
  @UiField
  Button modifyButton;
  @UiField
  Button removeButton;
  @UiField
  Button upButton;
  @UiField
  Button downButton;

  private final UniversalPopup popup;
  private Listener listener;
  private SearchesItemEditorPopup itemEditorPopup = new SearchesItemEditorPopup();
  private List<SearchesItem> searches = new ArrayList<>();
  private SearchesService searchesService = new RemoteSearchesService();
  private boolean modified = false;

  public SearchesEditorPopup() {
    initWidget(BINDER.createAndBindUi(this));

    addButton.addClickHandler(new ClickHandler() {

      @Override
      public void onClick(ClickEvent event) {
        itemEditorPopup.init(null, new SearchesItemEditorPopup.Listener() {

          @Override
          public void onHide() {
            searchesListBox.setFocus(true);
          }

          @Override
          public void onShow() {
          }

          @Override
          public void onDone(SearchesItem searchesItem) {
            addSearch(searchesItem);
            searchesListBox.setSelectedIndex(searchesListBox.getItemCount()-1);
            modified = true;
          }
        });
        itemEditorPopup.show();
      }
    });

    modifyButton.addClickHandler(new ClickHandler() {

      @Override
      public void onClick(ClickEvent event) {
        final int index = searchesListBox.getSelectedIndex();
        if (index != -1) {
          itemEditorPopup.init(searches.get(index), new SearchesItemEditorPopup.Listener() {

            @Override
            public void onHide() {
              searchesListBox.setFocus(true);
            }

            @Override
            public void onShow() {
            }

            @Override
            public void onDone(SearchesItem searchesItem) {
              searches.set(index, new SearchesItemImpl(searchesItem));
              searchesListBox.setItemText(index, searchesItem.getName());
              modified = true;
            }
          });
          itemEditorPopup.show();
        }
      }
    });

    removeButton.addClickHandler(new ClickHandler() {

      @Override
      public void onClick(ClickEvent event) {
        final int index = searchesListBox.getSelectedIndex();
        if (index != -1) {
          searches.remove(index);
          searchesListBox.removeItem(index);
          searchesListBox.setFocus(true);
          modified = true;
        }
      }
    });

    upButton.addClickHandler(new ClickHandler() {

      @Override
      public void onClick(ClickEvent event) {
        final int index = searchesListBox.getSelectedIndex();
        if (index != -1) {
          if (index > 0) {
            SearchesItem search = searches.get(index);
            searches.remove(index);
            searchesListBox.removeItem(index);
            insertSearch(search, index-1);
            searchesListBox.setSelectedIndex(index-1);
            modified = true;
          }
          searchesListBox.setFocus(true);
        }
      }
    });

    downButton.addClickHandler(new ClickHandler() {

      @Override
      public void onClick(ClickEvent event) {
        final int index = searchesListBox.getSelectedIndex();
        if (index != -1) {
          if (index < searchesListBox.getItemCount()-1) {
            SearchesItem search = searches.get(index);
            searches.remove(index);
            searchesListBox.removeItem(index);
            insertSearch(search, index+1);
            searchesListBox.setSelectedIndex(index+1);
            modified = true;
          }
          searchesListBox.setFocus(true);
        }
      }
    });

    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    popup = PopupFactory.createPopup(
        RootPanel.getBodyElement(), new CenterPopupPositioner(), chrome, true);
    popup.addPopupEventListener(new PopupEventListener.Impl() {

      @Override
      public void onShow(PopupEventSourcer source) {
        if (listener != null) {
          listener.onShow();
        }
      }

      @Override
      public void onHide(PopupEventSourcer source) {
        if (listener != null) {
          listener.onHide();
        }
      }
    });

    DialogButton buttonOk = new DialogButton(messages.ok(), new Command() {

      @Override
      public void execute() {
        if (modified) {
          searchesService.storeSearches(searches, new SearchesService.StoreCallback() {

            @Override
            public void onFailure(String message) {
              LOG.error().log("Searches storing error ", message);
            }

            @Override
            public void onSuccess() {
            }
          });
        }
        popup.hide();
        if (listener != null) {
          listener.onDone(searches);
        }
      }
    });
    DialogButton buttonCancel = new DialogButton(messages.cancel(), new Command() {

      @Override
      public void execute() {
        popup.hide();
      }
    });

    DialogBox.create(popup, messages.searches(), this, new DialogButton[] { buttonCancel, buttonOk });
  }

  public void init(List<SearchesItem> sourceSearches, Listener listener) {
    searches.clear();
    searchesListBox.clear();
    for (SearchesItem search : sourceSearches) {
      addSearch(search);
    }
    searchesListBox.setVisibleItemCount(searches.size());
    int index = searchesListBox.getSelectedIndex();
    if (index != -1) {
      searchesListBox.setItemSelected(index, false);
    }
    searchesListBox.setFocus(true);
    this.listener = listener;
    modified = false;
  }

  public void show() {
    popup.show();
  }

  public void hide() {
    popup.hide();
  }

  private void addSearch(SearchesItem search) {
    searches.add(new SearchesItemImpl(search));
    searchesListBox.addItem(search.getName());
  }

  private void insertSearch(SearchesItem search, int index) {
    searches.add(index, new SearchesItemImpl(search));
    searchesListBox.insertItem(search.getName(), index);
  }
}
