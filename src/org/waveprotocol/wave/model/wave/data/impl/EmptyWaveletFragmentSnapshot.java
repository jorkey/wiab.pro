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

package org.waveprotocol.wave.model.wave.data.impl;

import com.google.common.collect.ImmutableSet;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletFragmentData;

import java.util.Set;
import java.util.Collections;

/**
 * A basic implementation for an empty ReadableWaveletFragmentData.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class EmptyWaveletFragmentSnapshot extends EmptyWaveletSnapshot implements ReadableWaveletFragmentData {

  public EmptyWaveletFragmentSnapshot(WaveId waveId, WaveletId waveletId, ParticipantId creator,
      HashedVersion version, long creationTime) {
    super(waveId, waveletId, creator, version, creationTime);
  }

  @Override
  public ImmutableSet<SegmentId> getSegmentIds() {
    return ImmutableSet.of();
  }

  @Override
  public boolean isRaw(SegmentId segmentId) {
    return false;
  }
}
