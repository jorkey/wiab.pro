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

import org.waveprotocol.box.server.waveletstate.delta.DeltaWaveletState;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletState;
import org.waveprotocol.box.server.waveletstate.block.BlockWaveletState;
import org.waveprotocol.wave.model.id.WaveletName;

import com.google.inject.Inject;
import com.google.inject.Injector;

/**
 * Creates wavelet states by Guice injector.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class WaveletStateFactory {
  private final Injector injector;

  @Inject
  public WaveletStateFactory(Injector injector) {
    this.injector = injector;
  }

  public DeltaWaveletState createDeltaWaveletState(WaveletName waveletName) {
    DeltaWaveletState.GuiceFactory factory = injector.getInstance(DeltaWaveletState.GuiceFactory.class);
    DeltaWaveletState deltaState = factory.create(waveletName);
    return deltaState;
  }

  public BlockWaveletState createBlockWaveletState(WaveletName waveletName) {
    BlockWaveletState.GuiceFactory factory = injector.getInstance(BlockWaveletState.GuiceFactory.class);
    return factory.create(waveletName);
  }

  public SegmentWaveletState createSegmentWaveletState(WaveletName waveletName, BlockWaveletState blockState) {
    SegmentWaveletState.GuiceFactory factory = injector.getInstance(SegmentWaveletState.GuiceFactory.class);
    return factory.create(waveletName, blockState);
  }
}
