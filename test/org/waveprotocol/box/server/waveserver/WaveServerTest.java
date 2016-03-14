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
package org.waveprotocol.box.server.waveserver;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import static junit.framework.Assert.fail;
import junit.framework.TestCase;
import org.mockito.Matchers;
import static org.mockito.Matchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.executor.ExecutorAnnotations;
import org.waveprotocol.box.server.executor.TestExecutorsModule;
import org.waveprotocol.box.server.persistence.TestStoresModule;
import org.waveprotocol.box.server.persistence.deltas.DeltaStore;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.serialize.OperationSerializer;
import org.waveprotocol.box.server.waveletstate.delta.DeltaWaveletState;
import org.waveprotocol.box.server.waveletstate.TestWaveletStateModule;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.box.server.waveletstate.WaveletStateFactory;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletState;
import org.waveprotocol.box.server.waveserver.LocalWaveletContainer.Factory;
import org.waveprotocol.box.server.waveserver.WaveletProvider.OpenRequestCallback;
import org.waveprotocol.box.server.waveserver.WaveletProvider.SubmitRequestCallback;
import org.waveprotocol.wave.clientserver.ReturnCode;
import org.waveprotocol.wave.federation.Proto.ProtocolSignature;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.federation.WaveletFederationProvider;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.AddParticipant;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionZeroFactoryImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;

/**
 * @author josephg@gmail.com (Joseph Gentle)
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class WaveServerTest extends TestCase {
  private static final HashedVersionFactory V0_HASH_FACTORY =
      new HashedVersionZeroFactoryImpl(new IdURIEncoderDecoder(new JavaUrlCodec()));

  private static final String DOMAIN = "example.com";
  private static final WaveId WAVE_ID = WaveId.of(DOMAIN, "abc123");
  private static final WaveletId WAVELET_ID = WaveletId.of(DOMAIN, "conv+root");
  private static final WaveletName WAVELET_NAME = WaveletName.of(WAVE_ID, WAVELET_ID);

  private static final ParticipantId USER1 = ParticipantId.ofUnsafe("user1@" + DOMAIN);
  private static final ParticipantId USER2 = ParticipantId.ofUnsafe("user2@" + DOMAIN);

  private static final WaveletOperationContext CONTEXT = new WaveletOperationContext(USER1, 1234567890, 1);

  private static WaveletOperation addParticipantToWavelet(ParticipantId user) {
    return new AddParticipant(CONTEXT, user);
  }

  @Mock private SignatureHandler localSigner;
  @Mock private WaveletFederationProvider federationRemote;
  @Mock private WaveletNotificationDispatcher notifiee;
  @Mock private RemoteWaveletContainer.Factory remoteWaveletContainerFactory;
  @Mock private WaveDigester waveDigester;

  private CertificateManager certificateManager;
  private WaveMap waveMap;
  private WaveServerImpl waveServer;
  private Injector injector;

  @Override
  protected void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    when(localSigner.getDomain()).thenReturn(DOMAIN);
    when(localSigner.getSignerInfo()).thenReturn(null);
    when(localSigner.sign(Matchers.<ByteStringMessage<ProtocolWaveletDelta>>any()))
        .thenReturn(ImmutableList.<ProtocolSignature>of());

    injector = Guice.createInjector(new AbstractModule() {

      @Override
      protected void configure() {
        install(new TestExecutorsModule());
        install(new TestStoresModule());
        install(new TestWaveletStateModule());
      }
    });

    certificateManager = new CertificateManagerImpl(true, localSigner, null, null);
    Factory localWaveletContainerFactory = new LocalWaveletContainer.Factory() {

      @Override
      public LocalWaveletContainer create(WaveletNotificationSubscriber notifiee,
              WaveletName waveletName, String waveDomain) {
        WaveletStateFactory waveletStateFactory = new WaveletStateFactory(injector);
        DeltaWaveletState deltaState = waveletStateFactory.createDeltaWaveletState(waveletName);
        try {
          deltaState.open();
        } catch (WaveletStateException ex) {
          throw new RuntimeException(ex);
        }
        ListenableFuture<SegmentWaveletState> segmentStateFuture = WaveServerModule.loadSegmentWaveletState(
          waveletName, waveletStateFactory,
          injector.getInstance(Key.get(Executor.class, ExecutorAnnotations.WaveletLoadingExecutor.class)));
        return new LocalWaveletContainerImpl(waveletName, waveDomain, notifiee,
            new DeltaWaveletStateAccessorStub(deltaState), segmentStateFuture,
            injector.getInstance(Key.get(Executor.class, ExecutorAnnotations.StorageIndexingExecutor.class)),
            injector.getInstance(Key.get(Executor.class, ExecutorAnnotations.StorageContinuationExecutor.class)));
      }
    };

    waveMap =
        new WaveMap(injector.getInstance(DeltaStore.class), notifiee, notifiee, localWaveletContainerFactory,
            remoteWaveletContainerFactory, "example.com", 10, 1000, waveDigester,
            injector.getInstance(Key.get(Executor.class, ExecutorAnnotations.LookupExecutor.class)));
    waveServer =
        new WaveServerImpl(injector.getInstance(Key.get(Executor.class, ExecutorAnnotations.LookupExecutor.class)),
            certificateManager, federationRemote, waveMap);
    waveServer.initialize();
  }

  public void testWaveIdsList() throws WaveServerException {
    waveMap.getOrCreateLocalWavelet(WAVELET_NAME);
    ExceptionalIterator<WaveId, WaveServerException> waves = waveServer.getWaveIds();
    assertTrue(waves.hasNext());
    assertEquals(WAVE_ID, waves.next());
  }

  public void testWaveletNotification() {
    submitDeltaToNewWavelet(WAVELET_NAME, USER1, addParticipantToWavelet(USER2));

    verify(notifiee).waveletUpdate(eq(WAVELET_NAME),
        Matchers.<ImmutableList<WaveletDeltaRecord>>any(), eq(ImmutableSet.of(DOMAIN)));
  }

  private void submitDeltaToNewWavelet(final WaveletName name, ParticipantId user,
      WaveletOperation... ops) {
    HashedVersion version = V0_HASH_FACTORY.createVersionZero(name);

    WaveletDelta delta = new WaveletDelta(user, version, ImmutableList.copyOf(ops));

    final ProtocolWaveletDelta protoDelta = OperationSerializer.serialize(delta);

    waveServer.openRequest(name, Collections.singletonList(version), user, new OpenRequestCallback() {

      @Override
      public void onSuccess(HashedVersion connectVersion, HashedVersion lastCommittedVersion) {
        waveServer.submitRequest(name, protoDelta, new SubmitRequestCallback() {
          @Override
          public void onSuccess(int operationsApplied, HashedVersion hashedVersionAfterApplication,
              long applicationTimestamp) {
            // Wavelet was submitted.
          }

          @Override
          public void onFailure(ReturnCode responseCode, String errorMessage) {
            fail("Could not submit callback: " + responseCode + ": " + errorMessage);
          }
        });
      }

      @Override
      public void onFailure(ReturnCode responseCode, String errorMessage) {
        fail("Could not open callback: " + responseCode + ": " + errorMessage);
      }

    });
  }
}
