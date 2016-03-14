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

package org.waveprotocol.box.webclient.client;

import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.client.scheduler.Scheduler;
import org.waveprotocol.wave.client.scheduler.SchedulerInstance;
import org.waveprotocol.wave.client.scheduler.TimerService;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.clientserver.ReturnStatus;
import org.waveprotocol.wave.concurrencycontrol.channel.ViewChannel;
import org.waveprotocol.wave.concurrencycontrol.channel.OperationChannelMultiplexer.ChannelsOpeningListener;
import org.waveprotocol.wave.concurrencycontrol.channel.OperationChannel;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.OperationRuntimeException;
import org.waveprotocol.wave.model.raw.RawFragment;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletFragmentData;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.model.wave.opbased.WaveViewImpl;
import org.waveprotocol.wave.model.wave.data.impl.StartVersionHelper;
import org.waveprotocol.box.webclient.client.WaveWebSocketClient.ConnectionListener;

import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Asynchronous requester of fragments.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class FragmentRequesterImpl implements FragmentRequester {

  protected static LoggerBundle LOG = new DomLogger("fragment-requester");

  /** Maximum number of uploading segments requests to the server at same time. */
  private final static int MAX_ACTIVE_REQUESTS_COUNT = 2;

  /** Container of channel view. */
  private final ViewChannel viewChannel;

  /** Wave view. */
  private final WaveViewImpl<OpBasedWavelet> wave;

  /** Defining of segment start version helper. */
  private final StartVersionHelper startVersionHelper;

  /** Opening versions of waveletsEnd versions for fragments requesting. */
  private final Map<WaveletId, Long> openVersions = CollectionUtils.newHashMap();

  /** Segment Ids and creation versions to build next request. */
  private final LinkedHashMap<WaveletId, LinkedHashMap<SegmentId, Long>> segmentsToRequest = new LinkedHashMap<>();

  /** Requested segments from the server previous times. */
  private final Map<WaveletId, Set<SegmentId>> requestedSegmentIds = CollectionUtils.newHashMap();

  /** Count of uploading fragment requests to the server at this time. */
  private int activeRequestsCount;

  /** Own listeners. */
  private ConnectionListener connectionListener;
  private ChannelsOpeningListener channelsOpeningListener;

  /** External listeners. */
  private final List<Listener> listeners = CollectionUtils.newLinkedList();

  /** Asynchronous requester task. */
  private final Scheduler.Task uploadingTask = new Scheduler.Task() {
    @Override
    public void execute() {
      Iterator<Map.Entry<WaveletId, LinkedHashMap<SegmentId, Long>>> it = segmentsToRequest.entrySet().iterator();
      while (it.hasNext()) {
        if (activeRequestsCount >= MAX_ACTIVE_REQUESTS_COUNT) {
          return;
        }
        Map.Entry<WaveletId, LinkedHashMap<SegmentId, Long>> entry = it.next();
        final WaveletId waveletId = entry.getKey();
        LinkedHashMap<SegmentId, Long> segments = entry.getValue();
        Long openVersion = openVersions.get(waveletId);
        if (openVersion != null) {
          long endVersion = openVersion;
          long startLookVersion = startVersionHelper.getStartVersion(waveletId);
          if (startLookVersion != StartVersionHelper.NO_VERSION && startLookVersion > openVersion) {
            endVersion = startLookVersion;
          }
          LinkedHashMap<SegmentId, Long> startVersions = new LinkedHashMap<>();
          for (SegmentId segmentId : segments.keySet()) {
            long startVersion = startVersionHelper.getStartVersion(waveletId, segmentId, segments.get(segmentId));
            if (startVersion == StartVersionHelper.NO_VERSION || startVersion > endVersion) {
              startVersion = endVersion;
            }
            startVersions.put(segmentId, startVersion);
          }
          final Set<SegmentId> segmentIds = segments.keySet();
          Set<SegmentId> requested = requestedSegmentIds.get(waveletId);
          if (requested == null) {
            requested = CollectionUtils.newHashSet();
            requestedSegmentIds.put(waveletId, requested);
          }
          requested.addAll(segmentIds);
          viewChannel.fetchFragments(waveletId, startVersions, endVersion, MIN_FETCH_REPLY_SIZE, MAX_FETCH_REPLY_SIZE,
            new ViewChannel.FetchFragmentsCallback() {
              @Override
              public void onWaveFragmentsFetch(Map<SegmentId, RawFragment> fragments) {
                activeRequestsCount--;
                requestedSegmentIds.get(waveletId).removeAll(segmentIds);
                try {
                  OpBasedWavelet wavelet = wave.getWavelet(waveletId);
                  Preconditions.checkNotNull(wavelet, "No wavelet " + waveletId.toString());
                  ObservableWaveletFragmentData waveletData = (ObservableWaveletFragmentData)wavelet.getWaveletData();
                  for (Map.Entry<SegmentId, RawFragment> entry : fragments.entrySet()) {
                    waveletData.applyRawFragment(entry.getKey(), entry.getValue());
                  }
                } catch (OperationException ex) {
                  throw new OperationRuntimeException("Fragments applying", ex);
                }
                for (Listener listener : listeners) {
                  listener.onFragmentsUploaded(fragments.keySet());
                }
                scheduleRequest();
              }

              @Override
              public void onFailure(ReturnStatus status) {
                activeRequestsCount--;
                requestedSegmentIds.get(waveletId).removeAll(segmentIds);
                LOG.error().log("Fragments uploading error " + status.getMessage());
              }
            });
          activeRequestsCount++;
          it.remove();
        }
      }
    }
  };

  private final TimerService scheduler = SchedulerInstance.getLowPriorityTimer();

  private boolean closed = false;

  public FragmentRequesterImpl(ViewChannel viewChannel, WaveViewImpl<OpBasedWavelet> wave,
      StartVersionHelper startVersionHelper) {
    this.viewChannel = viewChannel;
    this.wave = wave;
    this.startVersionHelper = startVersionHelper;
    for (OpBasedWavelet wavelet : wave.getWavelets()) {
      openVersions.put(wavelet.getId(), wavelet.getVersion());
    }
  }

  @Override
  public void close() {
    if (scheduler.isScheduled(uploadingTask)) {
      scheduler.cancel(uploadingTask);
    }
    closed = true;
  }

  @Override
  public void newRequest(WaveletId waveletId) {
    Preconditions.checkArgument(!closed, "Is closed");
    LinkedHashMap<SegmentId, Long> segments = segmentsToRequest.get(waveletId);
    if (segments != null) {
      segments.clear();
    } else {
      segmentsToRequest.put(waveletId, new LinkedHashMap<SegmentId, Long>());
    }
  }

  @Override
  public boolean isFull() {
    Preconditions.checkArgument(!closed, "Is closed");
    int size = 0;
    for (LinkedHashMap<SegmentId, Long> segments : segmentsToRequest.values()) {
      size += segments.size();
    }
    return size >= MAX_FETCH_BLIPS_COUNT;
  }

  @Override
  public void addSegmentId(WaveletId waveletId, SegmentId segmentId, long creationVersion) {
    Preconditions.checkArgument(!closed, "Is closed");
    Set<SegmentId> requested = requestedSegmentIds.get(waveletId);
    if (requested == null || !requested.contains(segmentId)) {
      LinkedHashMap<SegmentId, Long> segments = segmentsToRequest.get(waveletId);
      Preconditions.checkNotNull(segments, "Request is not started");
      segments.put(segmentId, creationVersion);
    }
  }

  @Override
  public void scheduleRequest() {
    Preconditions.checkArgument(!closed, "Is closed");
    if (!segmentsToRequest.isEmpty() && !scheduler.isScheduled(uploadingTask)) {
      scheduler.schedule(uploadingTask);
    }
  }

  @Override
  public void addListener(Listener listener) {
    Preconditions.checkArgument(!closed, "Is closed");
    listeners.add(listener);
  }

  public ConnectionListener getConnectionListener() {
    if (connectionListener == null) {
      connectionListener = new ConnectionListener() {

        @Override
        public void onConnecting() {
        }

        @Override
        public void onConnected() {
        }

        @Override
        public void onDisconnected() {
          reset();
        }

        @Override
        public void onFinished(String error) {
          reset();
        }
      };
    }
    return connectionListener;
  }

  private void reset() {
    openVersions.clear();
    segmentsToRequest.clear();
    requestedSegmentIds.clear();
    activeRequestsCount = 0;
  }
}
