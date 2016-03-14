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

package org.waveprotocol.wave.client.wavepanel.view.dom.full;

import com.google.common.annotations.VisibleForTesting;
import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.append;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.close;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.open;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;

/**
 * A simple conversation view builder that does not contains a scrollable element.
 */
public class FlowConversationViewBuilder
    extends TopConversationViewBuilder {

  /**
   * Creates a new SimpleConversationViewBuilder.
   *
   * @param id DOM id
   * @param threadUi UI for the thread
   * @param participantsUi UI for the participants
   * @param participantsUi UI for the tags
   */
  public static FlowConversationViewBuilder createRoot(
      String id, UiBuilder participants, UiBuilder thread, UiBuilder tags) {
    return new FlowConversationViewBuilder(
        id, participants, thread, tags, WavePanelResourceLoader.getConversation().css());
  }  
  
  private final String id;
  private final UiBuilder participants;  
  private final UiBuilder thread;
  private final UiBuilder tags;
  private final Css css;  

  @VisibleForTesting
  FlowConversationViewBuilder(
      String id, UiBuilder participants, UiBuilder thread, UiBuilder tags, Css css) {
    this.id = id;
    this.participants = participants;    
    this.thread = thread;
    this.tags = tags;
    this.css = css;
  }

  @Override
  public void outputHtml(SafeHtmlBuilder out) {
    open(out, id, null, TypeCodes.kind(Type.ROOT_CONVERSATION));
    {
      // Participants
      participants.outputHtml(out);
      
      // Toolbar
      append(out, Components.TOOLBAR_CONTAINER.getDomId(id), css.toolbar(), null);
      
      // Non-scrollable panel
      open(out, Components.THREAD_CONTAINER.getDomId(id), null, null);      
      {
        thread.outputHtml(out);
      }  
      close(out);
      
      // Tags
      tags.outputHtml(out);
    }
    close(out);
  }
}
