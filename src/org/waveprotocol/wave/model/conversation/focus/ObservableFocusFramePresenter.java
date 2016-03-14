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
import org.waveprotocol.wave.model.wave.SourcesEvents;

/**
 * Observable focus frame presenter.
 * 
 * @author dyukon@gmail.com
 */
public interface ObservableFocusFramePresenter extends FocusFramePresenter,
    SourcesEvents<ObservableFocusFramePresenter.Listener> {
  
  /**
   * Listener to the focus events.
   */
  public interface Listener {

    /**
     * Called after the focus is moved from the blip
     * 
     * @param oldFocused old focused blip
     */
    void onFocusOut(ConversationBlip oldFocused);

    /**
     * Called after the focus is moved to the blip
     * 
     * @param newFocused new focused blip
     */    
    void onFocusIn(ConversationBlip newFocused);    
  }
}
