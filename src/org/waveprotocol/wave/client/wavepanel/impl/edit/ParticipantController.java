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

package org.waveprotocol.wave.client.wavepanel.impl.edit;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.user.client.Command;

import org.waveprotocol.box.webclient.client.WebClient;
import org.waveprotocol.wave.client.account.ContactManager;
import org.waveprotocol.wave.client.account.Profile;
import org.waveprotocol.wave.client.account.ProfileManager;
import org.waveprotocol.wave.client.common.safehtml.EscapeUtils;
import org.waveprotocol.wave.client.common.safehtml.SafeHtml;
import org.waveprotocol.wave.client.common.util.KeyCombo;
import org.waveprotocol.wave.client.common.util.KeyComboContext;
import org.waveprotocol.wave.client.common.util.KeyComboManager;
import org.waveprotocol.wave.client.common.util.KeyComboTask;
import org.waveprotocol.wave.client.scheduler.Scheduler;
import org.waveprotocol.wave.client.scheduler.SchedulerInstance;
import org.waveprotocol.wave.client.wave.LocalSupplementedWave;
import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.event.EventHandlerRegistry;
import org.waveprotocol.wave.client.wavepanel.event.KeySignalHandler;
import org.waveprotocol.wave.client.wavepanel.event.MouseComeLeaveEvent;
import org.waveprotocol.wave.client.wavepanel.event.WaveClickHandler;
import org.waveprotocol.wave.client.wavepanel.event.WaveMouseComeLeaveHandler;
import org.waveprotocol.wave.client.wavepanel.event.WaveMouseDownHandler;
import org.waveprotocol.wave.client.wavepanel.event.WaveMouseUpHandler;
import org.waveprotocol.wave.client.wavepanel.impl.contact.ContactModelList;
import org.waveprotocol.wave.client.wavepanel.impl.contact.ContactSelectorWidget;
import org.waveprotocol.wave.client.wavepanel.impl.edit.i18n.ParticipantMessages;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicParticipantView.ParticipantState;
import org.waveprotocol.wave.client.wavepanel.view.ParticipantView;
import org.waveprotocol.wave.client.wavepanel.view.ParticipantsView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.ParticipantsViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TypeCodes;
import org.waveprotocol.wave.client.widget.profile.ProfilePopupPresenter;
import org.waveprotocol.wave.client.widget.profile.ProfilePopupView;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;

import javax.annotation.Nullable;

/**
 * Installs the add/remove participant controls.
 */
public final class ParticipantController {
  
  //delay in marking tags as read
  private static final int MARK_READ_DELAY_MS = 1000;
  private final DomAsViewProvider views;
  private final ModelAsViewProvider models;
  private final LocalSupplementedWave supplement;
  private final ProfileManager profiles;
  private final ContactManager contacts;
  private final String localDomain;
  private final ContactModelList contactsView;
  private ContactSelectorWidget contactSelector;
  private final WaveletId waveletId;
  private final ParticipantsViewBuilder.Css css;
  private final ParticipantMessages messages;
  private boolean buttonDown = false;

  /**
   * @param localDomain nullable. if provided, automatic suffixing will occur.
   */
  ParticipantController(
      DomAsViewProvider views,
      ModelAsViewProvider models,
      LocalSupplementedWave supplement,
      ProfileManager profiles,
      ContactManager contacts,
      String localDomain,
      WaveletId waveletId,
      ParticipantsViewBuilder.Css css,
      ParticipantMessages messages) {
    this.views = views;
    this.models = models;
    this.supplement = supplement;
    this.profiles = profiles;
    this.contacts = contacts;
    this.localDomain = localDomain;
    this.waveletId = waveletId;
    this.css = css;
    this.messages = messages;
    contactsView = new ContactModelList(contacts, profiles);
  }

  /**
   * Builds and installs the participant control feature.
   * @param user the logged in user
   */
  public static void install(
      WavePanel panel,
      ModelAsViewProvider models,
      LocalSupplementedWave supplement,
      ProfileManager profiles,
      ContactManager contacts,
      WaveletId waveletId,
      String localDomain,
      ParticipantsViewBuilder.Css css,
      ParticipantMessages messages) {
    ParticipantController controller = new ParticipantController(
        panel.getViewProvider(),
        models,
        supplement,
        profiles,
        contacts,
        localDomain,
        waveletId,
        css,
        messages);
    controller.install(panel);
  }

  private void install(WavePanel panel) {
    EventHandlerRegistry handlers = panel.getHandlers();
    handlers.registerClickHandler(TypeCodes.kind(Type.ADD_PARTICIPANT), new WaveClickHandler() {
          @Override
          public boolean onClick(ClickEvent event, Element context) {
            handleAddButtonClicked(context);
            return true;
          }
        });
    handlers.registerMouseDownHandler(TypeCodes.kind(Type.ADD_PARTICIPANT),
        new WaveMouseDownHandler() {
          @Override
          public boolean onMouseDown(MouseDownEvent event, Element context) {
            buttonDown = true;
            checkButtonView();
            return false;
          }
        });
    handlers.registerMouseUpHandler(TypeCodes.kind(Type.ADD_PARTICIPANT),
        new WaveMouseUpHandler() {
          @Override
          public boolean onMouseUp(MouseUpEvent event, Element context) {
            buttonDown = false;
            checkButtonView();
            return false;
          }
        });
    handlers.registerMouseComeLeaveHandler(TypeCodes.kind(Type.ADD_PARTICIPANT),
        new WaveMouseComeLeaveHandler() {
          @Override
          public boolean onMouseComeLeave(MouseComeLeaveEvent event, Element context) {
            buttonDown = false;
            checkButtonView();
            return false;
          }
        });
    handlers.registerClickHandler(TypeCodes.kind(Type.PARTICIPANT), new WaveClickHandler() {
          @Override
          public boolean onClick(ClickEvent event, Element context) {
            handleParticipantClicked(context);
            return true;
          }
        });

    panel.getKeyRouter().register(KeyComboManager.getKeyCombosByTask(
        KeyComboContext.WAVE, KeyComboTask.ADD_PARTICIPANT),
        new KeySignalHandler() {
          @Override
          public boolean onKeySignal(KeyCombo key) {
            Element addButton = Document.get().getElementById(WebClient.ADD_PARTICIPANT_BUTTON_ID);
            addParticipant(addButton);
            return false;
          }
        });
  }

  /**
   * Constructs a list of {@link ParticipantId} with the supplied string with comma
   * separated participant addresses. The method will only succeed if all addresses
   * is valid.
   *
   * @param localDomain if provided, automatic suffixing will occur.
   * @param addresses string with comma separated participant addresses
   * @return the array of {@link ParticipantId} instances constructed using the given
   *         addresses string
   * @throws InvalidParticipantAddress if at least one of the addresses failed validation.
   */
  public static ParticipantId[] buildParticipantList(
      @Nullable String localDomain, String addresses) throws InvalidParticipantAddress {
    Preconditions.checkNotNull(addresses, "Expected non-null address");

    String[] addressList = addresses.split(",");
    ParticipantId[] participants = new ParticipantId[addressList.length];

    for (int i = 0; i < addressList.length; i++) {
      String address = addressList[i].trim();

      if (localDomain != null) {
        if (!address.isEmpty() && address.indexOf("@") == -1) {
          // If no domain was specified, assume that the participant is from the local domain.
          address = address + "@" + localDomain;
        } else if (address.equals("@")) {
          // "@" is a shortcut for the shared domain participant.
          address = address + localDomain;
        }
      }

      // Will throw InvalidParticipantAddress if address is not valid
      participants[i] = ParticipantId.of(address);
    }
    return participants;
  }

  private ContactSelectorWidget getSelectorWidget() {
    if (contactSelector == null) {
      contactSelector = new ContactSelectorWidget(contactsView, localDomain);
    }
    return contactSelector;
  }

  /**
   * Shows an add-participant profileUi.
   */
  private void handleAddButtonClicked(final Element addButton) {
    addParticipant(addButton);
  }

  /**
   * Shows a participation profileUi for the clicked participant.
   */
  private void handleParticipantClicked(Element context) {
    ParticipantView participantView = views.asParticipant(context);
    Pair<Conversation, ParticipantId> participation = models.getParticipant(participantView);
    Profile profile = profiles.getProfile(participation.second);

    // Summon a popup view from a participant, and attach profile-popup logic to
    // it.
    ProfilePopupView profileView = participantView.showParticipation();
    final Conversation conversation = participation.getFirst();
    final ParticipantId participantId = participation.getSecond();
    boolean isRemoved = ParticipantState.REMOVED.equals(participantView.getState());
    SafeHtml buttonText = EscapeUtils.fromSafeConstant(isRemoved ? messages.close()
        : messages.remove());
    Command buttonCommand = isRemoved ? null : new Command() {
      
      @Override
      public void execute() {
        conversation.removeParticipant(participantId);
        markAsRead();
      }
    };
    ProfilePopupPresenter profileUi = ProfilePopupPresenter.create(profile, profileView, profiles,
        (ObservableConversation) conversation, buttonText, buttonCommand);
    profileUi.show();
  }

  private void addParticipant(Element addButton) {
    if (addButton != null) {
      ParticipantsView participantsView = views.participantsFromAddButton(addButton);
      final Conversation conversation = models.getConversation(participantsView);
      if (conversation != null) {
        contacts.update();
        getSelectorWidget().setListener(new ContactSelectorWidget.Listener() {
          
          @Override
          public void onSelect(ParticipantId contact) {
            conversation.addParticipant(contact);
          }
        });
        contactSelector.setConversation(conversation);
        contactSelector.showInPopup(addButton);
      }
      markAsRead();
    }
  }

  private void checkButtonView() {
    Element addButton = Document.get().getElementById(WebClient.ADD_PARTICIPANT_BUTTON_ID);
    addButton.setClassName(buttonDown ? css.addButtonPressed() : css.addButton());
  }

  private void markAsRead() {
    SchedulerInstance.getLowPriorityTimer().scheduleDelayed(new Scheduler.Task() {
      
      @Override
      public void execute() {
        supplement.markParticipantsAsRead(waveletId);
      }
    }, MARK_READ_DELAY_MS);
  }
}
