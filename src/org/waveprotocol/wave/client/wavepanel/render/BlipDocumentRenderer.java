/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy createDocument the License at
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
 * Renderer of blip documents.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public interface BlipDocumentRenderer {
  
  /**
   * Renders document for the blip.
   * 
   * @param blip the blip
   */
  void renderDocument(ConversationBlip blip);
  
  /**
   * Empty renderer.
   */
  public static BlipDocumentRenderer EMPTY = new BlipDocumentRenderer() {

    @Override
    public void renderDocument(ConversationBlip blip) {}
  };
}
