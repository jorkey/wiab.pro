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

package org.waveprotocol.wave.model.conversation.focus;

import org.waveprotocol.wave.model.conversation.ConversationBlip;

/**
 * Focus frame presenter interface.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public interface FocusFramePresenter {

  /**
   * Order of focusing.
   */
  public interface FocusOrder {

    /**
     * @return previous unread blip starting from the given start blip
     * 
     * @param startBlip the given start blip
     */
    ConversationBlip getPreviousUnread(ConversationBlip startBlip);    
    
    /**
     * @return next unread blip starting from the given start blip
     * 
     * @param startBlip the given start blip
     */
    ConversationBlip getNextUnread(ConversationBlip startBlip);
  }
  
  /**
   * Validator of focus move between blips.
   */
  public interface FocusMoveValidator {
    
    /**
     * @return true, if focus can be immediately moved from the old blip to the new blip

     * @param oldBlip the old blip
     * @param newBlip the new blip
     */
    boolean canMoveFocus(ConversationBlip oldBlip, ConversationBlip newBlip);
  }
  
  /**
   * Gets the focused blip.
   * 
   * @return the frocused blip
   */
  ConversationBlip getFocusedBlip();
  
  /**
   * Sets the blip that has the focus frame.
   * If {@code blip} is null, the focus frame is removed.
   * 
   * @param blip blip to be focused
   */
  void focus(ConversationBlip blip);
  
  /**
   * Sets focused blip to null without triggering any events.
   */
  void clearFocus();

  /**
   * Gets the neighbor blip of the given blip.
   * 
   * @param next if true than the next blip is found, otherwise - the previous one
   * @return the neighbor blip
   */
  ConversationBlip getNeighborBlip(boolean next);
  
  /**
   * @return next unread blip as defined by an attached
   * {@link #setOrder(FocusOrder) ordering}, if there is one.
   */
  ConversationBlip getNextUnreadBlip();
  
  /**
   * Specifies the orderer to use when moving to the nextBlip and previous interesting blips.
   * 
   * @param order focus order
   */
  void setOrder(FocusOrder order);
  
  /**
   * Sets "editing" attribute.
   *
   * @param editing true, if the blip's content is being edited
   */
  void setEditing(boolean editing);
}
