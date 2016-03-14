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

import org.waveprotocol.box.server.waveletstate.delta.DeltaWaveletStateImpl;
import org.waveprotocol.box.server.waveletstate.delta.DeltaWaveletState;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletState;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletStateImpl;
import org.waveprotocol.box.server.waveletstate.block.BlockWaveletState;
import org.waveprotocol.box.server.waveletstate.block.BlockWaveletStateImpl;
import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class TestWaveletStateModule extends AbstractModule {

  public TestWaveletStateModule() {
  }

  @Override
  public void configure() {
    install(new FactoryModuleBuilder().implement(DeltaWaveletState.class, DeltaWaveletStateImpl.class).build(
      DeltaWaveletState.GuiceFactory.class));
    install(new FactoryModuleBuilder().implement(BlockWaveletState.class, BlockWaveletStateImpl.class).build(
      BlockWaveletState.GuiceFactory.class));
    install(new FactoryModuleBuilder().implement(SegmentWaveletState.class, SegmentWaveletStateImpl.class).build(
      SegmentWaveletState.GuiceFactory.class));
  }
}
