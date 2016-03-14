/**
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.waveprotocol.box.server.rpc.render.view;

import org.waveprotocol.box.server.rpc.render.uibuilder.UiBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.InlineConversationViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.TopConversationViewBuilder;

/**
 * Factory view used to create the builder used by the render.
 *
 */
public interface ViewFactory {
  InlineConversationViewBuilder createInlineConversationView(
      String id, UiBuilder threadUi, UiBuilder participantsUi);

  TopConversationViewBuilder createTopConversationView(
      String id, UiBuilder threadUi, UiBuilder participantsUi, UiBuilder tagsUi);
}
