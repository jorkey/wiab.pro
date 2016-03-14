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

package org.waveprotocol.wave.model.conversation.navigator;

import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversation;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationView;

/**
 * Navigator implementation for conversation structure.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class ConversationNavigator extends NavigatorImpl<ConversationThread, ConversationBlip> {
  
  public static ConversationNavigator create(ObservableQuasiConversationView conversationView) {
    return new ConversationNavigator(conversationView);
  }

  private ConversationNavigator(ObservableQuasiConversationView conversationView) {
    super();
    
    if (conversationView.getRoot() != null) {
      init(new ConversationAdapter(conversationView.getRoot()) );
    }
    
    conversationView.addListener(new ObservableQuasiConversationView.Listener() {

      @Override
      public void onConversationAdded(ObservableQuasiConversation conversation) {
        if (conversation.isRoot()) {    
          init(new ConversationAdapter(conversation));
        }  
      }

      @Override
      public void onConversationRemoved(ObservableQuasiConversation conversation) {
        if (conversation.isRoot()) {    
          clearIndex();
        }  
      }
    });
  }  
}
