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

package org.waveprotocol.wave.concurrencycontrol.channel;

import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.raw.RawFragment;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.Box;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.concurrencycontrol.channel.WaveViewService.OpenChannelStreamCallback;
import org.waveprotocol.wave.concurrencycontrol.channel.WaveViewService.FetchWaveCallback;
import org.waveprotocol.wave.concurrencycontrol.common.ChannelException;
import org.waveprotocol.wave.client.scheduler.Scheduler;
import org.waveprotocol.wave.client.scheduler.TimerService;
import org.waveprotocol.wave.clientserver.ReturnCode;
import org.waveprotocol.wave.clientserver.ReturnStatus;
import org.waveprotocol.wave.client.common.util.CountdownLatch;
import org.waveprotocol.wave.client.scheduler.BrowserBackedScheduler;
import org.waveprotocol.wave.client.scheduler.SchedulerTimerService;

import com.google.common.collect.HashBiMap;
import com.google.gwt.user.client.Command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Implementation of a view channel.
 * Repeats requests when indexing in process.
 *
 * @author unknown
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class ViewChannelImpl implements ViewChannel {

  /** Id of the wave being viewed. */
  private final WaveId waveId;

  /** Opened wavelets. */
  private final HashBiMap<WaveletId, String> channels = HashBiMap.create();

  /** Service through which RPCs are made. */
  private final WaveViewService waveService;

  /** Callback to indicate that indexing is in process. */
  private final IndexingCallback indexingCallback;
  
  /** Listener of channel */
  private Listener listener;

  /** Logger. */
  private final LoggerBundle logger;

  /** Counts the view channels for each wave. */
  private static final Map<WaveId, Integer> viewChannelsPerWave = new HashMap<>();

  /**
   * For 2 pairs of wave + playback channel. The second pair is used in
   * recovery whilest the first pair is closing.
   */
  private static final int DEFAULT_MAX_VIEW_CHANNELS_PER_WAVE = 4;

  private static int maxViewChannelsPerWave = DEFAULT_MAX_VIEW_CHANNELS_PER_WAVE;

  private static final int REPEAT_REQUEST_WHEN_INDEXING_MS = 2000;
  
  //
  // Mutable state.
  //
  private static enum State {
    /** Post-constructor state. */
    INITIAL,
    /** Wavelets being opened. */
    OPENING,
    /** Wavelets is opened. */
    OPENED,
    /** Channel is disconnected. */
    DISCONNECTED,
    /** Wavelets being closed.  */
    CLOSING,
    /** Channel is closed.  */
    CLOSED
  }

  /** State this channel is in. */
  private State state;

  /** Indexing processing. */
  private final TimerService repeatRequestScheduler = new SchedulerTimerService(new BrowserBackedScheduler());
  private boolean indexingInProcess = false;
  
  /**
   * Constructs a view channel.
   *
   * @param waveId            id of the wave for which this channel is a view
   * @param waveService       service through which RPCs are made
   * @param indexingCallback  callback to indicate that indexing is in process.
   * @param logger             logger for error messages
   */
  public ViewChannelImpl(WaveId waveId, WaveViewService waveService, 
      IndexingCallback indexingCallback, LoggerBundle logger) {
    this.waveId = waveId;
    this.waveService = waveService;
    this.indexingCallback = indexingCallback;
    this.logger = logger;
    this.state = State.INITIAL;
  }

  @Override
  public void fetchWaveView(final IdFilter waveletFilter, final boolean fromLastRead, 
      final int minBlipReplySize, final int maxBlipReplySize, final int maxBlipCount, final FetchWaveViewCallback callback) {
    Preconditions.checkState(state == State.INITIAL || state == State.OPENING || state == State.OPENED,
      "Invalid state: %s", state);
    waveService.viewFetchWave(waveletFilter, fromLastRead, minBlipReplySize, maxBlipReplySize, maxBlipCount,
      new FetchWaveCallback() {

      @Override
      public void onSuccess(WaveViewData waveView) {
        if (state == State.INITIAL || state == State.OPENING || state == State.OPENED) {
          if (indexingInProcess) {
            indexingInProcess = false;
            indexingCallback.onIndexingComplete();
          }
          callback.onWaveViewFetch(waveView);
        }
      }

      @Override
      public void onFailure(ReturnStatus status) {
        if (state == State.INITIAL || state == State.OPENING || state == State.OPENED) {
          if (status.getCode() == ReturnCode.INDEXING_IN_PROCESS) {
            indexingInProcess = true;
            String[] parts = status.getMessage().split(" ");
            indexingCallback.onIndexing(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            repeatRequestScheduler.scheduleDelayed(new Scheduler.Task() {

              @Override
              public void execute() {
                fetchWaveView(waveletFilter, fromLastRead, minBlipReplySize, maxBlipReplySize, maxBlipCount, callback);
              }
            }, REPEAT_REQUEST_WHEN_INDEXING_MS);
          } else {
            callback.onFailure(status);
          }
        }
      }
    });
  }

  @Override
  public void fetchFragments(final WaveletId waveletId, Map<SegmentId, Long> segments, 
      final long endVersion, final int minReplySize, final int maxReplySize, final FetchFragmentsCallback callback) {
    Preconditions.checkState(state == State.INITIAL || state == State.OPENING || state == State.OPENED,
      "Invalid state: %s", state);
    waveService.viewFetchFragments(waveletId, segments, endVersion, minReplySize, maxReplySize,
        new WaveViewService.FetchFragmentsCallback() {

      @Override
      public void onSuccess(Map<SegmentId, RawFragment> rawFragments) {
        if (state == State.INITIAL || state == State.OPENING || state == State.OPENED) {
          if (indexingInProcess) {
            indexingInProcess = false;
            indexingCallback.onIndexingComplete();
          }
          callback.onWaveFragmentsFetch(rawFragments);
        }
      }

      @Override
      public void onFailure(ReturnStatus status) {
        if (state == State.INITIAL || state == State.OPENING || state == State.OPENED) {
          if (status.getCode() == ReturnCode.INDEXING_IN_PROCESS) {
            indexingInProcess = true;
            String[] parts = status.getMessage().split(" ");
            indexingCallback.onIndexing(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            repeatRequestScheduler.scheduleDelayed(new Scheduler.Task() {

              @Override
              public void execute() {
                fetchFragments(waveletId, null, endVersion, minReplySize, maxReplySize, callback);
              }
            }, REPEAT_REQUEST_WHEN_INDEXING_MS);
          } else {
            callback.onFailure(status);
          }
        }
      }
    });
  }

  @Override
  public void open(final Map<WaveletId, List<HashedVersion>> knownWavelets,
      final Map<WaveletId, Set<SegmentId>> segmentIds, final Map<WaveletId, WaveletDelta> unacknowledgedDeltas,
      final Listener listener) {
    Preconditions.checkState(state == State.INITIAL || state == State.DISCONNECTED || state == State.OPENING,
      "Invalid state: %s", state);
    this.listener = listener;
    state = State.OPENING;
    logger.trace().log("New view channel initialized");
    final Box<ReturnStatus> viewOpenError = new Box();
    final Box<Long> totalIndexing = new Box(0L);
    final Box<Long> completeIndexing = new Box(0L);
    final Set<WaveletId> openedWavelets = CollectionUtils.newHashSet();
    final CountdownLatch viewOpenLatch = CountdownLatch.create(knownWavelets.size(), new Command() {

      @Override
      public void execute() {
        if (state == State.OPENING || state == State.OPENED) {
          if (totalIndexing.get() != 0L) {
            indexingCallback.onIndexing(totalIndexing.get(), completeIndexing.get());
            repeatRequestScheduler.scheduleDelayed(new Scheduler.Task() {

              @Override
              public void execute() {
                Map<WaveletId, List<HashedVersion>> wavelets = CollectionUtils.newHashMap(knownWavelets);
                for (WaveletId waveletId : openedWavelets) {
                  wavelets.remove(waveletId);
                }
                open(wavelets, segmentIds, unacknowledgedDeltas, listener);
              }
            }, REPEAT_REQUEST_WHEN_INDEXING_MS);
          } else if (viewOpenError.get() != null) {
            listener.onFailure(viewOpenError.get());
          } else {
            if (indexingInProcess) {
              indexingInProcess = false;
              indexingCallback.onIndexingComplete();
            }
            state = State.OPENED;
            registerChannel();
            try {
              listener.onConnected();
            } catch (ChannelException ex) {
              handleException("onConnected", ex);
            }
          }
        }
      }
    });
    for (Entry<WaveletId, List<HashedVersion>> waveletOpenData : knownWavelets.entrySet()) {
      final WaveletId waveletId = waveletOpenData.getKey();
      final List<TransformedWaveletDelta> preOpenUpdates = new ArrayList<>();
      final HashedVersion preOpenCommittedVersion[] = { HashedVersion.unsigned(0) };
      List<HashedVersion> knownVersions = waveletOpenData.getValue();
      final WaveletDelta unacknowledgedDelta = (unacknowledgedDeltas != null)? unacknowledgedDeltas.get(waveletId) : null;
      waveService.viewOpenWaveletChannel(waveletId, segmentIds.get(waveletId),
          knownVersions, unacknowledgedDelta, new OpenChannelStreamCallback() {

        @Override
        public void onWaveletOpen(String channelId, HashedVersion connectVersion, HashedVersion lastModifiedVersion,
            long lastModifiedTime, HashedVersion lastCommittedVersion, HashedVersion unacknowledgedDeltaVersion,
            Map<SegmentId, RawFragment> rawFragments) {
          if (state == State.OPENING || state == State.OPENED) {
            Preconditions.checkArgument(!openedWavelets.contains(waveletId), "Is already opened");
            channels.put(waveletId, channelId);
            if (preOpenCommittedVersion[0].compareTo(lastCommittedVersion) > 0) {
              lastCommittedVersion = preOpenCommittedVersion[0];
            }
            try {
              listener.onWaveletOpened(waveletId, connectVersion, lastModifiedVersion, lastModifiedTime,
                  lastCommittedVersion, unacknowledgedDeltaVersion, rawFragments);
            } catch (ChannelException ex) {
              handleException("onWaveletOpen", ex, waveletId);
              return;
            }
            try {
              // Skip deltas received prior to opening of the wavelet.
              while (!preOpenUpdates.isEmpty() &&
                  preOpenUpdates.get(0).getAppliedAtVersion() < lastModifiedVersion.getVersion()) {
                preOpenUpdates.remove(0);
              }
              if (!preOpenUpdates.isEmpty()) {
                listener.onUpdate(waveletId, preOpenUpdates, lastCommittedVersion);
              }
            } catch (ChannelException e) {
              handleException("onUpdate", e, waveletId);
            }
            viewOpenLatch.tick();
            openedWavelets.add(waveletId);
          }
        }

        @Override
        public void onFailure(ReturnStatus status) {
          if (state == State.OPENING || state == State.OPENED) {
            if (!openedWavelets.contains(waveletId)) {
              if (status.getCode() == ReturnCode.INDEXING_IN_PROCESS) {
                indexingInProcess = true;
                String[] parts = status.getMessage().split(" ");
                totalIndexing.set(totalIndexing.get() + Long.parseLong(parts[0]));
                completeIndexing.set(completeIndexing.get() + Long.parseLong(parts[1]));
              } else {
                viewOpenError.set(status);
              }
              viewOpenLatch.tick();
            } else {
              listener.onFailure(status);
            }
          }
        }

        @Override
        public void onUpdate(List<TransformedWaveletDelta> deltas, HashedVersion commitedVersion) {
          if (state == State.OPENING || state == State.OPENED) {
            if (openedWavelets.contains(waveletId)) {
              try {
                listener.onUpdate(waveletId, deltas, commitedVersion);
              } catch (ChannelException e) {
                handleException("onUpdate", e, waveletId);
              }
            } else {
              if (deltas != null) {
                preOpenUpdates.addAll(deltas);
              }
              if (commitedVersion != null) {
                preOpenCommittedVersion[0] = commitedVersion;
              }
            }
          }
        }
      });
    }
  }

  @Override
  public void submitDelta(final WaveletId waveletId, final WaveletDelta delta, final SubmitCallback callback) {
    Preconditions.checkState(state == State.OPENING || state == State.OPENED, "Invalid state: %s", state);
    String channelId = channels.get(waveletId);
    Preconditions.checkState(channelId != null,
        "Cannot submit to disconnected view channel: %s, delta version %s", this,
        delta.getTargetVersion());

    waveService.viewSubmit(channelId, delta, new WaveViewService.SubmitCallback() {

      @Override
      public void onResponse(int opsApplied, HashedVersion version, long timestampAfterApplication,
          ReturnStatus status) {
        if (state == State.OPENING || state == State.OPENED) {
          if (indexingInProcess) {
            indexingInProcess = false;
            indexingCallback.onIndexingComplete();
          }
          try {
            callback.onResponse(opsApplied, version, timestampAfterApplication, status);
          } catch (ChannelException e) {
            handleException("onResponse", e, waveletId);
          }
        }
      }

      @Override
      public void onFailure(ReturnStatus status) {
        if (state == State.OPENING || state == State.OPENED) {
          if (status.getCode() == ReturnCode.INDEXING_IN_PROCESS) {
            indexingInProcess = true;
            String[] parts = status.getMessage().split(" ");
            indexingCallback.onIndexing(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            repeatRequestScheduler.scheduleDelayed(new Scheduler.Task() {

              @Override
              public void execute() {
                submitDelta(waveletId, delta, callback);
              }
            }, REPEAT_REQUEST_WHEN_INDEXING_MS);
          } else {
            try {
              callback.onFailure(status);
            } catch (ChannelException e) {
              handleException("onFailure", e, waveletId);
            }
          }
        }
      }
    });
  }

  @Override
  public void disconnect() {
    logger.trace().log(this.toString() + " disconnected");
    handleDisconnect();
  }

  @Override
  public void close() {
    logger.trace().log(this.toString() + " closed");
    handleClose();
  }

  @Override
  public String toString() {
    return "[ViewChannel waveId: " + waveId + "\n state: " + state + "]";
  }

  /**
   * Set the number of view channels we can have per wave.
   */
  public static void setMaxViewChannelsPerWave(int size) {
    maxViewChannelsPerWave = size;
  }

  private void handleException(String methodName, ChannelException e) {
    // Throwing this exception back to the wave service will crash
    // the client so we must catch it here and fail just this view.
    triggerOnException(e);
    logger.error().log("View submit [" + methodName + "] raised exception: " + e);
    handleDisconnect();
  }
  
  private void handleException(String methodName, ChannelException e, WaveletId waveletId) {
    // Throwing this exception back to the wave service will crash
    // the client so we must catch it here and fail just this view.
    triggerOnException(e);
    logger.error().log("View submit [" + methodName + "] for wavelet " + waveId + "/" + waveletId
        + " raised exception: " + e);
    handleDisconnect();
  }

  /**
   * Tells the listener of an exception on handling server messages. Wave and
   * wavelet id context is attached to the exception.
   *
   * @param e exception causing failure
   * @param waveletId associated wavelet id (may be null)
   */
  private void triggerOnException(ChannelException e) {
    if (listener != null) {
      listener.onException(e);
    }
  }

  /**
   * Tracks the current channel globally.
   */
  private void registerChannel() {
    // Ensure only allow a small set of view channels per client per wave.
    synchronized (viewChannelsPerWave) {
      Integer viewChannelsForWave = viewChannelsPerWave.get(waveId);
      if (viewChannelsForWave == null) {
        viewChannelsPerWave.put(waveId, 1);
      } else if (viewChannelsForWave >= maxViewChannelsPerWave) {
        Preconditions.illegalState("Cannot create more than " + maxViewChannelsPerWave
            + " channels per wave. Wave id: " + waveId);
      } else {
        viewChannelsPerWave.put(waveId, viewChannelsForWave + 1);
      }
    }
  }

  private void handleDisconnect() {
    logger.trace().log(this.toString() + " disconnected");
    repeatRequestScheduler.cancelAll();
    channels.clear();
    unregisterChannel();
    state = State.DISCONNECTED;
    if (listener != null) {
      listener.onDisconnected();
    }
  }

  private void handleClose() {
    if (state != State.CLOSING && state != State.CLOSED) {
      repeatRequestScheduler.cancelAll();
      if (state == State.OPENED) {
        state = State.CLOSING;
        for (final String channelId : channels.values()) {
          waveService.viewChannelClose(channelId, new WaveViewService.CloseCallback() {

            @Override
            public void onSuccess() {
              channels.inverse().remove(channelId);
              if (channels.isEmpty()) {
                shutdown();
              }
            }

            @Override
            public void onFailure(ReturnStatus status) {
              logger.error().log("Closing wavelet channel error " + status);
              listener.onFailure(status);
            }
          });
        }
        waveService.viewShutdown();
      } else {
        waveService.viewShutdown();
        shutdown();
      }
    }
  }

  private void shutdown() {
    logger.trace().log(this.toString() + " closed");
    channels.clear();
    unregisterChannel();
    state = State.CLOSED;
    if (listener != null) {
      listener.onClosed();
    }
  }

  /**
   * Untrack the current channel globally.
   */
  private void unregisterChannel() {
    synchronized (viewChannelsPerWave) {
      Integer viewChannelsForWave = viewChannelsPerWave.get(waveId);
      if (viewChannelsForWave != null) {
        if (viewChannelsForWave <= 1) {
          viewChannelsPerWave.remove(waveId);
        } else {
          viewChannelsPerWave.put(waveId, viewChannelsForWave - 1);
        }
      }
    }
  }
}
