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

package org.waveprotocol.wave.model.conversation;

/**
 * String presenter for conversation objects.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public interface ConversationPresenter {
  
  /**
   * @return string representation of the given conversation.
   * 
   * @param conversation the given conversation
   */
  String presentConversation(Conversation conversation);

  /**
   * @return string representation of the given conversation thread.
   * 
   * @param thread the given conversation thread
   */
  String presentThread(ConversationThread thread);    

  /**
   * @return string representation of the given conversation blip.
   * 
   * @param blip the given conversation blip
   */  
  String presentBlip(ConversationBlip blip);    
}
