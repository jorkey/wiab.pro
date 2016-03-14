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
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import org.waveprotocol.box.search.query.QueryParser;
import org.waveprotocol.box.search.query.SearchQuery;
import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.searches.impl.SearchesItemImpl;
import org.waveprotocol.box.webclient.search.i18n.SearchWidgetMessages;
import org.waveprotocol.box.webclient.search.i18n.SearchesItemEditorMessages;
import org.waveprotocol.wave.client.common.regexp.RegExpWrapFactoryImpl;
import org.waveprotocol.wave.client.widget.dialog.DialogBox;
import org.waveprotocol.wave.client.widget.dialog.DialogButton;
import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupEventListener;
import org.waveprotocol.wave.client.widget.popup.PopupEventSourcer;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;

/**
 * Searches item editor.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public final class SearchesItemEditorPopup extends Composite {

  interface Binder extends UiBinder<HTMLPanel, SearchesItemEditorPopup> {
  }

  public interface Listener {

    void onHide();

    void onShow();

    void onDone(SearchesItem searchesItem);
  }

  public interface Resources extends ClientBundle {

    @Source("SearchesItemEditorPopup.css")
    Css style();
  }

  public interface Css extends CssResource {

    String self();

    String label();

    String nameTextBox();

    String queryTextBox();

    String commitButtonsPanel();

    String cancelButton();

    String commitButton();
  }

  private final static Binder BINDER = GWT.create(Binder.class);

  @UiField(provided = true)
  final static Css css = GWT.<Resources> create(Resources.class).style();

  static {
    // StyleInjector's default behaviour of deferred injection messes up
    // popups, which do synchronous layout queries for positioning. Therefore,
    // we force synchronous injection.
    StyleInjector.inject(css.getText(), true);
  }

  private final static SearchWidgetMessages searchWidgetMessages =
      GWT.create(SearchWidgetMessages.class);

  private final static SearchesItemEditorMessages searchesItemEditorMessages =
      GWT.create(SearchesItemEditorMessages.class);

  private final QueryParser queryParser = new QueryParser(new RegExpWrapFactoryImpl());

  @UiField
  Label nameLabel;
  @UiField
  TextBox nameTextBox;
  @UiField
  Label queryLabel;
  @UiField
  TextBox queryTextBox;

  private final UniversalPopup popup;
  private final DialogButton commitButton;
  private final DialogButton cancelButton;

  private Listener listener;

  public SearchesItemEditorPopup() {
    initWidget(BINDER.createAndBindUi(this));

    queryTextBox.addChangeHandler(new ChangeHandler() {

      @Override
      public void onChange(ChangeEvent event) {
        handleSetQuery();
      }
    });

    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    popup = PopupFactory.createPopup(RootPanel.getBodyElement(), new CenterPopupPositioner(), chrome, true);
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

    commitButton = new DialogButton(searchesItemEditorMessages.modify(), new Command() {

      @Override
      public void execute() {
        if (nameTextBox.getText() != null && !nameTextBox.getText().isEmpty()) {
          SearchesItem searchesItem = new SearchesItemImpl();
          searchesItem.setName(nameTextBox.getText());
          searchesItem.setQuery(queryTextBox.getText());
          popup.hide();
          if (listener != null) {
            listener.onDone(searchesItem);
          }
        }
      }
    });

    cancelButton = new DialogButton(searchesItemEditorMessages.cancel(), new Command() {

      @Override
      public void execute() {
        popup.hide();
      }
    });

    DialogBox.create(popup, searchesItemEditorMessages.search(), this,
        new DialogButton[] { cancelButton, commitButton });
  }

  public void init(SearchesItem searchesItem, Listener listener) {
    if (searchesItem == null) {
      nameTextBox.setText("");
      queryTextBox.setText("");
      commitButton.setTitle(searchesItemEditorMessages.add());
    } else {
      nameTextBox.setText(searchesItem.getName());
      queryTextBox.setText(searchesItem.getQuery());
      commitButton.setTitle(searchesItemEditorMessages.modify());
    }
    handleSetQuery();
    this.listener = listener;
    nameTextBox.setFocus(true);
  }

  public void show() {
    popup.show();
  }

  public void hide() {
    popup.hide();
  }

  private void handleSetQuery() {
    SearchQuery query = queryParser.parseQuery(queryTextBox.getText());
    if (query.getConditions().size() == 1) {
      if (query.isInbox()) {
        nameTextBox.setText(searchWidgetMessages.inbox());
        nameTextBox.setEnabled(false);
      } else if (query.isArchive()) {
        nameTextBox.setText(searchWidgetMessages.archive());
        nameTextBox.setEnabled(false);
      } else if (query.isPublic()) {
        nameTextBox.setText(searchWidgetMessages.pub());
        nameTextBox.setEnabled(false);
      } else {
        nameTextBox.setEnabled(true);
      }
    } else {
      nameTextBox.setEnabled(true);
    }
  }
}
