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
import org.waveprotocol.wave.clientserver.ReturnCode;
import org.waveprotocol.wave.clientserver.ReturnStatus;
import org.waveprotocol.wave.concurrencycontrol.common.UnsavedDataListener;
import org.waveprotocol.wave.concurrencycontrol.common.UnsavedDataListenerFactory;
import org.waveprotocol.wave.concurrencycontrol.testing.MockWaveViewService;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.operation.wave.AddParticipant;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.testing.BasicFactories;
import org.waveprotocol.wave.model.testing.FakeHashedVersionFactory;
import org.waveprotocol.wave.model.util.ImmediateExcecutionScheduler;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.impl.EmptyWaveletSnapshot;

import com.google.gwt.dev.util.collect.Lists;
import java.util.Collections;
import junit.framework.TestCase;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletFragmentData;
import org.waveprotocol.wave.model.wave.data.impl.EmptyWaveletFragmentSnapshot;
import org.waveprotocol.wave.model.wave.data.impl.WaveletFragmentDataImpl;

/**
 * Tests that the CC stack provides correct unsaved data info.
 *
 * @author jochen@google.com (Jochen Bekmann)
 * @author anorth@google.com (Alex North)
 * @author akaplanov@gmail.com (A. Kaplanov)
 */

public class UnsavedDataUpdateTest extends TestCase {

  private static final class FakeUnsavedDataListener implements UnsavedDataListener {
    public int unacknowledgedSize = 0;
    public int uncommittedSize;
    public int inFlight = 0;
    public int closeCalls = 0;
    private long lastAckVersion = 0;
    private long lastCommitVersion = 0;

    @Override
    public void onUpdate(UnsavedDataInfo unsavedDataInfo) {
      this.unacknowledgedSize = unsavedDataInfo.estimateUnacknowledgedSize();
      this.uncommittedSize = unsavedDataInfo.estimateUncommittedSize();
      this.inFlight = unsavedDataInfo.inFlightSize();
      this.lastAckVersion = unsavedDataInfo.lastAckVersion();
      this.lastCommitVersion = unsavedDataInfo.lastCommitVersion();
    }

    @Override
    public synchronized void onClose(boolean everythingCommitted) {
      ++closeCalls;
    }
  }

  /** ID of wavelets for testing. */
  private static final WaveletId WAVELET_ID_1 = WaveletId.of("example.com", "conv+1");
  private static final WaveletId WAVELET_ID_2 = WaveletId.of("example.com", "conv+2");

  private static final byte[] SIGNATURE1 = new byte[] { 1, 1, 1, 1 };
  private static final byte[] SIGNATURE2 = new byte[] { 2, 2, 2, 2 };
  private static final byte[] SIGNATURE3 = new byte[] { 3, 3, 3, 3 };

  /** ID of wave for testing. */
  private static final WaveId WAVE_ID = WaveId.of("example.com", "w+1");

  /** Channel Id for testing. */
  private static final String CHANNEL_ID = "ch1";

  /** User name for testing. */
  private static final ParticipantId USER_NAME = new ParticipantId("example@example.com");

  private static final AbstractLogger logger = new PrintLogger();

  private static final LoggerContext LOGGERS = new LoggerContext(logger, logger, logger, logger);

  private static final WaveletFragmentDataImpl.Factory DATA_FACTORY =
      BasicFactories.waveletFragmentDataImplFactory();

  private static final ObservableWaveletFragmentData NEW_SNAPSHOT = createSnapshot(WAVELET_ID_1, 0,
      new byte[0]);
  private static final KnownWavelet NEW_WAVELET =
      new KnownWavelet(NEW_SNAPSHOT, HashedVersion.unsigned(0), Accessibility.READ_WRITE);

  private static final ObservableWaveletFragmentData SNAPSHOT1 = createSnapshot(WAVELET_ID_1, 1, SIGNATURE1);
  private static final KnownWavelet KNOWN_WAVELET1 =
      new KnownWavelet(SNAPSHOT1, HashedVersion.unsigned(0), Accessibility.READ_WRITE);
  private static final ObservableWaveletFragmentData SNAPSHOT2 = createSnapshot(WAVELET_ID_2, 1, SIGNATURE1);
  private static final KnownWavelet KNOWN_WAVELET2 =
      new KnownWavelet(SNAPSHOT2, HashedVersion.unsigned(0), Accessibility.READ_WRITE);

  private static final long TIMESTAMP = 12345;

  private OperationChannelMultiplexerImpl mux;
  private MockWaveViewService waveViewService;
  private MockMuxListener muxListener;
  private FakeUnsavedDataListener fakeUnsavedDataListener;

  @Override
  protected void setUp() {
    ViewChannelImpl.setMaxViewChannelsPerWave(Integer.MAX_VALUE);
    waveViewService = new MockWaveViewService();
    ViewChannelImpl viewChannel = new ViewChannelImpl(WAVE_ID, waveViewService, 
      new ViewChannel.IndexingCallback() {

      @Override
      public void onIndexing(long totalVersions, long indexedVersions) {
      }

      @Override
      public void onIndexingComplete() {
      }
    }, logger);
    fakeUnsavedDataListener = new FakeUnsavedDataListener();
    mux = new OperationChannelMultiplexerImpl(WAVE_ID, viewChannel, DATA_FACTORY, LOGGERS,
        new UnsavedDataListenerFactory() {

          @Override
          public UnsavedDataListener create(WaveletId dummy) {
            return fakeUnsavedDataListener;
          }

          @Override
          public void destroy(WaveletId waveletId) {
          }
        },
        new ImmediateExcecutionScheduler(), FakeHashedVersionFactory.INSTANCE);
    muxListener = new MockMuxListener();
  }

  /**
   * Create a wave, send Op which is unacked and expect unsaved data.
   */
  public void testCreatingWave() throws ChannelException {
    // Connect to the server
    mux.setChannelsPresenceListener(muxListener);
    mux.open(Lists.create(NEW_WAVELET), Collections.EMPTY_MAP, muxListener);
    OperationChannel channel = muxListener.verifyOperationChannelCreated(NEW_SNAPSHOT,
        Accessibility.READ_WRITE);
    muxListener.verifyNoMoreInteractions();

    channel.send(createAddParticipantOp());

    // Expect the delta channel to not be ready, so we expect an open call, but
    // no success on open and no submit call going through.
    assertEquals(1, waveViewService.opens.size());
    assertEquals(0, waveViewService.submits.size());
    muxListener.verifyNoMoreInteractions();

    // We do expect unsaved data.
    assertUnsavedDataInfo(1, 1, 0, 0, 0);

    // Now bail out of the connection with an op pending, should close.
    mux.close();
    assertEquals(1, fakeUnsavedDataListener.closeCalls);
  }

  /**
   * Open a wave; send an Op; wait for the ack; wait for the commit with no outstanding ops.
   */
  public void testCommitNotification() throws ChannelException {
    // Connect to the server
    mux.setChannelsPresenceListener(muxListener);
    mux.open(Lists.create(KNOWN_WAVELET1), Collections.EMPTY_MAP, muxListener);
    OperationChannel channel = muxListener.verifyOperationChannelCreated(SNAPSHOT1,
        Accessibility.READ_WRITE);

    assertEquals(1, waveViewService.opens.size());
    WaveViewService.OpenChannelStreamCallback openCallback = waveViewService.lastOpen().callback;

    // Pretend we got the initial snapshot at version 1 signature1.
    muxListener.verifyNoMoreInteractions();
    openCallback.onWaveletOpen(CHANNEL_ID, HashedVersion.of(1L, SIGNATURE1),
        HashedVersion.of(1L, SIGNATURE1), 0, HashedVersion.unsigned(0), null, Collections.EMPTY_MAP);
    muxListener.verifyConnected();

    channel.send(createAddParticipantOp());
    assertEquals(1, waveViewService.submits.size());
    assertUnsavedDataInfo(1, 1, 1, 0, 0);

    // Server ack's the previous add participant op.
    HashedVersion v2 = HashedVersion.of(2, SIGNATURE2);
    WaveViewService.SubmitCallback submitCallback = waveViewService.lastSubmit().callback;
    submitCallback.onResponse(1, v2, TIMESTAMP, new ReturnStatus(ReturnCode.OK));

    assertUnsavedDataInfo(0, 1, 0, 2, 0);

    // Server sends commit for the add participant op.
    openCallback.onUpdate(null, v2);
    // Now we expect to be told everything is committed.
    assertUnsavedDataInfo(0, 0, 0, 2, 2);

    // Send another op, now it should be uncommitted.
    channel.send(createAddParticipantOp());
    assertEquals(2, waveViewService.submits.size());
    assertUnsavedDataInfo(1, 1, 1, 2, 2);

    // Now bail out of the connection with an op pending, should close.
    mux.close();
    assertEquals(1, waveViewService.closes.size());
    waveViewService.lastClose().callback.onSuccess();
    assertEquals(1, fakeUnsavedDataListener.closeCalls);
  }

  /**
   * Open a wave; send an Op; wait for the ack; send another Op; get ack; get LCV for first ack;
   * get a LCV for second ack.
   */
  public void testLastCommittedVersionUpdate() throws ChannelException {
    // Connect to the server
    mux.setChannelsPresenceListener(muxListener);
    mux.open(Lists.create(KNOWN_WAVELET1), Collections.EMPTY_MAP, muxListener);
    OperationChannel channel = muxListener.verifyOperationChannelCreated(SNAPSHOT1,
        Accessibility.READ_WRITE);
    muxListener.verifyNoMoreInteractions();
    assertEquals(1, waveViewService.opens.size());
    WaveViewService.OpenChannelStreamCallback openCallback = waveViewService.lastOpen().callback;

    // Pretend we got the initial snapshot at version 1 signature1.
    openCallback.onWaveletOpen(CHANNEL_ID, HashedVersion.of(1L, SIGNATURE1),
        HashedVersion.of(1L, SIGNATURE1), 0, HashedVersion.unsigned(0), null, Collections.EMPTY_MAP);
    muxListener.verifyConnected();

    // Send Op, get ack.
    channel.send(createAddParticipantOp());
    assertEquals(1, waveViewService.submits.size());
    WaveViewService.SubmitCallback submitCallback1 = waveViewService.lastSubmit().callback;
    HashedVersion v2 = HashedVersion.of(2, SIGNATURE2);
    submitCallback1.onResponse(1, v2, TIMESTAMP, new ReturnStatus(ReturnCode.OK));
    assertUnsavedDataInfo(0, 1, 0, 2, 0);

    // Send another Op, get ack.
    channel.send(createAddParticipantOp());
    assertEquals(2, waveViewService.submits.size());
    WaveViewService.SubmitCallback submitCallback2 = waveViewService.lastSubmit().callback;
    HashedVersion v3 = HashedVersion.of(3, SIGNATURE3);
    submitCallback2.onResponse(1, v3, TIMESTAMP, new ReturnStatus(ReturnCode.OK));
    assertUnsavedDataInfo(0, 2, 0, 3, 0);

    // Server sends commit for the first addParticipant op.
    openCallback.onUpdate(null, v2);
    // Now we expect to be told that NOT everything is committed.
    assertUnsavedDataInfo(0, 1, 0, 3, 2);

    // Server sends commit for the second addParticipant op.
    openCallback.onUpdate(null, v3);
    // Now we expect to be told that everything is committed.
    assertUnsavedDataInfo(0, 0, 0, 3, 3);

    mux.close();
    assertEquals(1, waveViewService.closes.size());
    waveViewService.lastClose().callback.onSuccess();
    assertEquals(1, fakeUnsavedDataListener.closeCalls);
  }

  public void testMuxCloseClosesAllUnsavedDataUpdaters() {
    mux.setChannelsPresenceListener(muxListener);
    mux.open(Lists.create(KNOWN_WAVELET1, KNOWN_WAVELET2), Collections.EMPTY_MAP, muxListener);
    OperationChannel ch1 = muxListener.verifyOperationChannelCreated(SNAPSHOT1,
        Accessibility.READ_WRITE);
    OperationChannel ch2 = muxListener.verifyOperationChannelCreated(SNAPSHOT2,
        Accessibility.READ_WRITE);
    muxListener.verifyNoMoreInteractions();

    MockOperationChannelListener listener1 = new MockOperationChannelListener();
    MockOperationChannelListener listener2 = new MockOperationChannelListener();
    ch1.setListener(listener1);
    ch2.setListener(listener2);

    mux.close();

    assertEquals(2, fakeUnsavedDataListener.closeCalls);
  }

  private AddParticipant createAddParticipantOp(String participantName) {
    AddParticipant addPart = new AddParticipant(new WaveletOperationContext(
        USER_NAME, -1L, 0L), new ParticipantId(participantName));
    return addPart;
  }

  private static ObservableWaveletFragmentData createSnapshot(WaveletId waveletId, final long version,
      final byte[] signature) {
    final HashedVersion hv = HashedVersion.of(version, signature);
    return DATA_FACTORY.create(new EmptyWaveletFragmentSnapshot(WAVE_ID, waveletId, USER_NAME, hv,
        1273307837000L));
  }

  private AddParticipant createAddParticipantOp() {
    return createAddParticipantOp("thedude@google.com");
  }

  private void assertUnsavedDataInfo(int unacknowledged, int uncommitted, int inFlight,
      long lastAckVersion, long lastCommitVersion) {
    assertEquals(unacknowledged, fakeUnsavedDataListener.unacknowledgedSize);
    assertEquals(uncommitted, fakeUnsavedDataListener.uncommittedSize);
    assertEquals(inFlight, fakeUnsavedDataListener.inFlight);
    assertEquals(lastAckVersion, fakeUnsavedDataListener.lastAckVersion);
    assertEquals(lastCommitVersion, fakeUnsavedDataListener.lastCommitVersion);
  }
}
