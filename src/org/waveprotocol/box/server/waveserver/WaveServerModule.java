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


import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.executor.ExecutorAnnotations;
import org.waveprotocol.box.server.frontend.ClientFrontend;
import org.waveprotocol.box.server.frontend.ClientFrontendImpl;
import org.waveprotocol.box.server.frontend.SupplementProvider;
import org.waveprotocol.box.server.frontend.SupplementProviderImpl;
import org.waveprotocol.box.server.waveletstate.WaveletStateFactory;
import org.waveprotocol.box.server.waveletstate.block.BlockWaveletState;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletState;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;

import org.waveprotocol.wave.crypto.CachedCertPathValidator;
import org.waveprotocol.wave.crypto.CertPathStore;
import org.waveprotocol.wave.crypto.DefaultCacheImpl;
import org.waveprotocol.wave.crypto.DefaultTimeSource;
import org.waveprotocol.wave.crypto.DefaultTrustRootsProvider;
import org.waveprotocol.wave.crypto.DisabledCertPathValidator;
import org.waveprotocol.wave.crypto.TimeSource;
import org.waveprotocol.wave.crypto.TrustRootsProvider;
import org.waveprotocol.wave.crypto.VerifiedCertChainCache;
import org.waveprotocol.wave.crypto.WaveCertPathValidator;
import org.waveprotocol.wave.crypto.WaveSignatureVerifier;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionFactoryImpl;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Guice Module for the prototype Server.
 *
 */
public class WaveServerModule extends AbstractModule {
  private static final Logger LOG =
      Logger.getLogger(WaveServerModule.class.getName());

  private static final IdURIEncoderDecoder URI_CODEC =
      new IdURIEncoderDecoder(new JavaUrlCodec());
  private static final HashedVersionFactory HASH_FACTORY =
      new HashedVersionFactoryImpl(URI_CODEC);

  private final boolean enableFederation;

  @Inject
  WaveServerModule(@Named(CoreSettings.ENABLE_FEDERATION) boolean enableFederation) {
    this.enableFederation = enableFederation;
  }

  @Override
  protected void configure() {
    bind(TimeSource.class).to(DefaultTimeSource.class).in(Singleton.class);

    if (enableFederation) {
      bind(SignatureHandler.class)
      .toProvider(SigningSignatureHandler.SigningSignatureHandlerProvider.class);
    } else {
      bind(SignatureHandler.class)
      .toProvider(NonSigningSignatureHandler.NonSigningSignatureHandlerProvider.class);
    }

    try {
      bind(WaveSignatureVerifier.class).toConstructor(WaveSignatureVerifier.class.getConstructor(
          WaveCertPathValidator.class, CertPathStore.class));
      bind(VerifiedCertChainCache.class).to(DefaultCacheImpl.class).in(Singleton.class);
      bind(DefaultCacheImpl.class).toConstructor(
          DefaultCacheImpl.class.getConstructor(TimeSource.class));
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }

    bind(WaveletNotificationDispatcher.class).in(Singleton.class);
    bind(WaveBus.class).to(WaveletNotificationDispatcher.class);
    bind(ClientFrontend.class).to(ClientFrontendImpl.class);
    bind(WaveletNotificationSubscriber.class).to(WaveletNotificationDispatcher.class);
    bind(TrustRootsProvider.class).to(DefaultTrustRootsProvider.class).in(Singleton.class);
    bind(CertificateManager.class).to(CertificateManagerImpl.class).in(Singleton.class);
    bind(WaveMap.class).in(Singleton.class);
    bind(WaveletProvider.class).to(WaveServerImpl.class).asEagerSingleton();
    bind(SupplementProvider.class).to(SupplementProviderImpl.class);
    bind(HashedVersionFactory.class).toInstance(HASH_FACTORY);
  }

  @Provides
  @SuppressWarnings("unused")
  private LocalWaveletContainer.Factory provideLocalWaveletContainerFactory(
      final DeltaWaveletStateMap deltaStateMap,
      final WaveletStateFactory waveletStateFactory,
      final @ExecutorAnnotations.WaveletLoadingExecutor Executor waveletLoadingExecutor,
      final @ExecutorAnnotations.StorageIndexingExecutor Executor storageIndexingExecutor,
      final @ExecutorAnnotations.StorageContinuationExecutor Executor storageContinuationExecutor) {
    return new LocalWaveletContainer.Factory() {
      @Override
      public LocalWaveletContainer create(WaveletNotificationSubscriber notifiee,
          WaveletName waveletName, String waveDomain) {
        return new LocalWaveletContainerImpl(waveletName, waveDomain, notifiee,
          deltaStateMap.get(waveletName), loadSegmentWaveletState(waveletName, waveletStateFactory, 
            waveletLoadingExecutor),
          storageIndexingExecutor, storageContinuationExecutor);
      }
    };
  }

  @Provides
  @SuppressWarnings("unused")
  private RemoteWaveletContainer.Factory provideRemoteWaveletContainerFactory(
      final DeltaWaveletStateMap deltaStateMap, final WaveletStateFactory waveletStateFactory,
      final @ExecutorAnnotations.WaveletLoadingExecutor Executor waveletLoadingExecutor,
      final @ExecutorAnnotations.StorageIndexingExecutor Executor storageIndexingExecutor,
      final @ExecutorAnnotations.StorageContinuationExecutor Executor storageContinuationExecutor) {
    return new RemoteWaveletContainer.Factory() {
      @Override
      public RemoteWaveletContainer create(WaveletNotificationSubscriber notifiee,
          WaveletName waveletName, String waveDomain) {
        return new RemoteWaveletContainerImpl(waveletName, notifiee,
          deltaStateMap.get(waveletName), loadSegmentWaveletState(waveletName, waveletStateFactory, 
            waveletLoadingExecutor),
          storageIndexingExecutor, storageContinuationExecutor);
      }
    };
  }

  @Provides
  @SuppressWarnings("unused")
  private WaveCertPathValidator provideWaveCertPathValidator(
      @Named(CoreSettings.WAVESERVER_DISABLE_SIGNER_VERIFICATION) boolean disableSignerVerification,
      TimeSource timeSource, VerifiedCertChainCache certCache,
      TrustRootsProvider trustRootsProvider) {
    if (disableSignerVerification) {
      return new DisabledCertPathValidator();
    } else {
      return new CachedCertPathValidator(certCache, timeSource, trustRootsProvider);
    }
  }
  
  @VisibleForTesting
  static ListenableFuture<SegmentWaveletState> loadSegmentWaveletState(
    final WaveletName waveletName,
    final WaveletStateFactory waveletStateFactory,
    Executor waveletLoadExecutor) {
    ListenableFutureTask<SegmentWaveletState> task
      = ListenableFutureTask.<SegmentWaveletState>create(
        new Callable<SegmentWaveletState>() {

          @Override
          public SegmentWaveletState call()
          throws PersistenceException, OperationException, WaveletStateException, InterruptedException, ExecutionException {
            BlockWaveletState blockState = waveletStateFactory.createBlockWaveletState(waveletName);
            blockState.open();
            SegmentWaveletState state = waveletStateFactory.createSegmentWaveletState(waveletName, blockState);
            state.open();
            return state;
          }
        });
    waveletLoadExecutor.execute(task);
    return task;
  }
}
