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

import com.google.common.collect.ImmutableList;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import java.util.Collections;

import junit.framework.TestCase;

import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.rpc.testing.FakeServerRpcController;
import org.waveprotocol.box.server.serialize.OperationSerializer;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.box.server.util.testing.TestingConstants;
import org.waveprotocol.wave.clientserver.ClientServer.OpenWaveletChannelRequest;
import org.waveprotocol.wave.clientserver.ClientServer.OpenWaveletChannelStream;
import org.waveprotocol.wave.clientserver.ClientServer.ResponseStatus.ResponseCode;
import org.waveprotocol.wave.clientserver.ClientServer.SubmitDeltaRequest;
import org.waveprotocol.wave.clientserver.ClientServer.SubmitDeltaResponse;
import org.waveprotocol.wave.clientserver.ReturnCode;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletOperation;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.data.WaveletData;

/**
 * Tests for the {@link WaveClientServerImpl}.
 *
 * @author unknown
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class WaveClienServerImplTest extends TestCase implements TestingConstants {

  private static final String FAIL_MESSAGE = "Failed";

  private static final HashedVersion COMMIT_VERSION = HashedVersion.unsigned(100L);
  private static final HashedVersion BEGIN_VERSION = HashedVersion.unsigned(101L);
  private static final HashedVersion END_VERSION = HashedVersion.unsigned(102L);

  private static final String CHANNEL_ID = "ch1";

  private static final ProtocolWaveletDelta DELTA = ProtocolWaveletDelta.newBuilder()
    .setAuthor(USER)
    .setHashedVersion(OperationSerializer.serialize(BEGIN_VERSION))
    .addOperation(ProtocolWaveletOperation.newBuilder().setNoOp(true).build()).build();

  private static final ImmutableList<ProtocolWaveletDelta> DELTAS = ImmutableList.of(DELTA);
  private static final DeltaSequence POJO_DELTAS =
      DeltaSequence.of(OperationSerializer.deserialize(DELTA, END_VERSION, 0L));

  private RpcController controller;

  private int counter = 0;

  private FakeClientFrontend frontend;

  private WaveClientServerImpl rpcImpl;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    counter = 0;
    controller = new FakeServerRpcController();
    frontend = new FakeClientFrontend();
    rpcImpl = new WaveClientServerImpl(frontend);
  }

  /**
   * Tests that an open results in a proper stream response.
   */
  public void testOpenSuccess() throws OperationException {
    OpenWaveletChannelRequest request = OpenWaveletChannelRequest.newBuilder()
        .setWaveletName(ModernIdSerialiser.INSTANCE.serialiseWaveletName(WAVELET_NAME)).build();
    counter = 0;
    rpcImpl.open(controller, request, new RpcCallback<OpenWaveletChannelStream>() {
      @Override
      public void run(OpenWaveletChannelStream stream) {
        ++counter;
        assertFalse(stream.hasTerminator());
        assertEquals(CHANNEL_ID, stream.getChannelId());
        assertEquals(END_VERSION, OperationSerializer.deserialize(
            stream.getChannelOpen().getLastModifiedVersion()));
        assertEquals(COMMIT_VERSION, OperationSerializer.deserialize(stream.getCommitVersion()));
      }
    });
    frontend.doOpenSuccess(WAVELET_NAME, CHANNEL_ID, Collections.EMPTY_MAP,
      END_VERSION, 0, BEGIN_VERSION, COMMIT_VERSION, null);
    assertEquals(1, counter);
    assertFalse(controller.failed());
  }

  /**
   * Tests that an open failure results in a proper wavelet failure message.
   */
  public void testOpenFailure() {
    OpenWaveletChannelRequest request = OpenWaveletChannelRequest.newBuilder()
        .setWaveletName(ModernIdSerialiser.INSTANCE.serialiseWaveletName(WAVELET_NAME)).build();
    counter = 0;
    rpcImpl.open(controller, request, new RpcCallback<OpenWaveletChannelStream>() {
      @Override
      public void run(OpenWaveletChannelStream stream) {
        ++counter;
        assertTrue(stream.hasTerminator());
        assertEquals(ResponseCode.INTERNAL_ERROR, stream.getTerminator().getStatus().getCode());
        assertEquals(FAIL_MESSAGE, stream.getTerminator().getStatus().getFailureReason());
      }
    });
    frontend.doOpenFailure(WAVELET_NAME, ReturnCode.INTERNAL_ERROR, FAIL_MESSAGE);
    assertEquals(1, counter);
    assertFalse(controller.failed());
  }

  /**
   * Tests that an commit results in a proper message.
   */
  public void testCommit() {
    OpenWaveletChannelRequest request = OpenWaveletChannelRequest.newBuilder()
        .setWaveletName(ModernIdSerialiser.INSTANCE.serialiseWaveletName(WAVELET_NAME))
        .build();
    counter = 0;
    rpcImpl.open(controller, request, new RpcCallback<OpenWaveletChannelStream>() {
      @Override
      public void run(OpenWaveletChannelStream stream) {
        ++counter;
        assertFalse(stream.hasTerminator());
        assertEquals(END_VERSION, OperationSerializer.deserialize(stream.getCommitVersion()));
      }
    });
    frontend.waveletCommitted(WAVELET_NAME, END_VERSION);
    assertEquals(1, counter);
    assertFalse(controller.failed());
  }

  /**
   * Tests that an open results in a proper wavelet update.
   */
  public void testUpdate() {
    OpenWaveletChannelRequest request = OpenWaveletChannelRequest.newBuilder()
        .setWaveletName(ModernIdSerialiser.INSTANCE.serialiseWaveletName(WAVELET_NAME)).build();
    counter = 0;
    rpcImpl.open(controller, request, new RpcCallback<OpenWaveletChannelStream>() {
      @Override
      public void run(OpenWaveletChannelStream stream) {
        ++counter;
        assertEquals(DELTAS.size(), stream.getDeltaCount());
        for (int i = 0; i < stream.getDeltaCount(); ++i) {
          assertEquals(DELTAS.get(i), stream.getDelta(i).getDelta());
          assertEquals(POJO_DELTAS.get(i).getResultingVersion(),
              OperationSerializer.deserialize(stream.getDelta(i).getResultingVersion()));
        }
        assertFalse(stream.hasCommitVersion());
      }
    });
    long dummyCreationTime = System.currentTimeMillis();
    WaveletData wavelet = WaveletDataUtil.createEmptyWavelet(WAVELET_NAME, PARTICIPANT,
        BEGIN_VERSION, dummyCreationTime);
    frontend.waveletUpdate(WAVELET_NAME, POJO_DELTAS);
    assertEquals(1, counter);
    assertFalse(controller.failed());
  }

  /**
   * Tests that a successful submit results in the proper submit response.
   */
  public void testSubmitSuccess() {
    SubmitDeltaRequest request = SubmitDeltaRequest.newBuilder()
      .setChannelId(CHANNEL_ID)
      .setDelta(DELTA)
      .build();
    counter = 0;
    rpcImpl.submit(controller, request, new RpcCallback<SubmitDeltaResponse>() {
      @Override
      public void run(SubmitDeltaResponse response) {
        ++counter;
        assertEquals(1, response.getOperationsApplied());
        assertEquals(ResponseCode.OK, response.getStatus().getCode());
      }
    });
    frontend.doSubmitSuccess(CHANNEL_ID);
    assertEquals(1, counter);
    assertFalse(controller.failed());
  }

  /**
   * Tests that a failed submit results in the proper submit failure response.
   */
  public void testSubmitFailed() {
    SubmitDeltaRequest request = SubmitDeltaRequest.newBuilder()
      .setChannelId(CHANNEL_ID)
      .setDelta(DELTA)
      .build();
    counter = 0;
    rpcImpl.submit(controller, request, new RpcCallback<SubmitDeltaResponse>() {
      @Override
      public void run(SubmitDeltaResponse response) {
        ++counter;
        assertEquals(ResponseCode.INTERNAL_ERROR, response.getStatus().getCode());
        assertEquals(FAIL_MESSAGE, response.getStatus().getFailureReason());
      }
    });
    frontend.doSubmitFailed(CHANNEL_ID, ReturnCode.INTERNAL_ERROR, FAIL_MESSAGE);
    assertEquals(1, counter);
    assertFalse(controller.failed());
  }

  /**
   * Tests that a bad wave id request is gracefully handled.
   */
  public void testOpenEncodingError() {
    OpenWaveletChannelRequest request = OpenWaveletChannelRequest.newBuilder()
        .setWaveletName("badwaveletname").build();
    counter = 0;
    rpcImpl.open(controller, request, new RpcCallback<OpenWaveletChannelStream>() {
      @Override
      public void run(OpenWaveletChannelStream response) {
        ++counter;
        assertTrue(response.hasTerminator());
        assertEquals(ResponseCode.BAD_REQUEST, response.getTerminator().getStatus().getCode());
      }
    });
    assertEquals(1, counter);
    assertFalse(controller.failed());
  }
}
