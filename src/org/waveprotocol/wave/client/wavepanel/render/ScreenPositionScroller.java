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
import org.waveprotocol.wave.model.supplement.ScreenPosition;

/**
 * Scroller to the given screen position.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public interface ScreenPositionScroller {
  
  /**
   * Scrolls to the given blip.
   * 
   * @param blip blip for scrolling
   */  
  void scrollToBlip(ConversationBlip blip);
  
  /**
   * Scrolls to the given screen position.
   * 
   * @param screenPosition screen position for scrolling
   */
  void scrollToScreenPosition(ScreenPosition screenPosition);
  
  /**
   * Gets current screen position.
   * 
   * @return screen position
   */
  ScreenPosition getScreenPosition();
}
