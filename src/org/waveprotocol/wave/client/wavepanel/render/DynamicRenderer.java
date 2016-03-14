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

/**
 * Dynamic renderer interface.
 * 
 * @param <T> type of rendered elements
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public interface DynamicRenderer<T> {
  
  /**
   * Starts rendering from the current position.
   */
  void dynamicRendering();
  
  /**
   * Starts rendering from the given start blip.
   * 
   * @param startBlip start blip
   */
  void dynamicRendering(ConversationBlip startBlip);
  
  /**
   * Starts rendering from the blip with the given id.
   * 
   * @param startBlipId start blip id
   */
  void dynamicRendering(String startBlipId);
  
  /**
   * Checks, if the blip is ready for interaction.
   * 
   * @param blip the blip
   * @return true, if the blip is rendered and ready to interact with.
   */
  boolean isBlipReady(ConversationBlip blip);
  
  /**
   * Checks, if the blip is ready for interaction.
   * 
   * @param blipId the blip id
   * @return true, if the blip is rendered and ready for interaction.
   */  
  boolean isBlipReady(String blipId);
  
  /**
   * Checks the blip's visibility on the screen.
   * 
   * @param blip the blip
   * @return true, if the blip is at least partially visible on the screen
   */
  boolean isBlipVisible(ConversationBlip blip);
  
  /**
   * Returns rendered element for the given blip.
   * If the blip isn't rendered, returns null.
   * 
   * @param blip the given blip
   * @return the blip's rendered element
   */
  T getElementByBlip(ConversationBlip blip);
  
  /**
   * Returns blip id for the rendered blip element.
   * If no such blip found, returns null.
   * 
   * @param element the given blip element
   * @return the blip id
   */  
  String getBlipIdByElement(T element);
}
