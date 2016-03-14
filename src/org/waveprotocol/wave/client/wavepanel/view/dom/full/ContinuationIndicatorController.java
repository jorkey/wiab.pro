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

package org.waveprotocol.wave.client.wavepanel.view.dom.full;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.Timer;

import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.event.EventDispatcherPanel;
import org.waveprotocol.wave.client.wavepanel.event.EventHandlerRegistry;
import org.waveprotocol.wave.client.wavepanel.event.MouseComeLeaveEvent;
import org.waveprotocol.wave.client.wavepanel.event.WaveClickHandler;
import org.waveprotocol.wave.client.wavepanel.event.WaveMouseComeLeaveHandler;
import org.waveprotocol.wave.client.wavepanel.impl.edit.Actions;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomUtil;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.i18n.BlipMessages;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;

/**
 * Controller of continuation indicator containing "Reply" and "Continue" buttons
 * and dynamically attached to the blip when mouse is over its bottom border.
 *
 * @author Denis Konovalchik (dyukon@gmail.com)
 */
public final class ContinuationIndicatorController implements WaveMouseComeLeaveHandler,
    WaveClickHandler {

  /**
   * Creates and installs the context menu feature.
   */
  public static void install(WavePanel panel, ModelAsViewProvider modelProvider,
      ConversationNavigator navigator, Actions actions) {
    DomAsViewProvider domAsViewProvider = panel.getViewProvider();
    ContinuationIndicatorController controller = new ContinuationIndicatorController(
        domAsViewProvider, modelProvider, navigator, actions);
    
    EventHandlerRegistry handlers = panel.getHandlers();
    handlers.registerMouseComeLeaveHandler(TypeCodes.kind(Type.BLIP_CONTINUATION_BAR), controller);
    handlers.registerMouseComeLeaveHandler(TypeCodes.kind(Type.CONTINUATION_BAR), controller);
    handlers.registerMouseComeLeaveHandler(TypeCodes.kind(Type.CONTINUATION_BUTTON), controller);
    handlers.registerMouseComeLeaveHandler(TypeCodes.kind(Type.CONTINUATION_TEXT), controller);
    handlers.registerClickHandler(TypeCodes.kind(Type.CONTINUATION_BUTTON), controller);
  }

  private final DomAsViewProvider domAsViewProvider;
  private final ModelAsViewProvider modelAsViewProvider;
  private final ConversationNavigator navigator;
  private final Actions actions;  
  private final ContinuationIndicatorViewBuilder.Css css;
  private final BlipMessages messages;

  private final Element indicator;
  private final Element addButton;
  private final Element replyButton;
  private final Element line;

  private BlipView currentBlipView;
  private boolean buttonsShown;
  private Element blipContinuationBar;

  private static final int SHOW_BUTTONS_TIMER_DELAY_MS = 300; //timer delay in miliseconds
  private final Timer showButtonsTimer = new Timer() {

    @Override
    public void run() {
      showButtons();
    }
  };

  ContinuationIndicatorController(DomAsViewProvider domAsViewProvider,
      ModelAsViewProvider modelAsViewProvider, ConversationNavigator navigator, Actions actions) {
    this.domAsViewProvider = domAsViewProvider;
    this.modelAsViewProvider = modelAsViewProvider;
    this.navigator = navigator;
    this.actions = actions;
    css = WavePanelResourceLoader.getContinuationIndicator().css();
    messages = WavePanelResourceLoader.getBlipMessages();

    indicator = getElement(ContinuationIndicatorViewBuilder.CONTINUATION_BAR_ID);
    addButton = getElement(ContinuationIndicatorViewBuilder.CONTINUATION_ADD_BUTTON_ID);
    replyButton = getElement(ContinuationIndicatorViewBuilder.CONTINUATION_REPLY_BUTTON_ID);
    line = getElement(ContinuationIndicatorViewBuilder.CONTINUATION_LINE_ID);
  }

  @Override
  public boolean onMouseComeLeave(MouseComeLeaveEvent event, Element context) {
    //ignore event with pressed left button
    if (EventDispatcherPanel.areMouseButtonsPressed(NativeEvent.BUTTON_LEFT)) {
      return false;
    }

    if (event.isCome()) {
      Element another = event.getAnotherElement();
      if (!isHotElement(another) &&
          Type.BLIP_CONTINUATION_BAR.equals(event.getContextElementType()) ) {
        Element metaElement = context.getParentElement();
        Element blipElement = metaElement.getParentElement();

        if (DomUtil.isQuasiDeleted(blipElement)) {
          return false;
        }
        currentBlipView = domAsViewProvider.asBlip(blipElement);

        blipContinuationBar = context;
        attachIndicator();
        showButtonsTimer.schedule(SHOW_BUTTONS_TIMER_DELAY_MS);
      }
    } else {
      Element another = event.getAnotherElement();
      if (!isHotElement(another)) {
        currentBlipView = null;
        detachIndicator();
      }
    }

    return true;
  }

  @Override
  public boolean onClick(ClickEvent event, Element button) {
    //ignore clicks if indicator isn't attached
    if (!buttonsShown) {
      return false;
    }

    //ignore not left-element clicks
    if (event.getNativeButton() != NativeEvent.BUTTON_LEFT) {
      return false;
    }

    ConversationBlip currentBlip = modelAsViewProvider.getBlip(currentBlipView);
    if (button.getId().equals(ContinuationIndicatorViewBuilder.CONTINUATION_REPLY_BUTTON_ID)) {
      actions.reply(currentBlip); //add reply to the blip
    } else {
      actions.addBlipAfter(currentBlip); //add continuation to the blip
    }

    detachIndicator();
    event.stopPropagation();
    event.preventDefault();
    return true;
  }

  private void attachIndicator() {
    // hide buttons, show line
    String invisibleButtonClass = css.button() + " " + css.buttonInvisible();
    addButton.setClassName(invisibleButtonClass);
    replyButton.setClassName(invisibleButtonClass);
    line.setClassName(css.line());

    Element panel = indicator.getParentElement();
    int left = blipContinuationBar.getAbsoluteLeft() - panel.getAbsoluteLeft();
    int top = blipContinuationBar.getAbsoluteTop() - panel.getAbsoluteTop();
    int right = panel.getAbsoluteRight() - blipContinuationBar.getAbsoluteRight();
    indicator.setAttribute("style", "left:" + left + "px;top:" + top + "px;right:" + right);
    indicator.setClassName(css.indicator() + " " + css.indicatorVisible());
  }

  private void showButtons() {
    // hide line, show buttons
    line.setClassName(css.line() + " " + css.lineInvisible());
    String visibleButtonClass = css.button() + " " + css.buttonVisible();
    addButton.setClassName(visibleButtonClass);
    replyButton.setClassName(visibleButtonClass);

    Element metaElement = blipContinuationBar.getParentElement();
    Element blipElement = metaElement.getParentElement();
    BlipView blipView = domAsViewProvider.asBlip(blipElement);
    ConversationBlip blip = modelAsViewProvider.getBlip(blipView);
    Element addButtonText = DomUtil.findFirstChildElement(addButton, Type.CONTINUATION_TEXT);
    addButtonText.setInnerText(
        blip != null && navigator.isBlipLastInParentThread(blip) ? messages.add() : messages.insert());

    buttonsShown = true;
  }

  private void detachIndicator() {
    showButtonsTimer.cancel();
    indicator.setClassName(css.indicator() + " " + css.indicatorInvisible());
    String invisibleButtonClass = css.button() + " " + css.buttonInvisible();
    addButton.setClassName(invisibleButtonClass);
    replyButton.setClassName(invisibleButtonClass);

    buttonsShown = false;
  }

  private boolean isHotElement(Element element) {
    Type type = DomUtil.getElementType(element);
    if (type != null) {
      switch (type) {
        case BLIP_CONTINUATION_BAR:
        case CONTINUATION_BAR:
        case CONTINUATION_BUTTON:
        case CONTINUATION_TEXT:
        case CONTINUATION_LINE:
          return true;
      }
    }
    return false;
  }

  private Element getElement(String id) {
    return Document.get().getElementById(id);
  }
}