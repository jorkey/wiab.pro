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

package org.waveprotocol.box.server.waveserver;

import org.waveprotocol.box.common.ThrowableReceiver;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.box.server.waveletstate.delta.DeltaWaveletState;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletState;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.box.server.waveletstate.IndexingState;
import org.waveprotocol.box.server.waveletstate.IndexingInProcessException;
import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.waveserver.DeltaWaveletStateMap.DeltaWaveletStateAccessor;

import org.waveprotocol.wave.concurrencycontrol.server.ConcurrencyControlCore;
import org.waveprotocol.wave.concurrencycontrol.server.WaveletDeltaHistory;
import org.waveprotocol.wave.federation.Proto.ProtocolAppliedWaveletDelta;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.TransformException;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.util.logging.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import org.waveprotocol.wave.concurrencycontrol.server.DeltaHistory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contains the history of a wavelet - applied and transformed deltas plus the
 * content of the wavelet.
 *
 * TODO(soren): Unload the wavelet (remove it from WaveMap) if it becomes
 * corrupt or fails to load from storage.
 */
public abstract class WaveletContainerImpl implements WaveletContainer {

  private static final Log LOG = Log.get(WaveletContainerImpl.class);

  private static final int AWAIT_LOAD_TIMEOUT_MS = 50000;

  protected enum State {
    /** Everything is working fine. */
    OK,

    /** Wavelet state is being loaded from storage. */
    LOADING,

    /** Wavelet state is being indexing. */
    INDEXING,

    /** Wavelet state is being closing. */
    CLOSING,

    /** Wavelet has been closed. */
    CLOSED,

    /** Wavelet has been deleted, the instance will not contain any data. */
    DELETED,

    /**
     * For some reason this instance is broken, e.g. a remote wavelet update
     * signature failed.
     */
    CORRUPTED
  }

  private final WaveletName waveletName;
  private final WaveletNotificationSubscriber notifiee;
  private final ParticipantId sharedDomainParticipantId;

  /** Is set at most once, before loadLatch is counted down. */
  private final DeltaWaveletStateAccessor deltaStateAccessor;
  private SegmentWaveletState segmentWaveletState;

  /** Is counted down when initial opening of storage is completed. */
  private final CountDownLatch loadLatch = new CountDownLatch(1);

  protected volatile State state = State.LOADING;

  private volatile IndexingState indexingState;

  private final Executor storageIndexingExecutor;
  private final Executor storageContinuationExecutor;

  /**
   * Constructs an empty WaveletContainer for a wavelet.
   * WaveletData is not set until a delta has been applied.
   *
   * @param waveletName the name of wavelet.
   * @param waveDomain the wave server domain.
   * @param notifiee the subscriber to notify of wavelet updates and commits.
   * @param deltaStateAccessor  the wavelet's delta history and current state.
   * @param segmentStateFuture the wavelet's segments.
   * @param storageIndexingExecutor the executor used to perform indexing.
   * @param storageContinuationExecutor the executor used to perform post wavelet loading logic.
   */
  public WaveletContainerImpl(WaveletName waveletName, String waveDomain, WaveletNotificationSubscriber notifiee,
      final DeltaWaveletStateAccessor deltaStateAccessor, final ListenableFuture<? extends SegmentWaveletState> segmentStateFuture,
      final Executor storageIndexingExecutor, final Executor storageContinuationExecutor) {
    this.waveletName = waveletName;
    this.notifiee = notifiee;
    this.sharedDomainParticipantId =
        waveDomain != null ? ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(waveDomain)
            : null;
    this.deltaStateAccessor = deltaStateAccessor;
    this.storageIndexingExecutor = storageIndexingExecutor;
    this.storageContinuationExecutor = storageContinuationExecutor;

    segmentStateFuture.addListener(
      new Runnable() {
        @Override
        public void run() {
          synchronized (WaveletContainerImpl.this) {
            try {
              Preconditions.checkState(segmentWaveletState == null,
                "Repeat attempts to set snapshot wavelet state");
              Preconditions.checkState(state == State.LOADING, "Unexpected state %s", state);
              segmentWaveletState = FutureUtil.getResultOrPropagateException(
                segmentStateFuture, PersistenceException.class);
              Preconditions.checkState(segmentWaveletState.getWaveletName().equals(getWaveletName()),
                "Wrong wavelet state, named %s, expected %s",
                segmentWaveletState.getWaveletName(), getWaveletName());
              if (!segmentWaveletState.isConsistent()) {
                state = State.INDEXING;
                indexingState = segmentWaveletState.startSynchronization(deltaStateAccessor.get(), storageIndexingExecutor);
                indexingState.getFuture().addListener(new Runnable() {

                  @Override
                  public void run() {
                    try {
                      indexingState.getFuture().get();
                      state = State.OK;
                    } catch (InterruptedException | ExecutionException ex) {
                      LOG.warning("Indexing exception " + getWaveletName(), ex);
                      state = State.CORRUPTED;
                    }
                  }
                }, MoreExecutors.sameThreadExecutor());
              } else {
                state = State.OK;
              }
            } catch (WaveletStateException | PersistenceException ex) {
              LOG.warning("Failed to load wavelet " + getWaveletName(), ex);
              state = State.CORRUPTED;
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
              LOG.warning("Interrupted loading wavelet " + getWaveletName(), ex);
              state = State.CORRUPTED;
            } catch (RuntimeException ex) {
              // TODO(soren): would be better to terminate the process in this case
              LOG.severe("Unexpected exception loading wavelet " + getWaveletName(), ex);
              state = State.CORRUPTED;
            }
            loadLatch.countDown();
          }
        }
      }, MoreExecutors.sameThreadExecutor());
  }

  /**
   * Blocks until the initial load of the wavelet state from storage completes.
   * Should be called without the read or write lock held.
   *
   * @throws WaveletStateException if the wavelet fails to load,
   *         either because of a storage access failure or timeout,
   *         or because the current thread is interrupted.
   */
  @Override
  public void awaitLoad() throws WaveletStateException {
    try {
      if (!loadLatch.await(AWAIT_LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        throw new WaveletStateException("Timed out waiting for wavelet to load");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new WaveletStateException("Interrupted waiting for wavelet to load");
    }
  }

  @Override
  public ListenableFuture close() {
    state = State.CLOSING;
    final SettableFuture<Void> future = SettableFuture.create();
    final ListenableFuture deltaCloseFuture = deltaStateAccessor.close();
    deltaCloseFuture.addListener(new Runnable() {

      @Override
      public void run() {
        boolean consistent = true;
        try {
          deltaCloseFuture.get();
        } catch (InterruptedException | ExecutionException ex) {
          LOG.severe("Exception of closing delta store", ex);
          consistent = false;
        }
        if (segmentWaveletState != null) {
          if (consistent) {
            segmentWaveletState.markAsConsistent();
          }
          segmentWaveletState.close().addListener(new Runnable() {

            @Override
            public void run() {
              state = State.CLOSED;
              future.set(null);
            }
          }, MoreExecutors.sameThreadExecutor());
        } else {
          state = State.CLOSED;
          future.set(null);
        }
      }
    }, storageContinuationExecutor);
    return future;
  }

  @Override
  public boolean isCorrupted() {
    return state == State.CORRUPTED;
  }

  @Override
  public WaveletName getWaveletName() {
    return waveletName;
  }

  @Override
  public boolean checkAccessPermission(ParticipantId participantId) throws WaveletStateException {
    awaitLoad();
    checkStateOk();
    // ParticipantId will be null if the user isn't logged in. A user who isn't logged in should
    // have access to public waves once they've been implemented.
    // If the wavelet is empty, everyone has access (to write the first delta).
    // TODO(soren): determine if off-domain participants should be denied access if empty
    return WaveletDataUtil.checkAccessPermission(segmentWaveletState.getParticipants(),
        participantId, sharedDomainParticipantId);
  }

  @Override
  public ReadableWaveletData getSnapshot() throws WaveletStateException {
    awaitLoad();
    checkStateOk();
    return segmentWaveletState.getSnapshot();
  }

  @Override
  public ReadableWaveletData getSnapshot(HashedVersion version) throws WaveletStateException {
    awaitLoad();
    checkStateOk();
    return segmentWaveletState.getSnapshot(version);
  }

  @Override
  public Set<SegmentId> getSegmentIds(long version) throws WaveletStateException {
    awaitLoad();
    checkStateOk();
    return segmentWaveletState.getSegmentIds(version);
  }

  @Override
  public void getIntervals(Map<SegmentId, VersionRange> ranges, boolean onlyFromCache,
      Receiver<Pair<SegmentId, Interval>> receiver) throws WaveletStateException {
    awaitLoad();
    checkStateOk();
    segmentWaveletState.getIntervals(ranges, onlyFromCache, receiver);
  }

  @Override
  public boolean hasParticipant(ParticipantId participant) throws WaveletStateException {
    awaitLoad();
    checkStateOk();
    return segmentWaveletState.getParticipants(
      segmentWaveletState.getLastModifiedVersion().getVersion()).contains(participant);
  }

  @Override
  public ParticipantId getSharedDomainParticipant() {
    return sharedDomainParticipantId;
  }

  @Override
  public ParticipantId getCreator() throws WaveletStateException {
    awaitLoad();
    checkStateOk();
    return segmentWaveletState.getCreator();
  }

  @Override
  public long getCreationTime() throws WaveletStateException {
    awaitLoad();
    checkStateOk();
    return segmentWaveletState.getCreationTime();
  }

  @Override
  public Set<ParticipantId> getParticipants() throws WaveletStateException {
    awaitLoad();
    checkStateOk();
    return segmentWaveletState.getParticipants();
  }

  @Override
  public Set<ParticipantId> getParticipants(long version) throws WaveletStateException {
    awaitLoad();
    checkStateOk();
    return segmentWaveletState.getParticipants(version);
  }

  @Override
  public boolean isEmpty() throws WaveletStateException {
    awaitLoad();
    checkStateOk();
    return segmentWaveletState.getSnapshot() == null;
  }

  @Override
  public HashedVersion getLastModifiedVersion() throws WaveletStateException {
    awaitLoad();
    checkStateOk();
    return segmentWaveletState.getLastModifiedVersion();
  }

  @Override
  public Pair<HashedVersion, Long> getLastModifiedVersionAndTime() throws WaveletStateException {
    awaitLoad();
    checkStateOk();
    return segmentWaveletState.getLastModifiedVersionAndTime();
  }

  @Override
  public HashedVersion getConnectVersion(List<HashedVersion> knownVersions) throws WaveletStateException {
    awaitLoad();
    checkStateOk();
    HashedVersion lastModifiedVersion = getLastModifiedVersion();
    if (knownVersions.size() == 1 && knownVersions.get(0).equals(lastModifiedVersion)) {
      return lastModifiedVersion;
    } else {
      DeltaHistory history = new WaveletDeltaHistory(deltaStateAccessor.get());
      return ConcurrencyControlCore.getConnectVersion(knownVersions, history);
    }
  }

  @Override
  public HashedVersion getLastCommittedVersion() throws WaveletStateException {
    awaitLoad();
    checkStateOk();
    if (deltaStateAccessor.getIfPresent() == null) {
      return getLastModifiedVersion();
    } else {
      return deltaStateAccessor.get().getLastPersistedVersion();
    }
  }

  @Override
  public void requestDeltaHistory(HashedVersion versionStart, HashedVersion versionEnd,
      ThrowableReceiver<WaveletDeltaRecord, WaveletStateException> receiver) throws AccessControlException,
      WaveletStateException {
    awaitLoad();
    checkStateOk();
    checkVersionIsDeltaBoundary(versionStart, "start version");
    checkVersionIsDeltaBoundary(versionEnd, "end version");
    deltaStateAccessor.get().getDeltaHistory(versionStart, versionEnd, receiver);
  }

  @Override
  public HashedVersion getNearestHashedVersion(long version) throws WaveServerException {
    awaitLoad();
    checkStateOk();
    return deltaStateAccessor.get().getNearestHashedVersion(version);
  }

  @Override
  public void remakeIndex() throws WaveletStateException {
    awaitLoad();
    if (state != State.INDEXING) {
      state = State.INDEXING;
      indexingState = segmentWaveletState.startRemaking(deltaStateAccessor.get(), storageIndexingExecutor);
    }
    try {
      indexingState.getFuture().get();
    } catch (ExecutionException ex) {
      state = State.CORRUPTED;
      throw new WaveletStateException(ex);
    } catch (InterruptedException ex) {
      state = State.CORRUPTED;
      Thread.currentThread().interrupt();
      throw new WaveletStateException("Interrupted waiting for wavelet to be re-indexed");
    }
    state = State.OK;
  }

  protected void notifyOfDeltas(ImmutableList<WaveletDeltaRecord> deltas,
      ImmutableSet<String> domainsToNotify) throws WaveletStateException {
    Preconditions.checkArgument(!deltas.isEmpty(), "empty deltas");
    HashedVersion endVersion = deltas.get(deltas.size() - 1).getResultingVersion();
    HashedVersion currentVersion = getLastModifiedVersion();
    Preconditions.checkArgument(endVersion.equals(currentVersion),
        "cannot notify of deltas ending in %s != current version %s", endVersion, currentVersion);
    notifiee.waveletUpdate(waveletName, deltas, domainsToNotify);
  }

  protected void notifyOfCommit(HashedVersion version, ImmutableSet<String> domainsToNotify) {
    notifiee.waveletCommitted(getWaveletName(), version, domainsToNotify);
  }

  /**
   * Verifies that the wavelet is in an operational state (not loading,
   * not corrupt).
   *
   * Should be preceded by a call to awaitLoad() so that the initial load from
   * storage has completed. Should be called with the read or write lock held.
   *
   * @throws WaveletStateException if the wavelet is loading or marked corrupt.
   */
  protected void checkStateOk() throws WaveletStateException {
    if (state == State.INDEXING) {
      throw new IndexingInProcessException("Indexing is in process",
        indexingState.getTargetVersion(), indexingState.getCurrentVersion());
    }
    if (state != State.OK) {
      throw new WaveletStateException("The wavelet is in an unusable state: " + state);
    }
  }

  protected void persistDelta(final HashedVersion version, final ImmutableSet<String> domainsToNotify) throws WaveletStateException {
    final ListenableFuture<Void> result = deltaStateAccessor.get().persist(version);
    result.addListener(
        new Runnable() {
          @Override
          public void run() {
            try {
              result.get();
            } catch (InterruptedException e) {
              state = State.CORRUPTED;
              Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
              state = State.CORRUPTED;
              LOG.severe("Version " + version, e);
              return;
            }
            try {
              deltaStateAccessor.get().flush(version);
            } catch (WaveletStateException e) {
              state = State.CORRUPTED;
              LOG.severe("Version " + version, e);
              return;
            }
            notifyOfCommit(version, domainsToNotify);
          }
        },
        storageContinuationExecutor);
  }

  protected ReadableWaveletData accessSnapshot() throws WaveletStateException {
    return segmentWaveletState.getSnapshot();
  }

  /**
   * Transform a wavelet delta if it has been submitted against a different head (currentVersion).
   * Must be called with write lock held.
   *
   * @param delta to possibly transform
   * @return the transformed delta and the version it was applied at
   *   (the version is the current version of the wavelet, unless the delta is
   *   a duplicate in which case it is the version at which it was originally
   *   applied)
   * @throws InvalidHashException if submitting against same version but different hash
   * @throws OperationException if transformation fails
   */
  protected WaveletDelta maybeTransformSubmittedDelta(WaveletDelta delta)
      throws InvalidHashException, OperationException, TransformException, WaveletStateException {
    HashedVersion targetVersion = delta.getTargetVersion();
    HashedVersion currentVersion = getLastModifiedVersion();
    if (targetVersion.equals(currentVersion)) {
      // Applied version is the same, we're submitting against head, don't need to do OT
      return delta;
    } else {
      // Not submitting against head, we need to do OT, but check the versions really are different
      if (targetVersion.getVersion() == currentVersion.getVersion()) {
        LOG.warning("Mismatched hash, expected " + currentVersion + ") but delta targets (" +
            targetVersion + ")");
        throw new InvalidHashException(currentVersion, targetVersion);
      } else {
        DeltaHistory history = new WaveletDeltaHistory(deltaStateAccessor.get());
        WaveletDelta transformedDelta = ConcurrencyControlCore.onClientDelta(delta, history);
        LOG.info("OT transformed " +
            delta.getTargetVersion().getVersion() + "-" + delta.getResultingVersion() + " -> " +
            transformedDelta.getTargetVersion().getVersion() + "-" + transformedDelta.getResultingVersion());
        return transformedDelta;
      }
    }
  }

  /**
   * Builds a {@link WaveletDeltaRecord} and applies it to the wavelet container.
   * The delta must be non-empty.
   */
  protected WaveletDeltaRecord applyDelta(
      ByteStringMessage<ProtocolAppliedWaveletDelta> appliedDelta, WaveletDelta transformed)
      throws InvalidProtocolBufferException, WaveletStateException {
    TransformedWaveletDelta transformedDelta =
        AppliedDeltaUtil.buildTransformedDelta(appliedDelta, transformed);

    WaveletDeltaRecord deltaRecord = new WaveletDeltaRecord(transformed.getTargetVersion(),
        appliedDelta, transformedDelta);
    segmentWaveletState.appendDelta(deltaRecord);
    deltaStateAccessor.get().appendDelta(deltaRecord);

    return deltaRecord;
  }

  /**
   * @param versionActuallyAppliedAt the version to look up
   * @return the applied delta applied at the specified hashed version
   */
  protected ByteStringMessage<ProtocolAppliedWaveletDelta> lookupAppliedDelta(
      HashedVersion versionActuallyAppliedAt) throws WaveletStateException {
    return deltaStateAccessor.get().getAppliedDelta(versionActuallyAppliedAt);
  }

  /**
   * @param endVersion the version to look up
   * @return the applied delta with the given resulting version
   */
  protected ByteStringMessage<ProtocolAppliedWaveletDelta> lookupAppliedDeltaByEndVersion(
      HashedVersion endVersion) throws WaveletStateException {
    return deltaStateAccessor.get().getAppliedDeltaByEndVersion(endVersion);
  }

  protected TransformedWaveletDelta lookupTransformedDelta(HashedVersion appliedAtVersion) throws WaveletStateException {
    return deltaStateAccessor.get().getTransformedDelta(appliedAtVersion);
  }

  /**
   * @throws AccessControlException with the given message if version does not
   *         match a delta boundary in the wavelet history.
   */
  private void checkVersionIsDeltaBoundary(HashedVersion version, String message)
      throws AccessControlException, WaveletStateException {
    HashedVersion actual = deltaStateAccessor.get().getHashedVersion(version.getVersion());
    if (!version.equals(actual)) {
      LOG.info("Unrecognized " + message + " at version " + version + ", actual " + actual);
      // We omit the hash from the message to avoid leaking it.
      throw new AccessControlException(
          "Unrecognized " + message + " at version " + version.getVersion());
    }
  }
}
