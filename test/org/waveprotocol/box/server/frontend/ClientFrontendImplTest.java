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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gxp.com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;

import junit.framework.TestCase;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.serialize.OperationSerializer;
import org.waveprotocol.box.server.frontend.ClientFrontend.FetchFragmentsRequestCallback;
import org.waveprotocol.box.server.frontend.ClientFrontend.FetchWaveViewRequestCallback;
import org.waveprotocol.box.server.frontend.ClientFrontend.OpenChannelRequestCallback;
import org.waveprotocol.box.server.frontend.ClientFrontend.UpdateChannelListener;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.box.server.waveserver.AppliedDeltaUtil;
import org.waveprotocol.box.server.waveserver.ByteStringMessage;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveserver.WaveletProvider.OpenRequestCallback;
import org.waveprotocol.box.server.waveserver.WaveletProvider.SubmitRequestCallback;
import org.waveprotocol.wave.clientserver.ReturnCode;
import org.waveprotocol.wave.federation.Proto.ProtocolAppliedWaveletDelta;
import org.waveprotocol.wave.federation.Proto.ProtocolSignedDelta;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.IdFilters;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.supplement.PrimitiveSupplementImpl;
import org.waveprotocol.wave.model.testing.DeltaTestUtil;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionFactoryImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveletData;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.supplement.Supplement;
import org.waveprotocol.wave.model.supplement.SupplementImpl;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.waveprotocol.box.server.executor.ExecutorAnnotations;
import org.waveprotocol.box.server.executor.TestExecutorsModule;
import org.waveprotocol.box.server.persistence.blocks.Interval;

/**
 * Tests for {@link ClientFrontendImpl}.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class ClientFrontendImplTest extends TestCase {
  interface ChannelListener extends OpenChannelRequestCallback, UpdateChannelListener {
  }

  private static final IdURIEncoderDecoder URI_CODEC =
      new IdURIEncoderDecoder(new JavaUrlCodec());
  private static final HashedVersionFactory HASH_FACTORY =
      new HashedVersionFactoryImpl(URI_CODEC);

  private static final WaveId WAVE_ID = WaveId.of("example.com", "waveId");
  private static final WaveletId W1 =
      WaveletId.of("example.com", IdConstants.CONVERSATION_ROOT_WAVELET);
  private static final WaveletId W2 = WaveletId.of("example.com", "conv+2");
  private static final WaveletName WN1 = WaveletName.of(WAVE_ID, W1);
  private static final WaveletName WN2 = WaveletName.of(WAVE_ID, W2);

  private static final ParticipantId USER = new ParticipantId("user@example.com");
  private static final ParticipantId USER1 = new ParticipantId("user1@example.com");
  private static final DeltaTestUtil UTIL = new DeltaTestUtil(USER);
  private static final String CONNECTION_ID = "con";

  private static final HashedVersion V0 = HASH_FACTORY.createVersionZero(WN1);
  private static final HashedVersion V1 = HashedVersion.unsigned(1L);
  private static final HashedVersion V2 = HashedVersion.unsigned(2L);
  private static final HashedVersion V3 = HashedVersion.unsigned(3L);

  private static final List<HashedVersion> KNOWN_V1 = Lists.newArrayList(V1);

  private static final String DOC1 = "doc1";
  private static final String DOC2 = "doc2";
  private static final List<SegmentId> SEGMENT_IDS =
    CollectionUtils.newLinkedList(SegmentId.ofBlipId(DOC1), SegmentId.ofBlipId(DOC2));
  private static final Map<SegmentId, VersionRange> VERSION_RANGE_1 =
    ImmutableMap.<SegmentId, VersionRange>builder()
      .put(SegmentId.ofBlipId(DOC1), VersionRange.of(1, 2))
      .put(SegmentId.ofBlipId(DOC2), VersionRange.of(1, 2)).build();

  private static final long TIMESTAMP = 1234567890L;

  private static final TransformedWaveletDelta DELTA = TransformedWaveletDelta.cloneOperations(
      USER, V1, TIMESTAMP, ImmutableList.of(UTIL.addParticipant(USER)));
  private static final TransformedWaveletDelta DELTA1 = TransformedWaveletDelta.cloneOperations(
      USER, V2, TIMESTAMP, ImmutableList.of(UTIL.addParticipant(USER1)));
  private static final TransformedWaveletDelta DELTA2 = TransformedWaveletDelta.cloneOperations(
      USER, V3, TIMESTAMP, ImmutableList.of(UTIL.removeParticipant(USER1)));

  private static final WaveletDeltaRecord DELTA_RECORD =
      new WaveletDeltaRecord(V0, makeAppliedDelta(V0, DELTA), DELTA);
  private static final WaveletDeltaRecord DELTA_RECORD1 =
      new WaveletDeltaRecord(V1, makeAppliedDelta(V1, DELTA1), DELTA1);
  private static final WaveletDeltaRecord DELTA_RECORD2 =
      new WaveletDeltaRecord(V2, makeAppliedDelta(V2, DELTA2), DELTA2);

  private static final ProtocolWaveletDelta SERIALIZED_DELTA =
      OperationSerializer.serialize(DELTA);

  private ClientFrontendImpl clientFrontend;
  private WaveletProvider waveletProvider;
  private Supplement supplement;

  @Captor
  ArgumentCaptor<List<ReadableWaveletData>> snapshotsCaptor;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    waveletProvider = mock(WaveletProvider.class);
    when(waveletProvider.getLastCommittedVersion(any(WaveletName.class))).thenReturn(V0);
    when(waveletProvider.getWaveletIds(any(WaveId.class))).thenReturn(ImmutableSet.<WaveletId>of());
    Mockito.doAnswer(new Answer<Void>() {

      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        List<HashedVersion> versions = (List<HashedVersion>)invocation.getArguments()[1];
        ((OpenRequestCallback) invocation.getArguments()[3]).onSuccess(
            versions.get(versions.size()-1), V0);
        return null;
      }
    }).when(waveletProvider).openRequest(Mockito.isA(WaveletName.class),
        Mockito.isA(List.class), Mockito.isA(ParticipantId.class), Mockito.isA(OpenRequestCallback.class));
    FragmentsFetcher fragmentsFetcher = mock(FragmentsFetcher.class);

    supplement = new SupplementImpl(new PrimitiveSupplementImpl());
    
    Injector injector = Guice.createInjector(new AbstractModule() {

      @Override
      protected void configure() {
        install(new TestExecutorsModule());
      }
    });
    
    clientFrontend = new ClientFrontendImpl(waveletProvider, fragmentsFetcher, new SupplementProvider() {

      @Override
      public Supplement getSupplement(WaveletName waveletName, ParticipantId participant, HashedVersion version, 
          Map<SegmentId, Interval> intervals) throws WaveServerException {
        return supplement;
      }

    }, new WaveletSubscriptions(), injector.getInstance(Key.get(Executor.class, 
      ExecutorAnnotations.WaveletLoadingExecutor.class)));
  }

  /********************************** Fetch wave view requests ************************************/

  public void testCannotFetchWaveViewWhenNotLoggedIn() throws Exception {
    FetchWaveViewRequestCallback callback = mock(FetchWaveViewRequestCallback.class);
    clientFrontend.fetchWaveViewRequest(null, WAVE_ID, IdFilters.ALL_IDS, false, -1, -1, -1, CONNECTION_ID, callback);
    verify(callback).onFailure(eq(ReturnCode.NOT_LOGGED_IN), anyString());
    Mockito.verifyNoMoreInteractions(callback);
  }

    /**
   * TODO(akaplanov)
   **
   * Tests that a snapshot matching the subscription filter is received.
   *
  @SuppressWarnings("unchecked")
  public void testSubscribedSnapshotRecieved() throws Exception {
    when(waveletProvider.getWaveletIds(WAVE_ID)).thenReturn(ImmutableSet.of(W1, W2));
    when(waveletProvider.checkExistence(WN1)).thenReturn(true);
    when(waveletProvider.checkExistence(WN2)).thenReturn(true);
    when(waveletProvider.getLastModifiedVersionAndTime(WN1)).thenReturn(Pair.of(V1, TIMESTAMP));
    when(waveletProvider.getLastModifiedVersionAndTime(WN2)).thenReturn(Pair.of(V1, TIMESTAMP));
    when(waveletProvider.checkAccessPermission(WN1, USER)).thenReturn(true);
    when(waveletProvider.checkAccessPermission(WN2, USER)).thenReturn(true);

    FetchWaveViewRequestCallback listener = mock(FetchWaveViewRequestCallback.class);
    clientFrontend.fetchWaveViewRequest(USER, WAVE_ID, IdFilters.ALL_IDS, false, CONNECTION_ID, listener);

    verify(listener).onWaveletSuccess(eq(W1), eq(TIMESTAMP), eq(V1), eq(Collections.EMPTY_MAP));
    verify(listener).onWaveletSuccess(eq(W2), eq(TIMESTAMP), eq(V1), eq(Collections.EMPTY_MAP));
    verify(listener).onFinish();
  }
*/
  
  /**
   * Tests that a snapshot not matching the subscription filter is not received.
   */
  @SuppressWarnings("unchecked")
  public void testUnsubscribedSnapshotNotRecieved() throws Exception {
    provideWavelet(WN1, V1);
    when(waveletProvider.checkAccessPermission(WN1, USER)).thenReturn(true);

    FetchWaveViewRequestCallback listener = mock(FetchWaveViewRequestCallback.class);
    clientFrontend.fetchWaveViewRequest(USER, WAVE_ID, IdFilter.ofPrefixes("non-existing"),
        false, -1, -1, -1, CONNECTION_ID, listener);

    verify(listener).onFailure(eq(ReturnCode.NOT_EXISTS), anyString());
  }

  /********************************** Fetch documents requests ************************************/

  public void testCannotFetchFragmentsWhenNotLoggedIn() throws Exception {
    FetchFragmentsRequestCallback listener = mock(FetchFragmentsRequestCallback.class);
    clientFrontend.fetchFragmentsRequest(null, WN1, VERSION_RANGE_1, -1, -1, CONNECTION_ID, listener);
    verify(listener).onFailure(eq(ReturnCode.NOT_LOGGED_IN), anyString());
  }

  /********************************* Wavelet channel life cycle ***********************************/

  public void testCannotOpenWaveletWhenNotLoggedIn() throws Exception {
    OpenChannelRequestCallback openListener = mock(OpenChannelRequestCallback.class);
    clientFrontend.openRequest(null, WN1, SEGMENT_IDS, KNOWN_V1, null, CONNECTION_ID, openListener, null);
    verify(openListener).onFailure(eq(ReturnCode.NOT_LOGGED_IN), anyString());
  }

  public void testTwoOpensReceiveDifferentChannelIds() throws Exception {
    provideWavelet(WN1, V1);
    when(waveletProvider.checkAccessPermission(WN1, USER)).thenReturn(true);

    OpenChannelRequestCallback listener1 = openWavelet(WN1, KNOWN_V1);
    String ch1 = verifyChannelId(listener1);

    OpenChannelRequestCallback listener2 = openWavelet(WN1, KNOWN_V1);
    String ch2 = verifyChannelId(listener2);

    assertFalse(ch1.equals(ch2));
  }

  public void testReceivedDeltasSentToClient() throws Exception {
    ReadableWaveletData snapshot = provideWavelet(WN1, V2);
    when(waveletProvider.checkAccessPermission(WN1, USER)).thenReturn(true);

    ChannelListener listener = openWavelet(WN1, Lists.newArrayList(V1, V2));
    verifyChannelId(listener);

    TransformedWaveletDelta delta = TransformedWaveletDelta.cloneOperations(USER, V3, TIMESTAMP,
        Arrays.asList(UTIL.noOp()));
    DeltaSequence deltas = DeltaSequence.of(delta);
    clientFrontend.waveletUpdate(WN1, deltas);

    verify(listener).onUpdate(eq(deltas), isNullHashedVersion());
  }

  public void testReceivedDeltasNotSentToClientAfterClose() throws Exception {
    ReadableWaveletData snapshot = provideWavelet(WN1, V1);
    when(waveletProvider.checkAccessPermission(WN1, USER)).thenReturn(true);

    ChannelListener listener = openWavelet(WN1, KNOWN_V1);
    String channel = verifyChannelId(listener);
    closeWavelet(channel);

    TransformedWaveletDelta delta = TransformedWaveletDelta.cloneOperations(USER, V2, TIMESTAMP,
        Arrays.asList(UTIL.noOp()));
    DeltaSequence deltas = DeltaSequence.of(delta);
    clientFrontend.waveletUpdate(WN1, deltas);

    verify(listener, never()).onUpdate(any(DeltaSequence.class), any(HashedVersion.class));
  }

  /**
   * Tests that submit requests are forwarded to the wavelet provider.
   */
  public void testSubmitForwardedToWaveletProvider() throws Exception {
    provideWavelet(WN1, V1);
    when(waveletProvider.getWaveletIds(WAVE_ID)).thenReturn(ImmutableSet.of(W1));
    when(waveletProvider.checkAccessPermission(WN1, USER)).thenReturn(true);

    OpenChannelRequestCallback listener = openWavelet(WN1, KNOWN_V1);
    String channelId = verifyChannelId(listener);

    SubmitRequestCallback submitListener = mock(SubmitRequestCallback.class);
    clientFrontend.submitRequest(USER, channelId, SERIALIZED_DELTA, submitListener);
    verify(waveletProvider).submitRequest(eq(WN1), eq(SERIALIZED_DELTA), any(SubmitRequestCallback.class));
    verifyZeroInteractions(submitListener);
  }

  public void testCannotSubmitAsDifferentUser() throws Exception {
    ParticipantId otherParticipant = new ParticipantId("another@example.com");
    provideWavelet(WN1, V1);
    OpenChannelRequestCallback listener = openWavelet(WN1, KNOWN_V1);
    String channelId = verifyChannelId(listener);

    SubmitRequestCallback submitListener = mock(SubmitRequestCallback.class);
    clientFrontend.submitRequest(otherParticipant, channelId, SERIALIZED_DELTA,
        submitListener);
    verify(submitListener).onFailure(eq(ReturnCode.NOT_LOGGED_IN), anyString());
    verify(submitListener, never()).onSuccess(anyInt(), any(HashedVersion.class), anyLong());
  }

  /******************************************* Diffs **********************************************/

  /**
   * TODO(akaplanov)
  @SuppressWarnings("unchecked")
  public void testOpenWaveFromLastReadState() throws Exception {
    WaveletData conversationSnapshot = provideWavelet(WN1, V1);

    // Provide old conversation snapshot
    WaveletData oldSnapshot = WaveletDataUtil.copyWavelet(conversationSnapshot);
    when(waveletProvider.getSnapshot(WN1, V1)).thenReturn(oldSnapshot);

    // Update conversation snapshot
    DELTA1.get(0).apply(conversationSnapshot);

    // Provide supplement snapshot
    supplement.setSeenVersion(W1, V1);

    // Provide access to wavelets
    when(waveletProvider.getWaveletIds(WAVE_ID)).thenReturn(ImmutableSet.of(W1));
    when(waveletProvider.checkAccessPermission(WN1, USER)).thenReturn(true);

    // Provide access to fragments
    ImmutableList.Builder<RawOperation> operations = ImmutableList.builder();
    for (WaveletOperation op : DELTA_RECORD1.getTransformedDelta()) {
      operations.add(new RawOperation(GsonSerializer.OPERATION_SERIALIZER,
          ImmutableList.of(op), op.getContext()));
    }
    final RawFragment fragment = new RawFragment(operations.build(), ImmutableList.<RawOperation>of());
    Mockito.doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        ((Receiver<Pair<SegmentId, RawFragment>>)invocation.getArguments()[2]).put(
          Pair.of(SegmentId.PARTICIPANTS_ID, fragment));
        return null;
      }
    }).when(waveletProvider).getIntervalsRawFragments(Mockito.isA(WaveletName.class),
        Mockito.isA(RawFragmentsRequest.class), Mockito.isA(Receiver.class));

    // Build diff for compare
    WaveletDiffData.Aggregator diffBuilder = new WaveletDiffData.Aggregator();
    diffBuilder.addDelta(DELTA1);
    diffBuilder.setLastModifiedVersion(DELTA1.getResultingVersion());
    diffBuilder.setLastModifiedTime(TIMESTAMP);
    WaveletDiffData diff = diffBuilder.build();

    // Open wavelet
    provideWavelet(WN1, V2);
    OpenChannelRequestCallback listener = openWavelet(WN1, Lists.newArrayList(V1));

    // Verify replyes
    verify(listener).onSuccess(anyString(), eq(ImmutableMap.of(SegmentId.PARTICIPANTS_ID, fragment)),
        eq(V1), eq(V2), eq(TIMESTAMP), eq(V0), isNullHashedVersion());
  }

  @SuppressWarnings("unchecked")
  public void testOpenPartiallyReadWave() throws Exception {
    WaveletData conversationSnapshot = provideWavelet(WN1, V1);

    // Provide old conversation snapshot
    WaveletData oldSnapshot = WaveletDataUtil.copyWavelet(conversationSnapshot);
    when(waveletProvider.getSnapshot(WN1, V1)).thenReturn(oldSnapshot);

    // Update conversation snapshot
    DELTA1.get(0).apply(conversationSnapshot);
    DELTA2.get(0).apply(conversationSnapshot);

    // Provide supplement snapshot
    supplement.markWaveletAsRead(W1, 1);
    supplement.markParticipantsAsRead(W1, 2);

    // Provide access to wavelets
    when(waveletProvider.getWaveletIds(WAVE_ID)).thenReturn(ImmutableSet.of(W1));
    when(waveletProvider.checkAccessPermission(WN1, USER)).thenReturn(true);

    // Provide access to operations
    ImmutableList.Builder<RawOperation> operations = ImmutableList.builder();
    for (WaveletOperation op : DELTA_RECORD1.getTransformedDelta()) {
      operations.add(new RawOperation(GsonSerializer.OPERATION_SERIALIZER,
          ImmutableList.of(op), op.getContext()));
    }
    for (WaveletOperation op : DELTA_RECORD2.getTransformedDelta()) {
      operations.add(new RawOperation(GsonSerializer.OPERATION_SERIALIZER,
          ImmutableList.of(op), op.getContext()));
    }
    final RawFragment fragment = new RawFragment(operations.build(),
        ImmutableList.<RawOperation>of());
    Mockito.doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        ((Receiver<Pair<SegmentId, RawFragment>>)invocation.getArguments()[2]).put(
          Pair.of(SegmentId.PARTICIPANTS_ID, fragment));
        return null;
      }
    }).when(waveletProvider).getRawFragments(Mockito.isA(WaveletName.class),
        Mockito.isA(RawFragmentsRequest.class), Mockito.isA(Receiver.class));

    // Build diff for compare
    WaveletDiffData.Aggregator diffAggregator = new WaveletDiffData.Aggregator();
    diffAggregator.addDelta(DELTA1);
    diffAggregator.addDelta(DELTA2);
    diffAggregator.setLastReadParticipantVersion(2);
    diffAggregator.setLastModifiedVersion(DELTA2.getResultingVersion());
    diffAggregator.setLastModifiedTime(TIMESTAMP);
    WaveletDiffData diff = diffAggregator.build();

    // Open wavelet
    provideWavelet(WN1, V3);
    OpenChannelRequestCallback listener = openWavelet(WN1, Lists.newArrayList(V1));

    // Verify replyes
    verify(listener).onSuccess(anyString(), eq(ImmutableMap.of(SegmentId.PARTICIPANTS_ID, fragment)),
        eq(V1), eq(V3), eq(TIMESTAMP), eq(V0), isNullHashedVersion());
  }
  */

  /**************************************** Tools functions ***************************************/

  private ChannelListener openWavelet(WaveletName waveletName,
      List<HashedVersion> knownVersions) {
    ChannelListener channelListener = mock(ChannelListener.class);
    clientFrontend.openRequest(USER, waveletName, SEGMENT_IDS, knownVersions, null, CONNECTION_ID,
        channelListener, channelListener);
    return channelListener;
  }

  private void closeWavelet(String channelId) {
    clientFrontend.closeRequest(USER, channelId);
  }

  private WaveletData provideWavelet(WaveletName name, HashedVersion version) throws WaveServerException,
      OperationException {
    WaveletData wavelet = WaveletDataUtil.createEmptyWavelet(name, USER, version, TIMESTAMP);
    when(waveletProvider.getSnapshot(name)).thenReturn(wavelet);
    when(waveletProvider.getWaveletIds(name.waveId)).thenReturn(ImmutableSet.of(name.waveletId));
    when(waveletProvider.getLastModifiedVersionAndTime(name)).thenReturn(Pair.of(version, TIMESTAMP));
    return wavelet;
  }

  private static String verifyChannelId(OpenChannelRequestCallback listener) {
    ArgumentCaptor<String> channelIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(listener).onSuccess(channelIdCaptor.capture(), any(Map.class), any(HashedVersion.class),
         any(HashedVersion.class), any(Long.class), any(HashedVersion.class), isNullHashedVersion());
    return channelIdCaptor.getValue();
  }

  private static HashedVersion isNullHashedVersion() {
    return (HashedVersion) Mockito.isNull();
  }

  private static ByteStringMessage<ProtocolAppliedWaveletDelta> makeAppliedDelta(
      HashedVersion targetVersion, TransformedWaveletDelta transformedDelta) {
    ProtocolWaveletDelta protocolDelta = OperationSerializer.serialize(transformedDelta);
    ByteStringMessage<ProtocolWaveletDelta> serializedDelta = ByteStringMessage.serializeMessage(protocolDelta);
    ProtocolSignedDelta.Builder signedDeltaBuilder = ProtocolSignedDelta.newBuilder();
    signedDeltaBuilder.setDelta(serializedDelta.getByteString());
    ProtocolSignedDelta signedDelta = signedDeltaBuilder.build();
    return AppliedDeltaUtil.buildAppliedDelta(signedDelta, targetVersion,
        transformedDelta.size(), transformedDelta.getApplicationTimestamp());
  }
}
