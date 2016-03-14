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

package org.waveprotocol.wave.client.wavepanel.impl.toolbar;

import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.impl.edit.EditSession;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Switches between two toolbars, a "view" and "edit" toolbar, as the client
 * moves in and out of edit mode.
 *
 * @author kalman@google.com (Benjamin Kalman)
 */
public final class ToolbarSwitcher implements EditSession.Listener {
  
  private final WavePanel panel;
  private final EditSession editSession;
  private final ViewToolbar viewToolbar;
  private final ParticipantId signedInUser;
  private final IdGenerator idGenerator;
  private final WaveId waveId;
  
  private EditToolbar editToolbar;

  private ToolbarSwitcher(WavePanel panel, EditSession editSession, ViewToolbar viewToolbar,
      ParticipantId signedInUser, IdGenerator idGenerator, WaveId waveId) {
    this.panel = panel;
    this.editSession = editSession;
    this.viewToolbar = viewToolbar;
    this.signedInUser = signedInUser;
    this.idGenerator = idGenerator;
    this.waveId = waveId;
  }

  /**
   * Creates a new {@link ToolbarSwitcher} and initialises it.
   */
  public static ToolbarSwitcher install(WavePanel panel, EditSession editSession,
      ViewToolbar viewToolbar, ParticipantId signedInUser, IdGenerator idGenerator, WaveId waveId) {
    ToolbarSwitcher switcher = new ToolbarSwitcher(panel, editSession, viewToolbar,
        signedInUser, idGenerator, waveId);
    switcher.init();
    return switcher;
  }  
  
  private void init() {
    viewToolbar.init();
    editSession.addListener(this);
    if (editSession.isEditing()) {
      startEditSession(editSession.getEditor());
    } else if (panel.hasContents()) {
      startViewSession();
    }
  }

  @Override
  public void onSessionStart(Editor editor, ConversationBlip blip) {
    viewToolbar.getWidget().removeFromParent();
    startEditSession(editor);
  }

  private void startEditSession(Editor editor) {
    panel.getContents().setToolbar(getEditToolbar().getWidget().getElement());
    panel.getGwtPanel().doAdopt(getEditToolbar().getWidget());
    getEditToolbar().enable(editor);
  }

  @Override
  public void onSessionEnd(Editor editor, ConversationBlip blip) {
    getEditToolbar().disable(editor);
    getEditToolbar().getWidget().removeFromParent();
    startViewSession();
  }

  private void startViewSession() {
    panel.getContents().setToolbar(viewToolbar.getWidget().getElement());
    panel.getGwtPanel().doAdopt(viewToolbar.getWidget());
  }
  
  private EditToolbar getEditToolbar() {
    if (editToolbar == null) {
      editToolbar = EditToolbar.create(signedInUser, idGenerator, waveId);
      editToolbar.init();      
    }
    return editToolbar;
  }
}
