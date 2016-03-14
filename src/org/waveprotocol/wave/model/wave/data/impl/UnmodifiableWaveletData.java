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

import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.BlipData;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveletDataListener;

import java.util.Collection;

/**
 * Presents a {@link ReadableWaveletData} as a {@link ObservableWaveletData},
 * where all mutation methods throw {@link UnsupportedOperationException}.
 *
 */
public final class UnmodifiableWaveletData extends ForwardingReadableWaveletData
    implements ObservableWaveletData {

  /**
   * Factory for wrapping {@link ReadableWaveletData}
   * as {@link ObservableWaveletData}.
   */
  public static class Factory implements ObservableWaveletData.Factory<UnmodifiableWaveletData> {
    @Override
    public UnmodifiableWaveletData create(ReadableWaveletData data) {
      return new UnmodifiableWaveletData(data);
    }
  }

  /** Factory instance. */
  public static final UnmodifiableWaveletData.Factory FACTORY = new Factory();

  private final ReadableWaveletData data;

  private UnmodifiableWaveletData(ReadableWaveletData data) {
    this.data = data;
  }

  @Override
  protected ReadableWaveletData delegate() {
    return data;
  }

  @Override
  public UnmodifiableBlipData getBlip(String documentName) {
    ReadableBlipData blip = data.getBlip(documentName);
    if (blip != null) {
      return new UnmodifiableBlipData(blip);
    }
    return null;
  }

  @Override
  public BlipData createBlip(String id, ParticipantId author,
      Collection<ParticipantId> contributors, DocInitialization content,
      long creationTime, long creationVersion,
      long lastModifiedTime, long lastModifiedVersion) {
    throw new UnsupportedOperationException(
        "UnmodifiableWaveletData doesn't support createDocument");
  }

  @Override
  public boolean addParticipant(ParticipantId participant, WaveletOperationContext opContext) {
    throw new UnsupportedOperationException(
        "UnmodifiableWaveletData doesn't support addParticipant");
  }

  @Override
  public boolean addParticipant(
      ParticipantId participant, int position, WaveletOperationContext opContext) {
    throw new UnsupportedOperationException(
        "UnmodifiableWaveletData doesn't support addParticipant");
  }

  @Override
  public boolean removeParticipant(ParticipantId participant, WaveletOperationContext opContext) {
    throw new UnsupportedOperationException(
        "UnmodifiableWaveletData doesn't support removeParticipant");
  }

  @Override
  public HashedVersion setHashedVersion(HashedVersion newHashedVersion) {
    throw new UnsupportedOperationException(
        "UnmodifiableWaveletData doesn't support setHashedVersion");
  }

  @Override
  public long setLastModifiedTime(long newTime) {
    throw new UnsupportedOperationException(
        "UnmodifiableWaveletData doesn't support setLastModifiedTime");
  }

  @Override
  public void addListener(WaveletDataListener listener) { } // there's nothing to hear

  @Override
  public void removeListener(WaveletDataListener listener) { }
}
