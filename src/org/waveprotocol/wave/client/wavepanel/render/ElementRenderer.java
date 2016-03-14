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

import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;

/**
 * Renderer of elements.
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 * 
 * @param <T> type of rendered elements
 */
public interface ElementRenderer<T> {

  //
  // Common
  //
    
  /**
   * Removes the element from its parent.
   * 
   * @param element
   */
  void remove(T element);

  /**
   * Sets element visibility.
   * 
   * @param element
   * @param visible true, if element should be made visible
   */
  void setElementVisible(T element, boolean visible);
  
  /**
   * Sets fixed height (minHeight and maxHeight) to the given element.
   * 
   * @param element the given element
   * @param fixedHeight fixed height in pixels
   */  
  void setElementHeightFixed(T element, int fixedHeight);

  /**
   * Fixes/unfixes current height of the given element.
   * 
   * @param element the given element
   * @param fixed true, if the height should be fixed
   */  
  void setElementCurrentHeightFixed(T element, boolean fixed);
  
  //
  // Placeholders
  //

  /**
   * Inserts placeholder element for the row owner thread before or after given element.
   * 
   * @param rowOwnerThread conversation row owner thread corresponding to the new placeholder
   * @param neighbor neighbor element
   * @param beforeNeighbor true, if the placeholder should be inserted before neighbor element
   * @return new placholder
   */
  T insertPlaceholder(ConversationThread rowOwnerThread, T neighbor, boolean beforeNeighbor);

  /**
   * @return placeholder element for the given conversation inline thread.
   * 
   * @param inlineThread the given conversation inline thread
   */
  T getPlaceholderByInlineThread(ConversationThread inlineThread);
  
  /**
   * Sets height to the given placeholder.
   * 
   * @param placeholder the given placeholder
   * @param height height in pixels
   */
  void setPlaceholderHeight(T placeholder, int height);  
  
  //
  // Blips
  //

  /**
   * Renders blip element and inserts it into element corresponding to the row owner thread
   * before or after given element.
   * 
   * @param rowOwnerThread conversation row owner thread
   * @param blip blip to render
   * @param neighbor neighbor element
   * @param beforeNeighbor true, if the blip should be inserted before neighbor element
   * @return new blip element
   */
  T insertBlip(ConversationThread rowOwnerThread, ConversationBlip blip, T neighbor,
      boolean beforeNeighbor);

  /**
   * Renders inner thread element and inserts it into parent blip element
   * before or after given neighbor thread.
   * 
   * @param parentBlip parent conversation blip
   * @param thread conversation inner thread to render
   * @param neighborThread neighbor thread
   * @param beforeNeighbor true, if the inner thread should be inserted before neighbor element
   * @return new inner thread element
   */
  T insertInlineThread(ConversationBlip parentBlip, ConversationThread thread,
      ConversationThread neighborThread, boolean beforeNeighbor);

  /**
   * Removes the inline thread element from its parent blip.
   * 
   * @param thread conversation inline thread corresponding to the removed thread element
   */  
  void removeInlineThread(ConversationThread thread);

  /**
   * @param blipElement rendered blip element
   * @return z-index value for the blip
   */
  int getZIndex(T blipElement);
  
  /**
   * Sets z-index value for the blip.
   * 
   * @param blipElement rendered blip element
   * @param zIndex z-index value
   */
  void setZIndex(T blipElement, int zIndex);
  
  /**
   * @return potential height of the given blip element after rendering
   * 
   * @param blip the given blip element
   */
  int getBlipPotentialHeight(T blip);  
}
