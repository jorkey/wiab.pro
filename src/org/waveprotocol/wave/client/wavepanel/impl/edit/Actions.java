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
import org.waveprotocol.wave.model.conversation.ConversationThread;

/**
 * Defines the UI actions that can be performed as part of the editing feature.
 * This includes editing, replying, and deleting blips in a conversation.
 *
 */
public interface Actions {
/**
 * The options of deletion blip or thread.
 */
  enum DeleteOption {
    /** Deletes non-empty blip or thread without confirmation. */
    WITHOUT_CONFIRMATION,
    /** Deletes non-empty blip or thread with confirmation. */
    WITH_CONFIRMATION,
    /** Doesn't delete non-empty blip or thread at all. */
    DELETE_EMPTY_ONLY
  }
  
  /**
   * Starts editing a blip.
   */
  void startEditing(ConversationBlip blip);

  /**
   * Stops editing a blip.
   */
  void stopEditing(boolean endWithDone);

  /**
   * Replies to a blip.
   */
  void reply(ConversationBlip blip);

  /**
   * Adds a continuation to a thread.
   */
  void addBlipToThread(ConversationThread thread);

  /**
   * Adds a continuation to a blip.
   */
  void addBlipAfter(ConversationBlip blip);

  /**
   * Deletes a blip.
   */
  void deleteBlip(ConversationBlip blip, DeleteOption option);

  /**
   * Deletes a thread.
   */
  void deleteParentThread(ConversationBlip blip, DeleteOption option);

  /**
   * Pops up a link info for the blip.
   */
  void popupLink(ConversationBlip blip);
  
  /**
   * Switch editor to draft mode. No changes will be sent to upstream
   */
  void enterDraftMode();
  
  /**
   * Switch editor back to normal mode. 
   * @param saveChanges save changes being made in draft mode
   */
  void leaveDraftMode(boolean saveChanges);
}
