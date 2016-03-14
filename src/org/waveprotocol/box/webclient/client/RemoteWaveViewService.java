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

import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.concurrencycontrol.channel.WaveViewService;
import org.waveprotocol.wave.federation.ProtocolHashedVersion;
import org.waveprotocol.wave.federation.ProtocolWaveletDelta;
import org.waveprotocol.wave.federation.jso.ProtocolWaveletDeltaJsoImpl;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.InvalidIdException;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.DocumentFactory;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.impl.WaveViewDataImpl;
import org.waveprotocol.wave.model.wave.data.impl.WaveletFragmentDataImpl;
import org.waveprotocol.wave.model.raw.RawBlipSnapshot;
import org.waveprotocol.wave.model.raw.RawFragment;
import org.waveprotocol.wave.model.raw.RawParticipantsSnapshot;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.OperationRuntimeException;
import org.waveprotocol.wave.model.raw.RawOperation;
import org.waveprotocol.wave.model.raw.RawSnapshot;
import org.waveprotocol.wave.model.raw.RawIndexSnapshot;
import org.waveprotocol.wave.model.raw.serialization.JsoSerializer;
import org.waveprotocol.wave.model.raw.serialization.JsoSerializerAdaptor;
import org.waveprotocol.wave.model.raw.serialization.WaveletOperationSerializer;
import org.waveprotocol.wave.model.util.CollectionUtils;

import org.waveprotocol.wave.clientserver.FetchWaveViewResponse;
import org.waveprotocol.wave.clientserver.OpenWaveletChannelStream.ChannelOpen;
import org.waveprotocol.wave.clientserver.OpenWaveletChannelStream.WaveletUpdate;
import org.waveprotocol.wave.clientserver.ReturnCode;
import org.waveprotocol.wave.clientserver.ReturnStatus;
import org.waveprotocol.wave.clientserver.ResponseStatus;
import org.waveprotocol.wave.clientserver.EmptyResponse;
import org.waveprotocol.wave.clientserver.FetchWaveViewResponse.WaveletFragment;
import org.waveprotocol.wave.clientserver.SegmentFragment;
import org.waveprotocol.wave.clientserver.FetchFragmentsResponse;
import org.waveprotocol.wave.clientserver.SegmentOperation;
import org.waveprotocol.wave.clientserver.OpenWaveletChannelStream;
import org.waveprotocol.wave.clientserver.SubmitDeltaResponse;
import org.waveprotocol.wave.clientserver.SegmentSnapshot;
import org.waveprotocol.wave.clientserver.jso.CloseWaveletChannelRequestJsoImpl;
import org.waveprotocol.wave.clientserver.jso.OpenWaveletChannelRequestJsoImpl;
import org.waveprotocol.wave.clientserver.jso.SubmitDeltaRequestJsoImpl;
import org.waveprotocol.wave.clientserver.jso.FetchWaveViewRequestJsoImpl;
import org.waveprotocol.wave.clientserver.jso.FetchFragmentsRequestJsoImpl;
import org.waveprotocol.wave.clientserver.jso.FetchFragmentsRequestJsoImpl.SegmentRequestJsoImpl;
import org.waveprotocol.wave.communication.Blob;

import org.waveprotocol.box.stat.AsyncCallContext;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implements the {@link WaveViewService} using RPCs.
 */
public final class RemoteWaveViewService implements WaveViewService {

  private static final LoggerBundle LOG = new DomLogger("rpc");

  private final WaveId waveId;
  private final WaveWebSocketClient socket;
  private final DocumentFactory<?> docFactory;
  private final WaveletOperationSerializer operationSerializer =
    new WaveletOperationSerializer(new JsoSerializerAdaptor());

  private boolean shutdowned = false;

  /**
   * Creates a service.
   *
   * @param waveId wave this service serves
   * @param socket websocket client
   * @param docFactory document factory to use when deserializing snapshots
   */
  public RemoteWaveViewService(WaveId waveId, WaveWebSocketClient socket, DocumentFactory<?> docFactory) {
    this.waveId = waveId;
    this.socket = socket;
    this.docFactory = docFactory;
  }

  @Override
  public void viewFetchWave(IdFilter waveletFilter, boolean fromLastRead,
      int minBlipReplySize, int maxblipReplySize, int maxBlipCount, final FetchWaveCallback callback) {
    LOG.trace().log("viewFetchWave called on " + waveId);
    Preconditions.checkArgument(!shutdowned, "Is shut down");
    FetchWaveViewRequestJsoImpl request = FetchWaveViewRequestJsoImpl.create();
    request.setWaveId(serialize(waveId));
    for (WaveletId waveletId : waveletFilter.getIds()) {
      request.addWaveletId(ModernIdSerialiser.INSTANCE.serialiseWaveletId(waveletId));
    }
    for (String prefix : waveletFilter.getPrefixes()) {
      request.addWaveletIdPrefix(prefix);
    }
    request.setFromLastRead(fromLastRead);
    request.setMinBlipReplySize(minBlipReplySize);
    request.setMaxBlipReplySize(maxblipReplySize);
    request.setMaxBlipCount(maxBlipCount);
    final AsyncCallContext callContext = AsyncCallContext.start("FetchWaveViewRequest");
    socket.fetchWaveView(request, new ResponseCallback<FetchWaveViewResponse>() {

      @Override
      public void run(FetchWaveViewResponse response) {
        callContext.stop();
        if (!shutdowned) {
          ReturnStatus status = deserialize(response.getStatus());
          if (status.getCode() == ReturnCode.OK) {
            List<ObservableWaveletData> wavelets = new LinkedList<>();
            for (WaveletFragment wavelet : response.getWavelet()) {
              wavelets.add(deserialize(wavelet));
            }
            callback.onSuccess(WaveViewDataImpl.create(waveId, wavelets));
          } else {
            callback.onFailure(status);
          }
        }
      }
    });
  }

  @Override
  public void viewFetchFragments(WaveletId waveletId, Map<SegmentId, Long> segments,
      long endVersion, int minReplySize, int maxReplySize, final FetchFragmentsCallback calllback) {
    LOG.trace().log("viewFetchFragments called on wave " + waveId + ", wavelet " + waveletId);
    Preconditions.checkArgument(!shutdowned, "Is shut down");
    FetchFragmentsRequestJsoImpl request = FetchFragmentsRequestJsoImpl.create();
    request.setWaveletName(serialize(WaveletName.of(waveId, waveletId)));
    for (SegmentId segmentId : segments.keySet()) {
      SegmentRequestJsoImpl segment = SegmentRequestJsoImpl.create();
      segment.setSegmentId(segmentId.serialize());
      segment.setStartVersion(segments.get(segmentId));
      request.addSegment(segment);
    }
    request.setEndVersion(endVersion);
    request.setMinReplySize(minReplySize);
    request.setMaxReplySize(maxReplySize);
    final AsyncCallContext callContext = AsyncCallContext.start("FetchFragmentsRequest");
    socket.fetchFragments(request, new ResponseCallback<FetchFragmentsResponse>() {

      @Override
      public void run(FetchFragmentsResponse response) {
        callContext.stop();
        if (!shutdowned) {
          ReturnStatus status = deserialize(response.getStatus());
          if (status.getCode() == ReturnCode.OK) {
            Map<SegmentId, RawFragment> fragments = CollectionUtils.newHashMap();
            for (SegmentFragment rawFragment : response.getFragment()) {
              SegmentId segmentId = SegmentId.of(rawFragment.getSegmentId());
              fragments.put(segmentId, deserialize(rawFragment));
            }
            calllback.onSuccess(fragments);
          } else {
            calllback.onFailure(status);
          }
        }
      }
    });
  }

  @Override
  public void viewOpenWaveletChannel(WaveletId waveletId, Set<SegmentId> segmentIds,
      List<HashedVersion> knownVersions, WaveletDelta unacknowledgedDelta,
      final OpenChannelStreamCallback listener) {
    LOG.trace().log("viewOpenWaveletChannel called on wave " + waveId + ", wavelet " + waveletId);
    Preconditions.checkArgument(!shutdowned, "Is shut down");
    OpenWaveletChannelRequestJsoImpl request = OpenWaveletChannelRequestJsoImpl.create();
    request.setWaveletName(serialize(WaveletName.of(waveId, waveletId)));
    if (segmentIds != null) {
      for (SegmentId segmentId : segmentIds) {
        request.addSegmentId(serialize(segmentId));
      }
    }
    for (HashedVersion version : knownVersions) {
      request.addKnownVersion(serialize(version));
    }
    if (unacknowledgedDelta != null) {
      request.setUnacknowledgedDelta(serialize(unacknowledgedDelta));
    }
    final AsyncCallContext callContext = AsyncCallContext.start("OpenWaveletChannelRequest");
    socket.open(request, new ResponseCallback<OpenWaveletChannelStream>() {

      @Override
      public void run(OpenWaveletChannelStream response) {
        callContext.stop();
        if (!shutdowned) {
          if (response.hasTerminator()) {
            listener.onFailure(deserialize(response.getTerminator().getStatus()));
          } else if (response.hasChannelOpen()) {
            LOG.trace().log("viewOpenWaveletChannel opened channel " + response.getChannelId() +
                " on wave " + waveId);
            ChannelOpen channelOpen = response.getChannelOpen();
            HashedVersion connectVersion = deserialize(channelOpen.getConnectVersion());
            HashedVersion lastModifiedVersion = deserialize(channelOpen.getLastModifiedVersion());
            long lastModifiedTime = channelOpen.getLastModifiedTime();
            HashedVersion committedVersion = deserialize(response.getCommitVersion());
            HashedVersion unacknowledgedDeltaVersion = channelOpen.hasUnacknowledgedDeltaVersion()?
                deserialize(channelOpen.getUnacknowledgedDeltaVersion()):null;
            Map<SegmentId, RawFragment> fragments = CollectionUtils.newHashMap();
            for (SegmentFragment rawFragment : channelOpen.getFragment()) {
              SegmentId segmentId = SegmentId.of(rawFragment.getSegmentId());
              fragments.put(segmentId, deserialize(rawFragment));
            }
            listener.onWaveletOpen(response.getChannelId(), connectVersion, lastModifiedVersion,
                lastModifiedTime, committedVersion, unacknowledgedDeltaVersion, fragments);
          } else {
            List<TransformedWaveletDelta> deltas = new ArrayList<>();
            for (WaveletUpdate update : response.getDelta()) {
              deltas.add(deserialize(update.getDelta(), update.getResultingVersion(),
                  update.getApplicationTimestamp()));
            }
            HashedVersion commitVersion = response.hasCommitVersion() ? deserialize(response.getCommitVersion()) : null;
            listener.onUpdate(deltas, commitVersion);
          }
        }
      }
    });
  }

  @Override
  public void viewSubmit(String channelId, WaveletDelta delta, final SubmitCallback callback) {
    LOG.trace().log("submit on " + channelId + " of wave " + waveId);
    Preconditions.checkArgument(!shutdowned, "Is shut down");
    SubmitDeltaRequestJsoImpl submitRequest = SubmitDeltaRequestJsoImpl.create();
    submitRequest.setChannelId(channelId);
    submitRequest.setDelta(serialize(delta));
    final AsyncCallContext callContext = AsyncCallContext.start("SubmitDeltaRequest");
    socket.submit(submitRequest, new ResponseCallback<SubmitDeltaResponse>() {

      @Override
      public void run(SubmitDeltaResponse response) {
        callContext.stop();
        if (!shutdowned) {
          ReturnStatus status = deserialize(response.getStatus());
          HashedVersion resultVersion = HashedVersion.unsigned(0);
          if (response.hasHashedVersionAfterApplication()) {
            resultVersion =
                WaveletOperationSerializer.deserialize(response.getHashedVersionAfterApplication());
          }
          callback.onResponse(response.getOperationsApplied(), resultVersion,
              response.getTimestampAfterApplication(), status);
        }
      }

    });
  }

  @Override
  public void viewChannelClose(String channelId, final CloseCallback callback) {
    LOG.trace().log("closing channel " + channelId + " of wave " + waveId);
    Preconditions.checkArgument(!shutdowned, "Is shut down");
    CloseWaveletChannelRequestJsoImpl request = CloseWaveletChannelRequestJsoImpl.create();
    request.setChannelId(channelId);
    final AsyncCallContext callContext = AsyncCallContext.start("CloseWaveletChannelRequest");
    socket.close(request, new ResponseCallback<EmptyResponse>() {

      @Override
      public void run(EmptyResponse response) {
        callContext.stop();
        callback.onSuccess();
      }
    });
  }

  @Override
  public void viewShutdown() {
    shutdowned = true;
  }

  //
  // Serialization.
  //

  private ProtocolWaveletDelta serialize(WaveletDelta delta) {
    ProtocolWaveletDeltaJsoImpl protocolDelta = ProtocolWaveletDeltaJsoImpl.create();
    for (WaveletOperation op : delta) {
      protocolDelta.addOperation(operationSerializer.serialize(op));
    }
    protocolDelta.setAuthor(delta.getAuthor().getAddress());
    protocolDelta.setHashedVersion(serialize(delta.getTargetVersion()));
    return protocolDelta;
  }

  private static String serialize(WaveId waveId) {
    return ModernIdSerialiser.INSTANCE.serialiseWaveId(waveId);
  }

  private static String serialize(WaveletName waveletName) {
    return ModernIdSerialiser.INSTANCE.serialiseWaveletName(waveletName);
  }

  private static String serialize(SegmentId segmentId) {
    return segmentId.serialize();
  }

  private ProtocolHashedVersion serialize(HashedVersion version) {
    return operationSerializer.serialize(version);
  }

  private TransformedWaveletDelta deserialize(ProtocolWaveletDelta delta,
      ProtocolHashedVersion end, long applicationTimestamp) {
    return operationSerializer.deserialize(delta, deserialize(end), applicationTimestamp);
  }

  private WaveletFragmentDataImpl deserialize(WaveletFragment waveletFragment) {
    WaveletId id = deserializeWaveletId(waveletFragment.getWaveletId());
    ParticipantId creator = null;
    long creationTime = 0;
    Map<SegmentId, RawFragment> fragments = new HashMap();
    for (SegmentFragment rawFragment : waveletFragment.getFragment()) {
      RawFragment fragment = deserialize(rawFragment);
      SegmentId segmentId = SegmentId.of(rawFragment.getSegmentId());
      if (segmentId.isIndex()) {
        creationTime = fragment.getIndexSnapshot().getCreationTime();
      } else if (segmentId.isParticipants()) {
        creator = fragment.getParticipantsSnapshot().getCreator();
      }
      fragments.put(segmentId, fragment);
    }
    HashedVersion lastModifiedVersion = deserialize(waveletFragment.getLastModifiedVersion());
    long lastModifiedTime = waveletFragment.getLastModifiedTime();

    WaveletFragmentDataImpl waveletData =
        new WaveletFragmentDataImpl(id, creator, creationTime,
            lastModifiedVersion, lastModifiedTime, waveId, docFactory);
    try {
      for (Map.Entry<SegmentId, RawFragment> entry : fragments.entrySet()) {
        waveletData.applyRawFragment(entry.getKey(), entry.getValue());
      }
    } catch (OperationException ex) {
      throw new OperationRuntimeException("Fragment applying error", ex);
    }
    return waveletData;
  }

  private static RawFragment deserialize(SegmentFragment fragment) {
    SegmentId segmentId = SegmentId.of(fragment.getSegmentId());
    RawSnapshot snapshot = null;
    if (fragment.hasSegmentSnapshot()) {
      SegmentSnapshot segmentSnapshot = fragment.getSegmentSnapshot();
      if (segmentId.isIndex()) {
        snapshot = new RawIndexSnapshot(JsoSerializer.INDEX_SERIALIZER,
            new Blob(segmentSnapshot.getRawSnapshot()));
      } else if (segmentId.isParticipants()) {
        snapshot = new RawParticipantsSnapshot(JsoSerializer.PARTICIPANTS_SERIALIZER,
            new Blob(segmentSnapshot.getRawSnapshot()));
      } else {
        Preconditions.checkArgument(segmentId.isBlip(), "Invalid segment type " + segmentId.toString());
        snapshot = new RawBlipSnapshot(JsoSerializer.BLIP_SERIALIZER,
            new Blob(segmentSnapshot.getRawSnapshot()), segmentId.getBlipId());
      }
    }
    ImmutableList.Builder<RawOperation> adjustOperations = ImmutableList.builder();
    for (SegmentOperation op : fragment.getAdjustOperation()) {
      adjustOperations.add(deserialize(op, segmentId, true));
    }
    ImmutableList.Builder<RawOperation> diffOperations = ImmutableList.builder();
    for (SegmentOperation op : fragment.getDiffOperation()) {
      diffOperations.add(deserialize(op, segmentId, false));
    }
    return new RawFragment(snapshot, adjustOperations.build(), diffOperations.build());
  }

  private static RawOperation deserialize(SegmentOperation operation, SegmentId segmentId, boolean adjust) {
    WaveletOperationContext context = new WaveletOperationContext(
      operation.hasAuthor() ? ParticipantId.ofUnsafe(operation.getAuthor()) : null,
      operation.getTimestamp(), operation.getTargetVersion(), adjust);
    return new RawOperation(JsoSerializer.OPERATION_SERIALIZER,
        new Blob(operation.getOperations()), segmentId, context);
  }

  private static WaveletId deserializeWaveletId(String waveId) {
    try {
      return ModernIdSerialiser.INSTANCE.deserialiseWaveletId(waveId);
    } catch (InvalidIdException ex) {
      throw new IllegalArgumentException(ex);
    }
  }

  private static HashedVersion deserialize(ProtocolHashedVersion version) {
    return WaveletOperationSerializer.deserialize(version);
  }

  public static ReturnStatus deserialize(ResponseStatus responseStatus) {
    return new ReturnStatus(deserialize(responseStatus.getCode()), responseStatus.getFailureReason());
  }

  public static ReturnCode deserialize(ResponseStatus.ResponseCode responseCode) {
    switch (responseCode) {
      case OK:
        return ReturnCode.OK;
      case BAD_REQUEST:
        return ReturnCode.BAD_REQUEST;
      case INTERNAL_ERROR:
        return ReturnCode.INTERNAL_ERROR;
      case NOT_AUTHORIZED:
        return ReturnCode.NOT_AUTHORIZED;
      case VERSION_ERROR:
        return ReturnCode.VERSION_ERROR;
      case INVALID_OPERATION:
        return ReturnCode.INVALID_OPERATION;
      case SCHEMA_VIOLATION:
        return ReturnCode.SCHEMA_VIOLATION;
      case SIZE_LIMIT_EXCEEDED:
        return ReturnCode.SIZE_LIMIT_EXCEEDED;
      case POLICY_VIOLATION:
        return ReturnCode.POLICY_VIOLATION;
      case QUARANTINED:
        return ReturnCode.QUARANTINED;
      case TOO_OLD:
        return ReturnCode.TOO_OLD;
      case NOT_EXISTS:
        return ReturnCode.NOT_EXISTS;
      case ALREADY_EXISTS:
        return ReturnCode.ALREADY_EXISTS;
      case NOT_LOGGED_IN:
        return ReturnCode.NOT_LOGGED_IN;
      case UNSUBSCRIBED:
        return ReturnCode.UNSUBSCRIBED;
      case INDEXING_IN_PROCESS:
        return ReturnCode.INDEXING_IN_PROCESS;
    }
    throw new IllegalArgumentException("Unsupported response code: " + responseCode);
  }
}