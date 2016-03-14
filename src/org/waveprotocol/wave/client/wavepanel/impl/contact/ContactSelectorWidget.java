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
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import java.util.Set;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.client.widget.common.ImplPanel;
import org.waveprotocol.wave.client.widget.popup.AlignedPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Selector for participants to add to a wave.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class ContactSelectorWidget extends Composite
    implements ContactInputWidget.Listener, ContactModelList.Listener {

  private static final LoggerBundle LOG = new DomLogger("contacts");

  public interface Listener {
    public void onSelect(ParticipantId contact);
  }

  interface Binder extends UiBinder<ImplPanel, ContactSelectorWidget> {
  }

  private static final Binder BINDER = GWT.create(Binder.class);

  interface Resources extends ClientBundle {
    /** CSS */
    @Source("ContactSelectorWidget.css")
    Css css();
  }

  interface Css extends CssResource {

    String self();

    String dock();

    String input();
  }

  @UiField(provided = true)
  static Css css = ContactResourceLoader.getConactSelector().css();

  @UiField
  ImplPanel self;

  @UiField
  DockLayoutPanel dockPanel;

  @UiField
  ContactInputWidget contactInput;

  @UiField
  ScrollPanel scrollPanel;

  @UiField
  FlowPanel contactsPanel;

  private final ContactModelList contacts;
  private final String localDomain;
  private Conversation conversation;
  private Listener listener;
  private String pattern;
  private int currentIndex;

  /**
   * Widget for select contact.
   *
   * @param contacts the list of user's contacts.
   * @param currentUser logged in user.
   */
  public ContactSelectorWidget(ContactModelList contacts, String localDomain) {
    this.contacts = contacts;
    this.localDomain = localDomain;
    initWidget(self = BINDER.createAndBindUi(this));
    contactInput.setListener(this);
    contacts.setListener(this);
  }

  public void setListener(final Listener listener) {
    this.listener = listener;
  }

  public void setConversation(Conversation conversation) {
    this.conversation = conversation;
  }

  /**
   * Shows in a popup, and returns the popup.
   * @param currentUser the logged in user.
   */
  public UniversalPopup showInPopup(Element relative) {
    reset();

    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    UniversalPopup popup = PopupFactory.createPopup(relative,
        AlignedPopupPositioner.BELOW_LEFT, chrome, true);
    popup.add(ContactSelectorWidget.this);
    popup.show();

    setFocus();

    return popup;
  }

  public void setPattern(String pattern) {
    this.pattern = pattern;
  }

  @Override
  public void onKeyUp() {
    LOG.trace().log("key up");
    if (currentIndex > 0) {
      setCurrentIndex(unsetCurrentIndex()-1);
      scrollPanel.ensureVisible(getContact(currentIndex));
    }
  }

  @Override
  public void onKeyDown() {
    LOG.trace().log("key down");
    if (currentIndex < contactsPanel.getWidgetCount()-1) {
      setCurrentIndex(unsetCurrentIndex()+1);
      scrollPanel.ensureVisible(getContact(currentIndex));
    }
  }

  @Override
  public void onInput(String value) {
    if (value != null && !value.equals(pattern)) {
      LOG.trace().log("on input \"" + value + "\"");
      setPattern(value);
      currentIndex = -1;
      refreshContactList();
    }
  }

  @Override
  public void onSelect(String value) {
    if (contactsPanel.getWidgetCount() != 0) {
      if (currentIndex != -1) {
        fireOnSelect(getContact(currentIndex).getModel().getParticipantId());
      }
    } else {
      try {
        if (localDomain != null) {
          if (!value.isEmpty() && value.indexOf("@") == -1) {
            // If no domain was specified, assume that the participant is from the local domain.
            value = value + "@" + localDomain;
          } else if (value.equals("@")) {
            // "@" is a shortcut for the shared domain participant.
            value = value + localDomain;
          }
        }
        ParticipantId participant = ParticipantId.of(value);
        fireOnSelect(participant);
      } catch (InvalidParticipantAddress ex) {
      }
    }
  }

  @Override
  public void onContactsUpdated() {
    LOG.trace().log("on contacts updated");
    refreshContactList();
  }

  @Override
  public void onContactUpdated(ContactModel contact) {
    Set<ParticipantId> existingParticipants = conversation.getParticipantIds();
    for (int i=0; i < contactsPanel.getWidgetCount(); i++) {
      ContactWidget widget = (ContactWidget)contactsPanel.getWidget(i);
      if (widget.getModel().getParticipantId().equals(contact.getParticipantId())) {
        Pair<Integer, Integer> matchParticipant = contact.matchParticipant(pattern);
        Pair<Integer, Integer> matchFullName = contact.matchFullName(pattern);
        widget.setContact(contact,
            matchParticipant, matchFullName, existingParticipants.contains(contact.getParticipantId()));
        break;
      }
    }
  }

  private void reset() {
    contactInput.setText("");
    setPattern("");
    currentIndex = -1;
    refreshContactList();
  }

  private void refreshContactList() {
    LOG.trace().log("refresh list");
    contactsPanel.clear();
    if (!"@".equals(pattern)) {
      Set<ParticipantId> existingParticipants = conversation.getParticipantIds();
      for (ContactModel contact : contacts) {
        Pair<Integer, Integer> matchParticipant = contact.matchParticipant(pattern);
        Pair<Integer, Integer> matchFullName = contact.matchFullName(pattern);
        if (matchParticipant != null || matchFullName != null) {
          ContactWidget participantWidget = new ContactWidget();
          participantWidget.setContact(contact,
              matchParticipant, matchFullName, existingParticipants.contains(contact.getParticipantId()));
          participantWidget.setListener(new ContactWidget.Listener() {

            @Override
            public void onMouseOver(ContactWidget contact) {
              unsetCurrentIndex();
              setCurrentIndex(contactsPanel.getWidgetIndex(contact));
            }

            @Override
            public void onSelect(ContactWidget contact) {
              fireOnSelect(contact.getModel().getParticipantId());
            }

          });
          contactsPanel.add(participantWidget);
        }
      }
      scrollPanel.scrollToTop();
      if (contactsPanel.getWidgetCount() != 0) {
        if (currentIndex == -1) {
          currentIndex = 0;
        }
        if (currentIndex < contactsPanel.getWidgetCount()) {
          setCurrentIndex(currentIndex);
        } else {
          setCurrentIndex(contactsPanel.getWidgetCount()-1);
        }
      }
    }
    setHeight();
  }

  private void fireOnSelect(ParticipantId participant) {
    listener.onSelect(participant);
    for (ContactModel contact : contacts) {
      if (contact.getParticipantId().equals(participant)) {
        onContactUpdated(contact);
        break;
      }
    }
    if (!contactInput.getText().isEmpty()) {
      reset();
    }
    setFocus();
  }

  private int unsetCurrentIndex() {
    LOG.trace().log("unset index " + currentIndex);
    int oldIndex = currentIndex;
    if (currentIndex != -1) {
      getContact(currentIndex).unMark();
      currentIndex = -1;
    }
    return oldIndex;
  }

  private void setCurrentIndex(int currentIndex) {
    LOG.trace().log("set index " + currentIndex);
    if (currentIndex != -1) {
      getContact(currentIndex).mark();
    }
    this.currentIndex = currentIndex;
  }

  private ContactWidget getContact(int index) {
    return (ContactWidget)contactsPanel.getWidget(index);
  }

  private void setHeight() {
    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
      @Override
      public void execute() {
        int visibleCount = contactsPanel.getWidgetCount();
        if (visibleCount > 7) {
          visibleCount = 7;
        }
        String height = 28 + visibleCount*37 + "px";
        ContactSelectorWidget.this.getParent().setHeight(height);
        dockPanel.setHeight(height);
      }
    });
  }

  private void setFocus() {
    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
      @Override
      public void execute() {
        contactInput.setFocus(true);
      }
    });
  }

}
