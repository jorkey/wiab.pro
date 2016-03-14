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

package org.waveprotocol.wave.model.conversation.quasi;

import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.conversation.focus.ObservableFocusFramePresenter;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.StringMap;
import org.waveprotocol.wave.model.supplement.SupplementedWave;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Implementation of conversation view with quasi-deletion support.
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class QuasiConversationViewImpl implements ObservableQuasiConversationView {

  public static QuasiConversationViewImpl create(ObservableFocusFramePresenter focusFrame) {
    return new QuasiConversationViewImpl(focusFrame);
  }

  private final ObservableFocusFramePresenter focusFrame;  

  private ObservableConversationView baseConversationView;
  private SupplementedWave supplement;  

  private final List<ObservableQuasiConversation> conversationList = new ArrayList<>();
  private final StringMap<ObservableQuasiConversation> conversationMap =
      CollectionUtils.createStringMap();
  private final CopyOnWriteSet<ObservableConversationView.Listener> baseListeners =
      CopyOnWriteSet.create();
  private final CopyOnWriteSet<Listener> quasiListeners = CopyOnWriteSet.create();

  private final ObservableConversationView.Listener conversationViewListener =
      new ObservableConversationView.Listener() {

    @Override
    public void onConversationAdded(ObservableConversation conversation) {
      ObservableQuasiConversation quasiConversation = QuasiConversationImpl.create(conversation,
          supplement, focusFrame);
      conversationList.add(quasiConversation);
      conversationMap.put(conversation.getId(), quasiConversation);

      triggerOnConversationAdded(quasiConversation);
    }

    @Override
    public void onConversationRemoved(ObservableConversation conversation) {
      ObservableQuasiConversation quasiConversation = conversationMap.get(conversation.getId());
      conversationList.remove(quasiConversation);
      conversationMap.remove(conversation.getId());

      triggerOnConversationRemoved(quasiConversation);
    }
  };

  private QuasiConversationViewImpl(ObservableFocusFramePresenter focusFrame) {
    this.focusFrame = focusFrame;
  }

  //
  // QuasiConversationView
  //

  @Override
  public void initialize(ObservableConversationView baseConversationView,
      SupplementedWave supplement) {
    Preconditions.checkArgument(this.baseConversationView == null, "Already initialized");
    
    this.baseConversationView = baseConversationView;
    this.supplement = supplement;
    
    baseConversationView.addListener(conversationViewListener);

    scanBaseConversationView();
  }

  //
  // ConversationView
  //

  @Override
  public Collection<? extends ObservableQuasiConversation> getConversations() {
    return conversationList;
  }

  @Override
  public ObservableQuasiConversation getRoot() {
    ObservableConversation rootConversation = baseConversationView.getRoot();
    return rootConversation != null ? conversationMap.get(rootConversation.getId()) : null;
  }

  @Override
  public ObservableQuasiConversation getConversation(String conversationId) {
    return conversationMap.get(conversationId);
  }

  @Override
  public String getId() {
    return baseConversationView.getId();
  }

  @Override
  public ObservableQuasiConversation createRoot() {
    ObservableConversation conversation = baseConversationView.createRoot();
    return conversationMap.get(conversation.getId());
  }

  @Override
  public ObservableQuasiConversation createConversation() {
    ObservableConversation conversation = baseConversationView.createConversation();
    return conversationMap.get(conversation.getId());
  }

  //
  // Listeners
  //

  @Override
  public void addListener(ObservableConversationView.Listener listener) {
    baseListeners.add(listener);
  }

  @Override
  public void removeListener(ObservableConversationView.Listener listener) {
    baseListeners.remove(listener);
  }

  @Override
  public void addListener(Listener listener) {
    quasiListeners.add(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    quasiListeners.remove(listener);
  }

  //
  // Private methods
  //

  /**
   * Scans conversations recursively and creates quasi conversation for any child conversation.
   */
  private void scanBaseConversationView() {
    Iterator<? extends ObservableConversation> it =
        baseConversationView.getConversations().iterator();
    while (it.hasNext()) {
      QuasiConversationImpl conversation = QuasiConversationImpl.create(it.next(), supplement,
          focusFrame);
      conversationList.add(conversation);
      conversationMap.put(conversation.getId(), conversation);
      
      // Inform listeners about conversation adding.
      triggerOnConversationAdded(conversation);
    }
  }

  //
  // Trigger events
  //

  private void triggerOnConversationAdded(ObservableQuasiConversation conversation) {
    for (ObservableConversationView.Listener l : baseListeners) {
      l.onConversationAdded(conversation);
    }
    for (Listener l : quasiListeners) {
      l.onConversationAdded(conversation);
    }
  }

  private void triggerOnConversationRemoved(ObservableQuasiConversation conversation) {
    for (ObservableConversationView.Listener l : baseListeners) {
      l.onConversationRemoved(conversation);
    }
    for (Listener l : quasiListeners) {
      l.onConversationRemoved(conversation);
    }
  }
}
