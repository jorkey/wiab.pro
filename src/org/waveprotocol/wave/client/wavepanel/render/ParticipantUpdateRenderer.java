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
package org.waveprotocol.wave.client.wavepanel.render;

import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Renderer of participants.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class ParticipantUpdateRenderer {

  public static ParticipantUpdateRenderer create(ModelAsViewProvider modelAsViewProvider,
      LiveProfileRenderer profileRenderer, ObservableConversationView conversationView) {
    ParticipantUpdateRenderer tpr = new ParticipantUpdateRenderer(modelAsViewProvider,
        profileRenderer);
    tpr.bindConversation(conversationView.getRoot());
    conversationView.addListener(tpr.conversationViewListener);
    return tpr;
  }
  
  private final ModelAsViewProvider modelAsViewProvider;
  private final LiveProfileRenderer profileRenderer;
  
  private ObservableConversation conversation;
  
  private final ObservableConversation.ParticipantListener participantListener =
      new ObservableConversation.ParticipantListener() {
        
    @Override
    public void onParticipantAdded(ParticipantId participant, WaveletOperationContext opContext) {
      modelAsViewProvider.getParticipantsView(conversation).appendParticipant(conversation,
          participant, opContext, isDiff(opContext));
      profileRenderer.monitorParticipation(conversation, participant);
    }

    @Override
    public void onParticipantRemoved(ParticipantId participant, WaveletOperationContext opContext) {
      modelAsViewProvider.getParticipantsView(conversation).removeParticipant(conversation,
          participant, opContext, isDiff(opContext));
      // Removed participant may be of interest, too (as a quasi-deleted).
      profileRenderer.monitorParticipation(conversation, participant);
    }        
  };
  
  private final ObservableConversationView.Listener conversationViewListener =
      new ObservableConversationView.Listener() {

    @Override
    public void onConversationAdded(ObservableConversation conversation) {
      if (conversation.isRoot()) {
        bindConversation(conversation);
      }
    }

    @Override
    public void onConversationRemoved(ObservableConversation conversation) {
      if (conversation.isRoot()) {
        unbindConversation();
      }
    }
  };
  
  //
  // Private methods
  //
  
  private ParticipantUpdateRenderer(ModelAsViewProvider modelAsViewProvider,
      LiveProfileRenderer profileRenderer) {
    this.modelAsViewProvider = modelAsViewProvider;
    this.profileRenderer = profileRenderer;
  }  
  
  private void bindConversation(ObservableConversation conversation) {
    if (conversation != null) {
      conversation.addParticipantListener(participantListener);

      this.conversation = conversation;
    }  
  }
  
  private void unbindConversation() {
    if (conversation != null) {
      conversation.removeParticipantListener(participantListener);
      conversation = null;
    }
  }
  
  private static boolean isDiff(WaveletOperationContext opContext) {
    return opContext != null && !opContext.isAdjust() && opContext.hasSegmentVersion();
  }
}
