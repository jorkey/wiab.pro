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
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.ReadableStringMap;
import org.waveprotocol.wave.model.util.StringMap;
import org.waveprotocol.wave.model.version.HashedVersion;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Canonical implementation of a {@link PrimitiveSupplement}.
 */
public final class PrimitiveSupplementImpl implements ObservablePrimitiveSupplement {
  /**
   * Holds thread state relevant to a particular wavelet.
   */
  private final static class WaveletThreadState {
    /** Collapsed state for threads */
    private final Map<String, ThreadState> threadCollapsed = new HashMap<>();

    void setThreadState(String tid, ThreadState collapsed) {
      if (collapsed == null) {
        threadCollapsed.remove(tid);
      } else {
        threadCollapsed.put(tid, collapsed);
      }
    }

    ThreadState getThreadState(String tid) {
      return threadCollapsed.get(tid);
    }

    public Iterable<String> getStatefulThreads() {
      return threadCollapsed.keySet();
    }
  }

  /**
   * Holds read state relevant to a particular wavelet.
   */
  private final static class WaveletReadState {

    /** Blip last-read versions. */
    private final Map<String, Long> blipVersions = new HashMap<>();

    /** Wavelet override last-read version.  Null means unspecified. */
    private Long waveletVersion;

    /** Participants-set last-read version.  Null means unspecified. */
    private Long participantVersion;

    /** Tags document last-read version. Null means unspecified. */
    private Long tagsVersion;

    WaveletReadState() {
    }

    void setBlipVersion(String id, long version) {
      // Monotonic guard
      if (!blipVersions.containsKey(id) || blipVersions.get(id) < version) {
        blipVersions.put(id, version);
      }
    }

    void clearBlipVersion(String id) {
      blipVersions.remove(id);
    }
    
    void setParticipantVersion(long version) {
      // Monotonic guard
      if (this.participantVersion == null || this.participantVersion < version) {
        this.participantVersion = version;
      }
    }

    void setTagsVersion(long version) {
      // Monotonic guard
      if (this.tagsVersion == null || this.tagsVersion < version) {
        this.tagsVersion = version;
      }
    }

    void setWaveletVersion(long version) {
      // Monotonic guard
      if (this.waveletVersion == null || this.waveletVersion < participantVersion) {
        this.waveletVersion = version;
      }
    }

    Long getBlipVersion(String id) {
      return blipVersions.get(id);
    }
    
    Long getParticipantVersion() {
      return participantVersion;
    }

    Long getTagsVersion() {
      return tagsVersion;
    }

    Long getWaveletVersion() {
      return waveletVersion;
    }

    Iterable<String> getReadBlips() {
      return blipVersions.keySet();
    }
  }

  /**
   * Holds look state relevant to a particular wavelet.
   */
  private final static class WaveletLookState {

    /** Blip first-look versions. */
    private final Map<String, Long> blipVersions = new HashMap<>();

    /** Wavelet override first-look version.  Null means unspecified. */
    private Long waveletVersion;
    
    void setBlipVersion(String id, long version) {
      blipVersions.put(id, version);
    }
    
    void clearBlipVersion(String id) {
      blipVersions.remove(id);
    }

    void setWaveletVersion(long version) {
      waveletVersion = version;
    }

    void clearWaveletVersion() {
      waveletVersion = null;
    }
    
    Long getBlipVersion(String id) {
      return blipVersions.get(id);
    }

    Long getWaveletVersion() {
      return waveletVersion;
    }
    
    Iterable<String> getLookedBlips() {
      return blipVersions.keySet();
    }
  }  
  
  /**
   * Holds blip state relevant to a particular wavelet.
   */
  private final static class WaveletBlipState {

    /** Focused blip id. */
    private String focusedBlipId;

    /** Screen position. */
    private ScreenPosition screenPosition;

    WaveletBlipState() {
    }

    void setFocusedBlipId(String focusedBlipId) {
      this.focusedBlipId = focusedBlipId;
    }

    void setScreenPosition(ScreenPosition screenPosition) {
      this.screenPosition = screenPosition;
    }

    String getFocusedBlipId() {
      return focusedBlipId;
    }

    ScreenPosition getScreenPosition() {
      return screenPosition;
    }
  }

  /** Maps wavelet ids to their read state. */
  private final Map<WaveletId, WaveletReadState> waveletReadStates = new HashMap<>();

  /** Maps wavelet ids to their look state. */
  private final Map<WaveletId, WaveletLookState> waveletLookStates = new HashMap<>();
  
  /** Maps wavelet ids to their collapsed state. */
  private final Map<WaveletId, WaveletThreadState> waveletThreadStates = new HashMap<>();

  /** Maps wavelet ids to their blip state. */
  private final Map<WaveletId, WaveletBlipState> waveletBlipStates = new HashMap<>();

  /** Folder allocations. */
  private final Set<Integer> folders = new HashSet<>();

  /** Maps wavelet ids to their read state. */
  private final Map<WaveletId, Long> waveletArchiveVersions = new HashMap<>();

  /** Maps wavelet ids to their last seen version and signature. */
  private final Map<WaveletId, HashedVersion> waveletSeenVersions = CollectionUtils.newHashMap();

  /** All the wanted evaluations known to this supplement. */
  private final Set<WantedEvaluation> wantedEvaluations = new HashSet<>();

  /** Listeners attached to this supplement. */
  private final CopyOnWriteSet<Listener> listeners = CopyOnWriteSet.create();

  /** Optional specification of follow state. */
  private Boolean follow;

  /** Optional specification of notification state. */
  private boolean pendingNotification = false;

  /** Maps wavelet ids to their notified version. */
  private final Map<WaveletId, Long> waveletNotifiedVersions = new HashMap<>();

  /** Maps gadget ids to their state maps. */
  private final StringMap<StringMap<String>> gadgetStates =
      CollectionUtils.<StringMap<String>>createStringMap();

  private static long unboxVersion (Long version) {
    return version == null ? NO_VERSION : version;
  }

  /**
   * Creates an empty primitive supplement.
   */
  public PrimitiveSupplementImpl() {
  }

  /**
   * Creates a primitive supplement as a copy of another.
   *
   * @param other  primitive supplement to copy
   */
  public PrimitiveSupplementImpl(PrimitiveSupplement other) {
    for (WaveletId wavelet : other.getReadWavelets()) {
      WaveletReadState readState = getWaveletReadState(wavelet);
      if (other.getLastReadWaveletVersion(wavelet) != NO_VERSION) {
        readState.setWaveletVersion(other.getLastReadWaveletVersion(wavelet));
      }
      if (other.getLastReadParticipantsVersion(wavelet) != NO_VERSION) {
        readState.setParticipantVersion(other.getLastReadParticipantsVersion(wavelet));
      }
      if (other.getLastReadTagsVersion(wavelet) != NO_VERSION) {
        readState.setTagsVersion(other.getLastReadTagsVersion(wavelet));
      }
      for (String blip : other.getReadBlips(wavelet)) {
        readState.setBlipVersion(blip, other.getLastReadBlipVersion(wavelet, blip));
      }
    }

    for (WaveletId wavelet : other.getWaveletsWithThreadState()) {
      WaveletThreadState threadState = getWaveletThreadState(wavelet);
      for (String thread : other.getStatefulThreads(wavelet)) {
        threadState.setThreadState(thread, other.getThreadState(wavelet, thread));
      }
    }

    for (WaveletId wavelet : other.getWaveletsWithBlipState()) {
      WaveletBlipState blipState = getWaveletBlipState(wavelet);
      blipState.setFocusedBlipId(other.getFocusedBlipId(wavelet));
      blipState.setScreenPosition(other.getScreenPosition(wavelet));
    }

    for (WaveletId waveletId : other.getArchiveWavelets()) {
      waveletArchiveVersions.put(waveletId, other.getArchiveWaveletVersion(waveletId));
    }

    for (int folder : other.getFolders()) {
      folders.add(folder);
    }

    for (WantedEvaluation evaluation: other.getWantedEvaluations()) {
      addWantedEvaluation(evaluation);
    }

    for (WaveletId waveletId : other.getSeenWavelets()) {
      waveletSeenVersions.put(waveletId, other.getSeenVersion(waveletId));
    }

    for (WaveletId waveletId : other.getNotifiedWavelets()) {
      waveletNotifiedVersions.put(waveletId, other.getNotifiedVersion(waveletId));
    }

    follow = other.getFollowed();
    pendingNotification = other.getPendingNotification();
  }

  @Override
  public long getFirstLookBlipVersion(WaveletId waveletId, String blipId) {
    Long version = waveletLookStates.containsKey(waveletId) ?
        waveletLookStates.get(waveletId).getBlipVersion(blipId) : null;
    return unboxVersion(version);
  }

  @Override
  public long getLastReadBlipVersion(WaveletId waveletId, String blipId) {
    Long version = waveletReadStates.containsKey(waveletId) ?
        waveletReadStates.get(waveletId).getBlipVersion(blipId) : null;
    return unboxVersion(version);
  }

  @Override
  public long getLastReadParticipantsVersion(WaveletId waveletId) {
    Long version = waveletReadStates.containsKey(waveletId) ?
        waveletReadStates.get(waveletId).getParticipantVersion() : null;
    return unboxVersion(version);
  }

  @Override
  public long getLastReadTagsVersion(WaveletId waveletId) {
    Long version = waveletReadStates.containsKey(waveletId) ?
        waveletReadStates.get(waveletId).getTagsVersion() : null;
    return unboxVersion(version);
  }

  @Override
  public long getFirstLookWaveletVersion(WaveletId waveletId) {
    Long version = waveletLookStates.containsKey(waveletId) ?
        waveletLookStates.get(waveletId).getWaveletVersion() : null;
    return unboxVersion(version);
  }

  @Override
  public long getLastReadWaveletVersion(WaveletId waveletId) {
    Long version = waveletReadStates.containsKey(waveletId) ?
        waveletReadStates.get(waveletId).getWaveletVersion() : null;
    return unboxVersion(version);
  }

  @Override
  public Iterable<WaveletId> getReadWavelets() {
    return waveletReadStates.keySet();
  }

  @Override
  public Iterable<String> getReadBlips(WaveletId waveletId) {
    if (waveletReadStates.containsKey(waveletId)) {
      return waveletReadStates.get(waveletId).getReadBlips();
    } else {
      return Collections.<String>emptyList();
    }
  }

  @Override
  public String getFocusedBlipId(WaveletId waveletId) {
    if (waveletBlipStates.containsKey(waveletId)) {
      return waveletBlipStates.get(waveletId).getFocusedBlipId();
    } else {
      return null;
    }
  }

  @Override
  public ScreenPosition getScreenPosition(WaveletId waveletId) {
    if (waveletBlipStates.containsKey(waveletId)) {
      return waveletBlipStates.get(waveletId).getScreenPosition();
    } else {
      return null;
    }
  }

  @Override
  public void setFocusedBlipId(WaveletId waveletId, String blipId) {
    WaveletBlipState state = getWaveletBlipState(waveletId);
    String oldFocusedBlipId = state.getFocusedBlipId();
    state.setFocusedBlipId(blipId);
    if (oldFocusedBlipId == null && blipId != null ||
        oldFocusedBlipId != null && !oldFocusedBlipId.equals(blipId)) {
      for (Listener listener : listeners) {
        listener.onFocusedBlipIdChanged(waveletId, oldFocusedBlipId, blipId);
      }
    }
  }

  @Override
  public void setScreenPosition(WaveletId waveletId, ScreenPosition screenPosition) {
    WaveletBlipState state = getWaveletBlipState(waveletId);
    ScreenPosition oldScreenPosition = state.getScreenPosition();
    state.setScreenPosition(screenPosition);
    if (oldScreenPosition == null && screenPosition != null ||
        oldScreenPosition != null && !oldScreenPosition.equals(screenPosition)) {
      for (Listener listener : listeners) {
        listener.onScreenPositionChanged(waveletId, oldScreenPosition, screenPosition);
      }
    }
  }

  @Override
  public void setFirstLookBlipVersion(WaveletId waveletId, String blipId, long version) {
    WaveletLookState state = getWaveletLookState(waveletId);
    long oldVersion = unboxVersion(state.getBlipVersion(blipId));
    state.setBlipVersion(blipId, version);
    if (version != oldVersion) {
      for (Listener listener : listeners) {
        listener.onFirstLookBlipVersionSet(waveletId, blipId, version);
      }
    }
  }

  @Override
  public void clearBlipLookState(WaveletId waveletId, String blipId) {
    WaveletLookState lookState = getWaveletLookState(waveletId);
    long oldVersion = unboxVersion(lookState.getBlipVersion(blipId));
    lookState.clearBlipVersion(blipId);
    if (oldVersion != NO_VERSION) {
      for (Listener listener :listeners ) {
        listener.onFirstLookBlipVersionSet(waveletId, blipId, NO_VERSION);
      }
    }
  }  
  
  @Override
  public void setLastReadBlipVersion(WaveletId waveletId, String blipId, long version) {
    WaveletReadState state = getWaveletReadState(waveletId);
    long oldVersion = unboxVersion(state.getBlipVersion(blipId));
    state.setBlipVersion(blipId, version);
    if (version != oldVersion) {
      for (Listener listener : listeners) {
        listener.onLastReadBlipVersionChanged(waveletId, blipId, oldVersion, version);
      }
    }
  }
  
  @Override
  public void setLastReadParticipantsVersion(WaveletId waveletId, long version) {
    WaveletReadState states = getWaveletReadState(waveletId);
    long oldVersion= unboxVersion(states.getParticipantVersion());
    states.setParticipantVersion(version);
    if (version != oldVersion) {
      for (Listener listener : listeners) {
        listener.onLastReadParticipantsVersionChanged(waveletId, oldVersion, version);
      }
    }
  }

  @Override
  public void setLastReadTagsVersion(WaveletId waveletId, long version) {
    WaveletReadState states = getWaveletReadState(waveletId);
    long oldVersion = unboxVersion(states.getTagsVersion());
    states.setTagsVersion(version);
    if (version != oldVersion) {
      for (Listener listener : listeners) {
        listener.onLastReadTagsVersionChanged(waveletId, oldVersion, version);
      }
    }
  }

  @Override
  public void setFirstLookWaveletVersion(WaveletId waveletId, long version) {
    WaveletLookState states = getWaveletLookState(waveletId);
    long oldVersion = unboxVersion(states.getWaveletVersion());
    states.setWaveletVersion(version);
    if (version != oldVersion) {
      for (Listener listener : listeners) {
        listener.onFirstLookWaveletVersionSet(waveletId, version);
      }
    }
  }

  @Override
  public void clearWaveletLookState(WaveletId waveletId) {
    WaveletLookState lookState = getWaveletLookState(waveletId);
    long oldVersion = unboxVersion(lookState.getWaveletVersion());
    lookState.clearWaveletVersion();
    if (oldVersion != NO_VERSION) {
      for (Listener listener :listeners ) {
        listener.onFirstLookWaveletVersionSet(waveletId, NO_VERSION);
      }
    }
  }
  
  @Override
  public void setLastReadWaveletVersion(WaveletId waveletId, long version) {
    WaveletReadState states = getWaveletReadState(waveletId);
    long oldVersion = unboxVersion(states.getWaveletVersion());
    states.setWaveletVersion(version);
    if (version != oldVersion) {
      for (Listener listener : listeners) {
        listener.onLastReadWaveletVersionChanged(waveletId, oldVersion, version);
      }
    }
  }
  
  @Override
  public void clearReadState() {
    waveletReadStates.clear();
  }
  
  @Override
  public void clearBlipReadState(WaveletId waveletId, String blipId) {
    WaveletReadState readState = getWaveletReadState(waveletId);
    long oldVersion = unboxVersion(readState.getBlipVersion(blipId));
    readState.clearBlipVersion(blipId);
    if (oldVersion != NO_VERSION) {
      for (Listener listener :listeners ) {
        listener.onLastReadBlipVersionChanged(waveletId, blipId, oldVersion, NO_VERSION);
      }
    }
  }
  
  @Override
  public ThreadState getThreadState(WaveletId waveletId, String threadId) {
    return getWaveletThreadState(waveletId).getThreadState(threadId);
  }

  @Override
  public void setThreadState(WaveletId waveletId, String threadId, ThreadState newState) {
    WaveletThreadState threads = getWaveletThreadState(waveletId);
    ThreadState oldState = threads.getThreadState(threadId);
    threads.setThreadState(threadId, newState);
    if (newState != oldState) {
      for (Listener listener : listeners) {
        listener.onThreadStateChanged(waveletId, threadId, oldState, newState);
      }
    }
  }

  @Override
  public Iterable<String> getStatefulThreads(WaveletId waveletId) {
    return getWaveletThreadState(waveletId).getStatefulThreads();
  }

  @Override
  public Iterable<WaveletId> getWaveletsWithThreadState() {
    return waveletThreadStates.keySet();
  }

  @Override
  public Iterable<WaveletId> getWaveletsWithBlipState() {
    return waveletBlipStates.keySet();
  }

  @Override
  public void addFolder(int id) {
    if (folders.add(id)) {
      for (Listener listener : listeners) {
        listener.onFolderAdded(id);
      }
    }
  }

  @Override
  public void removeAllFolders() {
    for (Integer folder : CollectionUtils.newHashSet(folders)) {
      removeFolder(folder);
    }
  }

  @Override
  public void removeFolder(int id) {
    if (folders.remove(id)) {
      for (Listener listener : listeners) {
        listener.onFolderRemoved(id);
      }
    }
  }

  @Override
  public Set<Integer> getFolders() {
    return folders;
  }

  @Override
  public boolean isInFolder(int id) {
    return folders.contains(id);
  }

  @Override
  public void follow() {
    if (follow != Boolean.TRUE) {
      follow = true;
      for (Listener listener : listeners) {
        listener.onFollowed();
      }
    }
  }

  @Override
  public void unfollow() {
    if (follow != Boolean.FALSE) {
      follow = false;
      for (Listener listener : listeners) {
        listener.onUnfollowed();
      }
    }
  }

  @Override
  public void clearFollow() {
    if (follow != null) {
      follow = null;
      for (Listener listener : listeners) {
        listener.onFollowCleared();
      }
    }
  }

  @Override
  public Boolean getFollowed() {
    return follow;
  }

  @Override
  public long getArchiveWaveletVersion(WaveletId waveletId) {
    if (waveletArchiveVersions.containsKey(waveletId)) {
      return unboxVersion(waveletArchiveVersions.get(waveletId));
    } else {
      return NO_VERSION;
    }
  }

  @Override
  public void archiveAtVersion(WaveletId waveletId, long waveletVersion) {
    long oldVersion = unboxVersion(waveletArchiveVersions.get(waveletId));
    waveletArchiveVersions.put(waveletId, waveletVersion);
    for (Listener listener : listeners) {
      listener.onArchiveVersionChanged(waveletId, oldVersion, waveletVersion);
    }
  }

  @Override
  public void clearArchiveState() {
    waveletArchiveVersions.clear();
    for (Listener listener : listeners) {
      listener.onArchiveClearChanged(false, true);
    }
  }

  @Override
  public Iterable<WaveletId> getArchiveWavelets() {
    return waveletArchiveVersions.keySet();
  }

  @Override
  public HashedVersion getSeenVersion(WaveletId waveletId) {
    HashedVersion seenSignature = waveletSeenVersions.get(waveletId);
    if (null == seenSignature) {
      return HashedVersion.unsigned(0);
    }
    return seenSignature;
  }

  @Override
  public void setSeenVersion(WaveletId waveletId, HashedVersion signature) {
    waveletSeenVersions.put(waveletId, signature);
  }

  @Override
  public void clearSeenVersion(WaveletId waveletId) {
    waveletSeenVersions.remove(waveletId);
  }

  @Override
  public Set<WaveletId> getSeenWavelets() {
    return Collections.unmodifiableSet(waveletSeenVersions.keySet());
  }

  @Override
  public Set<WantedEvaluation> getWantedEvaluations() {
    return CollectionUtils.immutableSet(wantedEvaluations);
  }

  @Override
  public void addWantedEvaluation(WantedEvaluation evaluation) {
    wantedEvaluations.add(evaluation);
    for (Listener listener : listeners) {
      listener.onWantedEvaluationsChanged(evaluation.getWaveletId());
    }
  }

  @Override
  public boolean getPendingNotification() {
    return pendingNotification;
  }

  @Override
  public long getNotifiedVersion(WaveletId waveletId) {
    Long version = waveletNotifiedVersions.containsKey(waveletId) ?
        waveletNotifiedVersions.get(waveletId) : null;
    return unboxVersion(version);
  }

  @Override
  public Set<WaveletId> getNotifiedWavelets() {
    return Collections.unmodifiableSet(waveletNotifiedVersions.keySet());
  }

  @Override
  public void setNotifiedVersion(WaveletId waveletId, long version) {
    waveletNotifiedVersions.put(waveletId, version);
  }

  @Override
  public void clearPendingNotification() {
    pendingNotification = false;
  }

  @Override
  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  @Override
  public ReadableStringMap<String> getGadgetState(String gadgetId) {
    if (!gadgetStates.containsKey(gadgetId)) {
      gadgetStates.put(gadgetId, CollectionUtils.<String> createStringMap());
    }
    return gadgetStates.get(gadgetId);
  }

  @Override
  public void setGadgetState(String gadgetId, String key, String value) {
    Preconditions.checkNotNull(key, "Private gadget state key is null.");
    StringMap<String> gadgetState;
    if (!gadgetStates.containsKey(gadgetId)) {
      gadgetState = CollectionUtils.<String> createStringMap();
      gadgetStates.put(gadgetId, gadgetState);
    } else {
      gadgetState = gadgetStates.get(gadgetId);
    }
    if (value != null) {
      gadgetState.put(key, value);
    } else {
      gadgetState.remove(key);
    }
  }

  // Private methods

  /**
   * Gets a per-wavelet read-state tracker for a wavelet id, creating one if it
   * does not already exist
   *
   * @param id  wavelet id
   * @return the read-state tracker for a wavelet (never null).
   */
  private WaveletReadState getWaveletReadState(WaveletId id) {
    WaveletReadState s = waveletReadStates.get(id);
    if (s == null) {
      s = new WaveletReadState();
      waveletReadStates.put(id, s);
    }
    return s;
  }

  /**
   * Gets a per-wavelet look-state tracker for a wavelet id, creating one if it
   * does not already exist
   *
   * @param id  wavelet id
   * @return the look-state tracker for a wavelet (never null).
   */
  private WaveletLookState getWaveletLookState(WaveletId id) {
    WaveletLookState s = waveletLookStates.get(id);
    if (s == null) {
      s = new WaveletLookState();
      waveletLookStates.put(id, s);
    }
    return s;
  }
  
  /**
   * Gets a per-wavelet thread-state tracker for a wavelet id, creating one if it
   * does not already exist
   *
   * @param  id  wavelet id
   * @return the thread-state tracker for a wavelet (never null).
   */
  private WaveletThreadState getWaveletThreadState(WaveletId id) {
    WaveletThreadState s = waveletThreadStates.get(id);
    if (s == null) {
      s = new WaveletThreadState();
      waveletThreadStates.put(id, s);
    }
    return s;
  }

  private WaveletBlipState getWaveletBlipState(WaveletId id) {
    WaveletBlipState s = waveletBlipStates.get(id);
    if (s == null) {
      s = new WaveletBlipState();
      waveletBlipStates.put(id, s);
    }
    return s;
  }
}
