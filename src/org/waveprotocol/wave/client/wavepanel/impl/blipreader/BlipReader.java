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

package org.waveprotocol.wave.client.wavepanel.impl.blipreader;

import com.google.common.annotations.VisibleForTesting;

import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.client.render.undercurrent.ScreenController;
import org.waveprotocol.wave.client.wave.InteractiveDocument;
import org.waveprotocol.wave.client.wave.LocalSupplementedWave;
import org.waveprotocol.wave.client.wavepanel.render.DynamicRenderer;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.conversation.BlipIterators;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.conversation.WaveletBasedConversation;
import org.waveprotocol.wave.model.conversation.focus.FocusFramePresenter;
import org.waveprotocol.wave.model.conversation.focus.ObservableFocusFramePresenter;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversation;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationView;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversation;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationBlip;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationThread;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationView;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.supplement.ObservableSupplementedWave;
import org.waveprotocol.wave.model.util.Preconditions;

/**
 * Inteprets focus-frame movement as reading actions, and also provides an
 * ordering for focus frame movement, based on unread content.
 */
public final class BlipReader {

  protected static LoggerBundle LOG = new DomLogger("reader");

  /**
   * Builds the reader.
   *
   * @param supplement the supplement
   * @param navigator the conversation navigator
   * @param focusFrame focus frame presenter
   * @param dynamicRenderer dynamic renderer
   * @param conversationView conversation view
   * @return the reader
   */
  public static BlipReader create(LocalSupplementedWave supplement, ConversationNavigator navigator,
      ObservableFocusFramePresenter focusFrame, DynamicRenderer dynamicRenderer,
      ObservableQuasiConversationView conversationView) {
    BlipReader blipReader = new BlipReader(supplement, navigator, dynamicRenderer);
    blipReader.initialize(focusFrame, conversationView);
    return blipReader;
  }

  private final LocalSupplementedWave supplement;
  private final ConversationNavigator navigator;
  private final DynamicRenderer dynamicRenderer;
  
  private QuasiConversationView conversationView;

  private final FocusFramePresenter.FocusOrder focusOrder = new FocusFramePresenter.FocusOrder() {

    @Override
    public ConversationBlip getPreviousUnread(ConversationBlip startBlip) {
      ConversationBlip nextBlip = startBlip;
      do {
        nextBlip = navigator.getPreviousBlip(nextBlip);
      } while (nextBlip != null && !supplement.isUnread(nextBlip));
      return nextBlip;
    }

    @Override
    public ConversationBlip getNextUnread(ConversationBlip startBlip) {
      ConversationBlip nextBlip = startBlip;
      do {
        nextBlip = navigator.getNextBlip(nextBlip);
      } while (nextBlip != null && !supplement.isUnread(nextBlip));
      return nextBlip;
    }
  };
  
  private final ObservableFocusFramePresenter.Listener focusListener =
      new ObservableFocusFramePresenter.Listener() {

    @Override
    public void onFocusOut(ConversationBlip oldFocused) {
      updateReading();
      focusedBlip = null;
    }

    @Override
    public void onFocusIn(ConversationBlip newFocused) {
      focusedBlip = newFocused;
      updateReading();
    }
  };

  private final ObservableQuasiConversationView.Listener conversationViewListener =
      new ObservableQuasiConversationView.Listener() {

    @Override
    public void onConversationAdded(ObservableQuasiConversation conversation) {
      if (conversation.isRoot()) {
        observeConversation(conversation);
      }
    }

    @Override
    public void onConversationRemoved(ObservableQuasiConversation conversation) {
      if (conversation.isRoot()) {
        unobserveConversation(conversation);
      }
    }
  };

  // Base conversation listener is needed because we need context to determine
  // if the blip was added locally or remotely.
  ObservableConversation.BlipListener blipListener = new ObservableConversation.BlipListener() {

    @Override
    public void onBlipAdded(ObservableConversationBlip blip, WaveletOperationContext opContext) {
      if (opContext != null && !supplement.isBlipLooked(blip)) {
        supplement.firstLookBlip(blip);
      }
    }

    @Override
    public void onBeforeBlipRemoved(ObservableConversationBlip blip,
        WaveletOperationContext opContext) {}
    
    @Override
    public void onBlipRemoved(ObservableConversationBlip blip, WaveletOperationContext opContext) {}
  };

  private final ObservableSupplementedWave.Listener supplementListener =
      new ObservableSupplementedWave.ListenerImpl() {

    @Override
    public void onMaybeBlipReadChanged(ObservableConversationBlip blip) {
//      if (blip == readingBlip) {
//        updateReading();
//      }
    }
  };
  
  private ConversationBlip focusedBlip;
  private ConversationBlip readingBlip;

  @VisibleForTesting
  BlipReader(final LocalSupplementedWave supplement, ConversationNavigator navigator,
      DynamicRenderer dynamicRenderer) {
    this.supplement = supplement;
    this.navigator = navigator;
    this.dynamicRenderer = dynamicRenderer;
    
    supplement.addListener(supplementListener);
  }

  public void initialize(ObservableFocusFramePresenter focusFrame,
      ObservableQuasiConversationView conversationView) {
    this.conversationView = conversationView;
    
    focusFrame.setOrder(focusOrder);
    focusFrame.addListener(focusListener);

    conversationView.addListener(conversationViewListener);
    if (conversationView.getRoot() != null) {
      observeConversation(conversationView.getRoot());
    }
  }

  public boolean isRead(ConversationBlip blip) {
    return !supplement.isUnread(blip);
  }

  public void read() {
    supplement.markAsRead();
    
    for (QuasiConversation conversation : conversationView.getConversations()) {
      conversation.terminateQuasiDeleted();
    }
  }

  public void read(ConversationBlip blip) {
    // Only blip without child quasi-deleted inner replies can be read
    if (!hasQuasiDeletedInlineReplies(blip)) {
      supplement.markAsRead(blip);
      clearDiffs(blip);
    }    
  }

  public ScreenController.Listener getScreenListener() {
    return new ScreenController.Listener() {

      @Override
      public void onScreenChanged() {
        updateReading();
      }
    };
  }

  public void look(ObservableConversationView conversationView) {
    for (Conversation conversation : conversationView.getConversations()) {
      WaveletId waveletId = WaveletBasedConversation.widFor(conversation.getId());
      if (!supplement.isWaveletLooked(waveletId)) {
        supplement.firstLookWavelet(waveletId);
      } else {
        for (ConversationBlip blip : BlipIterators.breadthFirst(conversation)) {
          if (!supplement.isBlipLooked(blip)) {
            supplement.firstLookBlip(blip);
          }
        }
      }
    }
  }

  // Private methods

  private void updateReading() {
    // Stop reading, if necessary.
    if (readingBlip != null && (readingBlip != focusedBlip || !shouldRead(readingBlip))) {
      stopReading();
    }

    // Start reading, if necessary.
    if (readingBlip == null && (focusedBlip != null && shouldRead(focusedBlip))) {
      startReading(focusedBlip);
    }
  }

  private boolean shouldRead(ConversationBlip blip) {
    return dynamicRenderer.isBlipReady(focusedBlip) &&
        dynamicRenderer.isBlipVisible(focusedBlip) &&
        // Only blip without child quasi-deleted inner replies can be read
        !hasQuasiDeletedInlineReplies(focusedBlip);
  }
  
  private void stopReading() {
    LOG.trace().log("stop reading blip: " + readingBlip);

    supplement.stopReading(readingBlip);
    clearDiffs(readingBlip);
    readingBlip = null;
  }

  private void startReading(ConversationBlip blip) {
    readingBlip = blip;
    LOG.trace().log("start reading blip: " + readingBlip);

    supplement.startReading(readingBlip);
    if (!((QuasiConversationBlip)readingBlip).isQuasiDeleted() && readingBlip.hasContent()) {
      InteractiveDocument document = readingBlip.getContent();
      document.startDiffRetention();
    }
  }

  private void clearDiffs(ConversationBlip blip) {
    if (blip.hasContent()) {
      InteractiveDocument document = blip.getContent();
      document.stopDiffRetention();
      document.clearDiffs(false);
    }
  }

  private boolean hasQuasiDeletedInlineReplies(ConversationBlip blip) {
    QuasiConversationBlip quasiBlip = (QuasiConversationBlip) blip;
    for (QuasiConversationThread quasiReply : quasiBlip.getReplyThreads()) {
      if (quasiReply.isInline() && quasiReply.isQuasiDeleted()) {
        return true;
      }
    }
    return false;
  }  
  
  private void observeConversation(ObservableQuasiConversation conversation) {
    Preconditions.checkArgument(conversation.isRoot(), "Conversation must be root");

    conversation.addBlipListener(blipListener);
  }

  private void unobserveConversation(ObservableQuasiConversation conversation) {
    Preconditions.checkArgument(conversation.isRoot(), "Conversation must be root");

    conversation.removeBlipListener(blipListener);
  }
}
