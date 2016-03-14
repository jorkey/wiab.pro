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

import com.google.common.util.concurrent.ListenableFuture;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.box.server.waveletstate.delta.DeltaWaveletState;
import org.waveprotocol.box.server.waveserver.DeltaWaveletStateMap.DeltaWaveletStateAccessor;

/**
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class DeltaWaveletStateAccessorStub implements DeltaWaveletStateAccessor {
  private final DeltaWaveletState state;

  public DeltaWaveletStateAccessorStub(DeltaWaveletState state) {
    this.state = state;
  }

  @Override
  public DeltaWaveletState get() throws WaveletStateException {
    return state;
  }

  @Override
  public DeltaWaveletState getIfPresent() throws WaveletStateException {
    return state;
  }

  @Override
  public ListenableFuture close() {
    return state.close();
  }
}
