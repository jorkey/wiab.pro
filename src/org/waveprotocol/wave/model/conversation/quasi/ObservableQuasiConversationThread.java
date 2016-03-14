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

import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationThread;
import org.waveprotocol.wave.model.document.operation.DocInitialization;

/**
 * Conversation thread with quasi-deletion support.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public interface ObservableQuasiConversationThread
    extends QuasiConversationThread, ObservableConversationThread {

  /**
   * Listener to the thread events.
   */
  public interface Listener {
    
    /**
     * Is called after quasi-deleted thread is terminated.
     */
    void onTerminated();
  }
  
  //
  // QuasiConversationThread
  //
  
  @Override
  ObservableConversationThread getBaseThread();

  //
  // ConversationThread
  //
  
  @Override
  ObservableQuasiConversation getConversation();
  
  @Override
  ObservableQuasiConversationBlip getParentBlip();  
  
  @Override
  Iterable<? extends ObservableQuasiConversationBlip> getBlips();

  @Override
  ObservableQuasiConversationBlip getFirstBlip();

  @Override
  ObservableQuasiConversationBlip appendBlip();

  @Override
  ObservableQuasiConversationBlip appendBlip(DocInitialization content);
  
  @Override
  ObservableQuasiConversationBlip insertBlip(ConversationBlip neighbor, boolean beforeNeighbor);
  
  //
  // Listener registration
  //
  
  /**
   * Adds a listener to this blip.
   * 
   * @param listener
   */
  void addListener(Listener listener);

  /**
   * Removes a listener from this blip.
   * 
   * @param listener
   */
  void removeListener(Listener listener);
}
