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

package org.waveprotocol.wave.client.wavepanel.impl.contact;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.ImageElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.widget.common.ImplPanel;
import org.waveprotocol.wave.model.util.Pair;

/**
 * A widget for displaying contacts.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
class ContactWidget extends Composite implements MouseOverHandler, ClickHandler {

  interface Listener {
    
    void onMouseOver(ContactWidget contact);
    
    void onSelect(ContactWidget contact);
  }

  interface Resources extends ClientBundle {
    
    /** CSS */
    @Source("ContactWidget.css")
    Css css();
  }

  interface Css extends CssResource {

    String self();

    String image();

    String participant();

    String fullName();

    String mark();
  }

  @UiField(provided = true)
  static Css css = ContactResourceLoader.getConact().css();

  interface Binder extends UiBinder<ImplPanel, ContactWidget> {
  }

  private static final Binder BINDER = GWT.create(Binder.class);

  @UiField
  ImplPanel self;
  
  @UiField
  ImageElement image;
  
  @UiField
  Element participant;
  
  @UiField
  Element fullName;
  
  private Listener listener;
  private ContactModel contact;

  public ContactWidget() {
    initWidget(self = BINDER.createAndBindUi(this));
    self.addDomHandler(this, MouseOverEvent.getType());
    self.addDomHandler(this, ClickEvent.getType());
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  public void setContact(
      ContactModel contact, Pair<Integer, Integer> matchParticipant,
      Pair<Integer, Integer> matchFullName, boolean exists) {
    this.contact = contact;
    image.setSrc(contact.getImageUrl());
    participant.setInnerHTML(makeTitleHTML(contact.getParticipantId().getAddress(), 
        matchParticipant, exists, false));
    if (contact.getFullName() != null && !contact.getFullName().isEmpty()) {
      fullName.setInnerHTML(makeTitleHTML(contact.getFullName(), matchFullName, exists,
          true));
    }
  }
  
  private String makeTitleHTML(String title, Pair<Integer, Integer> match,
      boolean italic, boolean inBrackets) {
    SafeHtmlBuilder html = new SafeHtmlBuilder();
    if (italic) {
      html.appendHtmlConstant("<i style=\"color: gray\">");
    }
    if (inBrackets) {
      html.appendEscaped("(");
    }
    if (match == null || match.first == match.second) {
      html.appendEscaped(title);
    } else {
      html.appendEscaped(title.substring(0, match.first));
      html.appendHtmlConstant("<b>");
      html.appendEscaped(title.substring(match.first, match.second));
      html.appendHtmlConstant("</b>");
      html.appendEscaped(title.substring(match.second));
    }
    if (inBrackets) {
      html.appendEscaped(")");
    }
    if (italic) {
      html.appendHtmlConstant("</i>");
    }
    return html.toSafeHtml().asString();
  }

  public ContactModel getModel() {
    return contact;
  }

  public void mark() {
    self.addStyleName(css.mark());
  }

  public void unMark() {
    self.removeStyleName(css.mark());
  }

  @Override
  public void onMouseOver(MouseOverEvent event) {
    if (listener != null) {
      listener.onMouseOver(this);
    }
  }

  @Override
  public void onClick(ClickEvent e) {
    if (listener != null) {
      listener.onSelect(this);
    }
  }
}
