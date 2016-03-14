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

import com.google.gwt.dev.util.collect.Lists;
import com.google.protobuf.ByteString;
import org.waveprotocol.wave.common.logging.AbstractLogger;
import org.waveprotocol.wave.common.logging.PrintLogger;
import org.waveprotocol.wave.concurrencycontrol.channel.ViewChannel.Listener;
import org.waveprotocol.wave.concurrencycontrol.common.ChannelException;
import org.waveprotocol.wave.clientserver.ReturnCode;
import org.waveprotocol.wave.clientserver.ReturnStatus;
import org.waveprotocol.wave.concurrencycontrol.testing.MockWaveViewService;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.raw.RawFragment;
import org.waveprotocol.wave.model.raw.RawOperation;
import org.waveprotocol.wave.model.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static junit.framework.Assert.assertEquals;
import junit.framework.TestCase;
import org.waveprotocol.wave.concurrencycontrol.channel.ViewChannel.IndexingCallback;
import org.waveprotocol.wave.model.id.SegmentId;

/**
 * Unit test for ViewChannelImpl.
 *
 * @author zdwang@google.com (David Wang)
 * @author akaplanov@gmail.com (A. Kaplanov)
 */

public class ViewChannelImplTest extends TestCase {

  /**
   * This is mock class to test that calls back from ViewChannel are as expected.
   */
  private static class MockViewChannelListener implements Listener {
    public enum MethodCall {
      ON_WAVELET_OPENED,
      ON_CONNECTED,
      ON_DISCONNECTED,
      ON_CLOSED,
      ON_FAILURE,
      ON_EXCEPTION,
      ON_UPDATE
    }

    public class MethodCallContext {
      public final MethodCall method;
      public final WaveletId waveletId;
      public final HashedVersion connectVersion;
      public final HashedVersion lastModifiedVersion;
      public final long lastModifiedTime;
      public final HashedVersion lastCommittedVersion;
      public final HashedVersion unacknowledgedDeltaVersion;
      public final Map<SegmentId, RawFragment> fragments;
      public final List<TransformedWaveletDelta> deltas;
      public final ReturnStatus status;
      public final ChannelException ex;

      public MethodCallContext(MethodCall method) {
        this.method = method;
        this.waveletId = null;
        this.fragments = null;
        this.connectVersion = null;
        this.lastModifiedVersion = null;
        this.lastModifiedTime = 0L;
        this.lastCommittedVersion = null;
        this.unacknowledgedDeltaVersion = null;
        this.deltas = null;
        this.status = null;
        this.ex = null;
      }

      public MethodCallContext(MethodCall method, WaveletId waveletId, HashedVersion connectVersion,
          HashedVersion lastModifiedVersion, long lastModifiedTime,
          HashedVersion lastCommittedVersion, HashedVersion unacknowledgedDeltaVersion,
          Map<SegmentId, RawFragment> fragments) {
        this.method = method;
        this.waveletId = waveletId;
        this.connectVersion = connectVersion;
        this.lastModifiedVersion = lastModifiedVersion;
        this.lastModifiedTime = lastModifiedTime;
        this.lastCommittedVersion = lastCommittedVersion;
        this.unacknowledgedDeltaVersion = unacknowledgedDeltaVersion;
        this.fragments = fragments;
        this.deltas = null;
        this.status = null;
        this.ex = null;
      }

      public MethodCallContext(MethodCall method, WaveletId waveletId,
          List<TransformedWaveletDelta> deltas, HashedVersion lastCommittedVersion) {
        this.method = method;
        this.waveletId = waveletId;
        this.deltas = deltas;
        this.fragments = null;
        this.connectVersion = null;
        this.lastModifiedVersion = null;
        this.lastModifiedTime = 0L;
        this.lastCommittedVersion = lastCommittedVersion;
        this.unacknowledgedDeltaVersion = null;
        this.status = null;
        this.ex = null;
      }

      public MethodCallContext(MethodCall method, ReturnStatus status) {
        this.method = method;
        this.waveletId = null;
        this.deltas = null;
        this.fragments = null;
        this.connectVersion = null;
        this.lastModifiedVersion = null;
        this.lastModifiedTime = 0L;
        this.lastCommittedVersion = null;
        this.unacknowledgedDeltaVersion = null;
        this.status = status;
        this.ex = null;
      }

      public MethodCallContext(MethodCall method, ChannelException ex) {
        this.method = method;
        this.waveletId = null;
        this.connectVersion = null;
        this.lastModifiedVersion = null;
        this.lastModifiedTime = 0L;
        this.lastCommittedVersion = null;
        this.unacknowledgedDeltaVersion = null;
        this.fragments = null;
        this.deltas = null;
        this.status = null;
        this.ex = ex;
      }

      public MethodCall method() {
        return method;
      }
    }

    private final ArrayList<MethodCallContext> methodCalls = new ArrayList<MethodCallContext>();

    @Override
    public void onWaveletOpened(WaveletId waveletId, HashedVersion connectVersion, 
        HashedVersion lastModifiedVersion, long lastModifiedTime, HashedVersion lastCommittedVersion, 
        HashedVersion unacknowledgedDeltaVersion, Map<SegmentId, RawFragment> fragments) throws ChannelException {
      methodCalls.add(new MethodCallContext(MethodCall.ON_WAVELET_OPENED, waveletId,
          connectVersion, lastModifiedVersion, lastModifiedTime, lastCommittedVersion, 
          unacknowledgedDeltaVersion, fragments));
    }

    @Override
    public void onConnected() throws ChannelException {
      methodCalls.add(new MethodCallContext(MethodCall.ON_CONNECTED));
    }

    @Override
    public void onDisconnected() {
      methodCalls.add(new MethodCallContext(MethodCall.ON_DISCONNECTED));
    }

    @Override
    public void onClosed() {
      methodCalls.add(new MethodCallContext(MethodCall.ON_CLOSED));
    }

    @Override
    public void onFailure(ReturnStatus status) {
      methodCalls.add(new MethodCallContext(MethodCall.ON_FAILURE, status));
    }

    @Override
    public void onException(ChannelException e) {
      methodCalls.add(new MethodCallContext(MethodCall.ON_EXCEPTION, e));
    }

    @Override
    public void onUpdate(WaveletId waveletId, List<TransformedWaveletDelta> deltas,
        HashedVersion lastCommittedVersion) throws ChannelException {
      methodCalls.add(new MethodCallContext(MethodCall.ON_UPDATE, waveletId, deltas, lastCommittedVersion));
    }

    public void expectedWaveletOpenCall(WaveletId waveletId, HashedVersion connectVersion, 
        HashedVersion lastModifiedVersion, HashedVersion lastCommittedVersion, HashedVersion unacknowledgedDeltaVersion, 
        List<RawFragment> fragments) {
      assertEquals(MethodCall.ON_WAVELET_OPENED, methodCalls.get(0).method);
      assertEquals(waveletId, methodCalls.get(0).waveletId);
      assertEquals(connectVersion, methodCalls.get(0).connectVersion);
      assertEquals(lastModifiedVersion, methodCalls.get(0).lastModifiedVersion);
      assertEquals(lastCommittedVersion, methodCalls.get(0).lastCommittedVersion);
      assertEquals(unacknowledgedDeltaVersion, methodCalls.get(0).unacknowledgedDeltaVersion);
      assertEquals(fragments, methodCalls.get(0).fragments);

      methodCalls.remove(0);
    }

    public void expectedConnectedCall() {
      assertEquals(MethodCall.ON_CONNECTED, methodCalls.get(0).method);

      methodCalls.remove(0);
    }

    public void expectedClosedCall() {
      assertEquals(MethodCall.ON_CLOSED, methodCalls.get(0).method);

      methodCalls.remove(0);
    }

    public void expectedFailureCall(ReturnStatus status) {
      assertEquals(MethodCall.ON_FAILURE, methodCalls.get(0).method);
      assertEquals(status, methodCalls.get(0).status);

      methodCalls.remove(0);
    }

    public void expectedChannelExceptionCall() {
      assertEquals(MethodCall.ON_EXCEPTION, methodCalls.get(0).method);
      assertEquals(ChannelException.class, methodCalls.get(0).ex.getClass());

      methodCalls.remove(0);
    }

    public void expectedUpdateCall(WaveletId waveletId, List<TransformedWaveletDelta> deltas,
        HashedVersion lastCommittedVersion) {
      assertEquals(MethodCall.ON_UPDATE, methodCalls.get(0).method);
      assertEquals(waveletId, methodCalls.get(0).waveletId);
      assertEquals(deltas, methodCalls.get(0).deltas);
      assertEquals(lastCommittedVersion, methodCalls.get(0).lastCommittedVersion);

      methodCalls.remove(0);
    }

    public void expectedNothing() {
      assertEquals(0, methodCalls.size());
    }

    public void clear() {
      methodCalls.clear();
    }
  }

  /**
   * This is mock class to test that calls back from ViewChannel are as expected.
   */
  private static class MockSubmitCallback implements SubmitCallback {
    public enum MethodCall {
      ON_RESPONSE,
      ON_FAILURE
    }

    public class MethodCallContext {
      final MethodCall method;
      final int opsApplied;
      final long timestampAfterApplication;
      final HashedVersion version;
      final ReturnStatus error;

      public MethodCallContext(MethodCall method, int opsApplied, HashedVersion version,
          long timestampAfterApplication) {
        this(method, null, opsApplied, version, timestampAfterApplication);
      }

      public MethodCallContext(MethodCall method, ReturnStatus error) {
        this(method, error, 0, null, 0);
      }

      public MethodCallContext(MethodCall method, ReturnStatus error, int opsApplied,
          HashedVersion version, long timestampAfterApplication) {
        this.method = method;
        this.error = error;
        this.opsApplied = opsApplied;
        this.version = version;
        this.timestampAfterApplication = timestampAfterApplication;
      }
    }

    ArrayList<MethodCallContext> methodCalls = new ArrayList<MethodCallContext>();

    @Override
    public void onResponse(int opsApplied, HashedVersion newVersion, long timestampAfterApplication,
        ReturnStatus responseStatus) throws ChannelException {
      methodCalls.add(new MethodCallContext(
          MethodCall.ON_RESPONSE, responseStatus, opsApplied, newVersion, timestampAfterApplication));
    }

    @Override
    public void onFailure(ReturnStatus responseStatus) throws ChannelException {
      methodCalls.add(new MethodCallContext(
          MethodCall.ON_FAILURE, responseStatus));
    }

    public void expectedResponseCall(int opsApplied, HashedVersion version, long timestampAfterApplication,
        ReturnStatus error) {
      MethodCallContext context = methodCalls.get(0);
      assertEquals(MockSubmitCallback.MethodCall.ON_RESPONSE, context.method);
      assertEquals(opsApplied, context.opsApplied);
      assertEquals(version, context.version);
      assertEquals(timestampAfterApplication, context.timestampAfterApplication);
      assertEquals(error, context.error);

      methodCalls.remove(0);
    }

    public void expectedFailureResponseCall(ReturnStatus error) {
      MethodCallContext context = methodCalls.get(0);
      assertEquals(MockSubmitCallback.MethodCall.ON_FAILURE, context.method);
      assertEquals(error, context.error);

      methodCalls.remove(0);
    }
  }

  /**
   * Wavelet id to use in the tests.
   */
  private static final WaveletId WAVELET_ID1 = WaveletId.of("example.com", "waveletId_1");
  private static final WaveletId WAVELET_ID2 = WaveletId.of("example.com", "waveletId_2");

  /**
   * Channel Id to be used in the tests.
   */
  private static final String CHANNEL_ID1 = "channelId_1";
  private static final String CHANNEL_ID2 = "channelId_2";
  private static final long TIMESTAMP = 1234567890;
  private static final HashedVersion HASHED_VERSION1 = HashedVersion.of(1, new byte[]{1, 2, 3, 4});
  private static final HashedVersion HASHED_VERSION2 = HashedVersion.of(2, new byte[]{1, 2, 3, 4});
  private static final AbstractLogger logger = new PrintLogger();

  //
  // Fields used in most or all tests.
  //

  private ViewChannelImpl channel;
  private MockViewChannelListener viewListener;
  private MockWaveViewService waveViewService;
  
  private IndexingCallback indexingCallback = new ViewChannel.IndexingCallback() {

    @Override
    public void onIndexing(long totalVersions, long indexedVersions) {
    }

    @Override
    public void onIndexingComplete() {
    }
  };

  @Override
  protected void setUp() {
    ByteString.copyFrom(new byte[]{1});
    WaveId waveId = WaveId.of("example.com", "waveid");
    ViewChannelImpl.setMaxViewChannelsPerWave(Integer.MAX_VALUE);
    waveViewService = new MockWaveViewService();
    viewListener = new MockViewChannelListener();
    channel = new ViewChannelImpl(waveId, waveViewService, indexingCallback, logger);
  }

  /**
   * Opens the channel from the client side only.
   */
  private void halfOpen() {
    Map<WaveletId, List<HashedVersion>> knownWavelets = new HashMap<>();
    Map<WaveletId, WaveletDelta> unacknowledgedDeltas = new HashMap<>();
    knownWavelets.put(WAVELET_ID1, Lists.create(HASHED_VERSION1));
    channel.open(knownWavelets, Collections.EMPTY_MAP, unacknowledgedDeltas, viewListener);
  }

 /**
   * Simulates the server responding with the opening wavelet response.
   */
  private void respondWithWaveletOpen() {
    waveViewService.lastOpen().callback.onWaveletOpen(CHANNEL_ID1,
      HASHED_VERSION1, HASHED_VERSION1, 0, HASHED_VERSION1, null, null);
  }

 /**
   * Simulates the server responding with the opening wavelet response.
   */
  private void respondWithWaveletClose() {
    waveViewService.lastClose().callback.onSuccess();
  }

  /**
   * Simulates the server sending a streaming update.
   */
  private List<TransformedWaveletDelta> respondWithUpdate(HashedVersion version) {
    TransformedWaveletDelta delta = new TransformedWaveletDelta(null, version, 0L,
        Arrays.<WaveletOperation>asList());
    List<TransformedWaveletDelta> deltas = new ArrayList<>();
    deltas.add(delta);
    waveViewService.lastOpen().callback.onUpdate(deltas, null);
    return deltas;
  }

  /**
   * Simulates the server sending a commit version.
   */
  private void respondWithCommitVersion(HashedVersion version) {
    waveViewService.lastOpen().callback.onUpdate(null, version);
  }

  private void respondToSubmit(int opsApplied, HashedVersion version, long timestamp, ReturnStatus error) {
    waveViewService.lastSubmit().callback.onResponse(opsApplied, version, timestamp, error);
  }

  private void respondToSubmitWithFailure(ReturnStatus error) {
    waveViewService.lastSubmit().callback.onFailure(error);
  }

  private void expectOpenCallbacks() {
    viewListener.expectedWaveletOpenCall(WAVELET_ID1, 
      HASHED_VERSION1, HASHED_VERSION1, HASHED_VERSION1, null, null);
    viewListener.expectedConnectedCall();
  }

  private void expectClosedCallback() {
    viewListener.expectedClosedCall();
  }

  /**
   * Opens the channel, and simulates the server responding with a channel and
   * a marker.
   */
  private void open() {
    halfOpen();
    respondWithWaveletOpen();
    expectOpenCallbacks();
  }

  private void close() {
    channel.close();
    respondWithWaveletClose();
    expectClosedCallback();
  }

  private void terminateOpenRpcWithFailure(ReturnStatus status) {
    waveViewService.lastOpen().callback.onFailure(status);
  }

  private static WaveletDelta emptyDelta() {
    return new WaveletDelta(null, null, Arrays.<WaveletOperation> asList());
  }

  /**
   * Test that when everything is ok, we can connect, submit, create wavelet and disconnect.
   * This is not supposed to be a thorough test.
   */
  public void testSunnyDayScenario() {
    open();
    assertEquals(1, waveViewService.opens.size());
    // pretend an update with update wavelet. Note we don't add any data in the wavelet
    // because it's not really relevant for the test.
    List<TransformedWaveletDelta> deltas = respondWithUpdate(HASHED_VERSION2);
    viewListener.expectedUpdateCall(WAVELET_ID1, deltas, null);

    // Submit a delta and check that we have the right channel id remembered.
    MockSubmitCallback submitListener = new MockSubmitCallback();
    channel.submitDelta(WAVELET_ID1, emptyDelta(), submitListener);
    assertEquals(1, waveViewService.submits.size());
    assertEquals(CHANNEL_ID1, waveViewService.lastSubmit().channelId);

    // Return a success message on the submitted delta
    byte[] hash = new byte[] {1, 2, 3, 4};
    respondToSubmit(1, HashedVersion.of(2, hash), TIMESTAMP, new ReturnStatus(ReturnCode.OK));
    submitListener.expectedResponseCall(1, HashedVersion.of(2, hash), TIMESTAMP,
        new ReturnStatus(ReturnCode.OK));

    // Check disconnect
    close();
    assertEquals(1, waveViewService.closes.size());
    waveViewService.lastClose().callback.onSuccess();
  }

  public void testOnConnectedCalledAfterAllWaveletsIsOpened() {
    Map<WaveletId, List<HashedVersion>> knownWavelets = new HashMap<>();
    knownWavelets.put(WAVELET_ID1, Lists.create(HASHED_VERSION1));
    knownWavelets.put(WAVELET_ID2, Lists.create(HASHED_VERSION1));
    Map<WaveletId, WaveletDelta> unacknowledgedDeltas = new HashMap<>();
    channel.open(knownWavelets, Collections.EMPTY_MAP, unacknowledgedDeltas, viewListener);
    waveViewService.opens.get(waveViewService.opens.size()-1).callback.onWaveletOpen(CHANNEL_ID1, 
      HASHED_VERSION1, HASHED_VERSION1, 0, HASHED_VERSION1, null, null);
    viewListener.expectedWaveletOpenCall(WAVELET_ID1, 
        HASHED_VERSION1, HASHED_VERSION1, HASHED_VERSION1, null, null);
    viewListener.expectedNothing();
    waveViewService.opens.get(waveViewService.opens.size()-2).callback.onWaveletOpen(CHANNEL_ID2, 
      HASHED_VERSION1, HASHED_VERSION1, 0, HASHED_VERSION1, null, null);
    viewListener.expectedWaveletOpenCall(WAVELET_ID2, 
        HASHED_VERSION1, HASHED_VERSION1, HASHED_VERSION1, null, null);
    viewListener.expectedConnectedCall();
  }

  /**
   * Tests that updates that arrive before the open finished are holded.
   */
  public void testUpdatesBeforeOpenHoldedUntilOpenFinished() {
    halfOpen();

    List<TransformedWaveletDelta> deltas = respondWithUpdate(HASHED_VERSION2);

    respondWithWaveletOpen();

    viewListener.expectedWaveletOpenCall(WAVELET_ID1, 
        HASHED_VERSION1, HASHED_VERSION1, HASHED_VERSION1, null, null);
    viewListener.expectedUpdateCall(WAVELET_ID1, deltas, HASHED_VERSION1);
    viewListener.expectedConnectedCall();
  }

  /**
   * Tests that old updates that arrive before the open finished are skipped.
   */
  public void testOldUpdatesBeforeOpenFinishedSkipped() {
    halfOpen();

    List<TransformedWaveletDelta> deltas =
        Lists.addAll(respondWithUpdate(HASHED_VERSION1), respondWithUpdate(HASHED_VERSION2));

    respondWithWaveletOpen();

    viewListener.expectedWaveletOpenCall(WAVELET_ID1, 
        HASHED_VERSION1, HASHED_VERSION1, HASHED_VERSION1, null, null);
    viewListener.expectedUpdateCall(WAVELET_ID1, deltas, HASHED_VERSION1);
    viewListener.expectedConnectedCall();
  }

  /**
   * Tests that commit version arrive before the open finished are holded.
   */
  public void testCommitVersionBeforeOpenHoldedUntilOpenFinished() {
    halfOpen();

    respondWithCommitVersion(HASHED_VERSION2);

    respondWithWaveletOpen();

    viewListener.expectedWaveletOpenCall(WAVELET_ID1, 
        HASHED_VERSION1, HASHED_VERSION1, HASHED_VERSION2, null, null);
    viewListener.expectedConnectedCall();
  }

  /**
   * Tests that closing the channel prevents updates that arrive later from
   * being passed to the channel listener.
   */
  public void testCloseMasksFutureUpdatesFromOpenListener() {
    open();
    close();

    respondWithUpdate(HASHED_VERSION2);
    viewListener.expectedNothing();
  }

  public void testCloseMasksFutureOpensFromOpenListener() {
    open();
    close();

    respondWithWaveletOpen();
    viewListener.expectedNothing();
  }

  public void testMultipleClose() {
    open();
    close();
    close();
    viewListener.expectedNothing();
  }

  public void testCloseWithoutOpen() {
    channel.close();
    assertEquals(0, waveViewService.closes.size());
    viewListener.expectedNothing();
  }

  public void testOpenAfterOpenThrowsException() {
    open();
    try {
      open();
      fail("Should not be able to open again after open is called.");
    } catch (RuntimeException ex) {
      // Expected error.
    }
  }

  public void testSubmitDeltaWithErrorResponse() {
    open();

    // submit a delta
    MockSubmitCallback submitListener = new MockSubmitCallback();
    channel.submitDelta(WAVELET_ID1, emptyDelta(), submitListener);

    // Success with error the submit delta.
    String errorMessage = "Bad things happened on the server";
    ReturnStatus error = new ReturnStatus(ReturnCode.OK, errorMessage);
    respondToSubmit(1, HashedVersion.of(2, new byte[] {1, 2, 3, 4}), TIMESTAMP, error);
    submitListener.expectedResponseCall(1, HashedVersion.of(2, new byte[] {1, 2, 3, 4}), TIMESTAMP, error);
  }

  public void testSubmitDeltaWithFailureResponse () {
    open();

    // submit a delta
    MockSubmitCallback submitListener = new MockSubmitCallback();
    channel.submitDelta(WAVELET_ID1, emptyDelta(), submitListener);

    // Fail the submit delta, and expect that the submit listened got the error.
    ReturnStatus error = new ReturnStatus(ReturnCode.INTERNAL_ERROR, "Channel error");
    respondToSubmitWithFailure(error);
    submitListener.expectedFailureResponseCall(error);
  }

  public void testSubmitDeltaOnClosedChannelThrowsIllegalStateException() {
    open();
    close();

    // submit a delta should fail
    try {
      channel.submitDelta(WAVELET_ID1, emptyDelta(), new MockSubmitCallback());
      fail("Should not be able to submit on a closed channel");
    } catch (IllegalStateException ex) {
      // expect exception
    }
  }

  public void testFailedOpenCallsListenerFailure() {
    halfOpen();
    // fail the open
    ReturnStatus status = new ReturnStatus(ReturnCode.INTERNAL_ERROR);
    terminateOpenRpcWithFailure(status);
    viewListener.expectedFailureCall(status);
  }

  public void testCannotCreateTooManyChannels() {
    ViewChannelImpl.setMaxViewChannelsPerWave(4);
    WaveId waveId = WaveId.of("example.com", "toomanywaveid");
    for  (int i = 0; i < 4; i++) {
      channel = new ViewChannelImpl(waveId, waveViewService, indexingCallback, logger);
      open();
    }
    try {
      channel = new ViewChannelImpl(waveId, waveViewService, indexingCallback, logger);
      open();
      fail("Should not be allowed to create any more view channels");
    } catch (IllegalStateException ex) {
      // expected
    }
  }

  public void testClosingOneChannelMakesRoomForAnother() {
    WaveId waveId = WaveId.of("example.com", "makeroomwaveid");
    ViewChannelImpl.setMaxViewChannelsPerWave(4);
    for (int i = 0; i < 4; i++) {
      channel = new ViewChannelImpl(waveId, waveViewService, indexingCallback, logger);
    }
    channel.close(); // Close the last channel, making room for another.
    channel = new ViewChannelImpl(waveId, waveViewService, indexingCallback, logger);
  }
}
