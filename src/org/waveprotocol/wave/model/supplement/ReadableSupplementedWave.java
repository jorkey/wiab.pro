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

package org.waveprotocol.wave.model.supplement;

import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.util.ReadableStringMap;
import org.waveprotocol.wave.model.version.HashedVersion;

import java.util.Set;

/**
 * A supplement that is readable.
 */
public interface ReadableSupplementedWave {

  /**
   * @return the state (collapsed etc.) of a conversation thread.
   *
   * @param thread  the thread to examine
   */
  ThreadState getThreadState(ConversationThread thread);

  /**
   * @return true if the blip is unread at the current version.
   *
   * @param blip the blip
   */
  boolean isUnread(ConversationBlip blip);

 /**
   * @param blip the blip
   *
   * @return true if the blip was anytime read.
   */
  boolean wasBlipEverRead(ConversationBlip blip);

  /**
   * @return true, if the blip has look version.
   *
   * @param blip the blip
   */
  boolean isBlipLooked(ConversationBlip blip);

  /**
   * @return true, if the blip is looked at the specified version.
   *
   * @param waveletId wavelet id
   * @param blipId blip id
   * @param version
   */
  boolean isBlipLooked(WaveletId waveletId, String blipId, long version);

  /**
   * @return true if the wavelet has been looked before.
   *
   * @param waveletId wavelet id
   */
  boolean isWaveletLooked(WaveletId waveletId);

  /**
   * @return the last read wavelet version.
   *
   * @param waveletId wavelet id
   */
  long getLastReadWaveletVersion(WaveletId waveletId);

  /**
   * @return true if the set of participants is unread at the current version.
   *
   * @param waveletId the id of wavelet to check if it has unread participants.
   */
  boolean isParticipantsUnread(WaveletId waveletId);

  /**
   * @return true if the participant list has been read before.
   *
   * @param waveletId id of the wavelet to check if it has unread participants.
   */
  boolean haveParticipantsEverBeenRead(WaveletId waveletId);

  /**
   * @return true if the wavelet has unread tag changes at the current version.
   *
   * @param waveletId id of the wavelet to check if it has unread tags.
   */
  boolean isTagsUnread(WaveletId waveletId);

 /**
   * @param waveletId id of the wavelet to check if it has unread tags.
   *
   * @return true if the tags was anytime read.
   */
  boolean wasTagsEverRead(WaveletId waveletId);

  /**
   * @return the folders to which this wave has been assigned.
   */
  Set<Integer> getFolders();

  /**
   * @return true if this wave should be in the inbox.
   */
  boolean isInbox();

  /**
   * @return the WantedEvaluationSet for a given wavelet.
   *
   * @param waveletId wavelet id
   */
  WantedEvaluationSet getWantedEvaluationSet(WaveletId waveletId);

  /**
   * @return true if the wave is mute.
   */
  boolean isMute();

  /**
   * @return true if the wave is archived.
   */
  boolean isArchived();

  /**
   * @return true if the wave is being followed, false if un-followed.
   */
  boolean isFollowed();

  /**
   * @return true if the wave is in trash, false if anything else.
   */
  boolean isTrashed();

  /**
   * @return the version and hash of the wavelet the last time
   *   this wavelet was opened (and sighted by the user).
   *
   * @param waveletId wavelet id
   */
  HashedVersion getSeenVersion(WaveletId waveletId);

  /**
   * @return true if there is some wavelet which has been seen, false otherwise.
   */
  boolean hasBeenSeen();

  /**
   * @return true if there is a pending notification, false otherwise.
   */
  boolean hasPendingNotification();

  /**
   * Reads the value of the given key from the state of the given gadget.
   * @return Value for the given key or null if gadget or key is missing.
   *
   * @param gadgetId ID of the gadget.
   * @param key State key.
   */
  String getGadgetStateValue(String gadgetId, String key);

  /**
   * @return Gadget state as StateMap for the given gadget.
   *
   * @param gadgetId ID of the gadget to get the state of.
   */
  ReadableStringMap<String> getGadgetState(String gadgetId);

  /**
   * @return id of the focused blip in the wavelet.
   *
   * @param waveletId wavelet id
   */
  String getFocusedBlipId(WaveletId waveletId);

  /**
   * @return screen position in the wavelet.
   *
   * @param waveletId wavelet id
   */
  ScreenPosition getScreenPosition(WaveletId waveletId);
}
