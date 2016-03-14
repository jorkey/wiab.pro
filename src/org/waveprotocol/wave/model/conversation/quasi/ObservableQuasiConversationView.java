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

import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.supplement.SupplementedWave;

import java.util.Collection;

/**
 * Observable conversation view with quasi-deletion support.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public interface ObservableQuasiConversationView extends QuasiConversationView,
    ObservableConversationView {

  /**
   * Listener to quasi-conversation adding / removing.
   */
  public interface Listener {
    
    /**
     * Notifies about adding of conversion.
     * 
     * @param conversation added conversation
     */
    void onConversationAdded(ObservableQuasiConversation conversation);
    
    /**
     * Notifies about removing of conversation.
     * 
     * @param conversation removed conversation
     */
    void onConversationRemoved(ObservableQuasiConversation conversation);
  }
  
  void addListener(Listener listener);
  
  void removeListener(Listener listener);
  
  //
  // QuasiConversationView
  //

  @Override
  public void initialize(ObservableConversationView baseConversationView,
      SupplementedWave supplement);

  //
  // ConversationView
  //
  
  @Override
  public Collection<? extends ObservableQuasiConversation> getConversations();
  
  @Override
  public ObservableQuasiConversation getRoot();

  @Override
  public ObservableQuasiConversation getConversation(String conversationId);

  @Override
  public ObservableQuasiConversation createRoot();

  @Override
  public ObservableQuasiConversation createConversation();
}
