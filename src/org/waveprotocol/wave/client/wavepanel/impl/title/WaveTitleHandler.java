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

package org.waveprotocol.wave.client.wavepanel.impl.title;

import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.editor.EditorContext;
import org.waveprotocol.wave.client.editor.EditorUpdateEvent;
import org.waveprotocol.wave.client.editor.EditorUpdateEvent.EditorUpdateListener;
import org.waveprotocol.wave.client.wavepanel.impl.edit.EditSession;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.TitleHelper;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversation;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationBlip;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationView;
import org.waveprotocol.wave.model.document.MutableDocument;
import org.waveprotocol.wave.model.document.util.Range;
import org.waveprotocol.wave.model.util.Preconditions;

/**
 * Handles automatic setting of the wave title if the following conditions are true:
 * <ol>
 * <li>The edited blip is a root blip.</li>
 * <li>The edited line is the first line.</li>
 * <li>No explicit title for this wave is set.</li>
 * </ol>
 *
 * @author yurize@apache.org (Yuri Zelikov)
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public final class WaveTitleHandler {

  private final ConversationNavigator navigator;
  
  /**
   * Listener to document update in the editor.
   * Applied only to the 1st blip in the conversation.
   */
  private final EditorUpdateListener editorUpdateListener = new EditorUpdateListener() {

    @Override
    public void onUpdate(EditorUpdateEvent event) {
      EditorContext editor = event.context();
      if (event.contentChanged() && editor.isEditing()) {
        maybeSetOrUpdateTitle(editor.getDocument());
      }
    }
  };

  private Conversation conversation;
  
  /**
   * Listener to removing of the 1st blip in the conversation.
   */
  private final ObservableQuasiConversation.BlipListener blipListener =
      new ObservableQuasiConversation.BlipListener.Impl() {

    @Override
    public void onBeforeBlipRemoved(ObservableQuasiConversationBlip blip) {
      if (blip == navigator.getFirstBlip(conversation.getRootThread())) {
        ConversationBlip nextBlip = navigator.getNextBlipInRow(blip);
        if (nextBlip != null && nextBlip.isContentInitialized()) {
          maybeSetOrUpdateTitle(nextBlip.getDocument());
        }
      }
    }
  };
  
  public static WaveTitleHandler install(ConversationNavigator navigator, EditSession editSession,
      ObservableQuasiConversationView conversationView) {
    WaveTitleHandler waveTitleHandler = new WaveTitleHandler(navigator, conversationView);
    editSession.addListener(waveTitleHandler.getEditSessionListener());
    conversationView.addListener(waveTitleHandler.getConversationViewListener());
    return waveTitleHandler;
  }

  private WaveTitleHandler(ConversationNavigator navigator,
      ObservableQuasiConversationView conversationView) {
    this.navigator = navigator;
    
    if (conversationView.getRoot() != null) {
      observeConversation(conversationView.getRoot());
    }
  }

  /**
   * @return listener to session start/finish.
   */
  public EditSession.Listener getEditSessionListener() {
    return new EditSession.Listener() {

      @Override
      public void onSessionStart(Editor editor, ConversationBlip blip) {
        if (blip.getThread().isRoot() && navigator.isBlipFirstInParentThread(blip)) {
          editor.addUpdateListener(editorUpdateListener);
        }        
      }

      @Override
      public void onSessionEnd(Editor editor, ConversationBlip blip) {
        if (blip.getThread().isRoot() && navigator.isBlipFirstInParentThread(blip)) {
          editor.removeUpdateListener(editorUpdateListener);
        }        
      }
    };
  }
  
  /**
   * @return listener to conversation adding/removing.
   */
  public ObservableQuasiConversationView.Listener getConversationViewListener() {
    return new ObservableQuasiConversationView.Listener() {

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
  }
  
  /**
   * Sets or replaces an automatic title for the wave by annotating the first
   * line of the root blip with <code>conv/title</code> annotation. Has
   * effect only when the first line of the root blip is edited and no explicit
   * title is set.
   */
  private void maybeSetOrUpdateTitle(MutableDocument document) {
    if (!TitleHelper.hasExplicitTitle(document)) {
      Range titleRange = TitleHelper.findImplicitTitle(document);
      TitleHelper.setImplicitTitle(document, titleRange.getStart(), titleRange.getEnd());
    }
  }
  
  private void observeConversation(ObservableQuasiConversation conversation) {
    Preconditions.checkNotNull(conversation, "null conversation");
    Preconditions.checkArgument(conversation.isRoot(), "not root conversation");
    
    conversation.addBlipListener(blipListener);
    this.conversation = conversation;
  }
  
  private void unobserveConversation(ObservableQuasiConversation conversation) {
    Preconditions.checkNotNull(conversation, "null conversation");
    Preconditions.checkArgument(conversation.isRoot(), "not root conversation");
    
    conversation.removeBlipListener(blipListener);
    this.conversation = conversation;
  }  
}
