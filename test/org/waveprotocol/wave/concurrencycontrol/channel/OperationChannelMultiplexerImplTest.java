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

import org.waveprotocol.wave.common.logging.AbstractLogger;
import org.waveprotocol.wave.common.logging.PrintLogger;
import org.waveprotocol.wave.concurrencycontrol.channel.OperationChannelMultiplexer.KnownWavelet;
import org.waveprotocol.wave.concurrencycontrol.channel.OperationChannelMultiplexerImpl.LoggerContext;
import org.waveprotocol.wave.concurrencycontrol.common.ChannelException;
import org.waveprotocol.wave.concurrencycontrol.common.Recoverable;
import org.waveprotocol.wave.concurrencycontrol.common.UnsavedDataListener;
import org.waveprotocol.wave.concurrencycontrol.common.UnsavedDataListenerFactory;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.operation.wave.NoOp;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.testing.BasicFactories;
import org.waveprotocol.wave.model.testing.DeltaTestUtil;
import org.waveprotocol.wave.model.testing.FakeHashedVersionFactory;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.ImmediateExcecutionScheduler;
import org.waveprotocol.wave.model.util.Scheduler;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.impl.EmptyWaveletSnapshot;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletFragmentData;
import org.waveprotocol.wave.model.wave.data.impl.WaveletFragmentDataImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import junit.framework.TestCase;

/**
 * Test for the multiplexer.
 *
 * @author zdwang@google.com (David Wang)
 * @author anorth@google.com (Alex North)
 */

public class OperationChannelMultiplexerImplTest extends TestCase {

  // TODO(anorth): this really tests the whole client end of the stack.
  // Make some simpler tests just for the mux.

  /**
   * Holds information about a single channel from a mux.
   */
  private static final class ChannelInfo {
    public final WaveletId waveletId;
    public final long initialVersion;
    public final byte[] initialSignature;
    public ObservableWaveletFragmentData snapshot;
    public final HashedVersion initialHashedVersion;
    public final KnownWavelet knownWavelet;
    public final Accessibility accessibility;

    public ChannelInfo(WaveletId waveletId, long initialVersion, byte[] initialSignature) {
      this(waveletId, initialVersion, initialSignature, Accessibility.READ_WRITE);
    }

    public ChannelInfo(WaveletId waveletId, long initialVersion, byte[] initialSignature,
        Accessibility accessibility) {
      this.waveletId = waveletId;
      this.initialVersion = initialVersion;
      this.initialSignature = initialSignature;
      this.snapshot = createSnapshot(waveletId, initialVersion, initialSignature);
      this.initialHashedVersion = HashedVersion.of(initialVersion, initialSignature);
      this.knownWavelet = createKnownWavelet(snapshot, initialVersion, initialSignature, accessibility);
      this.accessibility = accessibility;
    }
  }

  private static final class ConnectedChannel {
    public final OperationChannel channel;
    public final MockOperationChannelListener listener;

    public ConnectedChannel(OperationChannel channel, MockOperationChannelListener listener) {
      this.channel = channel;
      this.listener = listener;
    }
  }

  private static final class MuxInfo {
    public final ViewChannel.Listener viewListener;
    public final List<ChannelInfo> channelsInfo = new ArrayList<>();
    public final List<ConnectedChannel> channels = new ArrayList<>();
    public final Map<WaveletId, List<HashedVersion>> newVersions = new HashMap<>();

    public MuxInfo(ViewChannel.Listener viewListener, List<ChannelInfo> chsInfo,
        List<ConnectedChannel> operationChannels) {
      this.viewListener = viewListener;
      this.channelsInfo.addAll(chsInfo);
      this.channels.addAll(operationChannels);
    }

    public Map<WaveletId, List<HashedVersion>> getReconnectVersions() {
      Map<WaveletId, List<HashedVersion>> reconnectVersions = new HashMap<>();
      for (ChannelInfo chInfo : channelsInfo) {
        ObservableWaveletData snapshot = chInfo.knownWavelet.snapshot;
        List<HashedVersion> waveletVersions = new ArrayList<>();
        waveletVersions.add(snapshot.getHashedVersion());
        List<HashedVersion> newWaveletVersions = newVersions.get(chInfo.waveletId);
        if (newWaveletVersions != null) {
          waveletVersions.addAll(newWaveletVersions);
        }
        reconnectVersions.put(chInfo.waveletId, waveletVersions);
      }
      return reconnectVersions;
    }
  }

  private static class FakeScheduler implements Scheduler {
    Scheduler.Command command;

    @Override
    public void reset() {
      // Do nothing
    }

    @Override
    public boolean schedule(Command command) {
      this.command = command;
      return true;
    }
  }

  /** IDs of wavelets for testing. */
  private static final WaveletId WAVELET_ID_1 = WaveletId.of("example.com","w+1234");
  private static final WaveletId WAVELET_ID_2 = WaveletId.of("example.com","w+5678");

  /** ID of wave for testing. */
  private static final WaveId WAVE_ID = WaveId.of("example.com", "waveId_1");

  /** User name for testing. */
  private static final ParticipantId USER_ID = ParticipantId.ofUnsafe("fred@example.com");

  private static final byte[] NOSIG = new byte[0];
  private static final byte[] SIG1 = new byte[] { 1, 1, 1, 1 };
  private static final byte[] SIG2 = new byte[] { 2, 2, 2, 2 };
  private static final byte[] SIG3 = new byte[] { 3, 3, 3, 3 };
  private static final byte[] SIG4 = new byte[] { 4, 4, 4, 4 };
  private static final byte[] SIG5 = new byte[] { 5, 5, 5, 5 };
  private static final byte[] SIG6 = new byte[] { 6, 6, 6, 6 };
  private static final byte[] SIG7 = new byte[] { 7, 7, 7, 7 };


  private static final AbstractLogger logger = new PrintLogger();

  private static final LoggerContext LOGGERS = new LoggerContext(logger, logger, logger, logger);
  private static final DeltaTestUtil testUtil = new DeltaTestUtil(USER_ID);
  private static final WaveletFragmentDataImpl.Factory DATA_FACTORY =
      BasicFactories.waveletFragmentDataImplFactory();

  private OperationChannelMultiplexerImpl mux;
  private MockViewChannel viewChannel;
  private MockMuxListener muxListener;

  @Override
  public void setUp() {
    ViewChannelImpl.setMaxViewChannelsPerWave(Integer.MAX_VALUE);
    viewChannel = new MockViewChannel();
    UnsavedDataListenerFactory fakeListenerFactory = new UnsavedDataListenerFactory() {

      @Override
      public UnsavedDataListener create(WaveletId waveletId) {
        return null;
      }

      @Override
      public void destroy(WaveletId waveletId) {
      }
    };
    mux = new OperationChannelMultiplexerImpl(WAVE_ID, viewChannel, DATA_FACTORY, LOGGERS,
        fakeListenerFactory, new ImmediateExcecutionScheduler(), FakeHashedVersionFactory.INSTANCE);
    muxListener = new MockMuxListener();
  }

  public void testOpenMuxOpensViewChannelAndOperationChannels() throws ChannelException {
    final ChannelInfo chInfo = new ChannelInfo(WAVELET_ID_1, 1, SIG1);

    MuxInfo muxInfo = openMux(chInfo);

    viewChannel.checkExpectationsSatisified();
    muxListener.verifyNoMoreInteractions();
  }

  public void testOpReceivedOnChannel() throws ChannelException {
    final ChannelInfo chInfo = new ChannelInfo(WAVELET_ID_1, 1, SIG1);
    final int serverOps = 1;
    final byte[] finalSignature = SIG2;

    MuxInfo muxInfo = openMux(chInfo);

    // Receive a delta.
    checkReceiveDelta(muxInfo.viewListener, muxInfo.channels.get(0).channel,
        muxInfo.channels.get(0).listener, WAVELET_ID_1,
        chInfo.initialVersion, serverOps, finalSignature);

    viewChannel.checkExpectationsSatisified();
    muxListener.verifyNoMoreInteractions();
  }

  public void testChannelSendSubmitsToView() throws ChannelException {
    final ChannelInfo chInfo = new ChannelInfo(WAVELET_ID_1, 1, SIG1);

    MuxInfo muxInfo = openMux(chInfo);

    // Send an operation and check view submission
    WaveletOperation op = createOp();
    WaveletDelta delta = createDelta(chInfo.initialHashedVersion, op);
    viewChannel.expectSubmitDelta(WAVELET_ID_1, delta);
    muxInfo.channels.get(0).channel.send(op);

    viewChannel.checkExpectationsSatisified();
    muxListener.verifyNoMoreInteractions();
  }

  public void testAckResultsInChannelOp() throws ChannelException {
    final ChannelInfo chInfo = new ChannelInfo(WAVELET_ID_1, 1, SIG1);
    final byte[] finalSignature = SIG2;

    MuxInfo muxInfo = openMux(chInfo);

    checkSendDelta(viewChannel, muxInfo.channels.get(0).channel, chInfo.initialHashedVersion,
        WAVELET_ID_1);
    checkAckDelta(viewChannel, muxInfo.channels.get(0).channel,
        muxInfo.channels.get(0).listener, 1, 2, finalSignature);

    viewChannel.checkExpectationsSatisified();
    muxListener.verifyNoMoreInteractions();
  }

  public void testMultipleChannelsAreIndependent() throws ChannelException {
    final ChannelInfo chInfo1 = new ChannelInfo(WAVELET_ID_1, 1, SIG1);
    final ChannelInfo chInfo2 = new ChannelInfo(WAVELET_ID_2, 20, SIG2);

    MuxInfo muxInfo = openMux(chInfo1, chInfo2);

    // Check channels receive ops independently.
    final int serverOps1 = 5;
    ConnectedChannel ch1 = muxInfo.channels.get(0);
    ConnectedChannel ch2 = muxInfo.channels.get(1);
    checkReceiveDelta(muxInfo.viewListener, ch1.channel, ch1.listener,
        WAVELET_ID_1, chInfo1.initialVersion, serverOps1, SIG4);
    ch2.listener.checkOpsReceived(0);

    final int serverOps2 = 7;
    checkReceiveDelta(muxInfo.viewListener, ch2.channel, ch2.listener, WAVELET_ID_2, chInfo2.initialVersion,
        serverOps2, SIG5);
    ch1.listener.checkOpsReceived(0);

    // Check channels send ops independently.
    checkSendDelta(viewChannel, ch1.channel, HashedVersion.of(chInfo1.initialVersion + serverOps1, SIG4),
        WAVELET_ID_1);
    checkSendDelta(viewChannel, ch2.channel, HashedVersion.of(chInfo2.initialVersion + serverOps2, SIG5),
        WAVELET_ID_2);

    // Check acks are received independently.
    final byte[] ackSignature1 = SIG6;
    final byte[] ackSignature2 = SIG7;
    final long ackVersion1 = chInfo1.initialVersion + serverOps1 + 1;
    final long ackVersion2 = chInfo2.initialVersion + serverOps2 + 1;
    checkAckDelta(viewChannel, ch1.channel, ch1.listener, 1, ackVersion1, ackSignature1);
    checkAckDelta(viewChannel, ch2.channel, ch2.listener, 1, ackVersion2, ackSignature2);

    viewChannel.checkExpectationsSatisified();
    muxListener.verifyNoMoreInteractions();
  }

  public void testMuxCloseClosesViewAndChannels() throws ChannelException {
    final ChannelInfo chInfo1 = new ChannelInfo(WAVELET_ID_1, 1, SIG1);
    final ChannelInfo chInfo2 = new ChannelInfo(WAVELET_ID_2, 20, SIG2);

    MuxInfo muxInfo = openMux(chInfo1, chInfo2);

    viewChannel.expectClose();
    mux.close();

    // Receive lagging delta from view channel, expect nothing.
    final List<TransformedWaveletDelta> update = createServerDeltaList(1, 1, SIG4);
    muxInfo.viewListener.onUpdate(chInfo1.waveletId, update, null);
    muxInfo.channels.get(0).listener.checkOpsReceived(0);

    viewChannel.checkExpectationsSatisified();
    muxListener.verifyNoMoreInteractions();
  }

  public void testSendingOfOperationWaitsForReconnection() throws ChannelException {
    long knownVersion = 40;
    byte[] knownSig = SIG1;

    final ChannelInfo chInfo1 = new ChannelInfo(WAVELET_ID_1, knownVersion, knownSig);
    MuxInfo muxInfo = openMux(false, chInfo1);

    // Attempt to send a client op. Submission should be held until the view
    // connects.
    WaveletOperation clientOp = createOp();
    WaveletDelta delta = createDelta(HashedVersion.of(knownVersion, knownSig), clientOp);
    ConnectedChannel ch = muxInfo.channels.get(0);
    ch.channel.send(clientOp);
    ch.listener.checkOpsReceived(0);

    // Reconnect the channel, expect the client delta submission.
    viewChannel.expectSubmitDelta(WAVELET_ID_1, delta);
    connectChannel(muxInfo.viewListener, chInfo1);

    checkAckDelta(viewChannel, ch.channel, ch.listener, 1, knownVersion + 1, SIG2);

    // No snapshot, but check we can receive and send deltas on the channel.
    checkReceiveAndSend(muxInfo.viewListener, viewChannel, ch, WAVELET_ID_1, knownVersion + 1);

    viewChannel.checkExpectationsSatisified();
    muxListener.verifyNoMoreInteractions();
  }

  public void testMuxReconnectsAfterDisconnect() throws ChannelException {
    final ChannelInfo chInfo1 = new ChannelInfo(WAVELET_ID_1, 1, SIG1);
    final ChannelInfo chInfo2 = new ChannelInfo(WAVELET_ID_2, 20, SIG2);

    // First open.
    MuxInfo muxInfo = openMux(chInfo1, chInfo2);

    // Disconnecting of mux initiates reopening of view channel.
    reopenMux(muxInfo);

    // Don't expect the mux nor any channel to know about reconnection.
    muxListener.verifyNoMoreInteractions();
    ConnectedChannel ch1 = muxInfo.channels.get(0);
    ConnectedChannel ch2 = muxInfo.channels.get(1);
    ch1.listener.checkOpsReceived(0);
    ch2.listener.checkOpsReceived(0);

    // Check we can still receive and send deltas.
    checkReceiveAndSend(muxInfo.viewListener, viewChannel, ch1, WAVELET_ID_1, chInfo1.initialVersion);
    checkReceiveAndSend(muxInfo.viewListener, viewChannel, ch2, WAVELET_ID_2, chInfo2.initialVersion);

    viewChannel.checkExpectationsSatisified();
    muxListener.verifyNoMoreInteractions();
  }

  private static enum KnownWaveletDisconnectWhen {
    BEFORE_VIEW_CONNECTED,
    AFTER_VIEW_CONNECTED,
    AFTER_CHANNELS_OPENED,
  }

  /**
   * Tests that a mux reconnects with known wavelets if the view
   * channel fails before it opens.
   */
  public void testMuxReconnectsKnownWaveletBeforeViewConnected() throws ChannelException {
    doTestMuxReconnectsKnownWavelet(KnownWaveletDisconnectWhen.BEFORE_VIEW_CONNECTED);
  }

  /**
   * Tests that a mux reconnects with known wavelets if the view channel fails
   * after the channel id but before the operation channels opened.
   */
  public void testMuxReconnectsKnownWaveletAfterViewConnected() throws ChannelException {
    doTestMuxReconnectsKnownWavelet(KnownWaveletDisconnectWhen.AFTER_VIEW_CONNECTED);
  }

  /**
   * Tests that a mux reconnects with known wavelets if the view channel fails
   * after the operation channels opened.
   */
  public void testMuxReconnectsKnownWaveletAfterChannelsOpened() throws ChannelException {
    doTestMuxReconnectsKnownWavelet(KnownWaveletDisconnectWhen.AFTER_CHANNELS_OPENED);
  }

  /**
   * Helps test that a mux reconnects with known wavelets if the view channel
   * fails during reconnection.
   */
  private void doTestMuxReconnectsKnownWavelet(KnownWaveletDisconnectWhen when)
      throws ChannelException {
    ChannelInfo chInfo = new ChannelInfo(WAVELET_ID_1, 1, SIG1);

    MuxInfo muxInfo = openMux(false, chInfo);

    if (when.compareTo(KnownWaveletDisconnectWhen.AFTER_VIEW_CONNECTED) >= 0) {
      triggerAndCheckConnected(muxInfo.viewListener);
    }

    if (when.compareTo(KnownWaveletDisconnectWhen.AFTER_CHANNELS_OPENED) >= 0) {
      connectChannel(muxInfo.viewListener, chInfo);
      triggerAndCheckConnected(muxInfo.viewListener);
    }
    viewChannel.checkExpectationsSatisified();

    // Fail view, expect reconnection.
    reconnectViewAndCheckEverythingStillWorks(muxInfo);
    muxListener.verifyNoMoreInteractions();
  }

  private static enum NewWaveletDisconnectWhen {
    BEFORE_VIEW_CONNECTED,
    AFTER_VIEW_CONNECTED,
    AFTER_SEND_DELTA,
    AFTER_ACK_DELTA,
  }

  /**
   * Tests that a mux reconnects with a new version-zero channel if the view
   * channel fails before it opens.
   */
  public void testMuxReconnectsNewWaveletBeforeViewConnected() throws ChannelException {
    doTestMuxReconnectsNewWavelet(NewWaveletDisconnectWhen.BEFORE_VIEW_CONNECTED);
  }

  /**
   * Tests that the mux reconnects at version zero when the server fails before
   * the client sends any ops on a wavelet.
   */
  public void testMuxReconnectsNewWaveletAfterViewConnected() throws ChannelException {
    doTestMuxReconnectsNewWavelet(NewWaveletDisconnectWhen.AFTER_VIEW_CONNECTED);
  }

  /**
   * Tests that the mux reconnects at version zero when the server fails and
   * loses the client's version-zero delta before acknowledging it, forgetting
   * the wavelet entirely.
   */
  public void testMuxReconnectsNewWaveletAfterFirstSend() throws ChannelException {
    doTestMuxReconnectsNewWavelet(NewWaveletDisconnectWhen.AFTER_SEND_DELTA);
  }

  /**
   * Tests that the mux reconnects at version zero when the server fails and
   * loses the client's version-zero delta, forgetting the wavelet entirely.
   */
  public void testMuxReconnectsNewWaveletWhenServerLosesFirstDelta() throws ChannelException {
    doTestMuxReconnectsNewWavelet(NewWaveletDisconnectWhen.AFTER_ACK_DELTA);
  }

  /**
   * Helps test that the mux reconnects at version zero for a locally-created
   * channel.
   */
  private void doTestMuxReconnectsNewWavelet(NewWaveletDisconnectWhen when)
      throws ChannelException {
    ChannelInfo chInfo = new ChannelInfo(WAVELET_ID_1, 0, NOSIG);
    byte[] deltaSig = SIG1;

    MuxInfo muxInfo = openMux(false);

    if (when.compareTo(NewWaveletDisconnectWhen.AFTER_VIEW_CONNECTED) >= 0) {
      triggerAndCheckConnected(muxInfo.viewListener);
    }

    ConnectedChannel ch = createOperationChannel(WAVELET_ID_1, USER_ID);
    muxInfo.channelsInfo.add(chInfo);
    muxInfo.channels.add(ch);
    connectChannel(muxInfo.viewListener, chInfo);

    WaveletDelta clientDelta = null;
    if (when.compareTo(NewWaveletDisconnectWhen.AFTER_SEND_DELTA) >= 0) {
      clientDelta = checkSendDelta(viewChannel, ch.channel, chInfo.initialHashedVersion,
          chInfo.waveletId);
    }

    if (when.compareTo(NewWaveletDisconnectWhen.AFTER_ACK_DELTA) >= 0) {
      checkAckDelta(viewChannel,  ch.channel, ch.listener, 1, chInfo.initialVersion + 1, deltaSig);
      chInfo.snapshot = createSnapshot(chInfo.waveletId, chInfo.initialVersion + 1, deltaSig);
      muxInfo.newVersions.put(WAVELET_ID_1, CollectionUtils.newLinkedList(HashedVersion.of(1, deltaSig)));
    }

    if (when.compareTo(NewWaveletDisconnectWhen.AFTER_SEND_DELTA) >= 0) {
      // Expect the client delta to be resubmitted.
      Map<WaveletId, WaveletDelta> unacknowledgetDeltas = null;
      if (when.compareTo(NewWaveletDisconnectWhen.AFTER_ACK_DELTA) < 0) {
        unacknowledgetDeltas = new HashMap<>();
        unacknowledgetDeltas.put(chInfo.waveletId, clientDelta);
      }
      reopenMux(muxInfo, unacknowledgetDeltas, null, true);
    } else {
      reopenMux(muxInfo);
    }

    viewChannel.checkExpectationsSatisified();

    if (when.compareTo(NewWaveletDisconnectWhen.AFTER_SEND_DELTA) >= 0) {
      if (when.compareTo(NewWaveletDisconnectWhen.AFTER_ACK_DELTA) < 0) {
        // (Re-)ack the resubmitted delta and check the channel works.
        checkAckDelta(viewChannel, ch.channel, ch.listener, 1, chInfo.initialVersion + 1, deltaSig);
      }
      checkReceiveAndSend(muxInfo.viewListener, viewChannel, ch, chInfo.waveletId, chInfo.initialVersion + 1);
    } else {
      // Check we can send on then channel.
      checkReceiveAndSend(muxInfo.viewListener, viewChannel, ch, chInfo.waveletId, chInfo.initialVersion);
    }
  }

  public void testMuxReconnectUsingScheduler() throws ChannelException {
    final ChannelInfo chInfo1 = new ChannelInfo(WAVELET_ID_1, 1, SIG1);

    FakeScheduler scheduler = new FakeScheduler();
    viewChannel = new MockViewChannel();
    mux = new OperationChannelMultiplexerImpl(WAVE_ID, viewChannel, DATA_FACTORY,
        LOGGERS, null, scheduler, FakeHashedVersionFactory.INSTANCE);

    MuxInfo muxInfo = openMux(chInfo1);
    muxListener.verifyNoMoreInteractions();

    // Send but don't ack delta.
    WaveletDelta delta =
        checkSendDelta(viewChannel, muxInfo.channels.get(0).channel,
          chInfo1.initialHashedVersion, WAVELET_ID_1);

    // Reconnect channel.
    Map<WaveletId, WaveletDelta> unacknowledgetDeltas = new HashMap<>();
    unacknowledgetDeltas.put(chInfo1.waveletId, delta);
    reopenMux(muxInfo, unacknowledgetDeltas, scheduler, true);
  }

  public void testMuxReconnectsAgainAfterReconnectFailure() throws ChannelException {
    final ChannelInfo chInfo = new ChannelInfo(WAVELET_ID_1, 1, SIG1);

    MuxInfo muxInfo = openMux(chInfo);

    // Disconnect, expect reconnection.
    reopenMux(muxInfo, null, null, false);

    // Disconnect, expect reconnection again.
    reopenMux(muxInfo, null, null, false);

    muxListener.verifyNoMoreInteractions();
  }

  private static ObservableWaveletFragmentData createSnapshot(WaveletId waveletId, final long version,
      final byte[] signature) {
    final HashedVersion hv = HashedVersion.of(version, signature);
    return DATA_FACTORY.create(new EmptyWaveletSnapshot(WAVE_ID, waveletId, USER_ID, hv,
        1273307837000L));
  }

  private static KnownWavelet createKnownWavelet(ObservableWaveletFragmentData snapshot, long version,
      byte[] signature, Accessibility accessibility) {
    HashedVersion commitVersion = HashedVersion.of(version, signature);
    return new KnownWavelet(snapshot, commitVersion, accessibility);
  }

  private static List<TransformedWaveletDelta> createServerDeltaList(long version, int numOps,
      byte[] signature) {
    TransformedWaveletDelta delta =
        testUtil.makeTransformedDelta(0L, HashedVersion.of(version + numOps, signature), numOps);
    return Collections.singletonList((TransformedWaveletDelta)delta);
  }

  private static NoOp createOp() {
    return testUtil.noOp();
  }

  private static WaveletDelta createDelta(HashedVersion targetVersion, WaveletOperation... ops) {
    return new WaveletDelta(USER_ID, targetVersion, Arrays.asList(ops));
  }

  private static Map<WaveletId, List<HashedVersion>> createKnownVersions(
      WaveletId waveletId, long version, byte[] hash) {
    return CollectionUtils.immutableMap(waveletId, Collections.singletonList(
        HashedVersion.of(version, hash)));
  }

  private static Map<WaveletId, List<HashedVersion>> createKnownVersions(
      WaveletId waveletId1, long version1, byte[] hash1, WaveletId waveletId2,
      long version2, byte[] hash2) {
    assertFalse(waveletId1.equals(waveletId2));
    return CollectionUtils.immutableMap(
        waveletId1, Collections.singletonList(HashedVersion.of(version1, hash1)),
        waveletId2, Collections.singletonList(HashedVersion.of(version2, hash2)));
  }

  private static void checkReceiveDelta(ViewChannel.Listener viewListener,
      OperationChannel opChannel, MockOperationChannelListener opChannelListener,
      WaveletId waveletId, long version, int numOps, byte[] signature) throws ChannelException {
    // Receive delta from view channel, expect ops on op channel.
    final List<TransformedWaveletDelta> update = createServerDeltaList(version, numOps, signature);
    viewListener.onUpdate(waveletId, update, null);
    opChannelListener.checkOpsReceived(1);
    opChannelListener.clear();
    for (int i = 0; i < numOps; ++i) {
      assertNotNull(opChannel.receive());
    }
    assertNull(opChannel.receive());
  }

  /**
   * Sends an operation on an operation channel and expects the delta to
   * be submitted to the view.
   */
  private static WaveletDelta checkSendDelta(MockViewChannel viewChannel,
      OperationChannel opChannel, HashedVersion initialVersion, WaveletId expectedWaveletId)
      throws ChannelException {
    WaveletOperation op = createOp();
    WaveletDelta delta = createDelta(initialVersion, op);
    viewChannel.expectSubmitDelta(expectedWaveletId, delta);
    opChannel.send(op);
    return delta;
  }

  /**
   * Acks a delta and checks that the fake version-incrementing op is received
   * from the operation channel.
   */

  private static void checkAckDelta(MockViewChannel viewChannel, OperationChannel opChannel,
      MockOperationChannelListener opChannelListener, int ackedOps, long version, byte[] signature)
      throws ChannelException {
    viewChannel.ackSubmit(ackedOps, version, signature, 0);
    opChannelListener.checkOpsReceived(1);
    opChannelListener.clear();
    assertNotNull(opChannel.receive());
    opChannelListener.checkOpsReceived(0);
    opChannelListener.clear();
  }

  /**
   * Receives a one-op delta and sends/acks another, checking expectations.
   */
  private static void checkReceiveAndSend(ViewChannel.Listener viewListener, MockViewChannel
      viewChannel, ConnectedChannel ch, WaveletId waveletId, long version)
      throws ChannelException {
    checkReceiveDelta(viewListener, ch.channel, ch.listener, waveletId, version, 1, SIG2);
    checkSendDelta(viewChannel, ch.channel, HashedVersion.of(version + 1, SIG2), waveletId);
    checkAckDelta(viewChannel, ch.channel, ch.listener, 1, version + 2, SIG3);
  }

  /**
   * Opens a new mux and returns the created view mock.
   */
  private MuxInfo openMux(ChannelInfo... chsInfo) throws ChannelException {
    return openMux(true, chsInfo);
  }

  private MuxInfo openMux(boolean doConnect, ChannelInfo... chsInfo) throws ChannelException {
    List<KnownWavelet> knownWavelets = new ArrayList<>();
    for (ChannelInfo chInfo : chsInfo) {
      knownWavelets.add(chInfo.knownWavelet);
    }
    Map<WaveletId, List<HashedVersion>> expectedWavelets =
        OperationChannelMultiplexerImpl.getSignaturesFromWavelets(knownWavelets);
    viewChannel.expectOpen(expectedWavelets, Collections.EMPTY_MAP, null);
    mux.setChannelsPresenceListener(muxListener);
    mux.open(knownWavelets, Collections.EMPTY_MAP, muxListener);
    ViewChannel.Listener viewListener = viewChannel.takeListener();
    List<ConnectedChannel> channels = new ArrayList<ConnectedChannel>();
    for (ChannelInfo chInfo : chsInfo) {
      OperationChannel channel = muxListener.verifyOperationChannelCreated(chInfo.snapshot,
          chInfo.accessibility);
      MockOperationChannelListener listener = new MockOperationChannelListener();
      channel.setListener(listener);
      if (doConnect) {
        connectChannel(viewListener, chInfo);
      }
      channels.add(new ConnectedChannel(channel, listener));
    }
    muxListener.verifyNoMoreInteractions();
    if (doConnect) {
      triggerAndCheckConnected(viewListener);
    }
    return new MuxInfo(viewListener, CollectionUtils.newLinkedList(chsInfo), channels);
  }

  private void reopenMux(MuxInfo muxInfo) throws ChannelException {
    reopenMux(muxInfo, null, null, true);
  }

  private void reopenMux(MuxInfo muxInfo, Map<WaveletId, WaveletDelta> unacknowledgedDeltas,
      FakeScheduler scheduler, boolean doConnect) throws ChannelException {
    viewChannel.expectDisconnect();
    if (scheduler != null) {
      assertNull(scheduler.command);
    }
    mux.disconnect();
    
    viewChannel.expectOpen(muxInfo.getReconnectVersions(), Collections.EMPTY_MAP, unacknowledgedDeltas);
    if (unacknowledgedDeltas != null) {
      for (Entry<WaveletId, WaveletDelta> entry : unacknowledgedDeltas.entrySet()) {
        viewChannel.expectSubmitDelta(entry.getKey(), entry.getValue());
      }
    }
    mux.reopen(Collections.EMPTY_MAP);
    if (scheduler != null) {
      assertNotNull(scheduler.command);
      scheduler.command.execute();
    }

    if (doConnect) {
      connectChannels(muxInfo);
      triggerAndCheckConnected(muxInfo.viewListener);
    }
    viewChannel.checkExpectationsSatisified();
  }

  /**
   * Reconnects a view and checks that the channel is usable and expectations are satisfied.
   */
  private void reconnectViewAndCheckEverythingStillWorks(MuxInfo muxInfo) throws ChannelException {
    // Perform the reconnect.
    reopenMux(muxInfo);

    // Check everything still works.
    for (int i=0; i < muxInfo.channelsInfo.size(); i++) {
      checkReceiveAndSend(muxInfo.viewListener, viewChannel,
          muxInfo.channels.get(i), WAVELET_ID_1,
          muxInfo.channelsInfo.get(i).initialVersion);

    }
    viewChannel.checkExpectationsSatisified();
  }

  private ConnectedChannel createOperationChannel(WaveletId waveletId, ParticipantId address) {
    mux.createOperationChannel(waveletId, address);
    OperationChannel channel = muxListener.verifyOperationChannelCreated(createSnapshot(
        WAVELET_ID_1, 0, NOSIG), Accessibility.READ_WRITE);
    MockOperationChannelListener channelListener = new MockOperationChannelListener();
    channel.setListener(channelListener);
    return new ConnectedChannel(channel, channelListener);
  }

  private static void connectChannels(MuxInfo muxInfo) throws ChannelException {
    for (ChannelInfo chInfo : muxInfo.channelsInfo) {
      connectChannel(muxInfo.viewListener, chInfo);
    }
  }

  private static void connectChannel(ViewChannel.Listener viewListener, ChannelInfo chInfo)
      throws ChannelException {
    viewListener.onWaveletOpened(chInfo.waveletId, chInfo.snapshot.getHashedVersion(),
        chInfo.snapshot.getHashedVersion(), 0L, chInfo.initialHashedVersion, null, Collections.EMPTY_MAP);
  }

  private void triggerAndCheckConnected(ViewChannel.Listener viewListener)
      throws ChannelException {
    viewListener.onConnected();
    checkOpenFinished();
  }

  private void checkOpenFinished() {
    muxListener.verifyConnected();
  }
}
