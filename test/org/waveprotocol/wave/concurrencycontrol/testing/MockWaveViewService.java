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

package org.waveprotocol.wave.concurrencycontrol.testing;

import junit.framework.Assert;

import org.waveprotocol.wave.concurrencycontrol.channel.WaveViewService;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mock WaveViewService. Captures arguments for easy access in tests.
 *
 */
public class MockWaveViewService implements WaveViewService {
  public static class FetchWaveArguments {
    public final IdFilter waveletFilter;
    public final boolean fromLastRead;
    public final int minReplySize;
    public final int maxReplySize;
    public final int maxattedantBlips;
    public final FetchWaveCallback callback;

    public FetchWaveArguments(IdFilter waveletFilter, boolean fromLastRead, 
        int minReplySize, int maxReplySize, int maxBlipCount, FetchWaveCallback callback) {
      this.waveletFilter = waveletFilter;
      this.fromLastRead = fromLastRead;
      this.minReplySize = minReplySize;
      this.maxReplySize = maxReplySize;
      this.maxattedantBlips = maxBlipCount;
      this.callback = callback;
    }
  }

  public static class FetchFragmentsArguments {
    public final WaveletId waveletId;
    public final Map<SegmentId, Long> startVersions;
    public final long endVersion;
    public final int minReplySize;
    public final int maxReplySize;
    public final FetchFragmentsCallback calllback;
    
    public FetchFragmentsArguments(WaveletId waveletId, Map<SegmentId, Long> startVersions, long endVersion, 
        int minReplySize, int maxReplySize, FetchFragmentsCallback calllback) {
      this.waveletId = waveletId;
      this.startVersions = startVersions;
      this.endVersion = endVersion;
      this.minReplySize = minReplySize;
      this.maxReplySize = maxReplySize;
      this.calllback = calllback;
    }
  }

  public static class OpenArguments {
    public final WaveletId waveletId;
    public final List<HashedVersion> knownVersions;
    public final WaveletDelta unacknowledgedDelta;
    public final OpenChannelStreamCallback callback;

    private OpenArguments(WaveletId waveletId, List<HashedVersion> knownVersions,
        WaveletDelta unacknowledgedDelta, OpenChannelStreamCallback listener) {
      this.waveletId = waveletId;
      this.knownVersions = knownVersions;
      this.unacknowledgedDelta = unacknowledgedDelta;
      this.callback = listener;
    }
  }

  public static class SubmitArguments {
    public String channelId;
    public WaveletId waveletId;
    public WaveletDelta delta;
    public SubmitCallback callback;

    public SubmitArguments(String channelId, WaveletDelta delta, SubmitCallback callback) {
      this.channelId = channelId;
      this.delta = delta;
      this.callback = callback;
    }
  }

  public static class CloseArguments {
    public final String channelId;
    public final CloseCallback callback;

    private CloseArguments(String channelId, CloseCallback callback) {
      this.channelId = channelId;
      this.callback = callback;
    }
  }

  public final List<FetchWaveArguments> fetchWaves = new ArrayList<FetchWaveArguments>();
  public final List<FetchFragmentsArguments> fetchDocuments = new ArrayList<FetchFragmentsArguments>();
  public final List<OpenArguments> opens = new ArrayList<OpenArguments>();
  public final List<SubmitArguments> submits = new ArrayList<SubmitArguments>();
  public final List<CloseArguments> closes = new ArrayList<CloseArguments>();

  public FetchWaveArguments lastFetchWave() {
    Assert.assertFalse(fetchWaves.isEmpty());
    return fetchWaves.get(fetchWaves.size() - 1);
  }

  public FetchFragmentsArguments lastFetchDocuments() {
    Assert.assertFalse(fetchDocuments.isEmpty());
    return fetchDocuments.get(fetchDocuments.size() - 1);
  }

  public OpenArguments lastOpen() {
    Assert.assertFalse(opens.isEmpty());
    return opens.get(opens.size() - 1);
  }

  public CloseArguments lastClose() {
    Assert.assertFalse(closes.isEmpty());
    return closes.get(closes.size() - 1);
  }

  public SubmitArguments lastSubmit() {
    Assert.assertFalse(submits.isEmpty());
    return submits.get(submits.size() - 1);
  }

  @Override
  public void viewFetchWave(IdFilter waveletFilter, boolean fromLastRead, 
      int minReplySize, int maxReplySize, int maxBlipCount, FetchWaveCallback callback) {
    fetchWaves.add(new FetchWaveArguments(waveletFilter, fromLastRead, minReplySize, maxReplySize, maxBlipCount, callback));
  }

  @Override
  public void viewFetchFragments(WaveletId waveletId, Map<SegmentId, Long> segments, long endVersion,
      int minReplySize, int maxReplySize, FetchFragmentsCallback calllback) {
    fetchDocuments.add(new FetchFragmentsArguments(waveletId, segments, endVersion,
      minReplySize, maxReplySize, calllback));
  }

  @Override
  public void viewOpenWaveletChannel(WaveletId waveletId, Set<SegmentId> segmentIds, 
      List<HashedVersion> knownVersions, WaveletDelta unacknowledgedDelta, OpenChannelStreamCallback listener) {
    opens.add(new OpenArguments(waveletId, knownVersions, unacknowledgedDelta, listener));
  }

  @Override
  public void viewChannelClose(String channelId, CloseCallback callback) {
    closes.add(new CloseArguments(channelId, callback));
  }

  @Override
  public void viewSubmit(String channelId, WaveletDelta delta, SubmitCallback callback) {
    submits.add(new SubmitArguments(channelId, delta, callback));
  }

  @Override
  public void viewShutdown() {
  }
}
