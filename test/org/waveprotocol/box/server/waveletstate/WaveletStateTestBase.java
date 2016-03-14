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

package org.waveprotocol.box.server.waveletstate;

import org.waveprotocol.box.server.executor.TestExecutorsModule;
import org.waveprotocol.box.server.persistence.TestStoresModule;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.waveserver.AppliedDeltaUtil;
import org.waveprotocol.box.server.waveserver.ByteStringMessage;
import org.waveprotocol.box.server.waveserver.WaveServerTestUtil;
import org.waveprotocol.wave.federation.Proto.ProtocolAppliedWaveletDelta;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.testing.DeltaTestUtil;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionFactoryImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.protobuf.InvalidProtocolBufferException;

import junit.framework.TestCase;

/**
 * Tests for {@link DeltaWaveletState} implementations.
 *
 * @author anorth@google.com (Alex North)
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class WaveletStateTestBase extends TestCase {
  private static final String DOMAIN = "example.com";

  private static final WaveId WAVE_ID = WaveId.of(DOMAIN, "waveId");
  private static final WaveletId WAVELET_ID = WaveletId.of(DOMAIN, "waveletId");
  protected static final WaveletName WAVELET_NAME = WaveletName.of(WAVE_ID, WAVELET_ID);

  protected static final ParticipantId AUTHOR = ParticipantId.ofUnsafe("author@example.com");
  protected static final DeltaTestUtil UTIL = new DeltaTestUtil(AUTHOR);
  protected static final long TS = 1234567890L;
  protected static final long TS2 = TS + 1;
  protected static final long TS3 = TS2 + 1;

  static final IdURIEncoderDecoder URI_CODEC =
      new IdURIEncoderDecoder(new JavaUrlCodec());
  static final HashedVersionFactory HASH_FACTORY = new HashedVersionFactoryImpl(URI_CODEC);
  protected static final HashedVersion V0 = HASH_FACTORY.createVersionZero(WAVELET_NAME);

  protected WaveletDeltaRecord d1;
  protected WaveletDeltaRecord d2;
  protected WaveletDeltaRecord d3;

  protected Injector injector;
  protected WaveletStateFactory waveletStateFactory;

  protected void init() throws Exception {
    d1 = makeDelta(V0, TS, 2);
    d2 = makeDelta(d1.getResultingVersion(), TS2, 2);
    d3 = makeDelta(d2.getResultingVersion(), TS3, 1);

    injector = Guice.createInjector(new AbstractModule() {

      @Override
      protected void configure() {
        install(new TestExecutorsModule());
        install(new TestStoresModule());
        install(new TestWaveletStateModule());
      }
    });
    
    waveletStateFactory = new WaveletStateFactory(injector);
  }

  /**
   * Creates a delta of no-ops and builds the corresponding applied and
   * transformed delta objects.
   */
  static WaveletDeltaRecord makeDelta(HashedVersion appliedAtVersion, long timestamp,
      int numOps) throws InvalidProtocolBufferException {
    // Use no-op delta so the ops can actually apply.
    WaveletDelta delta = UTIL.makeNoOpDelta(appliedAtVersion, timestamp, numOps);
    ByteStringMessage<ProtocolAppliedWaveletDelta> appliedDelta =
        WaveServerTestUtil.buildAppliedDelta(delta, timestamp);
    TransformedWaveletDelta transformedDelta =
        AppliedDeltaUtil.buildTransformedDelta(appliedDelta, delta);
    return new WaveletDeltaRecord(appliedAtVersion, appliedDelta, transformedDelta);
  }
}
