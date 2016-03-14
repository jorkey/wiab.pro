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

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Command;

import org.waveprotocol.wave.client.common.util.WaveRefConstants;
import org.waveprotocol.wave.client.editor.content.ContentDocument;
import org.waveprotocol.wave.client.editor.content.ContentElement;
import org.waveprotocol.wave.client.editor.content.ContentNode;
import org.waveprotocol.wave.client.wave.InteractiveDocument;
import org.waveprotocol.wave.client.wavepanel.impl.edit.i18n.ActionMessages;
import org.waveprotocol.wave.client.wavepanel.render.ObservableDynamicRenderer;
import org.waveprotocol.wave.client.wavepanel.render.ScreenPositionScroller;
import org.waveprotocol.wave.client.wavepanel.view.BlipLinkPopupView;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.widget.dialog.DialogBox;
import org.waveprotocol.wave.model.conversation.Blips;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationBlip;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationThread;
import org.waveprotocol.wave.model.conversation.focus.ObservableFocusFramePresenter;
import org.waveprotocol.wave.model.gadget.GadgetConstants;
import org.waveprotocol.wave.model.image.ImageConstants;
import org.waveprotocol.wave.model.img.ImgConstants;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.waveref.WaveRef;
import org.waveprotocol.wave.util.escapers.GwtWaverefEncoder;

import java.util.Iterator;

/**
 * Defines the UI actions that can be performed as part of the editing feature.
 * This includes editing, replying, and deleting blips in a conversation.
 */
public final class ActionsImpl implements Actions {
  
  private static final ActionMessages messages = GWT.create(ActionMessages.class);

  private final ModelAsViewProvider views;
  private final ObservableFocusFramePresenter focusFrame;
  private final ScreenPositionScroller scroller;
  private final EditSession edit;
  private final ObservableDynamicRenderer dynamicRenderer;
  private final ConversationNavigator navigator;

  ActionsImpl(ModelAsViewProvider views, ObservableFocusFramePresenter focus,
      ScreenPositionScroller scroller, EditSession edit, ObservableDynamicRenderer dynamicRenderer,
      ConversationNavigator navigator) {
    this.views = views;
    this.focusFrame = focus;
    this.scroller = scroller;
    this.edit = edit;
    this.dynamicRenderer = dynamicRenderer;
    this.navigator = navigator;

    observe(dynamicRenderer);
  }

  /**
   * Creates an action performer.
   *
   * @param views view provider
   * @param focus focus-frame feature
   * @param scroller screen position scroller
   * @param edit blip-content editing feature
   * @param dynamicRenderer dynamic renderer
   * @param navigator conversation navigator
   * @return action performer.
   */
  public static ActionsImpl create(ModelAsViewProvider views, ObservableFocusFramePresenter focus,
      ScreenPositionScroller scroller, EditSession edit, ObservableDynamicRenderer dynamicRenderer,
      ConversationNavigator navigator) {
    ActionsImpl actions = new ActionsImpl(views, focus, scroller, edit, dynamicRenderer, navigator);
    edit.setActions(actions);
    return actions;
  }

  private BlipLinkPopupView blipLinkPopupView;
  private ConversationBlip blipToFocusAndEdit;

  // Actions

  @Override
  public void startEditing(ConversationBlip blip) {
    focusAndEdit(blip, false);
  }

  @Override
  public void stopEditing(boolean endWithDone) {
    edit.finishEditing(endWithDone);
  }

  @Override
  public void reply(ConversationBlip blip) {
    InteractiveDocument content = (InteractiveDocument) blip.getContent();
    ContentDocument doc = content.getDocument();
    // Insert the reply at a good spot near the current non-collapsed selection,
    // or use the end of the document as a fallback.
    ConversationThread thread = null;
    if (DocumentUtil.hasRangeSelected(doc)) {
      int location = DocumentUtil.getLocationNearSelection(doc);
      thread = getAliveInlineReply(blip, location);
      if (thread == null) {
        thread = blip.addReplyThread(location);
      }
    } else {
      thread = getAliveOutlineReply(blip);
      if (thread == null) {
        thread = blip.addReplyThread();
      }
    }
    ConversationBlip newBlip = thread.appendBlip();
    focusAndEdit(newBlip, true);
  }

  @Override
  public void addBlipToThread(ConversationThread thread) {
    ConversationBlip newBlip = thread.appendBlip();
    focusAndEdit(newBlip, true);
  }

  @Override
  public void addBlipAfter(ConversationBlip blip) {
    ConversationThread thread = blip.getThread();
    ConversationBlip newBlip = thread.insertBlip(blip, false);
    focusAndEdit(newBlip, true);
  }

  @Override
  public void deleteBlip(final ConversationBlip blip, DeleteOption option) {
    QuasiConversationBlip quasiBlip = (QuasiConversationBlip) blip;
    if (quasiBlip.isQuasiDeleted()) {
      return; // cannot delete blip twice
    }
    if (!isEmpty(blip)) {
      switch (option) {
        case DELETE_EMPTY_ONLY:
          return;

        case WITH_CONFIRMATION:
          focusFrame.focus(blip);
          DialogBox.confirm(messages.confirmBlipDeletion(), new Command() {
            
            @Override
            public void execute() {
              deleteBlipInner(blip);
            }
          });
          return;
      }
    }
    deleteBlipInner(blip);
  }

  @Override
  public void deleteParentThread(ConversationBlip blip, DeleteOption option) {
    final ConversationThread thread = blip.getThread();
    
    Preconditions.checkArgument(!thread.isRoot(), "Attempt to delete root thread");
    
    switch (option) {
      case DELETE_EMPTY_ONLY:
        return;

      case WITH_CONFIRMATION:
        focusFrame.focus(blip);
        String message = messages.confirmThreadDeletion();
        if (!doesThreadHaveSingleBlip(thread)) {
          message += "\n" + messages.threadDeletionWarning();
        }
        DialogBox.confirm(message, new Command() {
          
          @Override
          public void execute() {
            deleteThreadInner(thread);
          }
        });
        return;
    }
    deleteThreadInner(thread);
  }

  @Override
  public void popupLink(ConversationBlip blip) {
    //hide previous
    if (blipLinkPopupView != null) {
      blipLinkPopupView.hide();
    }

    focusFrame.focus(blip);
    WaveRef waveRef = WaveRef.of(blip.getWavelet().getWaveId(), blip.getWavelet().getId(), blip.getId());
    String waveRefStringValue = WaveRefConstants.WAVE_URI_PREFIX +
        GwtWaverefEncoder.encodeToUriPathSegment(waveRef);

    BlipView blipView = views.getBlipView(blip);
    blipLinkPopupView = blipView.createLinkPopup();
    blipLinkPopupView.setLinkInfo(waveRefStringValue);
    blipLinkPopupView.show();
  }

  @Override
  public void enterDraftMode() {
    edit.enterDraftMode();
}

  @Override
  public void leaveDraftMode(boolean saveChanges) {
    edit.leaveDraftMode(saveChanges);
  }
  
  // Private methods

  private void observe(ObservableDynamicRenderer dynamicRenderer) {
    dynamicRenderer.addListener(new ObservableDynamicRenderer.ListenerImpl() {

      @Override
      public void onBlipReady(ConversationBlip blip) {
        if (blipToFocusAndEdit == blip) {
          focusAndEditRendered(blip, true);
          blipToFocusAndEdit = null;
        }
      }
    });
  }

  /**
   * Moves focus to a blip, and starts editing it.
   */
  private void focusAndEdit(ConversationBlip blip, boolean scroll) {
    if (dynamicRenderer.isBlipReady(blip)) {
      focusAndEditRendered(blip, scroll);
    } else {
      blipToFocusAndEdit = blip;
    }
  }

  private void focusAndEditRendered(ConversationBlip blip, boolean scroll) {
    edit.stopEditing();
    focusFrame.focus(blip);
    if (scroll) {
      scroller.scrollToBlip(blip);
    }
    edit.startEditing(blip);
  }

  private boolean isEmpty(ConversationBlip blip) {
    if (!blip.getReplyThreads().iterator().hasNext()) {
      return false;
    }
    InteractiveDocument blipDocument = blip.getContent();
    return !containsContent(blipDocument.getDocument().getMutableDoc().getDocumentElement());
  }

  private static boolean containsContent(ContentElement element) {
    for (ContentNode childNode = element.getFirstChild(); childNode != null;
        childNode = childNode.getNextSibling()) {
      if (childNode.isTextNode()) {
        return true;
      }
      if (childNode.isElement()) {
        ContentElement childElement = childNode.asElement();
        switch (childElement.getTagName()) {
          case GadgetConstants.TAGNAME: // gadget
          case ImageConstants.TAGNAME: // attachment
          case ImgConstants.TAGNAME: // img
            return true;
        }
        if (containsContent(childElement) ) {
          return true;
        }
      }
    }
    return false;
  }

  private void deleteBlipInner(ConversationBlip blip) {
    if (blip == focusFrame.getFocusedBlip()) {
      removeFocusFromCurrentBlip();
    }
    ConversationThread thread = blip.getThread();
    if (doesThreadHaveSingleBlip(thread)) {
      deleteThread(thread);
    } else {
      blip.delete();
    }
  }

  private void deleteThreadInner(ConversationThread thread) {
    // If the currently focused blip is predecessor of the thread => remove focus
    ConversationBlip focusedBlip = focusFrame.getFocusedBlip();
    if (navigator.isThreadPredecessorOfBlip(thread, focusedBlip)) {
      removeFocusFromCurrentThread();
    }
    deleteThread(thread);
  }

  private void deleteThread(ConversationThread thread) {
    if (thread.isInline()) {
      // Delete thread's anchor.
      QuasiConversationThread quasiThread = (QuasiConversationThread) thread;
      ConversationBlip parentBlip = quasiThread.getParentBlip().getBaseBlip();
      int beginLocation = getLocationByReply(parentBlip, thread);
      assert beginLocation != Blips.INVALID_INLINE_LOCATION : "Thread location is not found!";      
      int endLocation = beginLocation + Blips.ANCHOR_SIZE;      
      parentBlip.getContent().getMutableDocument().deleteRange(beginLocation, endLocation);
    }
    thread.delete();
  }  
  
  private static ConversationThread getAliveInlineReply(ConversationBlip blip, int location) {
    Iterator<? extends ConversationBlip.LocatedReplyThread> it = blip.locateReplyThreads().iterator();
    while (it.hasNext()) {
      ConversationBlip.LocatedReplyThread locatedThread = it.next();
      if (locatedThread.getLocation() == location) {
        ConversationThread reply = locatedThread.getThread();
        if (!((QuasiConversationThread)reply).isQuasiDeleted()) {
          return reply;
        }
      }
    }
    return null;
  }

  private static int getLocationByReply(ConversationBlip blip, ConversationThread reply) {
    String replyId = reply.getId();
    Iterator<? extends ConversationBlip.LocatedReplyThread> it = blip.locateReplyThreads().iterator();
    while (it.hasNext()) {
      ConversationBlip.LocatedReplyThread locatedThread = it.next();
      if (replyId.equals(locatedThread.getThread().getId()) ) {
        return locatedThread.getLocation();
      }
    }
    return Blips.INVALID_INLINE_LOCATION;
  }

  private static ConversationThread getAliveOutlineReply(ConversationBlip blip) {
    for (ConversationThread reply : blip.getReplyThreads()) {
      if (!reply.isInline() && !((QuasiConversationThread)reply).isQuasiDeleted()) {
        return reply;
      }
    }
    return null;
  }

  private boolean doesThreadHaveSingleBlip(ConversationThread thread) {
    Iterator<? extends ConversationBlip> it = thread.getBlips().iterator();
    if (!it.hasNext()) {
      return false;
    }
    it.next();
    return !it.hasNext();
  }
  
  private void removeFocusFromCurrentBlip() {
    ConversationBlip lastChildBlip = navigator.getLastBlipInBlipTree(focusFrame.getFocusedBlip(), false);
    ConversationBlip nextBlip = navigator.getNextBlip(lastChildBlip);
    if (nextBlip == null) {
      nextBlip = navigator.getPreviousBlip(focusFrame.getFocusedBlip());
    }
    focusFrame.focus(nextBlip);
  }
  
  private void removeFocusFromCurrentThread() {
    ConversationThread thread = focusFrame.getFocusedBlip().getThread();
    ConversationBlip lastThreadBlip = navigator.getLastBlip(thread);
    ConversationBlip lastChildBlip = navigator.getLastBlipInBlipTree(lastThreadBlip, false);
    ConversationBlip nextBlip = navigator.getNextBlip(lastChildBlip);
    if (nextBlip == null) {
      ConversationBlip firstBlip = navigator.getFirstBlip(thread);
      nextBlip = navigator.getPreviousBlip(firstBlip);
    }
    focusFrame.focus(nextBlip);
  }
}
