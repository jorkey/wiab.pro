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

import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;

import com.google.common.collect.ImmutableSet;

/**
 * A basic implementation for an empty ReadableWaveletData.
 */
public class EmptyWaveletSnapshot implements ReadableWaveletData {

  private final WaveId waveId;
  private final WaveletId waveletId;
  private final ParticipantId creator;
  private final long creationTime;
  private final HashedVersion hashedVersion;

  /**
   * Creates an empty snapshot.
   */
  public EmptyWaveletSnapshot(WaveId waveId, WaveletId waveletId, ParticipantId creator,
      HashedVersion version, long creationTime) {
    this.waveId = waveId;
    this.waveletId = waveletId;
    this.creator = creator;
    this.hashedVersion = version;
    this.creationTime = creationTime;
  }

  @Override
  public long getCreationTime() {
    return creationTime;
  }

  @Override
  public ParticipantId getCreator() {
    return creator;
  }

  @Override
  public long getVersion() {
    return hashedVersion.getVersion();
  }

  @Override
  public HashedVersion getHashedVersion() {
    return hashedVersion;
  }

  @Override
  public ReadableBlipData getBlip(String documentName) {
    return null;
  }

  @Override
  public ImmutableSet<String> getDocumentIds() {
    return ImmutableSet.of();
  }

  @Override
  public long getLastModifiedTime() {
    return getCreationTime();
  }

  @Override
  public ImmutableSet<ParticipantId> getParticipants() {
    return ImmutableSet.of();
  }

  @Override
  public ImmutableSet<String> getTags() {
    return ImmutableSet.of();
  }

  @Override
  public WaveId getWaveId() {
    return waveId;
  }

  @Override
  public WaveletId getWaveletId() {
    return waveletId;
  }
}
