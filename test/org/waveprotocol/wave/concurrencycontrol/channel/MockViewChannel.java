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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import org.waveprotocol.wave.concurrencycontrol.common.ChannelException;
import org.waveprotocol.wave.clientserver.ReturnCode;
import org.waveprotocol.wave.clientserver.ReturnStatus;
import org.waveprotocol.wave.concurrencycontrol.testing.CcTestingUtils;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * A mock view channel which allows setting of expectations.
 *
 * @author anorth@google.com (Alex North)
 */
public final class MockViewChannel implements ViewChannel {

  private enum Method { FETCH_WAVE_VIEW, FETCH_FRAGMENTS, OPEN, SUBMIT, CLOSE, DISCONNECT }

  private final Queue<Object[]> expectations = new LinkedList<Object[]>();
  private final Queue<FetchWaveViewCallback> fetchWaveViewCallbacks = new LinkedList<FetchWaveViewCallback>();
  private final Queue<FetchFragmentsCallback> fetchDocumentsCallbacks = new LinkedList<FetchFragmentsCallback>();
  private final Queue<Listener> listeners = new LinkedList<Listener>();
  private final Queue<SubmitCallback> awaitingAck = new LinkedList<SubmitCallback>();

  public void expectFetchWaveView(IdFilter waveletFilter, boolean fromLastRead) {
    expectations.add(new Object[] {Method.FETCH_WAVE_VIEW, waveletFilter, fromLastRead});
  }

  public void expectFetchFragments(WaveletId waveletId, HashedVersion startVersion, HashedVersion endVersion,
      List<SegmentId> segmentIds, int contentSize) {
    expectations.add(new Object[] {Method.FETCH_FRAGMENTS, waveletId, startVersion, endVersion, segmentIds, contentSize });
  }

  public void expectOpen(Map<WaveletId, List<HashedVersion>> knownWavelets,
      Map<WaveletId, Set<SegmentId>> segmentIds, Map<WaveletId, WaveletDelta> unacknowledgedDeltas) {
    expectations.add(new Object[] {Method.OPEN, knownWavelets, segmentIds, unacknowledgedDeltas});
  }

  public void expectSubmitDelta(WaveletId waveletId, WaveletDelta delta) {
    expectations.add(new Object[] {Method.SUBMIT, waveletId, delta});
  }

  public void expectClose() {
    expectations.add(new Object[] {Method.CLOSE});
  }

  public void expectDisconnect() {
    expectations.add(new Object[] {Method.DISCONNECT});
  }

  public void checkExpectationsSatisified() {
    assertTrue("Unsatisified view channel expectations", expectations.isEmpty());
  }

  @Override
  public void fetchWaveView(IdFilter waveletFilter, boolean fromLastRead, 
      int minReplySize, int maxReplySize, int maxBlipCount, FetchWaveViewCallback callback) {
    Object[] expected = expectations.remove();
    assertEquals(expected[0], Method.FETCH_WAVE_VIEW);
    assertEquals(expected[1], waveletFilter);
    assertEquals(expected[2], fromLastRead);
    assertEquals(expected[3], minReplySize);
    assertEquals(expected[4], maxReplySize);
    assertEquals(expected[5], maxBlipCount);
    fetchWaveViewCallbacks.add(callback);
  }

  @Override
  public void fetchFragments(WaveletId waveletId, Map<SegmentId, Long> segments, long endVersion, 
      int minReplySize, int maxReplySize, FetchFragmentsCallback callback) {
    Object[] expected = expectations.remove();
    assertEquals(expected[0], Method.FETCH_FRAGMENTS);
    assertEquals(expected[1], waveletId);
    assertEquals(expected[2], segments);
    assertEquals(expected[3], endVersion);
    assertEquals(expected[4], minReplySize);
    assertEquals(expected[5], maxReplySize);
    fetchDocumentsCallbacks.add(callback);
  }

  @Override
  public void open(Map<WaveletId, List<HashedVersion>> knownWavelets, Map<WaveletId, Set<SegmentId>> segmentIds, 
      Map<WaveletId, WaveletDelta> unacknowledgedDeltas, Listener listener) {
    Object[] expected = expectations.remove();
    assertEquals(expected[0], Method.OPEN);
    assertEquals(expected[1], knownWavelets);
    assertEquals(expected[2], segmentIds);
    assertEquals(expected[3], unacknowledgedDeltas);
    listeners.add(listener);
  }

  @Override
  public void submitDelta(WaveletId waveletId, WaveletDelta delta, SubmitCallback callback) {
    Object[] expected = expectations.remove();
    assertEquals(expected[0], Method.SUBMIT);
    assertEquals(expected[1], waveletId);
    assertTrue(CcTestingUtils.deltasAreEqual((WaveletDelta) expected[2], delta));
    awaitingAck.add(callback);
  }

  @Override
  public void close() {
    Object[] expected = expectations.remove();
    assertEquals(expected[0], Method.CLOSE);
    // Emulate the real view channel in sending close() synchronously.
    for (Listener l : listeners) {
      l.onClosed();
    }
  }

  @Override
  public void disconnect() {
    Object[] expected = expectations.remove();
    assertEquals(expected[0], Method.DISCONNECT);
    expectations.clear();
    fetchWaveViewCallbacks.clear();
    fetchDocumentsCallbacks.clear();
    listeners.clear();
    awaitingAck.clear();
  }
  
  public Listener takeListener() {
    return listeners.remove();
  }

  public void ackSubmit(int opsApplied, long version, byte[] signature, long timestamp) throws ChannelException {
    SubmitCallback nextCallback = awaitingAck.remove();
    nextCallback.onResponse(opsApplied, HashedVersion.of(version, signature), timestamp,
        new ReturnStatus(ReturnCode.OK));
  }

  public void nackSubmit(String reason, long version, byte[] signature) throws ChannelException {
    SubmitCallback nextCallback = awaitingAck.remove();
    nextCallback.onResponse(0, HashedVersion.of(version, signature), 0, new ReturnStatus(ReturnCode.OK));
  }
}
