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

import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.supplement.SupplementedWave;

import java.util.Collection;

/**
 * Conversation view with quasi-deletion support.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public interface QuasiConversationView extends ConversationView {
  
  /**
   * Scans base conversation and initializes own tree.
   * 
   * @param baseConversationView the base conversation view.
   * @param supplement the user supplement.
   */
  void initialize(ObservableConversationView baseConversationView, SupplementedWave supplement);

  //
  // ConversationView
  //
  
  @Override
  public Collection<? extends QuasiConversation> getConversations();
  
  @Override
  public QuasiConversation getRoot();

  @Override
  public QuasiConversation getConversation(String conversationId);

  @Override
  public QuasiConversation createConversation();

  @Override
  public QuasiConversation createRoot();
}
