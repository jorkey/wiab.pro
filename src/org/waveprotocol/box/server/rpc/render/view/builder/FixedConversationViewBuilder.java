/**
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
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
 * A TopConversationViewBuilder that has a scroll bar on the thread container.
 *
 */
public final class FixedConversationViewBuilder extends TopConversationViewBuilder {

  private final Css css;
  private final String id;
  private final UiBuilder rootThread;
  private final UiBuilder participants;

  @VisibleForTesting
  FixedConversationViewBuilder(
      Css css, String id, UiBuilder rootThread, UiBuilder participants) {
    this.css = css;
    this.id = id;
    this.rootThread = rootThread;
    this.participants = participants;
  }

  /**
   * Creates a new ConversationViewBuilder.
   *
   * @param id DOM id
   * @param threadUi UI for the thread
   * @param participantsUi UI for the participants
   */
  public static FixedConversationViewBuilder createRoot(WavePanelResources resources,
      String id, UiBuilder threadUi, UiBuilder participantsUi) {
    return new FixedConversationViewBuilder(resources.getConversation().css(), id, threadUi,
        participantsUi);
  }

  @Override
  public void outputHtml(SafeHtmlBuilder out) {
    open(out, id, css.fixedSelf(), TypeCodes.kind(Type.ROOT_CONVERSATION));
    participants.outputHtml(out);
    append(out, Components.TOOLBAR_CONTAINER.getDomId(id), css.toolbar(), null);
    // Scroll panel
    open(out, Components.THREAD_CONTAINER.getDomId(id), css.fixedThread(), null);
    rootThread.outputHtml(out);
    close(out);
    close(out);
  }
}
