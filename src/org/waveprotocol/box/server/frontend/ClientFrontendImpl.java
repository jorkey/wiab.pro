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

package org.waveprotocol.box.server.frontend;

import org.waveprotocol.box.server.waveserver.WaveBus;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveserver.WaveletProvider.SubmitRequestCallback;
import org.waveprotocol.box.server.waveserver.ByteStringMessage;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.box.server.executor.ExecutorAnnotations;
import org.waveprotocol.box.server.waveletstate.IndexingInProcessException;
import org.waveprotocol.box.stat.Timed;
import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.common.ThrowableReceiver;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;
import org.waveprotocol.wave.model.id.*;
import org.waveprotocol.wave.model.operation.wave.*;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.supplement.Supplement;
import org.waveprotocol.wave.model.raw.RawFragment;
import org.waveprotocol.wave.clientserver.ReturnCode;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.util.logging.Log;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.SettableFuture;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implements {@link ClientFrontend}.
 *
 * When a wavelet is added and it's not at version 0, buffer updates until a
 * request for the wavelet's history has completed.
 *
 * @author unknown
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class ClientFrontendImpl implements ClientFrontend, WaveBus.Subscriber {
  private static final Log LOG = Log.get(ClientFrontendImpl.class);

  private final static AtomicInteger channelCounter = new AtomicInteger(0);

  private final WaveletProvider waveletProvider;
  private final FragmentsFetcher fragmentsFetcher;
  private final SupplementProvider supplementProvider;
  private final Executor executor;
  private final WaveletSubscriptions subscriptions;

  /**
   * Constructor.
   *
   * @param waveletProvider
   */
  @Inject
  ClientFrontendImpl(WaveletProvider waveletProvider, FragmentsFetcher fragmentsFetcher,
      SupplementProvider supplementReader, WaveBus waveBus,
      @ExecutorAnnotations.ClientFrontendExecutor Executor executor) {
    this.waveletProvider = waveletProvider;
    this.fragmentsFetcher = fragmentsFetcher;
    this.supplementProvider = supplementReader;
    this.executor = executor;
    this.subscriptions = new WaveletSubscriptions();
    waveBus.subscribe(this);
  }

  /**
   * Constructor.
   *
   * @param waveletProvider
   * @param supplementReader reader of user supplement.
   */
  @VisibleForTesting
  ClientFrontendImpl(
      WaveletProvider waveletProvider, FragmentsFetcher fragmentsFetcher,
      SupplementProvider supplementReader, WaveletSubscriptions subscriptions,
      @ExecutorAnnotations.WaveletLoadingExecutor Executor waveletLoadExecutor) {
    this.waveletProvider = waveletProvider;
    this.fragmentsFetcher = fragmentsFetcher;
    this.supplementProvider = supplementReader;
    this.executor = waveletLoadExecutor;
    this.subscriptions = subscriptions;
  }

  @Timed
  @Override
  public void fetchWaveViewRequest(final ParticipantId loggedInUser, final WaveId waveId, IdFilter waveletIdFilter,
      boolean fromLastRead, final int minBlipReplySize, final int maxBlipReplySize, final int maxBlipCount,
      String connectionId, final FetchWaveViewRequestCallback callback) {
    LOG.info("Received fetchWaveViewRequest from " + loggedInUser + " for " + waveId + ", filter "
        + waveletIdFilter);

    // Check arguments.
    if (loggedInUser == null) {
      LOG.warning("Not logged in");
      callback.onFailure(ReturnCode.NOT_LOGGED_IN, "Not logged in");
      return;
    }

    // Get wavelet Ids.
    final WaveView waveView;
    try {
      waveView = WaveView.create(waveletProvider, waveId, waveletIdFilter);
    } catch (WaveServerException ex) {
      LOG.warning("Failed to retrieve visible wavelets for " + loggedInUser, ex);
      callback.onFailure(ReturnCode.INTERNAL_ERROR, "Failed to retrieve visible wavelets");
      return;
    }
    if (waveView.isEmpty()) {
      LOG.warning("No visible wavelets for " + loggedInUser + ", filter " + waveletIdFilter.toString());
      callback.onFailure(ReturnCode.NOT_EXISTS, "No visible wavelets");
      return;
    }
    if (waveView.getRootWaveletId() == null) {
      LOG.warning("User " + loggedInUser + " is unsubscribed");
      callback.onFailure(ReturnCode.NOT_AUTHORIZED, "Not subscribed");
      return;
    }

    // Initialize result values.
    final AtomicBoolean somethingFetched = new AtomicBoolean(false);
    final AtomicLong alreadyIndexed = new AtomicLong(0);
    final AtomicLong totalToIndexing = new AtomicLong(0);
    final AtomicReference<Exception> exception = new AtomicReference<>();

    // Open supplement wavelet.
    final SettableFuture<Supplement> supplementFuture = SettableFuture.create();
    ListenableFutureTask<Void> supplementReplyFuture = null;
    if (waveView.getUserDataWaveletId() == null) {
      supplementFuture.set(null);
    } else {
      final WaveletName waveletName = WaveletName.of(waveId, waveView.getUserDataWaveletId());
      Callable<Void> task = new Callable<Void>() {

        @Override
        public Void call() throws Exception {
          Timer timer = Timing.start("Fetch " + waveletName.toString());
          try {
            FragmentsBuffer buffer = new FragmentsBuffer(waveletName);
            if (fragmentsFetcher.fetchWavelet(buffer, loggedInUser, null, -1, -1, -1)) {
              supplementFuture.set(supplementProvider.getSupplement(waveletName, loggedInUser,
                  buffer.getLastModifiedVersion(), buffer.getIntervals()));
              Map<SegmentId, RawFragment> rawFragments = buffer.getRawFragments();
              LOG.info("Replied: " + waveView.getUserDataWaveletId().toString()
                  + ", version " + buffer.getLastModifiedVersion().toString()
                  + ", segments " + rawFragments.keySet());
              callback.onWaveletSuccess(waveView.getUserDataWaveletId(),
                  buffer.getLastModifiedTime(), buffer.getLastModifiedVersion(),
                  rawFragments);
              somethingFetched.set(true);
              return null;
            }
          } catch (IndexingInProcessException ex) {
            IndexingInProcessException indexExeption = (IndexingInProcessException)ex.getCause();
            totalToIndexing.addAndGet(indexExeption.getTargetVersion());
            alreadyIndexed.addAndGet(indexExeption.getCurrentVersion());
          } catch (Exception ex) {
            exception.set(ex);
          } finally {
            Timing.stop(timer);
          }
          supplementFuture.set(null);
          return null;
        }
      };
      supplementReplyFuture = ListenableFutureTask.create(task);
      executor.execute(supplementReplyFuture);
    }
    // Open other wavelets.
    for (final WaveletId waveletId : waveView.getWaveletIds()) {
      final WaveletName waveletName = WaveletName.of(waveId, waveletId);
      if (!waveletId.equals(waveView.getUserDataWaveletId())) {
        FragmentsBuffer buffer = new FragmentsBuffer(waveletName);
        try {
          if (fragmentsFetcher.fetchWavelet(buffer, loggedInUser, supplementFuture,
              minBlipReplySize, maxBlipReplySize, maxBlipCount)) {
            Map<SegmentId, RawFragment> rawFragments = buffer.getRawFragments();
            LOG.info("Replied: " + waveletId.toString()
                + ", version " + buffer.getLastModifiedVersion().toString()
                + ", segments " + rawFragments.keySet());
            callback.onWaveletSuccess(waveletId, buffer.getLastModifiedTime(), buffer.getLastModifiedVersion(),
                rawFragments);
            somethingFetched.set(true);
          }
        } catch (IndexingInProcessException ex) {
          totalToIndexing.addAndGet(ex.getTargetVersion());
          alreadyIndexed.addAndGet(ex.getCurrentVersion());
        } catch (WaveServerException ex) {
          exception.set(ex);
          break;
        }
      }
    }
    // Waiting while supplement wavelet not yet be written to reply.
    if (supplementReplyFuture != null) {
      try {
        supplementReplyFuture.get();
      } catch (InterruptedException | ExecutionException ex) {
      }
    }
    // Send reply.
    if (totalToIndexing.get() != 0) {
      LOG.warning("Indexing in process for wave " + waveId + ", total " + totalToIndexing + ", indexed " + alreadyIndexed);
      callback.onFailure(ReturnCode.INDEXING_IN_PROCESS, totalToIndexing + " " + alreadyIndexed);
    } else if (exception.get() != null) {
      LOG.severe("Failed to retrieve wave " + waveId, exception.get());
      callback.onFailure(ReturnCode.INTERNAL_ERROR, "Wave server failure retrieving wave " + waveId);
    } else if (!somethingFetched.get()) {
      LOG.warning("No visible wavelets for " + loggedInUser + ", filter " + waveletIdFilter.toString());
      callback.onFailure(ReturnCode.NOT_EXISTS, "No visible wavelets");
    } else {
      callback.onFinish();
    }
  }

  @Timed
  @Override
  public void fetchFragmentsRequest(ParticipantId loggedInUser, WaveletName waveletName, Map<SegmentId, VersionRange> ranges,
      int minReplySize, int maxReplySize, String connectionId, FetchFragmentsRequestCallback callback) {
    LOG.info("Received fetchFragmentsRequest from " + loggedInUser + " for " + waveletName
      + " segment Ids: " + ranges.keySet());

    // Check arguments.
    if (loggedInUser == null) {
      LOG.warning("User " + loggedInUser + " is not logged in");
      callback.onFailure(ReturnCode.NOT_LOGGED_IN, "Not logged in");
      return;
    }
    if (ranges.isEmpty()) {
      callback.onFailure(ReturnCode.BAD_REQUEST, "No version ranges");
      return;
    }

    // Get fragments.
    try {
      // First request gets first fragment.
      FragmentsBuffer buffer = new FragmentsBuffer(waveletName);
      fragmentsFetcher.fetchOptionalFragments(buffer, ranges, minReplySize, maxReplySize);
      if (buffer.getRawFragments().keySet().containsAll(ranges.keySet())) {
        LOG.info("Replied all fragments");
      } else {
        LOG.info("Replied: " + buffer.getRawFragments().keySet());
      }
      callback.onSuccess(buffer.getRawFragments());
    } catch (IndexingInProcessException ex) {
      LOG.warning("Indexing in process for wavelet " + waveletName + ", total " + ex.getTargetVersion() + ", indexed " + ex.getCurrentVersion());
      callback.onFailure(ReturnCode.INDEXING_IN_PROCESS, ex.getTargetVersion() + " " + ex.getCurrentVersion());
    } catch (Exception ex) {
      LOG.severe("Failed to retrieve fragments of wavelet " + waveletName, ex);
      callback.onFailure(ReturnCode.INTERNAL_ERROR, "Wave server failure retrieving fragments of wavelet "
          + waveletName);
    }
  }

  @Timed
  @Override
  public void openRequest(final ParticipantId loggedInUser, final WaveletName waveletName,
      final List<SegmentId> segmentIds, List<HashedVersion> knownVersions,
      final ProtocolWaveletDelta unacknowledgedDelta, String connectionId,
      final OpenChannelRequestCallback openCallback, UpdateChannelListener updateListener) {
    LOG.info("received openRequest from " + loggedInUser + " for " + waveletName +
        ", known versions " + knownVersions);

    // Check arguments.
    if (loggedInUser == null) {
      LOG.warning("User " + loggedInUser + " is not logged in");
      openCallback.onFailure(ReturnCode.NOT_LOGGED_IN, "Not logged in");
      return;
    }

    // Generate channel Id.
    final String channelId = generateChannelID();
    final WaveletSubscription subscription = subscriptions.subscribe(
      waveletName, loggedInUser, channelId, connectionId, updateListener);
    LOG.info("Subscribed " + loggedInUser + " to " + waveletName + " channel " + channelId
        + " connection " + connectionId);

    // Open channel.
    waveletProvider.openRequest(waveletName, knownVersions, loggedInUser, new WaveletProvider.OpenRequestCallback() {

      @Override
      public void onSuccess(HashedVersion connectVersion, HashedVersion lastCommittedVersion) {
        try {
          HashedVersion lastModifiedVersion;
          long lastModifyTime = 0;
          Pair<HashedVersion, Long> versionAndTime = waveletProvider.getLastModifiedVersionAndTime(waveletName);
          lastModifiedVersion = versionAndTime.first;
          lastModifyTime = versionAndTime.second;
          if (lastModifiedVersion.getVersion() < connectVersion.getVersion()) {
            LOG.warning("Connect version is large than current version");
            openCallback.onFailure(ReturnCode.BAD_REQUEST, "Connect version is large than current version");
            return;
          }
          FragmentsBuffer buffer = new FragmentsBuffer(waveletName);
          buffer.setSerializeSnapshots(false);
          HashedVersion unacknowledgedDeltaVersion = null;
          if (lastModifiedVersion.getVersion() > connectVersion.getVersion()) {
            FragmentsRequest.Builder requestBuilder = new FragmentsRequest.Builder();
            if (!segmentIds.isEmpty()) {
              requestBuilder.addRanges(segmentIds, connectVersion.getVersion(), lastModifiedVersion.getVersion());
            } else {
              requestBuilder.setStartVersion(connectVersion.getVersion());
              requestBuilder.setEndVersion(lastModifiedVersion.getVersion());
            }
            requestBuilder.build();
            fragmentsFetcher.fetchFragmentsRequest(buffer, requestBuilder.build());
            if (unacknowledgedDelta != null) {
              unacknowledgedDeltaVersion =
                getUnacknowledgedDeltaVersion(waveletName, connectVersion, lastCommittedVersion, unacknowledgedDelta);
            }
          }
          openCallback.onSuccess(channelId, buffer.getRawFragments(), connectVersion, lastModifiedVersion, lastModifyTime,
            lastCommittedVersion, unacknowledgedDeltaVersion);
        } catch (IndexingInProcessException ex) {
          LOG.warning("Indexing in process for wavelet " + waveletName + ", total " + ex.getTargetVersion() + ", indexed " + ex.getCurrentVersion());
          openCallback.onFailure(ReturnCode.INDEXING_IN_PROCESS, ex.getTargetVersion() + " " + ex.getCurrentVersion());
        } catch (Exception ex) {
          LOG.severe("Failed to retrive wavelet " + waveletName, ex);
          subscriptions.unsubscribe(subscription);
          openCallback.onFailure(ReturnCode.INTERNAL_ERROR, "Wave server failure retrieving wavelet "
              + waveletName);
        }
      }

      @Override
      public void onFailure(ReturnCode responseCode, String errorMessage) {
        if (responseCode != ReturnCode.INDEXING_IN_PROCESS) {
          LOG.severe("Failure during wavelet opening: " + responseCode.toString() + ": " + errorMessage);
        }
        subscriptions.unsubscribe(subscription);
        openCallback.onFailure(responseCode, errorMessage);
      }
    });
  }

  @Timed
  @Override
  public void closeRequest(ParticipantId loggedInUser, String channelId) {
    LOG.info("received closeRequest from " + loggedInUser + " channelId " + channelId);

    if (loggedInUser == null) {
      return;
    }

    WaveletSubscription subscription = subscriptions.getSubscriptionByChannelId(channelId);
    if (subscription != null) {
      subscriptions.unsubscribe(subscription);
    }
  }

  @Timed
  @Override
  public void submitRequest(ParticipantId loggedInUser, final String channelId,
      final ProtocolWaveletDelta delta, final SubmitRequestCallback callback) {
    final ParticipantId author = new ParticipantId(delta.getAuthor());

    if (!author.equals(loggedInUser)) {
      LOG.warning("User " + loggedInUser + " is not logged in");
      callback.onFailure(ReturnCode.NOT_LOGGED_IN, "Author field on delta must match logged in user");
      return;
    }

    final WaveletSubscription subscription = subscriptions.getSubscriptionByChannelId(channelId);
    if (subscription != null && subscription.isUnsubscribeStarted()) {
      subscription.submitRequest();

      waveletProvider.submitRequest(subscription.getWaveletName(), delta, new SubmitRequestCallback() {
        @Override
        public void onSuccess(int operationsApplied,
            HashedVersion hashedVersionAfterApplication, long applicationTimestamp) {
          subscription.submitResponse(hashedVersionAfterApplication);
          callback.onSuccess(operationsApplied, hashedVersionAfterApplication,
              applicationTimestamp);
        }

        @Override
        public void onFailure(ReturnCode responseCode, String errorMessage) {
          subscription.submitError();
          LOG.severe("Failure during submit: " + responseCode.toString() + ": " + errorMessage);
          callback.onFailure(responseCode, errorMessage);
        }
      });
    }
  }

  @Timed
  @Override
  public void disconnect(ParticipantId loggedInUser, String connectionId) {
    if (loggedInUser != null) {
      LOG.info("User " + loggedInUser.toString() + " is disconnected");
      for (WaveletSubscription subscription : subscriptions.getSubscriptionsByConnectionId(connectionId)) {
        subscriptions.unsubscribe(subscription);
      }
    }
  }

  @Timed
  @Override
  public void waveletCommitted(WaveletName waveletName, HashedVersion version) {
    for (WaveletSubscription subscription : subscriptions.getSubscriptions(waveletName)) {
      subscription.onCommit(version);
      if (!subscription.isUnsubscribeStarted() && subscription.unsubscribe()) {
        subscriptions.unsubscribe(subscription);
      }
    }
  }

  @Timed
  @Override
  public void waveletUpdate(WaveletName waveletName, DeltaSequence deltas) {
    if (deltas.isEmpty()) {
      return;
    }

    Set<ParticipantId> participants = Sets.newHashSet();
    for (WaveletSubscription subscription : subscriptions.getSubscriptions(waveletName)) {
      participants.add(subscription.getParticipantId());
    }

    Multimap<ParticipantId, TransformedWaveletDelta> deltasForSubscriptions = LinkedListMultimap.create();
    for (TransformedWaveletDelta delta : deltas) {
      for (WaveletOperation op : delta) {
        if (op instanceof AddParticipant) {
          ParticipantId p = ((AddParticipant) op).getParticipantId();
          participants.add(p);
        }
        else if (op instanceof RemoveParticipant) {
          ParticipantId p = ((RemoveParticipant) op).getParticipantId();
          for (WaveletSubscription subscription : subscriptions.getSubscriptions(waveletName, p)) {
            subscription.onUpdate(DeltaSequence.of(deltasForSubscriptions.get(p)));
            if (subscription.unsubscribe()) {
              subscriptions.unsubscribe(subscription);
            }
          }
          deltasForSubscriptions.removeAll(p);
          participants.remove(p);
        }
      }
      for (ParticipantId p : participants) {
        deltasForSubscriptions.put(p, delta);
      }
    }

    for (Entry<ParticipantId, TransformedWaveletDelta> e : deltasForSubscriptions.entries()) {
      for (WaveletSubscription subscription : subscriptions.getSubscriptions(waveletName, e.getKey())) {
        subscription.onUpdate(DeltaSequence.of(e.getValue()));
      }
    }
  }

  @Timed
  HashedVersion getUnacknowledgedDeltaVersion(WaveletName waveletName,
      HashedVersion startVersion, HashedVersion endVersion,
      final ProtocolWaveletDelta unacknowledgedDelta) throws WaveServerException {
    final AtomicReference<HashedVersion> unacknowledgedDeltaVersion = new AtomicReference<>();
    final ByteString unacknowledgedDeltaBytes = ByteStringMessage.serializeMessage(unacknowledgedDelta).getByteString();
    waveletProvider.getDeltaHistory(waveletName, startVersion, endVersion,
        new ThrowableReceiver<WaveletDeltaRecord, WaveServerException>() {

          @Override
          public boolean put(WaveletDeltaRecord delta) {
            if (delta.getAppliedDelta() != null
                && unacknowledgedDeltaBytes.equals(
                delta.getAppliedDelta().getMessage().getSignedOriginalDelta().getDelta())) {
              unacknowledgedDeltaVersion.set(delta.getResultingVersion());
              return false;
            }
            return true;
          }
        });
    return unacknowledgedDeltaVersion.get();
  }

  private String generateChannelID() {
    return "ch" + channelCounter.addAndGet(1);
  }
}
