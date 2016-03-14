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

import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import java.util.Arrays;
import java.util.concurrent.Executor;
import junit.framework.TestCase;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.executor.ExecutorAnnotations;
import org.waveprotocol.box.server.executor.TestExecutorsModule;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.TestStoresModule;
import org.waveprotocol.box.server.persistence.deltas.DeltaStore;
import org.waveprotocol.box.server.waveletstate.delta.DeltaWaveletState;
import org.waveprotocol.box.server.waveletstate.TestWaveletStateModule;
import org.waveprotocol.box.server.waveletstate.WaveletStateFactory;
import org.waveprotocol.box.server.waveletstate.block.BlockWaveletState;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletState;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.OperationException;

/**
 * @author josephg@gmail.com (Joseph Gentle)
 * @author soren@google.com (Soren Lassen)
 */
public class WaveMapTest extends TestCase {

  private static final String DOMAIN = "example.com";
  private static final WaveId WAVE_ID = WaveId.of(DOMAIN, "abc123");
  private static final WaveletId WAVELET_ID = WaveletId.of(DOMAIN, "conv+root");
  private static final WaveletName WAVELET_NAME = WaveletName.of(WAVE_ID, WAVELET_ID);

  @Mock private WaveletNotificationDispatcher notifiee;
  @Mock private RemoteWaveletContainer.Factory remoteWaveletContainerFactory;
  @Mock private WaveDigester waveDigester;
  @Mock private DeltaStore deltaStore;

  private WaveMap waveMap;

  @Override
  protected void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    final Injector injector = Guice.createInjector(new AbstractModule() {

      @Override
      protected void configure() {
        install(new TestExecutorsModule());
        install(new TestStoresModule());
        install(new TestWaveletStateModule());
      }
    });

    final WaveletStateFactory waveletStateFactory = new WaveletStateFactory(injector);
    
    LocalWaveletContainer.Factory localWaveletContainerFactory =
        new LocalWaveletContainer.Factory() {
          @Override
          public LocalWaveletContainer create(WaveletNotificationSubscriber notifiee,
              WaveletName waveletName, String domain) {
            DeltaWaveletState deltaState;
            BlockWaveletState blockState;
            SegmentWaveletState segmentState;
            try {
              deltaState = waveletStateFactory.createDeltaWaveletState(waveletName);
              deltaState.open();
              blockState = waveletStateFactory.createBlockWaveletState(waveletName);
              blockState.open();
              segmentState = waveletStateFactory.createSegmentWaveletState(waveletName, blockState);
              segmentState.open();
            } catch (WaveletStateException e) {
              throw new RuntimeException(e);
            }
            return new LocalWaveletContainerImpl(waveletName, DOMAIN, notifiee,
                new DeltaWaveletStateAccessorStub(deltaState), Futures.immediateFuture(segmentState),
                injector.getInstance(Key.get(Executor.class, ExecutorAnnotations.StorageIndexingExecutor.class)),
                injector.getInstance(Key.get(Executor.class, ExecutorAnnotations.StorageContinuationExecutor.class)));
          }
        };

    waveMap = new WaveMap(deltaStore, notifiee, notifiee, localWaveletContainerFactory,
        remoteWaveletContainerFactory, "example.com", 10, 1000, waveDigester,
        injector.getInstance(Key.get(Executor.class, ExecutorAnnotations.LookupExecutor.class)));
  }

  public void testWavesStartWithNoWavelets() throws WaveletStateException, PersistenceException {
    when(deltaStore.lookup(WAVE_ID)).thenReturn(ImmutableSet.<WaveletId>of());
    assertNull(waveMap.getLocalWavelet(WAVELET_NAME));
    assertNull(waveMap.getRemoteWavelet(WAVELET_NAME));
  }

  public void testWaveAvailableAfterLoad() throws PersistenceException, WaveServerException {
    when(deltaStore.getWaveIdIterator()).thenReturn(eitr(WAVE_ID));

    ExceptionalIterator<WaveId, WaveServerException> waves = waveMap.getWaveIds();
    assertTrue(waves.hasNext());
    assertEquals(WAVE_ID, waves.next());
  }

  public void testWaveletAvailableAfterLoad() throws WaveletStateException, PersistenceException {
    when(deltaStore.getWaveIdIterator()).thenReturn(eitr(WAVE_ID));
    when(deltaStore.lookup(WAVE_ID)).thenReturn(ImmutableSet.<WaveletId>of(WAVELET_ID));

    assertNotNull(waveMap.getLocalWavelet(WAVELET_NAME));
  }

  public void testGetOrCreateCreatesWavelets() throws WaveletStateException, PersistenceException {
    when(deltaStore.lookup(WAVE_ID)).thenReturn(ImmutableSet.<WaveletId>of());
    LocalWaveletContainer wavelet = waveMap.getOrCreateLocalWavelet(WAVELET_NAME);
    assertSame(wavelet, waveMap.getLocalWavelet(WAVELET_NAME));
  }

  private ExceptionalIterator<WaveId, PersistenceException> eitr(WaveId... waves) {
    return ExceptionalIterator.FromIterator.<WaveId, PersistenceException>create(
        Arrays.asList(waves).iterator());
  }
}
