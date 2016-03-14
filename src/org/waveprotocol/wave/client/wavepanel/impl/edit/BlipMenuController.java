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

package org.waveprotocol.wave.client.wavepanel.impl.edit;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ContextMenuEvent;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Timer;

import org.waveprotocol.wave.client.common.util.KeyComboContext;
import org.waveprotocol.wave.client.common.util.KeyComboManager;
import org.waveprotocol.wave.client.common.util.KeyComboTask;
import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.event.EventDispatcherPanel;
import org.waveprotocol.wave.client.wavepanel.event.EventHandlerRegistry;
import org.waveprotocol.wave.client.wavepanel.event.MouseComeLeaveEvent;
import org.waveprotocol.wave.client.wavepanel.event.WaveClickHandler;
import org.waveprotocol.wave.client.wavepanel.event.WaveContextMenuHandler;
import org.waveprotocol.wave.client.wavepanel.event.WaveDoubleClickHandler;
import org.waveprotocol.wave.client.wavepanel.event.WaveMouseComeLeaveHandler;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomUtil;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TypeCodes;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.i18n.BlipMessages;
import org.waveprotocol.wave.client.widget.menu.PopupMenu;
import org.waveprotocol.wave.client.widget.popup.RelativePopupPositioner;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;

/**
 * Controller of the blip's menu.
 */
public class BlipMenuController implements WaveMouseComeLeaveHandler, WaveClickHandler,
    WaveDoubleClickHandler, WaveContextMenuHandler {

  /** Menu showing delay in ms. */
  private final static int SHOW_MENU_DELAY_MS = 100;  
  
  private static final BlipMessages messages = GWT.create(BlipMessages.class);

  /**
   * Creates and installs the blipView popupMenu feature.
   */
  public static void install(WavePanel panel, ModelAsViewProvider modelProvider, Actions actions,
      ConversationNavigator navigator) {
    BlipMenuController controller = new BlipMenuController(actions, panel, modelProvider,
        navigator);
    EventHandlerRegistry handlers = panel.getHandlers();
    handlers.registerMouseComeLeaveHandler(TypeCodes.kind(Type.BLIP_TIME), controller);
    handlers.registerMouseComeLeaveHandler(TypeCodes.kind(Type.BLIP_MENU_BUTTON), controller);
    handlers.registerClickHandler(TypeCodes.kind(Type.BLIP_TIME), controller);
    handlers.registerClickHandler(TypeCodes.kind(Type.BLIP_MENU_BUTTON), controller);
    handlers.registerDoubleClickHandler(TypeCodes.kind(Type.BLIP), controller);
  }

  private final Actions actions;
  private final DomAsViewProvider viewProvider;
  private final ModelAsViewProvider modelProvider;
  private final ConversationNavigator navigator;

  private PopupMenu popupMenu;
  private Element menuContextElement;
  private NativeEvent menuContextEvent;

  private final Timer showTimer = new Timer() {

    @Override
    public void run() {
      showPopupMenu();
    }
  };

  private boolean editSessionStarted;

  // Walkaround of GWT's bug (when popup is too narrow at the first appearance).
  private boolean inited = false;

  BlipMenuController(Actions actions, WavePanel panel, ModelAsViewProvider modelProvider,
      ConversationNavigator navigator) {
    this.actions = actions;
    this.viewProvider = panel.getViewProvider();
    this.modelProvider = modelProvider;
    this.navigator = navigator;
  }

  @Override
  public boolean onClick(ClickEvent event, Element element) {
    if (DomUtil.isQuasiDeleted(element)) {
      return false;
    }

    if (!isEnabled()) {
      return false;
    }
    menuContextElement = getMenuContext(element);
    menuContextEvent = event.getNativeEvent();
    hideAndScheduleShowPopupMenu();
    event.stopPropagation();
    event.preventDefault();
    return true;
  }

  @Override
  public boolean onMouseComeLeave(MouseComeLeaveEvent event, Element element) {
    // Ignore deleted blip.
    if (DomUtil.isQuasiDeleted(element)) {
      return false;
    }

    // Ignore mouse moving between 'bliptime' and 'blipmenu' elements.
    Element thisMenuContext = getMenuContext(element);
    Element anotherMenuContext = getMenuContext(event.getAnotherElement());
    if (thisMenuContext == anotherMenuContext) {
      return false;
    }

    // Ignore mouse coming with pressed left button.
    if (event.isCome() &&
        EventDispatcherPanel.areMouseButtonsPressed(NativeEvent.BUTTON_LEFT)) {
      return false;
    }

    menuContextElement = thisMenuContext;
    menuContextEvent = event.getNativeEvent();

    if(event.isCome() && isEnabled()) {
      hideAndScheduleShowPopupMenu();
    } else {
      hidePopupMenu();
    }
    return true;
  }

  @Override
  public boolean onContextMenu(ContextMenuEvent event, Element context) {
    // Ignore quasi-deleted or disabled blip.
    if (DomUtil.isQuasiDeleted(context) || !isEnabled()) {
      return false;
    }

    menuContextElement = context;
    menuContextEvent = event.getNativeEvent();
    hideAndScheduleShowPopupMenu();
    event.stopPropagation();
    event.preventDefault();
    return true;
  }

  @Override
  public boolean onDoubleClick(DoubleClickEvent event, Element context) {
    // Ignore empty or disabled blip.
    if (DomUtil.findFirstChildElement(context, Type.META) == null || !isEnabled()) {
      return false;
    }

    // Ignore deleted blip.
    if (DomUtil.isQuasiDeleted(context)) {
      return true;
    }

    menuContextElement = context;
    menuContextEvent = event.getNativeEvent();
    hideAndScheduleShowPopupMenu();
    event.stopPropagation();
    event.preventDefault();
    return true;
  }

  private Element getMenuContext(Element e) {
    if (DomUtil.doesElementHaveType(e, Type.BLIP_MENU_BUTTON)) {
      return e;
    }
    if (DomUtil.doesElementHaveType(e, Type.BLIP_TIME)) {
      return DomUtil.findFirstSiblingElement(e, Type.BLIP_MENU_BUTTON);
    }
    return null;
  }

  private void hideAndScheduleShowPopupMenu() {
    hidePopupMenu();
    showTimer.schedule(SHOW_MENU_DELAY_MS);
  }

  private void hidePopupMenu() {
    showTimer.cancel();
    if (popupMenu != null) {
      popupMenu.hide();
      popupMenu = null;
    }
  }

  private void showPopupMenu() {
    if (popupMenu == null) {
      popupMenu = createPopupMenu();
      popupMenu.show();
    }
  }

  private PopupMenu createPopupMenu() {
    final PopupMenu menu = new PopupMenu(menuContextElement, new RelativePopupPositioner() {

      @Override
      public void setPopupPositionAndMakeVisible(Element relative, Element popup) {
        int left, top;
        if(DomUtil.doesElementHaveType(
            menuContextElement, Type.BLIP_MENU_BUTTON)) {
          int popupWidth = popup.getOffsetWidth();
          left = relative.getAbsoluteRight() - popupWidth;
          top = relative.getAbsoluteBottom();
        }  else {
          left = menuContextEvent.getClientX();
          top = menuContextEvent.getClientY();
        }
        Style popupStyle = popup.getStyle();
        popupStyle.setLeft(left, Style.Unit.PX);
        popupStyle.setTop(top, Style.Unit.PX);
        popupStyle.setPosition(Style.Position.FIXED);
        popupStyle.setVisibility(Style.Visibility.VISIBLE);
      }
    });

    BlipView blipView = getBlip();
    final ConversationBlip blip = modelProvider.getBlip(blipView);
    boolean beingEdited = blipView.isBeingEdited();
    boolean focused = blipView.isFocused();

    // "Edit" popup menu item.
    if (!beingEdited) {
      menu.addItem(messages.edit(), ((focused && inited) ? KeyComboManager.getFirstKeyComboHintByTask(
          KeyComboContext.WAVE, KeyComboTask.EDIT_BLIP) : null), new Command() {

            @Override
            public void execute() {
              actions.startEditing(blip);
              menu.hide();
            }
          }, true);
    }

    // "Add" popup menu item.
    if (!beingEdited) {
      String text = navigator.isBlipLastInParentThread(blip) ? messages.add() : messages.insert();
      menu.addItem(text, ((focused && inited) ? KeyComboManager.getFirstKeyComboHintByTask(
          KeyComboContext.TEXT_EDITOR, KeyComboTask.CANCEL_EDITING) : null), new Command() {

            @Override
            public void execute() {
              actions.addBlipAfter(blip);
              menu.hide();
            }
          }, true);
    }

    // "Reply" popup menu item.
    if (!beingEdited) {
      menu.addItem(messages.reply(), ((focused && inited) ? KeyComboManager.getFirstKeyComboHintByTask(
          KeyComboContext.WAVE, KeyComboTask.REPLY_TO_BLIP) : null), new Command() {

            @Override
            public void execute() {
              actions.reply(blip);
              menu.hide();
            }
          }, true);
    }

    // "Done" popup menu item.
    if (beingEdited) {
      menu.addItem(messages.done(), ((focused && inited) ? KeyComboManager.getFirstKeyComboHintByTask(
          KeyComboContext.TEXT_EDITOR, KeyComboTask.DONE_WITH_EDITING) : null), new Command() {

            @Override
            public void execute() {
              actions.stopEditing(true);
              menu.hide();
            }
          }, true);
    }

    // "Delete" popup menu item.
    menu.addItem(messages.delete(), ((focused && inited) ? KeyComboManager.getFirstKeyComboHintByTask(
        KeyComboContext.WAVE, KeyComboTask.DELETE_BLIP) : null), new Command() {

          @Override
          public void execute() {
            actions.deleteBlip(blip, Actions.DeleteOption.WITH_CONFIRMATION);
            menu.hide();
          }
        }, true);

    // "Delete thread" popup menu item.
    ConversationThread thread = blip.getThread();
    if (!thread.isRoot() && thread.getFirstBlip() == blip &&
        navigator.getChildBlipCount(thread) > 1) {
      menu.addItem(messages.deleteThread(), null, new Command() {

        @Override
        public void execute() {
          actions.deleteParentThread(blip, Actions.DeleteOption.WITH_CONFIRMATION);
          menu.hide();
        }
      }, true);
    }

    // "Link" popup menu item.
    if (!beingEdited) {
      menu.addItem(messages.link(), ((focused && inited) ? KeyComboManager.getFirstKeyComboHintByTask(
          KeyComboContext.WAVE, KeyComboTask.POPUP_LINK) : null), new Command() {

            @Override
            public void execute() {
              actions.popupLink(blip);
              menu.hide();
            }
          }, true);
    }

    inited = true;

    return menu;
  }

  private BlipView getBlip() {
    BlipView blipView;
    if(DomUtil.doesElementHaveType(menuContextElement, Type.BLIP_MENU_BUTTON)) {
      blipView = getBlipByMenuButton(menuContextElement);
    } else {
      blipView = viewProvider.asBlip(menuContextElement);
    }
    return blipView;
  }

  private BlipView getBlipByMenuButton(Element menuButton) {
    Element eTimeArrow = menuButton.getParentElement();
    Element eMetaBar = eTimeArrow.getParentElement();
    Element eMeta = eMetaBar.getParentElement();
    Element eBlip = eMeta.getParentElement();
    return viewProvider.asBlip(eBlip);
  }

  private boolean isEnabled() {
    return !editSessionStarted || getBlip().isFocused();
  }
}
