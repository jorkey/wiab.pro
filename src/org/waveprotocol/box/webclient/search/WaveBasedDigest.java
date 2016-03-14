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

package org.waveprotocol.box.webclient.search;

import org.waveprotocol.wave.client.state.BlipReadStateMonitor;
import org.waveprotocol.wave.client.state.InboxStateMonitor;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationStructure;
import org.waveprotocol.wave.model.conversation.InboxState;
import org.waveprotocol.wave.model.conversation.TitleHelper;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.Blip;
import org.waveprotocol.wave.model.wave.ObservableWavelet;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.WaveViewListener;
import org.waveprotocol.wave.model.wave.Wavelet;
import org.waveprotocol.wave.model.wave.WaveletListener;

import java.util.List;

/**
 * Produces a digest from a wave.
 *
 * @author hearnden@google.com (David Hearnden)
 */
public final class WaveBasedDigest
   implements Digest, BlipReadStateMonitor.Listener,
   InboxStateMonitor.Listener, WaveViewListener, WaveletListener {

  private final static int PARTICIPANT_SNIPPET_SIZE = 2;
  private final static double NO_TIME = 1;

  /** The wave to digest. */
  private final WaveContext wave;
  /** Observers of this digest. */
  // TODO(hearnden): make a single listener.
  private final CopyOnWriteSet<Listener> listeners = CopyOnWriteSet.create();

  // Lazily-constructed cached answers.
  private List<ParticipantId> participantSnippet;
  private ParticipantId author;
  private double lastModified = NO_TIME;

  WaveBasedDigest(WaveContext wave) {
    this.wave = wave;
  }

  /**
   * Creates a digest.
   */
  public static WaveBasedDigest create(WaveContext wave) {
    WaveBasedDigest digest = new WaveBasedDigest(wave);
    digest.init();
    return digest;
  }

  private void init() {
    for (ObservableWavelet wavelet : wave.getWave().getWavelets()) {
      wavelet.addListener(this);
    }
    wave.getWave().addListener(this);
    wave.getBlipMonitor().addListener(this);
    wave.getInboxMonitor().addListener(this);
  }

  /**
   * Releases listeners from observed resources.
   */
  void destroy() {
    wave.getBlipMonitor().removeListener(this);
    wave.getInboxMonitor().removeListener(this);
    wave.getWave().removeListener(this);
    for (ObservableWavelet wavelet : wave.getWave().getWavelets()) {
      wavelet.removeListener(this);
    }
  }

  private void ensureParticipants() {
    if (participantSnippet != null) {
      return;
    }

    // Participant order is defined as follows:
    //
    // 1. Let the conversations be in their natural order, except with the main
    // conversation promoted to first. Project out the participants of those
    // conversations into a list.
    // 2. The digest author is the first participant in that list.
    // 3. The participant snippet is the next PARTICIPANT_SNIPPET_SIZE unique
    // participants in that list.
    Conversation main = ConversationStructure.getMainConversation(wave.getConversations());
    List<Conversation> conversations = CollectionUtils.newLinkedList();
    conversations.addAll(wave.getConversations().getConversations());
    // Waves are not forced to have conversations in them, so it is legitimate
    // for main to be null.
    if (main != null) {
      conversations.remove(main);
      conversations.add(0, main);
    }

    // Collect the author and participants in the same list, then partition
    // afterwards.
    participantSnippet = CollectionUtils.newArrayList();
    outer: for (Conversation conversation : conversations) {
      for (ParticipantId participant : conversation.getParticipantIds()) {
        if (participantSnippet.size() < PARTICIPANT_SNIPPET_SIZE + 1) {
          participantSnippet.add(participant);
        } else {
          break outer;
        }
      }
    }

    if (!participantSnippet.isEmpty()) {
      author = participantSnippet.get(0);
      participantSnippet = participantSnippet.subList(1, participantSnippet.size());
    }
  }

  private void invalidateParticipants() {
    author = null;
    participantSnippet = null;
  }

  private void ensureLmt() {
    for (Wavelet wavelet : wave.getWave().getWavelets()) {
      if (!IdUtil.isConversationalId(wavelet.getId())) {
        // Skip non conversational wavelets.
        continue;
      }
      lastModified = Math.max(lastModified, wavelet.getLastModifiedTime());
    }
  }

  @SuppressWarnings("unused")
  private void invalidateLmt() {
    lastModified = NO_TIME;
  }

  @Override
  public WaveId getWaveId() {
    return wave.getWave().getWaveId();
  }

  @Override
  public ParticipantId getAuthor() {
    ensureParticipants();
    return author;
  }

  @Override
  public List<ParticipantId> getParticipantsSnippet() {
    ensureParticipants();
    return participantSnippet;
  }

  @Override
  public String getTitle() {
    return TitleHelper.extractTitle(wave);
  }

  @Override
  public String getSnippet() {
    return TitleHelper.extractSnippet(wave);
  }

  @Override
  public int getBlipCount() {
    return wave.getBlipMonitor().getReadCount() +
        wave.getBlipMonitor().getUnreadCount();
  }

  @Override
  public int getUnreadCount() {
    return wave.getBlipMonitor().getUnreadCount();
  }

  @Override
  public double getLastModifiedTime() {
    ensureLmt();
    return lastModified;
  }

  @Override
  public InboxState getInboxState() {
    if (wave.getSupplement().isInbox()) {
      return InboxState.INBOX;
    }
    if (wave.getSupplement().isArchived()) {
      return InboxState.ARCHIVE;
    }
    return InboxState.MUTE;
  }

  //
  // Events sent by this digest.
  //


  /**
   * Observes digest changes.
   */
  public interface Listener {
    void onChanged();
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  private void fireOnChanged() {
    for (Listener listener : listeners) {
      listener.onChanged();
    }
  }

  //
  // Events of interest to this digest.
  //

  @Override
  public void onReadStateChanged() {
    fireOnChanged();
  }

  @Override
  public void onInboxStateChanged() {
    fireOnChanged();
  }

  @Override
  public void onLastModifiedTimeChanged(
      ObservableWavelet wavelet, long oldTime, long newTime) {
    if (newTime != oldTime) {
      fireOnChanged();
    }
  }

  @Override
  public void onWaveletAdded(ObservableWavelet wavelet) {
    wavelet.addListener(this);
  }

  @Override
  public void onWaveletRemoved(ObservableWavelet wavelet) {
    wavelet.removeListener(this);
  }

  @Override
  public void onParticipantAdded(ObservableWavelet wavelet, ParticipantId participant,
      WaveletOperationContext opContext) {
    invalidateParticipants();
    fireOnChanged();
  }

  @Override
  public void onParticipantRemoved(ObservableWavelet wavelet, ParticipantId participant,
      WaveletOperationContext opContext) {
    invalidateParticipants();
    fireOnChanged();
  }

  @Override
  public void onTagAdded(ObservableWavelet wavelet, String tag, WaveletOperationContext opContext) {
  }

  @Override
  public void onTagRemoved(ObservableWavelet wavelet, String tag, WaveletOperationContext opContext) {
  }

  //
  // Events not of interest.
  //

  @Override
  public void onBlipAdded(ObservableWavelet wavelet, Blip blip) {
  }

  @Override
  public void onBlipRemoved(ObservableWavelet wavelet, Blip blip) {
  }

  @Override
  public void onBlipSubmitted(ObservableWavelet wavelet, Blip blip) {
  }

  @Override
  public void onBlipContributorAdded(
      ObservableWavelet wavelet, Blip blip, ParticipantId contributor) {
  }

  @Override
  public void onBlipContributorRemoved(
      ObservableWavelet wavelet, Blip blip, ParticipantId contributor) {
  }

  @Override
  public void onBlipTimestampModified(
      ObservableWavelet wavelet, Blip blip, long oldTime, long newTime) {
  }

  @Override
  public void onBlipVersionModified(
      ObservableWavelet wavelet, Blip blip, Long oldVersion, Long newVersion) {
  }

  @Override
  @Deprecated
  public void onRemoteBlipContentModified(Blip blip) {
  }

  @Override
  public void onHashedVersionChanged(
      ObservableWavelet wavelet, HashedVersion oldHashedVersion, HashedVersion newHashedVersion) {
  }

  @Override
  public void onVersionChanged(ObservableWavelet wavelet, long oldVersion, long newVersion) {
  }
}
