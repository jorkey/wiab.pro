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

import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * View of a participant collection.
 *
 */
public interface ParticipantsView extends View, IntrinsicParticipantsView {

  /**
   * Appends a rendering of a participant with showing diffs, if necessary.
   *
   * @param conversation conversation in which the participant participates
   * @param participantId participant to render
   * @param opContext wavelet operation context
   * @param showDiff - if true, the diffs should be shown
   */
  ParticipantView appendParticipant(Conversation conversation, ParticipantId participantId,
      WaveletOperationContext opContext, boolean showDiff);
  
  /**
   * Removes a rendering of a participant with showing diffs, if necessary.
   * 
   * @param conversation conversation in which the participant is removed
   * @param participantId participant id
   * @param opContext wavelet operation context
   * @param showDiff - if true, the diffs should be shown
   */
  ParticipantView removeParticipant(Conversation conversation, ParticipantId participantId,
      WaveletOperationContext opContext, boolean showDiff);
  
  /**
   * Clears all diffs shown before.
   */
  void clearDiffs();
}
