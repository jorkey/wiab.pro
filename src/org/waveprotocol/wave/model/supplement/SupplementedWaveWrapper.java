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
 * Base implementation of anything that decorates a supplemented wave.
 *
 * @param <T> supplemented wave type.
 *
 * @author hearnden@google.com (David Hearnden)
 */
public abstract class SupplementedWaveWrapper<T extends SupplementedWave>
    implements SupplementedWave {

  /** Decorated supplement . */
  protected final T delegate;

  protected SupplementedWaveWrapper(T delegate) {
    this.delegate = delegate;
  }

  // ReadableSupplementedWave's overrides

  @Override
  public boolean isWaveletLooked(WaveletId waveletId) {
    return delegate.isWaveletLooked(waveletId);
  }

  @Override
  public long getLastReadWaveletVersion(WaveletId waveletId) {
    return delegate.getLastReadWaveletVersion(waveletId);
  }

  @Override
  public boolean isUnread(ConversationBlip blip) {
    return delegate.isUnread(blip);
  }

  @Override
  public boolean wasBlipEverRead(ConversationBlip blip) {
    return delegate.wasBlipEverRead(blip);
  }

  @Override
  public boolean isBlipLooked(ConversationBlip blip) {
    return delegate.isBlipLooked(blip);
  }

  @Override
  public boolean isBlipLooked(WaveletId waveletId, String blipId, long version) {
    return delegate.isBlipLooked(waveletId, blipId, version);
  }

  @Override
  public String getFocusedBlipId(WaveletId waveletId) {
    return delegate.getFocusedBlipId(waveletId);
  }

  @Override
  public ScreenPosition getScreenPosition(WaveletId waveletId) {
    return delegate.getScreenPosition(waveletId);
  }

  @Override
  public boolean isParticipantsUnread(WaveletId waveletId) {
    return delegate.isParticipantsUnread(waveletId);
  }

  @Override
  public boolean isTagsUnread(WaveletId waveletId) {
    return delegate.isTagsUnread(waveletId);
  }

  @Override
  public boolean wasTagsEverRead(WaveletId waveletId) {
    return delegate.wasTagsEverRead(waveletId);
  }

  @Override
  public boolean isTrashed() {
    return delegate.isTrashed();
  }

  // WritableSupplementedWave's overrides

  @Override
  public void markAsRead(WaveletId waveletId) {
    delegate.markAsRead(waveletId);
  }

  @Override
  public void markAsRead(ConversationBlip blip) {
    delegate.markAsRead(blip);
  }

  @Override
  public void markAsUnread() {
    delegate.markAsUnread();
  }

  @Override
  public void setFocusedBlipId(WaveletId waveletId, String blipId) {
    delegate.setFocusedBlipId(waveletId, blipId);
  }

  @Override
  public void setScreenPosition(WaveletId waveletId, ScreenPosition screenPosition) {
    delegate.setScreenPosition(waveletId, screenPosition);
  }

  @Override
  public void markAsRead() {
    delegate.markAsRead();
  }

  @Override
  public void markParticipantsAsRead(WaveletId waveletId) {
    delegate.markParticipantsAsRead(waveletId);
  }

  @Override
  public void markTagsAsRead(WaveletId waveletId) {
    delegate.markTagsAsRead(waveletId);
  }

  //
  // Inbox and folders.
  //

  @Override
  public boolean isArchived() {
    return delegate.isArchived();
  }

  @Override
  public boolean isFollowed() {
    return delegate.isFollowed();
  }

  @Override
  public boolean isInbox() {
    return delegate.isInbox();
  }

  @Override
  public boolean isMute() {
    return delegate.isMute();
  }

  @Override
  public void inbox() {
    delegate.inbox();
  }

  @Override
  public void archive() {
    delegate.archive();
  }

  @Override
  public void follow() {
    delegate.follow();
  }

  @Override
  public void unfollow() {
    delegate.unfollow();
  }

  @Override
  public void mute() {
    delegate.mute();
  }

  @Override
  public Set<Integer> getFolders() {
    return delegate.getFolders();
  }

  @Override
  public void moveToFolder(int folderId) {
    delegate.moveToFolder(folderId);
  }

  // Seen.

  @Override
  public void see() {
    delegate.see();
  }

  @Override
  public void see(WaveletId waveletId) {
    delegate.see(waveletId);
  }

  @Override
  public void firstLookWavelet(WaveletId waveletId) {
    delegate.firstLookWavelet(waveletId);
  }

  @Override
  public void firstLookBlip(ConversationBlip blip) {
    delegate.firstLookBlip(blip);
  }

  @Override
  public HashedVersion getSeenVersion(WaveletId id) {
    return delegate.getSeenVersion(id);
  }

  @Override
  public boolean hasBeenSeen() {
    return delegate.hasBeenSeen();
  }

  // Abuse.

  @Override
  public void addWantedEvaluation(WantedEvaluation evaluation) {
    delegate.addWantedEvaluation(evaluation);
  }

  @Override
  public WantedEvaluationSet getWantedEvaluationSet(WaveletId waveletId) {
    return delegate.getWantedEvaluationSet(waveletId);
  }

  // Collapse state

  @Override
  public ThreadState getThreadState(ConversationThread thread) {
    return delegate.getThreadState(thread);
  }


  @Override
  public void setThreadState(ConversationThread thread, ThreadState state) {
    delegate.setThreadState(thread, state);
  }

  // Notifications

  @Override
  public boolean hasPendingNotification() {
    return delegate.hasPendingNotification();
  }

  @Override
  public void markAsNotified() {
    delegate.markAsNotified();
  }

  @Override
  public boolean haveParticipantsEverBeenRead(WaveletId waveletId) {
    return delegate.haveParticipantsEverBeenRead(waveletId);
  }

  // Gadgets

  @Override
  public ReadableStringMap<String> getGadgetState(String gadgetId) {
    return delegate.getGadgetState(gadgetId);
  }

  @Override
  public String getGadgetStateValue(String gadgetId, String key) {
    return delegate.getGadgetStateValue(gadgetId, key);
  }

  @Override
  public void setGadgetState(String gadgetId, String key, String value) {
    delegate.setGadgetState(gadgetId, key, value);
  }
}
