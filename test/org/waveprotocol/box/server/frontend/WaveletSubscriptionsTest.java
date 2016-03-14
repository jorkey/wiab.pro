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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import junit.framework.TestCase;

import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.testing.DeltaTestUtil;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.box.server.frontend.ClientFrontend.UpdateChannelListener;
import org.waveprotocol.wave.concurrencycontrol.wave.CcBasedWaveView.OpenListener;

import java.util.List;

/**
 * Tests {@link userSubscriptions}.
 *
 * @author unknown
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class WaveletSubscriptionsTest extends TestCase {
  private static final WaveId W1 = WaveId.of("example.com", "111");
  private static final WaveId W2 = WaveId.of("example.com", "222");

  private static final WaveletId WA = WaveletId.of("example.com", "AAA");
  private static final WaveletId WB = WaveletId.of("example.com", "BBB");

  private static final WaveletName W1A = WaveletName.of(W1, WA);
  private static final WaveletName W2A = WaveletName.of(W2, WA);

  private static final ParticipantId USER = ParticipantId.ofUnsafe("user@host.com");
  private static final DeltaTestUtil UTIL = new DeltaTestUtil(USER);

  private static final String CHANNEL_ID = "ch";
  private static final String CHANNEL1_ID = "ch1";
  private static final String CHANNEL2_ID = "ch2";
  private static final String CONNECTION_ID = "con";

  private static final HashedVersion V2 = HashedVersion.unsigned(2);
  private static final HashedVersion V3 = HashedVersion.unsigned(3);

  private static final TransformedWaveletDelta DELTA = UTIL.makeTransformedDelta(0L, V2, 2);
  private static final DeltaSequence DELTAS = DeltaSequence.of(DELTA);

  private WaveletSubscriptions s;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    s = new WaveletSubscriptions();
  }

  // TODO(arb): add tests testing subscriptions with channelIds.

  /**
   * Tests that {@link userSubscriptions#matchSubscriptions(WaveletName)} accurately
   * reflects subscription, independent of whether we actually are on any
   * wavelets.
   */
  public void testMatchSubscriptions() {
    assertEquals(ImmutableList.<OpenListener>of(), s.getSubscriptions(W1A));

    UpdateChannelListener l1 = mock(UpdateChannelListener.class, "listener 1");
    UpdateChannelListener l2 = mock(UpdateChannelListener.class, "listener 2");
    UpdateChannelListener l3 = mock(UpdateChannelListener.class, "listener 3");
    UpdateChannelListener l4 = mock(UpdateChannelListener.class, "listener 4");
    UpdateChannelListener l5 = mock(UpdateChannelListener.class, "listener 5");
    String channelId = "";

    s.subscribe(W1A, USER, channelId, CONNECTION_ID, l1);
    s.subscribe(W1A, USER, channelId, CONNECTION_ID, l2);
    s.subscribe(W2A, USER, channelId, CONNECTION_ID, l3);
    s.subscribe(W2A, USER, channelId, CONNECTION_ID, l4);
    s.subscribe(W1A, USER, channelId, CONNECTION_ID, l5);

    checkListenersMatchSubscriptions(ImmutableList.of(l1, l2, l5), s.getSubscriptions(W1A));
    checkListenersMatchSubscriptions(ImmutableList.of(l3, l4), s.getSubscriptions(W2A));
  }

  /**
   * Method to check whether the given subscriptions contain exactly the expected
   * {@link UpdateChannelListener}s.
   *
   * @param expectedListeners the {@link List} of {@link UpdateChannelListener}s we are
   *        expecting
   * @param matchedSubscriptions the {@link List} of subscriptions to get the
   *        {@link UpdateChannelListener} from
   */
  private void checkListenersMatchSubscriptions(List<UpdateChannelListener> expectedListeners,
      List<WaveletSubscription> matchedSubscriptions) {
    List<UpdateChannelListener> actualListeners = Lists.newArrayList();
    for (WaveletSubscription subscription : matchedSubscriptions) {
      actualListeners.add(subscription.getUpdateListener());
    }
    assertEquals(expectedListeners, actualListeners);
  }

  public void testEmptyDeltaSequenceNotReceived() {
    UpdateChannelListener listener = mock(UpdateChannelListener.class);
    s.subscribe(W1A, USER, CHANNEL_ID, CONNECTION_ID, listener);
    onUpdate(W1A, DeltaSequence.empty());
    verifyZeroInteractions(listener);
  }

  /**
   * Tests that a single delta update is received by the listener.
   */
  public void testSingleDeltaReceived() {
    UpdateChannelListener listener = mock(UpdateChannelListener.class);
    s.subscribe(W1A, USER, CHANNEL_ID, CONNECTION_ID, listener);
    onUpdate(W1A, DELTAS);
    verify(listener).onUpdate(DELTAS, null);
  }

  /**
   * Tests that multiple deltas are received.
   */
  public void testUpdateSeveralDeltas() {
    TransformedWaveletDelta delta2 = UTIL.noOpDelta(V2.getVersion());

    UpdateChannelListener listener = mock(UpdateChannelListener.class);
    s.subscribe(W1A, USER, CHANNEL1_ID, CONNECTION_ID, listener);

    DeltaSequence bothDeltas =  DeltaSequence.of(DELTA, delta2);
    onUpdate(W1A, bothDeltas);
    verify(listener).onUpdate(bothDeltas, null);

    // Also succeeds when sending the two deltas via separate onUpdates()
    DeltaSequence delta2Sequence = DeltaSequence.of(delta2);
    s.subscribe(W2A, USER, CHANNEL2_ID, CONNECTION_ID, listener);
    onUpdate(W2A, DELTAS);
    onUpdate(W2A, DeltaSequence.of(delta2));
    verify(listener).onUpdate(DELTAS, null);
    verify(listener).onUpdate(delta2Sequence, null);
  }

  /**
   * Tests that delta updates are held back while a submit is in flight.
   */
  public void testDeltaHeldBackWhileOutstandingSubmit() {
    UpdateChannelListener listener = mock(UpdateChannelListener.class);
    WaveletSubscription s1 = s.subscribe(W1A, USER, CHANNEL_ID, CONNECTION_ID, listener);

    s1.submitRequest();
    onUpdate(W1A, DELTAS);
    verifyZeroInteractions(listener);

    s1.submitResponse(V3); // V3 not the same as update delta.
    verify(listener).onUpdate(DELTAS, null);
  }

  /**
   * Tests that a delta with an end version matching one submitted on this
   * channel is dropped.
   */
  public void testOwnDeltasAreDropped() {
    UpdateChannelListener listener = mock(UpdateChannelListener.class);
    WaveletSubscription s1 = s.subscribe(W1A, USER, CHANNEL_ID, CONNECTION_ID, listener);

    s1.submitRequest();
    s1.submitResponse(V2);
    onUpdate(W1A, DELTAS);
    verifyZeroInteractions(listener);
  }

  /**
   * Tests that a a delta with an end version matching one submitted on this
   * channel is dropped even if received before the submit completes.
   */
  public void testOwnDeltaDroppedAfterBeingHeldBack() {
    UpdateChannelListener listener = mock(UpdateChannelListener.class);
    WaveletSubscription s1 = s.subscribe(W1A, USER, CHANNEL_ID, CONNECTION_ID, listener);

    s1.submitRequest();
    onUpdate(W1A, DELTAS);
    s1.submitResponse(V2);
    verifyZeroInteractions(listener);
  }

  /**
   * Tests that deltas not sended to channels of unsubscribed connections.
   */
  public void testDeltasNotSendedToUnsubscribedChannels() {
    UpdateChannelListener listener = mock(UpdateChannelListener.class);
    WaveletSubscription s1 = s.subscribe(W1A, USER, CHANNEL1_ID, CONNECTION_ID, listener);
    WaveletSubscription s2 = s.subscribe(W2A, USER, CHANNEL2_ID, CONNECTION_ID, listener);

    s.unsubscribe(s1);
    s.unsubscribe(s2);

    onUpdate(W1A, DELTAS);
    onUpdate(W2A, DELTAS);
    verifyZeroInteractions(listener);
  }

  private void onUpdate(WaveletName waveletName, DeltaSequence deltas) {
    for (WaveletSubscription subscription : s.getSubscriptions(waveletName)) {
      subscription.onUpdate(deltas);
    }
  }
}
