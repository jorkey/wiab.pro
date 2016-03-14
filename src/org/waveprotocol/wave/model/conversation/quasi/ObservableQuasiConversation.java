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
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Observable conversation with quasi-deletion support.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public interface ObservableQuasiConversation extends QuasiConversation, ObservableConversation {
  
  /**
   * Listener to changes in replies.
   */
  public interface ReplyListener {

    /**
     * Notifies this listener that a thread was added.
     *
     * @param thread the new thread
     */
    void onReplyAdded(ObservableQuasiConversationThread thread);

    /**
     * Notifies this listener that a thread is going to be removed.
     *
     * @param thread the thread to be removed
     */
    void onBeforeReplyRemoved(ObservableQuasiConversationThread thread);

    /**
     * Notifies this listener that a thread is going to be quasi-removed.
     *
     * @param thread the thread to be quasi-removed
     */
    void onBeforeReplyQuasiRemoved(ObservableQuasiConversationThread thread);    
    
    /**
     * Notifies this listener that a thread was removed.
     * 
     * @param thread the removed thread
     */
    void onReplyRemoved(ObservableQuasiConversationThread thread);
    
    /**
     * Notifies this listener that a thread was quasi-removed.
     *
     * @param thread the quasi-removed thread
     */
    void onReplyQuasiRemoved(ObservableQuasiConversationThread thread);
    
    /**
     * Primitive ReplyListener implemenation.
     */
    public class Impl implements ReplyListener {

      @Override
      public void onReplyAdded(ObservableQuasiConversationThread thread) {}

      @Override
      public void onBeforeReplyRemoved(ObservableQuasiConversationThread thread) {}

      @Override
      public void onBeforeReplyQuasiRemoved(ObservableQuasiConversationThread thread) {}

      @Override
      public void onReplyRemoved(ObservableQuasiConversationThread thread) {}

      @Override
      public void onReplyQuasiRemoved(ObservableQuasiConversationThread thread) {}      
    }
  }
  
  public interface BlipListener {

    /**
     * Notifies this listener that a blip was added.
     *
     * @param blip the new blip
     */
    void onBlipAdded(ObservableQuasiConversationBlip blip);

    /**
     * Notifies this listener that a blip is going to be removed.
     *
     * @param blip the blip to be removed
     */
    void onBeforeBlipRemoved(ObservableQuasiConversationBlip blip);
    
    /**
     * Notifies this listener that a blip is going to be quasi-removed.
     *
     * @param blip the blip to be quasi-removed
     */
    void onBeforeBlipQuasiRemoved(ObservableQuasiConversationBlip blip);    
    
    /**
     * Notifies this listener that a blip was removed.
     *
     * @param blip the removed blip
     */
    void onBlipRemoved(ObservableQuasiConversationBlip blip);
    
    /**
     * Notifies this listener that a blip was quasi-removed.
     *
     * @param blip the quasi-removed blip
     */
    void onBlipQuasiRemoved(ObservableQuasiConversationBlip blip);
    
    /**
     * Primitive BlipListener implementation.
     */
    public class Impl implements BlipListener {

      @Override
      public void onBlipAdded(ObservableQuasiConversationBlip blip) {}

      @Override
      public void onBeforeBlipRemoved(ObservableQuasiConversationBlip blip) {}

      @Override
      public void onBeforeBlipQuasiRemoved(ObservableQuasiConversationBlip blip) {}

      @Override
      public void onBlipRemoved(ObservableQuasiConversationBlip blip) {}

      @Override
      public void onBlipQuasiRemoved(ObservableQuasiConversationBlip blip) {}
    }
  }  
  
  public interface BlipContentListener {
    
    /**
     * Notifies this listener that a contributor was added to a blip.
     * 
     * @param blip conversation blip
     * @param contributor id of the contributor added to the blip
     */
    void onBlipContributorAdded(ObservableQuasiConversationBlip blip, ParticipantId contributor);

    /**
     * Notifies this listener that a contributor was removed from a blip.
     * 
     * @param blip conversation blip
     * @param contributor id of the contributor removed from the blip
     */
    void onBlipContributorRemoved(ObservableQuasiConversationBlip blip, ParticipantId contributor);

    /**
     * Notifies the listener that a blip was submitted.
     * 
     * @param blip submitted blip
     */
    void onBlipSubmitted(ObservableQuasiConversationBlip blip);

    /**
     * Notifies the listener that a blip timestamp changed.
     * 
     * @param blip conversation blip
     * @param oldTimestamp old timestamp
     * @param newTimestamp new timestamp
     */
    void onBlipTimestampChanged(ObservableQuasiConversationBlip blip, long oldTimestamp,
        long newTimestamp);
  }
  
  public interface Listener extends ReplyListener, BlipListener, ParticipantListener, TagListener,
      BlipContentListener {}  
  
  //
  // Listener registration
  //

  /**
   * Adds a reply listener to this conversation.
   * 
   * @param listener reply listener
   */
  void addReplyListener(ReplyListener listener);

  /**
   * Removes a reply listener from this conversation.
   * 
   * @param listener reply listener
   */
  void removeReplyListener(ReplyListener listener);

  /**
   * Adds a blip listener to this conversation.
   * 
   * @param listener blip listener
   */
  void addBlipListener(BlipListener listener);

  /**
   * Removes a blip listener from this conversation.
   * 
   * @param listener blip listener
   */
  void removeBlipListener(BlipListener listener);
  
  /**
   * Adds a blip content listener to this conversation.
   * 
   * @param listener blip content listener
   */
  void addBlipContentListener(BlipContentListener listener);

  /**
   * Removes a blip content listener from this conversation.
   * 
   * @param listener blip content listener
   */
  void removeBlipContentListener(BlipContentListener listener);  
  
  /**
   * Adds a common listener to this conversation.
   * 
   * @param listener common listener
   */
  void addListener(Listener listener);

  /**
   * Removes a common listener from this conversation.
   * 
   * @param listener common listener
   */
  void removeListener(Listener listener);  
  
  //
  // QuasiConversation
  //

  @Override
  ObservableConversation getBaseConversation();
  
  //
  // Conversation
  //

  @Override
  ObservableQuasiConversationThread getThread(String threadId);

  @Override
  ObservableQuasiConversationBlip getBlip(String blipId);

  @Override
  ObservableQuasiConversationThread getRootThread();
}
