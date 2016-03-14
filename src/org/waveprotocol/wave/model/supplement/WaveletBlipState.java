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

package org.waveprotocol.wave.model.supplement;

/**
 * Supplement-like interface relevant for a single wavelet in a conversation.
 * Exposes the focused blip and top screen blip ids.
 */
public interface WaveletBlipState {

  /**
   * Returns the focused blip id. Null means that the focused blip is unspecified.
   */  
  String getFocusedBlipId();

  /**
   * Returns the screen position. Null means that the top screen blip is unspecified.
   */  
  ScreenPosition getScreenPosition();
  
  /**
   * Sets focused blip id.
   */
  void setFocusedBlipId(String blipId);
  
  /**
   * Sets screen position.
   */
  void setScreenPosition(ScreenPosition screenPosition);
  
  /**
   * Removes this state from the {@link WaveletBlipStateCollection}.
   */
  void remove();  
}
