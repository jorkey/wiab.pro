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

import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.util.ReadableStringMap;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.util.Preconditions;

import java.util.HashSet;
import java.util.Set;

/**
 * Canonical implementation of a {@link Supplement}.
 *
 */
public final class SupplementImpl implements Supplement {

  /** Semantic-free ADT. */
  private final PrimitiveSupplement primitive;

  /**
   * Creates a supplement.
   *
   * @param primitive underlying data-holding primitive
   */
  public SupplementImpl(PrimitiveSupplement primitive) {
    this.primitive = primitive;
  }

  @Override
  public ThreadState getThreadState(WaveletId waveletId, String threadId) {
    return primitive.getThreadState(waveletId, threadId);
  }

  @Override
  public void setThreadState(WaveletId waveletId, String threadId, ThreadState newState) {
    primitive.setThreadState(waveletId, threadId, newState);
  }

  /**
   * Tests if a component is unread, given its last-read and last-modified
   * versions.
   *
   * @param read      last-read version
   * @param modified  last-modified version
   * @return true if the last-read version is the special
   *         {@link PrimitiveSupplement#NO_VERSION} value, or if it is less than
   *         the last-modified value.
   */
  private boolean isUnread(long read, long modified) {
    return read == PrimitiveSupplement.NO_VERSION || read < modified;
  }

  @Override
  public boolean wasBlipEverRead(WaveletId waveletId, String blipId, long creationVersion) {
    return primitive.getLastReadBlipVersion(waveletId, blipId) != PrimitiveSupplement.NO_VERSION &&
        !isUnread(primitive.getLastReadWaveletVersion(waveletId), creationVersion);
  }

  /**
   * {@inheritDoc}
   *
   * A blip is unread if, and only if (a) the read-version for that blip either
   * does not exist or is less than the last-modified version; and (b) the
   * wavelet-override version either does not exist or is less than the blip's
   * last-modified version.
   */
  @Override
  public boolean isBlipUnread(WaveletId waveletId, String blipId, long version) {
    return isUnread(primitive.getLastReadBlipVersion(waveletId, blipId), version)
        && isUnread(primitive.getLastReadWaveletVersion(waveletId), version);
  }

  /**
   * {@inheritDoc}
   *
   * The participants collection is unread if, and only if (a) its read-version
   * either does not exist or is less than its last-modified version; and (b)
   * the wavelet-override version either does not exist or is less than the
   * participants' last-modified version.
   */
  @Override
  public boolean isParticipantsUnread(WaveletId waveletId, long version) {
    return isUnread(primitive.getLastReadParticipantsVersion(waveletId), version)
        && isUnread(primitive.getLastReadWaveletVersion(waveletId), version);
  }

  @Override
  public boolean haveParticipantsEverBeenRead(WaveletId waveletId) {
    return primitive.getLastReadParticipantsVersion(waveletId) != PrimitiveSupplement.NO_VERSION
        || primitive.getLastReadWaveletVersion(waveletId) != PrimitiveSupplement.NO_VERSION;
  }

  @Override
  public boolean isTagsUnread(WaveletId waveletId, long version) {
    return isUnread(primitive.getLastReadTagsVersion(waveletId), version)
        && isUnread(primitive.getLastReadWaveletVersion(waveletId), version);
  }

  @Override
  public boolean wasTagsEverRead(WaveletId waveletId) {
    return primitive.getLastReadTagsVersion(waveletId) != PrimitiveSupplement.NO_VERSION;
  }

  @Override
  public String getFocusedBlipId(WaveletId waveletId) {
    return primitive.getFocusedBlipId(waveletId);
  }

  @Override
  public ScreenPosition getScreenPosition(WaveletId waveletId) {
    return primitive.getScreenPosition(waveletId);
  }

  @Override
  public long getFirstLookWaveletVersion(WaveletId waveletId) {
    return primitive.getFirstLookWaveletVersion(waveletId);
  }

  @Override
  public boolean isWaveletLooked(WaveletId waveletId) {
    if (primitive.getLastReadWaveletVersion(waveletId) != PrimitiveSupplement.NO_VERSION) {
      return true;
    }
    return primitive.getFirstLookWaveletVersion(waveletId) != PrimitiveSupplement.NO_VERSION;
  }

  @Override
  public long getLastReadWaveletVersion(WaveletId waveletId) {
    return primitive.getLastReadWaveletVersion(waveletId);
  }

  @Override
  public long getFirstLookBlipVersion(WaveletId waveletId, String blipId) {
    return primitive.getFirstLookBlipVersion(waveletId, blipId);
  }

  @Override
  public boolean isBlipLooked(WaveletId waveletId, String blipId, long blipCreationVersion) {
    long lastReadWaveletVersion = primitive.getLastReadWaveletVersion(waveletId);
    if (lastReadWaveletVersion != PrimitiveSupplement.NO_VERSION &&
        lastReadWaveletVersion >= blipCreationVersion) {
      return true;
    }
    if (primitive.getLastReadBlipVersion(waveletId, blipId) != PrimitiveSupplement.NO_VERSION) {
      return true;
    }
    long firstLookWaveletVersion = primitive.getFirstLookWaveletVersion(waveletId);
    if (firstLookWaveletVersion != PrimitiveSupplement.NO_VERSION &&
        firstLookWaveletVersion >= blipCreationVersion) {
      return true;
    }
    return primitive.getFirstLookBlipVersion(waveletId, blipId) != PrimitiveSupplement.NO_VERSION;
  }

  @Override
  public long getLastReadBlipVersion(WaveletId waveletId, String blipId) {
    return primitive.getLastReadBlipVersion(waveletId, blipId);
  }

  @Override
  public long getLastReadParticipantsVersion(WaveletId waveletId) {
    return primitive.getLastReadParticipantsVersion(waveletId);
  }

  @Override
  public long getLastReadTagsVersion(WaveletId waveletId) {
    return primitive.getLastReadTagsVersion(waveletId);
  }

  /**
   * {@inheritDoc}
   *
   * If the blip is already considered as read, this method has no effect.
   */
  @Override
  public void markBlipAsRead(WaveletId waveletId, String blipId, long version) {
    if (isBlipUnread(waveletId, blipId, version)) {
      // Clear look version to save space in snapshot.
      primitive.clearBlipLookState(waveletId, blipId);

      primitive.setLastReadBlipVersion(waveletId, blipId, version);
    }
  }

  /**
   * {@inheritDoc}
   *
   * If the participants collection is already considered as read, this method
   * has no effect.
   */
  @Override
  public void markParticipantsAsRead(WaveletId waveletId, long version) {
    if (isParticipantsUnread(waveletId, version)) {
      primitive.setLastReadParticipantsVersion(waveletId, version);
    }
  }

  /**
   * {@inheritDoc}
   *
   * If the tags document is already considered as read, this method
   * has no effect.
   */
  @Override
  public void markTagsAsRead(WaveletId waveletId, long version) {
    if (isTagsUnread(waveletId, version)) {
      primitive.setLastReadTagsVersion(waveletId, version);
    }
  }

  @Override
  public void markWaveletAsRead(WaveletId waveletId, long version) {
    // Clear look version to save space in snapshot.
    primitive.clearWaveletLookState(waveletId);

    primitive.setLastReadWaveletVersion(waveletId, version);
  }

  @Override
  public void setFirstLookBlipVersion(WaveletId waveletId, String blipId, long version) {
    Preconditions.checkState(!isBlipLooked(waveletId, blipId, version), "Already looked");
    primitive.setFirstLookBlipVersion(waveletId, blipId, version);
  }

  @Override
  public void setFirstLookWaveletVersion(WaveletId waveletId, long version) {
    Preconditions.checkState(!isWaveletLooked(waveletId), "Already looked");
    primitive.setFirstLookWaveletVersion(waveletId, version);
  }

  @Override
  public void markAsUnread() {
    primitive.clearReadState();
  }

  @Override
  public void setFocusedBlipId(WaveletId waveletId, String blipId) {
    primitive.setFocusedBlipId(waveletId, blipId);
  }

  @Override
  public void setScreenPosition(WaveletId waveletId, ScreenPosition screenPosition) {
    primitive.setScreenPosition(waveletId, screenPosition);
  }

  @Override
  public Set<Integer> getFolders() {
    return toSet(primitive.getFolders());
  }

  @Override
  public void moveToFolder(int id) {
    primitive.removeAllFolders();
    primitive.addFolder(id);
  }

  @Override
  public void removeAllFolders() {
    primitive.removeAllFolders();
  }

  @Override
  public boolean isArchived(WaveletId waveletId, long version) {
    return (primitive.getArchiveWaveletVersion(waveletId) >= version);
  }

  @Override
  public void follow() {
    primitive.follow();
  }

  @Override
  public void unfollow() {
    primitive.clearArchiveState();
    primitive.unfollow();
  }

  @Override
  public boolean isFollowed(boolean defaultFollowed) {
    Boolean explicitFollow = primitive.getFollowed();
    return explicitFollow != null ? explicitFollow : defaultFollowed;
  }

  @Override
  public void clearArchive() {
    primitive.clearArchiveState();
  }

  @Override
  public void archive(WaveletId waveletId, long version) {
    primitive.archiveAtVersion(waveletId, version);
  }

  @Override
  public void setSeenVersion(WaveletId waveletId, HashedVersion signature) {
    primitive.setSeenVersion(waveletId, signature);
  }

  @Override
  public HashedVersion getSeenVersion(WaveletId waveletId) {
    return primitive.getSeenVersion(waveletId);
  }

  @Override
  public long getNotifiedVersion(WaveletId waveletId) {
    return primitive.getNotifiedVersion(waveletId);
  }

  @Override
  public Set<WaveletId> getSeenWavelets() {
    return primitive.getSeenWavelets();
  }

  @Override
  public boolean hasSeenVersion() {
    return !primitive.getSeenWavelets().isEmpty();
  }

  private static <T> Set<T> toSet(Iterable<T> items) {
    Set<T> set = new HashSet<>();
    for (T item : items) {
      set.add(item);
    }
    return set;
  }

  @Override
  public WantedEvaluationSet getWantedEvaluationSet(WaveletId waveletId) {
    Set<WantedEvaluation> relevantEvaluations = new HashSet<>();
    for (WantedEvaluation evaluation : primitive.getWantedEvaluations()) {
      // evaluation.getWaveletId() may be null - so must compare in this order
      if (waveletId.equals(evaluation.getWaveletId())) {
        relevantEvaluations.add(evaluation);
      }
    }
    return new SimpleWantedEvaluationSet(waveletId, relevantEvaluations);
  }

  @Override
  public void addWantedEvaluation(WantedEvaluation evaluation) {
    primitive.addWantedEvaluation(evaluation);
  }

  @Override
  public boolean hasPendingNotification() {
    Boolean pending = primitive.getPendingNotification();
    return (pending == null ? false : pending);
  }

  @Override
  public boolean hasPendingNotification(WaveletId waveletId) {
    if (!hasNotifiedVersion()) {
      // If we have not used the new approach of notified versions yet,
      // we need to check if there is a pending notification recorded
      // using the old pending notification flag.
      // TODO(user): migrate UDWs to replace the pending notification
      // flag with notified versions at the current version.
      return hasPendingNotification();
    }
    return getSeenVersion(waveletId).getVersion() < getNotifiedVersion(waveletId);
  }

  @Override
  public void markWaveletAsNotified(WaveletId waveletId, long version) {
    primitive.setNotifiedVersion(waveletId, version);
  }

  @Override
  public boolean hasNotifiedVersion() {
    return !primitive.getNotifiedWavelets().isEmpty();
  }

  @Override
  public void clearPendingNotification() {
    primitive.clearPendingNotification();
  }

  @Override
  public ReadableStringMap<String> getGadgetState(String gadgetId) {
    return primitive.getGadgetState(gadgetId);
  }

  @Override
  public void setGadgetState(String gadgetId, String key, String value) {
    primitive.setGadgetState(gadgetId, key, value);
  }
}
