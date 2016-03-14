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

package org.waveprotocol.wave.client.wavepanel.impl.indicator;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;

import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.event.WaveMouseDownHandler;
import org.waveprotocol.wave.client.wavepanel.impl.edit.Actions;
import org.waveprotocol.wave.client.wavepanel.view.ReplyBoxView;
import org.waveprotocol.wave.client.wavepanel.view.ThreadView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TypeCodes;
import org.waveprotocol.wave.model.conversation.ConversationThread;

/**
 * Interprets click on the reply indicators.
 */
public final class ReplyIndicatorController implements WaveMouseDownHandler {
  private final DomAsViewProvider panel;
  private final Actions actions;
  private final ModelAsViewProvider modelAsViewProvider;

  /**
   * Creates a reply indicator handler.
   *
   * @param actions
   * @param panel
   */
  private ReplyIndicatorController(Actions actions, DomAsViewProvider panel,
      ModelAsViewProvider modelAsViewProvider) {
    this.actions = actions;
    this.panel = panel;    
    this.modelAsViewProvider = modelAsViewProvider;
  }

  /**
   * Installs the reply indicator feature in a wave panel.
   */
  public static void install(WavePanel panel, ModelAsViewProvider modelAsViewProvider,
      Actions handler) {
    ReplyIndicatorController controller = new ReplyIndicatorController(
        handler, panel.getViewProvider(), modelAsViewProvider);
    panel.getHandlers().registerMouseDownHandler(TypeCodes.kind(Type.REPLY_BOX), controller);
  }  
  
  @Override
  public boolean onMouseDown(MouseDownEvent event, Element context) {
    if (event.getNativeButton() != NativeEvent.BUTTON_LEFT) {
      return false;
    }

    ReplyBoxView indicatorView = panel.asReplyBox(context);
    ThreadView threadView = indicatorView.getParent();
    ConversationThread thread = modelAsViewProvider.getThread(threadView);
    actions.addBlipToThread(thread);
    event.preventDefault();
    return true;
  }
}