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

import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.focus.ObservableFocusFramePresenter;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationBlip;

/**
 * Curries the actions from {@link Actions} with blip/thread context from the
 * focus frame.
 *
 * @author hearnden@google.com (David Hearnden)
 */
public final class FocusedActions {
  
  private final ObservableFocusFramePresenter focus;
  private final Actions actions;

  /**
   * Implements the wave panel's editing UI actions.
   *
   * @param focus focus-frame feature
   * @param actions context-free actions
   */
  public FocusedActions(ObservableFocusFramePresenter focus, Actions actions) {
    this.focus = focus;
    this.actions = actions;
  }

  void startEditing() {
    if (isGoodFocusedBlip()) {
      actions.startEditing(getFocusedBlip());
    }
  }

  void reply() {
    if (isGoodFocusedBlip()) {
      actions.reply(getFocusedBlip());
    }
  }

  void add() {
    if (isGoodFocusedBlip()) {
      actions.addBlipAfter(getFocusedBlip());
    }
  }

  void delete(Actions.DeleteOption option) {
    if (isGoodFocusedBlip()) {
      actions.deleteBlip(getFocusedBlip(), option);
    }
  }

  void popupLink() {
    if (isGoodFocusedBlip()) {
      actions.popupLink(getFocusedBlip());
    }
  }

  private ConversationBlip getFocusedBlip() {
    return focus.getFocusedBlip();
  }

  private boolean isGoodFocusedBlip() {
    QuasiConversationBlip quasiBlip = (QuasiConversationBlip) getFocusedBlip();
    return quasiBlip != null && !quasiBlip.isQuasiDeleted();
  }
}
