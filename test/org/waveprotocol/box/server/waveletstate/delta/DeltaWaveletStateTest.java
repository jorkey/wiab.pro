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

package org.waveprotocol.box.server.waveletstate.delta;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.protobuf.InvalidProtocolBufferException;

import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.box.server.waveletstate.WaveletStateFactory;
import org.waveprotocol.box.server.waveletstate.WaveletStateTestBase;

import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.version.HashedVersion;

import org.waveprotocol.box.common.ThrowableReceiver;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Tests for {@link DeltaWaveletState} implementations.
 *
 * @author anorth@google.com (Alex North)
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class DeltaWaveletStateTest extends WaveletStateTestBase {

  private DeltaWaveletState target;

  @Override
  public void setUp() throws Exception {
    init();

    target = waveletStateFactory.createDeltaWaveletState(WAVELET_NAME);
    target.open();
  }

  public void testReportsWaveletName() {
    assertEquals(WAVELET_NAME, target.getWaveletName());
  }

  public void testEmptyStateIsEmpty() {
    assertEquals(V0, target.getHashedVersion(0));

    assertNull(target.getTransformedDelta(V0));
    assertNull(target.getAppliedDelta(V0));
  }

  public void testHashedVersionAccessibleOnDeltaBoundaries() throws Exception {
    appendDeltas(d1, d2, d3);
    assertEquals(V0, target.getHashedVersion(0));
    assertEquals(d1.getResultingVersion(), target.getHashedVersion(2));
    assertEquals(d2.getResultingVersion(), target.getHashedVersion(4));
    assertEquals(d3.getResultingVersion(), target.getHashedVersion(5));
    assertNull(target.getHashedVersion(1));
    assertNull(target.getHashedVersion(3));
    assertNull(target.getHashedVersion(6));
  }

  public void testDeltasAccessibleByBeginVersion() throws Exception {
    appendDeltas(d1, d2, d3);
    assertEquals(d1.getTransformedDelta(), target.getTransformedDelta(V0));
    assertEquals(d1.getAppliedDelta(), target.getAppliedDelta(V0));

    assertEquals(d2.getTransformedDelta(), target.getTransformedDelta(d1.getResultingVersion()));
    assertEquals(d2.getAppliedDelta(), target.getAppliedDelta(d1.getResultingVersion()));

    assertEquals(d3.getTransformedDelta(), target.getTransformedDelta(d2.getResultingVersion()));
    assertEquals(d3.getAppliedDelta(), target.getAppliedDelta(d2.getResultingVersion()));

    // Wrong hashes return null.
    assertNull(target.getTransformedDelta(HashedVersion.unsigned(0)));
    assertNull(target.getAppliedDelta(HashedVersion.unsigned(0)));
  }

  public void testDeltasAccesssibleByEndVersion() throws Exception {
    appendDeltas(d1, d2, d3);
    for (WaveletDeltaRecord d : Arrays.asList(d1, d2, d3)) {
      assertEquals(d.getTransformedDelta(),
          target.getTransformedDeltaByEndVersion(d.getResultingVersion()));
      assertEquals(d.getAppliedDelta(),
          target.getAppliedDeltaByEndVersion(d.getResultingVersion()));
    }

    // Wrong hashes return null.
    assertNull(target.getTransformedDeltaByEndVersion(
        HashedVersion.unsigned(d1.getResultingVersion().getVersion())));
    assertNull(target.getAppliedDeltaByEndVersion(
        HashedVersion.unsigned(d1.getResultingVersion().getVersion())));
  }

  public void testDeltaHistoryRequiresCorrectHash() throws Exception {
    appendDeltas(d1);
    target.persist(d1.getResultingVersion());
    ThrowableReceiver<WaveletDeltaRecord, WaveletStateException> deltasReceiver =
      new ThrowableReceiver<WaveletDeltaRecord, WaveletStateException>() {

      @Override
      public boolean put(WaveletDeltaRecord delta) throws WaveletStateException {
        return true;
      }
    };

    // Wrong start hash.
    checkGetDeltasThrowsException(HashedVersion.unsigned(0), d1.getResultingVersion(), deltasReceiver,
        IllegalArgumentException.class);

    // Wrong end hash.
    checkGetDeltasThrowsException(V0, HashedVersion.unsigned(d1.getResultingVersion().getVersion()),
        deltasReceiver, IllegalArgumentException.class);
  }

  @SuppressWarnings("rawtypes")
  private void checkGetDeltasThrowsException(HashedVersion startVersion, HashedVersion endVersion,
      ThrowableReceiver<WaveletDeltaRecord, WaveletStateException> receiver, Class exceptionClass) {
    try {
      target.getDeltaHistory(startVersion, endVersion, receiver);
      fail("Expected exception not thrown.");
    } catch (Exception ex) {
      assertEquals(IllegalArgumentException.class, exceptionClass);
    }
  }

  public void testSingleDeltaHistoryAccessible() throws Exception {
    appendDeltas(d1);
    target.persist(d1.getResultingVersion());
    final List<WaveletDeltaRecord> deltas = new LinkedList<>();
    ThrowableReceiver<WaveletDeltaRecord, WaveletStateException> deltasReceiver =
      new ThrowableReceiver<WaveletDeltaRecord, WaveletStateException>() {

      @Override
      public boolean put(WaveletDeltaRecord delta) throws WaveletStateException {
        deltas.add(delta);
        return true;
      }
    };
    target.getDeltaHistory(V0, d1.getResultingVersion(), deltasReceiver);
    assertEquals(1, deltas.size());
    assertEquals(d1, deltas.get(0));
  }

  public void testDeltaHistoryQueriesCorrectHistory() throws Exception {
    appendDeltas(d1, d2, d3);
    target.persist(d3.getResultingVersion());

    checkHistoryForDeltas(d1);
    checkHistoryForDeltas(d1, d2);
    checkHistoryForDeltas(d2, d3);
    checkHistoryForDeltas(d1, d2, d3);
  }

  public void testDeltaHistoryInterruptQueriesCorrectHistory() throws Exception {
    appendDeltas(d1, d2, d3);
    target.persist(d3.getResultingVersion());

    checkHistoryForDeltasWithInterrupt(0, d1);
    checkHistoryForDeltasWithInterrupt(1, d1, d2);
    checkHistoryForDeltasWithInterrupt(2, d1, d2, d3);
  }

  /**
   * Checks that a request for the deltas spanning a contiguous sequence of
   * delta facets produces correct results.
   */
  private void checkHistoryForDeltas(WaveletDeltaRecord... deltas) throws WaveletStateException {
    HashedVersion beginVersion = deltas[0].getAppliedAtVersion();
    HashedVersion endVersion = deltas[deltas.length - 1].getTransformedDelta().getResultingVersion();

    {
      List<WaveletDeltaRecord> expected = Lists.newArrayListWithExpectedSize(deltas.length);
      for (WaveletDeltaRecord d : deltas) {
        expected.add(d);
      }
      final List<WaveletDeltaRecord> receivedDeltas = new LinkedList<>();
      ThrowableReceiver<WaveletDeltaRecord, WaveletStateException> deltasReceiver =
        new ThrowableReceiver<WaveletDeltaRecord, WaveletStateException>() {

        @Override
        public boolean put(WaveletDeltaRecord delta) throws WaveletStateException {
          receivedDeltas.add(delta);
          return true;
        }
      };
      target.getDeltaHistory(beginVersion, endVersion, deltasReceiver);
      assertTrue(Iterables.elementsEqual(expected, receivedDeltas));
    }
  }

private void checkHistoryForDeltasWithInterrupt(final int interruptIndex, WaveletDeltaRecord... deltas) throws WaveletStateException {
    HashedVersion beginVersion = deltas[0].getAppliedAtVersion();
    HashedVersion endVersion = deltas[interruptIndex].getTransformedDelta().getResultingVersion();

    {
      List<WaveletDeltaRecord> expected = Lists.newArrayListWithExpectedSize(interruptIndex+1);
      for (int i=0; i <= interruptIndex; i++) {
        expected.add(deltas[i]);
      }
      final AtomicInteger index = new AtomicInteger(0);
      final List<WaveletDeltaRecord> transformedDeltas = new ArrayList<>();
      target.getDeltaHistory(beginVersion, endVersion, new ThrowableReceiver<WaveletDeltaRecord, WaveletStateException>() {

        @Override
        public boolean put(WaveletDeltaRecord delta) throws WaveletStateException {
          transformedDeltas.add(delta);
          return index.getAndIncrement() < interruptIndex;
        }
      });
      assertTrue(Iterables.elementsEqual(expected, transformedDeltas));
    }
  }

  public void checkSingleDeltaPersistFutureDone() throws Exception {
    appendDeltas(d1);
    Future<Void> future = target.persist(d1.getResultingVersion());
    assertEquals(null, future.get());
    assertEquals(d1.getResultingVersion(), target.getLastPersistedVersion());
  }

  public void checkManyDeltasPersistFutureDone() throws Exception {
    appendDeltas(d1, d2, d3);
    Future<Void> future = target.persist(d3.getResultingVersion());
    assertEquals(null, future.get());
    assertEquals(d3.getResultingVersion(), target.getLastPersistedVersion());
  }

  public void testCanPersistOnlySomeDeltas() throws Exception {
    appendDeltas(d1, d2, d3);
    Future<Void> future = target.persist(d2.getResultingVersion());
    assertEquals(null, future.get());
    assertEquals(d2.getResultingVersion(), target.getLastPersistedVersion());

    future = target.persist(d3.getResultingVersion());
    assertEquals(null, future.get());
    assertEquals(d3.getResultingVersion(), target.getLastPersistedVersion());
  }

  /**
   * Applies a delta to the target.
   */
  private void appendDeltas(WaveletDeltaRecord... deltas) throws InvalidProtocolBufferException,
      OperationException {
    for (WaveletDeltaRecord delta : deltas) {
      target.appendDelta(delta);
    }
  }
}
