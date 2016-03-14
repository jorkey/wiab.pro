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

package org.waveprotocol.wave.client.wavepanel.view;

import org.waveprotocol.wave.model.conversation.ConversationBlip;

/**
 * A view interface for a thread.
 *
 */
public interface ThreadView extends View, IntrinsicThreadView {

  @Override
  View getParent();

  /**
   * Inserts a blip in this thread.
   *
   * @param blip blip to render
   * @param neighbor view before/after which to render blip
   * @param beforeNeighbor true, if the blip should be rendered before neighbor
   */
  BlipView insertBlip(ConversationBlip blip, View neighbor, boolean beforeNeighbor);

  /**
   * Inserts a placeholder in this thread.
   *
   * @param neighbor view before/after which to render placeholder
   * @param beforeNeighbor true, if the placeholder should be rendered before neighbor
   */
  PlaceholderView insertPlaceholder(View prev, boolean beforeNeighbor);

  boolean isRoot();
}
