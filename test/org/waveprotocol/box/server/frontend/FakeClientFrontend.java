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

import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.waveserver.WaveBus;
import org.waveprotocol.box.server.waveserver.WaveletProvider.SubmitRequestCallback;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.raw.RawFragment;
import org.waveprotocol.wave.clientserver.ReturnCode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;

/**
  * Implementation of a ClientFrontend which only records requests and will make callbacks when it
  * receives wavelet listener events.
  */
public class FakeClientFrontend implements ClientFrontend, WaveBus.Subscriber {
  private static class SubmitRecord {
    final SubmitRequestCallback listener;
    final int operations;
    SubmitRecord(int operations, SubmitRequestCallback listener) {
      this.operations = operations;
      this.listener = listener;
    }
  }

  private final Map<WaveId, FetchWaveViewRequestCallback> fetchWaveCallbacks = new HashMap<>();
  private final Map<WaveletName, FetchFragmentsRequestCallback> fetchFragmentsCallbacks = new HashMap<>();
  private final Map<WaveletName, OpenChannelRequestCallback> openCallbacks = new HashMap<>();
  private final Map<WaveletName, UpdateChannelListener> updateCallbacks = new HashMap<>();

  private final Map<String, SubmitRecord> submitRecords = new HashMap<>();

  public void doOpenSuccess(WaveletName waveletName, String channelId, Map<SegmentId, RawFragment> rawFragments, 
      HashedVersion lastModifiedVersion, long lastModifiedTime,
      HashedVersion connectVersion, HashedVersion commitVersion,
     HashedVersion unacknowlwdgedDeltaVersion) {
    OpenChannelRequestCallback callback = openCallbacks.remove(waveletName);
    if (callback != null) {
      callback.onSuccess(channelId, rawFragments, connectVersion, lastModifiedVersion,
        lastModifiedTime, commitVersion, unacknowlwdgedDeltaVersion);
    }
  }

  public void doOpenFailure(WaveletName waveletName, ReturnCode returnCode, String responseMessage) {
    OpenChannelRequestCallback callback = openCallbacks.remove(waveletName);
    if (callback != null) {
      callback.onFailure(returnCode, responseMessage);
    }
  }

  public void doSubmitFailed(String channelId, ReturnCode returnCode, String errorMessage) {
    SubmitRecord record = submitRecords.remove(channelId);
    if (record != null) {
      record.listener.onFailure(returnCode, errorMessage);
    }
  }

  /** Reports a submit success with resulting version 0 application timestamp 0 */
  public void doSubmitSuccess(String channelId) {
    HashedVersion fakeHashedVersion = HashedVersion.of(0, new byte[0]);
    doSubmitSuccess(channelId, fakeHashedVersion, 0);
  }

  /** Reports a submit success with the specified resulting version and application timestamp */
  public void doSubmitSuccess(String channelId, HashedVersion resultingVersion,
      long applicationTimestamp) {
    SubmitRecord record = submitRecords.remove(channelId);
    if (record != null) {
      record.listener.onSuccess(record.operations, resultingVersion, applicationTimestamp);
    }
  }

  public void doUpdateFailure(WaveletName waveletName, ReturnCode returnCode, String errorMessage) {
    UpdateChannelListener listener = updateCallbacks.get(waveletName);
    if (listener != null) {
      listener.onTerminate(returnCode, errorMessage);
    }
  }

  @Override
  public void fetchWaveViewRequest(ParticipantId loggedInUser, WaveId waveId, IdFilter waveletIdFilter,
      boolean fromLastRead, int minReplySize, int maxReplySize, int maxBlipsCount, 
      String connectionId, FetchWaveViewRequestCallback listener) {
    fetchWaveCallbacks.put(waveId, listener);
  }

  @Override
  public void fetchFragmentsRequest(ParticipantId loggedInUser, WaveletName waveletName, 
      Map<SegmentId, VersionRange> ranges, int minReplySize, int maxReplySize, String connectionId,
      FetchFragmentsRequestCallback callback) {
    fetchFragmentsCallbacks.put(waveletName, callback);
  }

  @Override
  public void openRequest(ParticipantId loggedInUser, WaveletName waveletName, List<SegmentId> segmentIds, 
      List<HashedVersion> knownVersions, ProtocolWaveletDelta unacknowledgedDelta, 
      String connectionId, OpenChannelRequestCallback openCallback, UpdateChannelListener updateListener) {
    openCallbacks.put(waveletName, openCallback);
    updateCallbacks.put(waveletName, updateListener);
  }

  @Override
  public void closeRequest(ParticipantId loggedInUser, String channelId) {
    submitRecords.remove(channelId);
  }

  @Override
  public void submitRequest(ParticipantId loggedInUser, String channelId, ProtocolWaveletDelta delta,
      SubmitRequestCallback listener) {
    submitRecords.put(channelId, new SubmitRecord(delta.getOperationCount(), listener));
  }

  @Override
  public void disconnect(ParticipantId loggedInUser, String connectionId) {
  }

  @Override
  public void waveletCommitted(WaveletName waveletName, HashedVersion version) {
    UpdateChannelListener listener = updateCallbacks.get(waveletName);
    if (listener != null) {
      listener.onUpdate(null, version);
    }
  }

  @Override
  public void waveletUpdate(WaveletName waveletName, DeltaSequence newDeltas) {
    UpdateChannelListener listener = updateCallbacks.get(waveletName);
    if (listener != null) {
      listener.onUpdate(newDeltas, null);
    }
  }
}
