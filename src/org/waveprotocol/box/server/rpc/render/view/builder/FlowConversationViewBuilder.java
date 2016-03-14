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
package org.waveprotocol.box.server.rpc.render.view.builder;

import com.google.common.annotations.VisibleForTesting;
import org.waveprotocol.box.server.rpc.render.common.safehtml.SafeHtmlBuilder;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.append;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.close;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.open;
import org.waveprotocol.box.server.rpc.render.uibuilder.UiBuilder;
import org.waveprotocol.box.server.rpc.render.view.TypeCodes;
import org.waveprotocol.box.server.rpc.render.view.View.Type;

/**
 * A simple conversatin view builder that does not contains a scrollable
 * element.
 *
 */
public class FlowConversationViewBuilder extends TopConversationViewBuilder {
  private final Css css;
  private final String id;
  private final UiBuilder rootThread;
  private final UiBuilder participants;
  private final UiBuilder tags;

  @VisibleForTesting
  FlowConversationViewBuilder(Css css, String id, UiBuilder rootThread,
      UiBuilder participants, UiBuilder tags) {
    this.css = css;
    this.id = id;
    this.rootThread = rootThread;
    this.participants = participants;
    this.tags = tags;
  }

  /**
   * Creates a new SimpleConversationViewBuilder.
   *
   * @param id DOM id
   * @param threadUi UI for the thread
   * @param participantsUi UI for the participants
   */
  public static FlowConversationViewBuilder createRoot(WavePanelResources resources,
      String id, UiBuilder threadUi, UiBuilder participantsUi, UiBuilder tagsUi) {
    return new FlowConversationViewBuilder(
        resources.getConversation().css(), id, threadUi, participantsUi, tagsUi);
  }

  @Override
  public void outputHtml(SafeHtmlBuilder out) {
    open(out, id, null, TypeCodes.kind(Type.ROOT_CONVERSATION));
    {
      participants.outputHtml(out);
      append(out, Components.TOOLBAR_CONTAINER.getDomId(id), css.toolbar(), null);
      // Non-scrollable panel.
      open(out, Components.THREAD_CONTAINER.getDomId(id), null, null);
      {
        rootThread.outputHtml(out);
      }  
      close(out);
      tags.outputHtml(out);
    }
    close(out);
  }
}
