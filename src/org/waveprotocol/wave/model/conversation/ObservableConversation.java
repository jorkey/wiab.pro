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

import org.waveprotocol.wave.model.document.ObservableDocument;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Extends {@link Conversation} to provide events.
 *
 * @author anorth@google.com (Alex North)
 */
public interface ObservableConversation extends Conversation {

  public interface ReplyListener {

    /**
     * Notifies this listener that a thread was added.
     *
     * @param thread the new thread
     * @param opContext document operation context
     */
    void onReplyAdded(ObservableConversationThread thread, WaveletOperationContext opContext);

    /**
     * Notifies this listener that a thread is going to be removed.
     *
     * @param thread the thread to be removed
     * @param opContext document operation context
     */
    void onBeforeReplyRemoved(ObservableConversationThread thread, WaveletOperationContext opContext);

    /**
     * Notifies this listener that a thread was removed. The thread is no longer usable.
     *
     * @param thread the removed thread
     * @param opContext document operation context
     */
    void onReplyRemoved(ObservableConversationThread thread, WaveletOperationContext opContext);
  }
  
  public interface BlipListener {

    /**
     * Notifies this listener that a blip was added.
     *
     * @param blip the new blip
     * @param opContext document operation context
     */
    void onBlipAdded(ObservableConversationBlip blip, WaveletOperationContext opContext);

    /**
     * Notifies this listener that a blip is going to be removed.
     *
     * @param blip the blip to be removed
     * @param opContext document operation context
     */
    void onBeforeBlipRemoved(ObservableConversationBlip blip, WaveletOperationContext opContext);

    /**
     * Notifies this listener that a blip was removed. The only valid methods on
     * the blip are those which query its state, not modify it.
     *
     * @param blip the deleted blip
     * @param opContext document operation context
     */
    void onBlipRemoved(ObservableConversationBlip blip, WaveletOperationContext opContext);
  }
  
  /**
   * Receives events about the anchoring of a conversation.
   */
  interface AnchorListener {
    
    /**
     * Notifies this listener that the conversation anchor has changed.
     *
     * @param oldAnchor the old anchor, may be null
     * @param newAnchor new anchor, may be null
     */
    void onAnchorChanged(Anchor oldAnchor, Anchor newAnchor);
  }
  
  /**
   * Receives events about participants in the conversation.
   */ 
  public interface ParticipantListener {
    
    /**
     * Notifies this listener that a participant was added.
     * 
     * @param participant the added participant
     * @param opContext wavelet operation context
     */
    void onParticipantAdded(ParticipantId participant, WaveletOperationContext opContext);

    /**
     * Notifies this listener that a participant was removed.
     * 
     * @param participant the removed participant
     * @param opContext wavelet operation context
     */
    void onParticipantRemoved(ParticipantId participant, WaveletOperationContext opContext);    
  }
  
  /**
   * Receives events about tags in the conversation.
   */   
  public interface TagListener {
    
    /**
     * Notifies this listener that a tag was added.
     * 
     * @param tag the added tag
     * @param opContext wavelet operation context
     */
    void onTagAdded(String tag, WaveletOperationContext opContext);

    /**
     * Notifies this listener that a tag was removed.
     * 
     * @param tag the removed tag
     * @param opContext wavelet operation context
     */
    void onTagRemoved(String tag, WaveletOperationContext opContext);
  }

  /**
   * Receives events about blip content in the conversation.
   */  
  public interface BlipContentListener {
    
    /**
     * Notifies this listener that a contributor was added to a blip.
     * 
     * @param blip conversation blip
     * @param contributor id of the contributor added to the blip
     */
    void onBlipContributorAdded(ObservableConversationBlip blip, ParticipantId contributor);

    /**
     * Notifies this listener that a contributor was removed from a blip.
     * 
     * @param blip conversation blip
     * @param contributor id of the contributor removed from the blip
     */
    void onBlipContributorRemoved(ObservableConversationBlip blip, ParticipantId contributor);

    /**
     * Notifies the listener that a blip was submitted.
     * 
     * @param blip submitted blip
     */
    void onBlipSubmitted(ObservableConversationBlip blip);

    /**
     * Notifies the listener that a blip timestamp changed.
     * 
     * @param blip conversation blip
     * @param oldTimestamp old timestamp
     * @param newTimestamp new timestamp
     */
    void onBlipTimestampChanged(ObservableConversationBlip blip, long oldTimestamp,
        long newTimestamp);
  }
  
  public interface Listener extends ReplyListener, BlipListener,
      ParticipantListener, TagListener, BlipContentListener {}
  
  //
  // Listener registration
  //

  /**
   * Adds an anchor listener to this conversation.
   * 
   * @param listener anchor listener
   */
  void addAnchorListener(AnchorListener listener);

  /**
   * Removes an anchor listener from this conversation.
   * 
   * @param listener anchor listener
   */
  void removeAnchorListener(AnchorListener listener);
 
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
   * Adds a participant listener to this conversation.
   * 
   * @param listener participant listener
   */
  void addParticipantListener(ParticipantListener listener);

  /**
   * Removes a participant listener from this conversation.
   * 
   * @param listener participant listener
   */
  void removeParticipantListener(ParticipantListener listener);
  
  /**
   * Adds a tag listener to this conversation.
   * 
   * @param listener tag listener
   */
  void addTagListener(TagListener listener);

  /**
   * Removes a tag listener from this conversation.
   * 
   * @param listener tag listener
   */
  void removeTagListener(TagListener listener);
  
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
  // Conversation
  //
  
  @Override
  ObservableConversationBlip getBlip(String id);

  @Override
  ObservableConversationThread getThread(String id);

  @Override
  ObservableConversationThread getRootThread();

  @Override
  ObservableDocument getDataDocument(String name);
}
