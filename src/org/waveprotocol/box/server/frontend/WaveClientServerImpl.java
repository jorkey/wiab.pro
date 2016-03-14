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
import org.waveprotocol.box.server.serialize.OperationSerializer;
import org.waveprotocol.box.server.rpc.ServerRpcController;
import org.waveprotocol.box.server.rpc.ServerRpcProvider;
import org.waveprotocol.box.server.waveserver.WaveletProvider.SubmitRequestCallback;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.box.stat.Timed;
import org.waveprotocol.wave.clientserver.ClientServer;
import org.waveprotocol.wave.clientserver.ClientServer.EmptyResponse;
import org.waveprotocol.wave.clientserver.ClientServer.FetchWaveViewResponse;
import org.waveprotocol.wave.clientserver.ClientServer.OpenWaveletChannelStream;
import org.waveprotocol.wave.clientserver.ClientServer.ResponseStatus;
import org.waveprotocol.wave.clientserver.ClientServer.ResponseStatus.ResponseCode;
import org.waveprotocol.wave.clientserver.ClientServer.SubmitDeltaResponse;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.InvalidIdException;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.raw.RawOperation;
import org.waveprotocol.wave.model.raw.RawFragment;
import org.waveprotocol.wave.model.raw.RawSnapshot;
import org.waveprotocol.wave.util.logging.Log;
import org.waveprotocol.wave.federation.Proto.ProtocolHashedVersion;
import org.waveprotocol.wave.clientserver.ClientServer.FetchFragmentsResponse;
import org.waveprotocol.wave.clientserver.ClientServer.OpenWaveletChannelStream.WaveletUpdate;
import org.waveprotocol.wave.clientserver.ClientServer.OpenWaveletChannelStream.WaveletUpdate.Builder;
import org.waveprotocol.wave.clientserver.ClientServer.FetchWaveViewResponse.WaveletFragment;
import org.waveprotocol.wave.clientserver.ClientServer.SegmentOperation;
import org.waveprotocol.wave.clientserver.ClientServer.FetchFragmentsRequest.SegmentRequest;
import org.waveprotocol.wave.clientserver.ClientServer.SegmentSnapshot;
import org.waveprotocol.wave.clientserver.ReturnCode;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;

/**
 * RPC interface implementation for the wave server. Adapts incoming and
 * outgoing RPCs to the client frontend interface.
 *
 * @author unknown
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class WaveClientServerImpl implements
    ClientServer.FetchService.Interface,
    ClientServer.WaveletChannelService.Interface,
    ClientServer.DeltaSubmissionService.Interface,
    ClientServer.TransportAuthenticationService.Interface,
    ClientServer.DisconnectService.Interface {

  private static final Log LOG = Log.get(WaveClientServerImpl.class);

  private final ClientFrontend frontend;
  private boolean handleAuthentication;

  @Inject
  public WaveClientServerImpl(ClientFrontend frontend) {
    this.frontend = frontend;
  }

  public void registerServices(ServerRpcProvider server) {
    server.registerService(ClientServer.FetchService.newReflectiveService(this));
    server.registerService(ClientServer.WaveletChannelService.newReflectiveService(this));
    server.registerService(ClientServer.DeltaSubmissionService.newReflectiveService(this));
    server.registerService(ClientServer.TransportAuthenticationService.newReflectiveService(this));
    server.registerService(ClientServer.DisconnectService.newReflectiveService(this));
  }

  @Timed
  @Override
  public void fetchWaveView(RpcController controller, ClientServer.FetchWaveViewRequest request,
      final RpcCallback<FetchWaveViewResponse> done) {
    WaveId waveId;
    try {
      waveId = ModernIdSerialiser.INSTANCE.deserialiseWaveId(request.getWaveId());
    } catch (InvalidIdException e) {
      LOG.warning("Invalid wave id in fetch", e);
      FetchWaveViewResponse.Builder builder = FetchWaveViewResponse.newBuilder();
      done.run(builder.setStatus(ResponseStatus.newBuilder().setCode(ResponseCode.BAD_REQUEST)
          .setFailureReason(e.getMessage())).build());
      return;
    }
    List<WaveletId> waveletIds = new ArrayList<>();
    try {
      for (String waveletId : request.getWaveletIdList()) {
        waveletIds.add(ModernIdSerialiser.INSTANCE.deserialiseWaveletId(waveletId));
      }
    } catch (InvalidIdException e) {
      LOG.warning("Invalid wavelet id in fetch", e);
      FetchWaveViewResponse.Builder builder = FetchWaveViewResponse.newBuilder();
      done.run(builder.setStatus(ResponseStatus.newBuilder().setCode(ResponseCode.BAD_REQUEST)
          .setFailureReason(e.getMessage())).build());
      return;
    }
    IdFilter waveletIdFilter = IdFilter.of(waveletIds, request.getWaveletIdPrefixList());

    ParticipantId loggedInUser = asBoxController(controller).getLoggedInUser();
    String connectionId = asBoxController(controller).getConnectionId();
    boolean fromLastRead = request.hasFromLastRead() && request.getFromLastRead();
    final FetchWaveViewResponse.Builder builder = FetchWaveViewResponse.newBuilder();
    frontend.fetchWaveViewRequest(loggedInUser, waveId, waveletIdFilter,
        fromLastRead, request.getMinBlipReplySize(), request.getMaxBlipReplySize(), request.getMaxBlipCount(),
        connectionId, new ClientFrontend.FetchWaveViewRequestCallback() {

      @Override
      public synchronized void onWaveletSuccess(WaveletId waveletId, long lastModifyTime, HashedVersion lastModifyVersion,
          Map<SegmentId, RawFragment> fragments) {
        Timer timer = Timing.start("Serialize to WaveletFragment");
        try {
          WaveletFragment.Builder waveletBuilder = WaveletFragment.newBuilder();
          waveletBuilder.setWaveletId(waveletId.serialise());
          waveletBuilder.setLastModifiedVersion(OperationSerializer.serialize(lastModifyVersion));
          waveletBuilder.setLastModifiedTime(lastModifyTime);
          for (Map.Entry<SegmentId, RawFragment> entry : fragments.entrySet()) {
            ClientServer.SegmentFragment.Builder fragmentBuilder = waveletBuilder.addFragmentBuilder();
            fragmentBuilder.setSegmentId(entry.getKey().serialize());
            if (entry.getValue().hasSnapshot()) {
              fragmentBuilder.setSegmentSnapshot(serialize(entry.getValue().getSnapshot()));
            }
            for (RawOperation rawOp : entry.getValue().getAdjustOperations()) {
              fragmentBuilder.addAdjustOperation(serialize(rawOp));
            }
            for (RawOperation rawOp : entry.getValue().getDiffOperations()) {
              fragmentBuilder.addDiffOperation(serialize(rawOp));
            }
          }
          builder.addWavelet(waveletBuilder);
        } finally {
          Timing.stop(timer);
        }
      }

      @Override
      public void onFinish() {
        builder.setStatus(ResponseStatus.newBuilder().setCode(ResponseCode.OK));
        done.run(builder.build());
      }

      @Override
      public void onFailure(ReturnCode responseCode, String errorMessage) {
        done.run(builder.setStatus(ResponseStatus.newBuilder().setCode(deserialize(responseCode))
            .setFailureReason(errorMessage)).build());
      }
    });
  }

  @Override
  public void fetchFragments(RpcController controller, ClientServer.FetchFragmentsRequest request,
      final RpcCallback<FetchFragmentsResponse> done) {
    WaveletName waveletName;
    try {
      waveletName = ModernIdSerialiser.INSTANCE.deserialiseWaveletName(request.getWaveletName());
    } catch (InvalidIdException e) {
      LOG.warning("Invalid id in fetch", e);
      FetchFragmentsResponse.Builder builder = FetchFragmentsResponse.newBuilder();
      done.run(builder.setStatus(ResponseStatus.newBuilder().setCode(ResponseCode.BAD_REQUEST)
          .setFailureReason(e.getMessage())).build());
      return;
    }

    ParticipantId loggedInUser = asBoxController(controller).getLoggedInUser();
    String connectionId = asBoxController(controller).getConnectionId();
    ImmutableMap.Builder<SegmentId, VersionRange> ranges = ImmutableMap.builder();
    long endVersion = request.getEndVersion();
    for (SegmentRequest segment : request.getSegmentList()) {
      ranges.put(SegmentId.of(segment.getSegmentId()),
        VersionRange.of(segment.getStartVersion(), endVersion));
    }
    frontend.fetchFragmentsRequest(loggedInUser, waveletName, ranges.build(),
        request.getMinReplySize(), request.getMaxReplySize(),
        connectionId, new ClientFrontend.FetchFragmentsRequestCallback() {

      @Override
      public void onSuccess(Map<SegmentId, RawFragment> fragments) {
        Timer timer = Timing.start("Serialize to FetchFragmentsResponse");
        try {
          FetchFragmentsResponse.Builder builder = FetchFragmentsResponse.newBuilder();
          builder.setStatus(ResponseStatus.newBuilder().setCode(ResponseCode.OK));
          for (Map.Entry<SegmentId, RawFragment> entry : fragments.entrySet()) {
            ClientServer.SegmentFragment.Builder fragmentBuilder = builder.addFragmentBuilder();
            fragmentBuilder.setSegmentId(entry.getKey().serialize());
            if (entry.getValue().hasSnapshot()) {
              fragmentBuilder.setSegmentSnapshot(serialize(entry.getValue().getSnapshot()));
            }
            for (RawOperation rawOp : entry.getValue().getAdjustOperations()) {
              fragmentBuilder.addAdjustOperation(serialize(rawOp));
            }
            for (RawOperation rawOp : entry.getValue().getDiffOperations()) {
              fragmentBuilder.addDiffOperation(serialize(rawOp));
            }
          }
          done.run(builder.build());
        } finally {
          Timing.stop(timer);
        }
      }

      @Override
      public void onFailure(ReturnCode responseCode, String errorMessage) {
        FetchFragmentsResponse.Builder builder = FetchFragmentsResponse.newBuilder();
        done.run(builder.setStatus(ResponseStatus.newBuilder().setCode(deserialize(responseCode))
            .setFailureReason(errorMessage)).build());
      }
    });
  }

  @Timed
  @Override
  public void open(RpcController controller, ClientServer.OpenWaveletChannelRequest request,
      final RpcCallback<ClientServer.OpenWaveletChannelStream> callback) {
    ParticipantId loggedInUser = asBoxController(controller).getLoggedInUser();
    String connectionId = asBoxController(controller).getConnectionId();
    WaveletName waveletName;
    try {
      waveletName = ModernIdSerialiser.INSTANCE.deserialiseWaveletName(request.getWaveletName());
    } catch (InvalidIdException e) {
      LOG.warning("Invalid Id in open", e);
      OpenWaveletChannelStream.Builder builder = OpenWaveletChannelStream.newBuilder();
      builder.setTerminator(ClientServer.WaveletChannelTerminator.newBuilder().setStatus(
          ResponseStatus.newBuilder().setCode(ResponseCode.BAD_REQUEST)
          .setFailureReason(e.getMessage())).build());
      callback.run(builder.build());
      return;
    }
    List<SegmentId> segmentIds = new ArrayList();
    for (String segmentId : request.getSegmentIdList()) {
      segmentIds.add(SegmentId.of(segmentId));
    }
    List<HashedVersion> knownVersions = new ArrayList<>();
    for (ProtocolHashedVersion version : request.getKnownVersionList()) {
      knownVersions.add(OperationSerializer.deserialize(version));
    }
    ProtocolWaveletDelta unacknowlwdgedDelta = null;
    if (request.hasUnacknowledgedDelta()) {
      unacknowlwdgedDelta = request.getUnacknowledgedDelta();
    }
    frontend.openRequest(loggedInUser, waveletName, segmentIds, knownVersions, unacknowlwdgedDelta,
        connectionId, new ClientFrontend.OpenChannelRequestCallback() {

      @Override
      public void onSuccess(String channelId, Map<SegmentId, RawFragment> fragments, HashedVersion connectVersion,
          HashedVersion lastModifyVersion, long lastModifyTime, HashedVersion lastCommittedVersion,
          HashedVersion unacknowledgedDeltaVersion) {
        ClientServer.OpenWaveletChannelStream.Builder builder = ClientServer.OpenWaveletChannelStream.newBuilder();
        Timer timer = Timing.start("Serialize to ChannelOpen");
        try {
          builder.setChannelId(channelId).setCommitVersion(OperationSerializer.serialize(lastCommittedVersion));
          OpenWaveletChannelStream.ChannelOpen.Builder channelOpenBuilder =
              OpenWaveletChannelStream.ChannelOpen.newBuilder();
          channelOpenBuilder.setConnectVersion(OperationSerializer.serialize(connectVersion));
          channelOpenBuilder.setLastModifiedVersion(OperationSerializer.serialize(lastModifyVersion));
          channelOpenBuilder.setLastModifiedTime(lastModifyTime);
          for (Map.Entry<SegmentId, RawFragment> entry : fragments.entrySet()) {
            ClientServer.SegmentFragment.Builder fragmentBuilder = channelOpenBuilder.addFragmentBuilder();
            fragmentBuilder.setSegmentId(entry.getKey().serialize());
            for (RawOperation rawOp : entry.getValue().getAdjustOperations()) {
              fragmentBuilder.addAdjustOperation(serialize(rawOp));
            }
            for (RawOperation rawOp : entry.getValue().getDiffOperations()) {
              fragmentBuilder.addDiffOperation(serialize(rawOp));
            }
          }
          if (unacknowledgedDeltaVersion != null) {
            channelOpenBuilder.setUnacknowledgedDeltaVersion(OperationSerializer.serialize(unacknowledgedDeltaVersion));
          }
          builder.setChannelOpen(channelOpenBuilder);
        } finally {
          Timing.stop(timer);
        }
        timer = Timing.start("RpcCallback.run");
        try {
          callback.run(builder.build());
        } finally {
          Timing.stop(timer);
        }
      }

      @Override
      public void onFailure(ReturnCode responseCode, String errorMessage) {
        ClientServer.OpenWaveletChannelStream.Builder builder = ClientServer.OpenWaveletChannelStream.newBuilder();
        builder.setTerminator(ClientServer.WaveletChannelTerminator.newBuilder().setStatus(
            ResponseStatus.newBuilder().setCode(deserialize(responseCode)).setFailureReason(errorMessage).build()));
        callback.run(builder.build());
      }
    }, new ClientFrontend.UpdateChannelListener() {

      @Override
      public void onUpdate(DeltaSequence deltas, HashedVersion committedVersion) {
        ClientServer.OpenWaveletChannelStream.Builder builder = ClientServer.OpenWaveletChannelStream.newBuilder();
        if (deltas != null) {
          for (TransformedWaveletDelta delta : deltas) {
            Builder updateBuilder = WaveletUpdate.newBuilder();
            updateBuilder.setDelta(OperationSerializer.serialize(delta));
            updateBuilder.setResultingVersion(OperationSerializer.serialize(
                delta.getResultingVersion()));
            updateBuilder.setApplicationTimestamp(delta.getApplicationTimestamp());
            builder.addDelta(updateBuilder);
          }
        }
        if (committedVersion != null) {
          builder.setCommitVersion(OperationSerializer.serialize(committedVersion));
        }
        callback.run(builder.build());
      }

      @Override
      public void onTerminate(ReturnCode responseCode, String errorMessage) {
        ClientServer.OpenWaveletChannelStream.Builder builder = ClientServer.OpenWaveletChannelStream.newBuilder();
        builder.setTerminator(ClientServer.WaveletChannelTerminator.newBuilder().setStatus(
            ResponseStatus.newBuilder().setCode(deserialize(responseCode)).setFailureReason(errorMessage).build()));
        callback.run(builder.build());
      }
    });
  }

  @Timed
  @Override
  public void close(RpcController controller, ClientServer.CloseWaveletChannelRequest request,
      RpcCallback<ClientServer.EmptyResponse> done) {
    ParticipantId loggedInUser = asBoxController(controller).getLoggedInUser();
    frontend.closeRequest(loggedInUser, request.getChannelId());
    done.run(EmptyResponse.getDefaultInstance());
  }

  @Timed
  @Override
  public void submit(RpcController controller, ClientServer.SubmitDeltaRequest request,
      final RpcCallback<ClientServer.SubmitDeltaResponse> done) {
    ParticipantId loggedInUser = asBoxController(controller).getLoggedInUser();
    frontend.submitRequest(loggedInUser, request.getChannelId(), request.getDelta(),
        new SubmitRequestCallback() {
          @Override
          public void onSuccess(int operationsApplied,
              HashedVersion hashedVersionAfterApplication, long applicationTimestamp) {
            done.run(SubmitDeltaResponse.newBuilder()
                .setStatus(ResponseStatus.newBuilder().setCode(ResponseCode.OK))
                .setOperationsApplied(operationsApplied)
                .setHashedVersionAfterApplication(OperationSerializer.serialize(hashedVersionAfterApplication))
                .setTimestampAfterApplication(applicationTimestamp)
                .build());
          }

          @Override
          public void onFailure(ReturnCode responseCode, String error) {
            ResponseStatus status = ResponseStatus.newBuilder()
                .setCode(deserialize(responseCode)).setFailureReason(error).build();
            done.run(SubmitDeltaResponse.newBuilder()
                .setStatus(status).build());
          }
        });
  }

  @Timed
  @Override
  public void authenticate(RpcController controller, ClientServer.TransportAuthenticationRequest request,
      RpcCallback<ClientServer.EmptyResponse> done) {
    Preconditions.checkState(handleAuthentication,
        "ProtocolAuthenticate should be handled in ServerRpcProvider");
    done.run(EmptyResponse.getDefaultInstance());
  }

  @Timed
  @Override
  public void disconnect(RpcController controller, ClientServer.DisconnectRequest request,
      RpcCallback<ClientServer.EmptyResponse> done) {
    ParticipantId loggedInUser = asBoxController(controller).getLoggedInUser();
    String connectionId = asBoxController(controller).getConnectionId();
    frontend.disconnect(loggedInUser, connectionId);
    done.run(EmptyResponse.getDefaultInstance());
  }

  ServerRpcController asBoxController(RpcController controller) {
    // This cast is safe (because of how the WaveClientServerImpl is instantiated). We need to do this
    // because ServerRpcController implements an autogenerated interface.
    return (ServerRpcController) controller;
  }

  private static SegmentSnapshot serialize(RawSnapshot rawSnapshot) {
    SegmentSnapshot.Builder snapshot = SegmentSnapshot.newBuilder();
    snapshot.setRawSnapshot(rawSnapshot.serialize().getData());
    return snapshot.build();
  }

  private static SegmentOperation serialize(RawOperation rawOp) {
    SegmentOperation.Builder op = SegmentOperation.newBuilder();
    if (rawOp.getContext().getCreator() != null) {
      op.setAuthor(rawOp.getContext().getCreator().getAddress());
    }
    op.setTargetVersion(rawOp.getContext().getSegmentVersion());
    op.setTimestamp(rawOp.getContext().getTimestamp());
    op.setOperations(rawOp.serialize().getData());
    return op.build();
  }

  private static ResponseCode deserialize(ReturnCode responseCode) {
    switch (responseCode) {
      case OK:
        return ResponseCode.OK;
      case BAD_REQUEST:
        return ResponseCode.BAD_REQUEST;
      case INTERNAL_ERROR:
        return ResponseCode.INTERNAL_ERROR;
      case NOT_AUTHORIZED:
        return ResponseCode.NOT_AUTHORIZED;
      case VERSION_ERROR:
        return ResponseCode.VERSION_ERROR;
      case INVALID_OPERATION:
        return ResponseCode.INVALID_OPERATION;
      case SCHEMA_VIOLATION:
        return ResponseCode.SCHEMA_VIOLATION;
      case SIZE_LIMIT_EXCEEDED:
        return ResponseCode.SIZE_LIMIT_EXCEEDED;
      case POLICY_VIOLATION:
        return ResponseCode.POLICY_VIOLATION;
      case QUARANTINED:
        return ResponseCode.QUARANTINED;
      case TOO_OLD:
        return ResponseCode.TOO_OLD;
      case NOT_EXISTS:
        return ResponseCode.NOT_EXISTS;
      case ALREADY_EXISTS:
        return ResponseCode.ALREADY_EXISTS;
      case NOT_LOGGED_IN:
        return ResponseCode.NOT_LOGGED_IN;
      case UNSUBSCRIBED:
        return ResponseCode.UNSUBSCRIBED;
      case INDEXING_IN_PROCESS:
        return ResponseCode.INDEXING_IN_PROCESS;
    }
    throw new IllegalArgumentException("Unsupported response code: " + responseCode);
  }
}
