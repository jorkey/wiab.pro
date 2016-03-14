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

package org.waveprotocol.wave.client.wavepanel.impl.edit;

import org.waveprotocol.wave.client.common.util.KeyCombo;
import org.waveprotocol.wave.client.common.util.KeyComboContext;
import org.waveprotocol.wave.client.common.util.KeyComboTask;
import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.event.KeySignalHandler;
import org.waveprotocol.wave.client.wavepanel.event.KeySignalRouter;
import org.waveprotocol.wave.model.conversation.focus.ObservableFocusFramePresenter;

import java.util.EnumSet;

/**
 * Defines the UI actions that can be performed as part of the editing feature.
 * This includes editing, replying, and deleting blips in a conversation.
 *
 */
public final class EditController implements KeySignalHandler {

  /** Action performer. */
  private final FocusedActions actions;

  EditController(FocusedActions actions) {
    this.actions = actions;
  }

  /**
   * Creates and installs the edit control feature.
   */
  public static void install(ObservableFocusFramePresenter focus, Actions actions, WavePanel panel) {
    new EditController(new FocusedActions(focus, actions)).install(panel.getKeyRouter());
  }

  private void install(KeySignalRouter keys) {
    keys.registerTasks(KeyComboContext.WAVE, EnumSet.of(
        KeyComboTask.EDIT_BLIP,
        KeyComboTask.REPLY_TO_BLIP,
        KeyComboTask.CONTINUE_THREAD,
        KeyComboTask.DELETE_BLIP,
        KeyComboTask.DELETE_BLIP_WITHOUT_CONFIRMATION,
        KeyComboTask.POPUP_LINK), this);
  }

  @Override
  public boolean onKeySignal(KeyCombo key) {
    switch (key.getAssignedTask(KeyComboContext.WAVE)) {
      case EDIT_BLIP:
        actions.startEditing();
        return true;
      case CONTINUE_THREAD:
        actions.add();
        return true;
      case REPLY_TO_BLIP:
        actions.reply();
        return true;
      case DELETE_BLIP:
        actions.delete(Actions.DeleteOption.WITH_CONFIRMATION);
        return true;
      case DELETE_BLIP_WITHOUT_CONFIRMATION:
        actions.delete(Actions.DeleteOption.WITHOUT_CONFIRMATION);
        return true;
      case POPUP_LINK:
        actions.popupLink();
        return true;
    }
    throw new AssertionError("unknown key: " + key.getHint());
  }
}
