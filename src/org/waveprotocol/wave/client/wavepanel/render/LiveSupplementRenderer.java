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

import org.waveprotocol.wave.client.state.ThreadReadStateMonitor;
import org.waveprotocol.wave.client.wave.InteractiveDocument;
import org.waveprotocol.wave.client.wave.LocalSupplementedWave;
import org.waveprotocol.wave.client.wavepanel.view.BlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationBlip;
import org.waveprotocol.wave.model.supplement.ObservableSupplementedWave;
import org.waveprotocol.wave.model.util.IdentitySet;

/**
 * Listens to supplement updates and update the read state of blips.
 */
public final class LiveSupplementRenderer
    extends ObservableSupplementedWave.ListenerImpl
    implements ThreadReadStateMonitor.Listener {

  private final LocalSupplementedWave supplement;
  private final ModelAsViewProvider views;
  private final ConversationNavigator navigator;

  private LiveSupplementRenderer(LocalSupplementedWave supplement, ModelAsViewProvider views,
      ThreadReadStateMonitor readMonitor, ConversationNavigator navigator) {
    this.supplement = supplement;
    this.views = views;
    this.navigator = navigator;

    readMonitor.addListener(this);
  }

  public static LiveSupplementRenderer create(
      LocalSupplementedWave supplement, ModelAsViewProvider views,
      ThreadReadStateMonitor readMonitor, ConversationNavigator navigator) {
    return new LiveSupplementRenderer(supplement, views, readMonitor, navigator);
  }

  public void init() {
    supplement.addListener(this);
  }

  public void destroy() {
    supplement.removeListener(this);
  }

  @Override
  public void onMaybeBlipReadChanged(ObservableConversationBlip blip) {
    LiveSupplementRenderer.this.renderAsRead(blip, false, false);
  }

  @Override
  public void onMaybeWaveletReadChanged(ObservableConversation conversation) {
    renderAsRead(conversation);
  }

  @Override
  public void onReadStateChanged(IdentitySet<ConversationThread> threads) {
    // do nothing here
  }

  private void renderAsRead(ConversationBlip blip, boolean clearDiffHighlight,
      boolean clearDeletedReplies) {
    boolean isRead = !supplement.isUnread(blip);

    BlipMetaView metaView = views.getBlipMetaView(blip);
    if (metaView != null) {
      metaView.setRead(isRead);
    }

    // clear diffs
    if (clearDiffHighlight && isRead && blip.hasContent()) {
      InteractiveDocument document = blip.getContent();
      document.stopDiffRetention();
      document.clearDiffs(clearDeletedReplies);
      // switch diffs on to display future diffs
      document.startDiffRetention();
    }
  }

  private void renderAsRead(Conversation conversation) {
    for (ConversationBlip blip = navigator.getFirstBlip(conversation.getRootThread());
        blip != null; blip = navigator.getNextBlip(blip)) {
      QuasiConversationBlip quasiBlip = (QuasiConversationBlip) blip;
      if (!quasiBlip.isQuasiDeleted()) {
        renderAsRead(blip, true, true);
      }
    }
  }
}
