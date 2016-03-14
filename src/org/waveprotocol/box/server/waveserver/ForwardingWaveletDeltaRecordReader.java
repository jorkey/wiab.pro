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

import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecordReader;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.box.common.Receiver;

import java.io.IOException;
import org.waveprotocol.box.common.ThrowableReceiver;

/**
 * Forwards calls to a delegate {@link WaveletDeltaRecordReader}.
 *
 * @author soren@google.com (Soren Lassen)
 */
public abstract class ForwardingWaveletDeltaRecordReader implements WaveletDeltaRecordReader {

  protected abstract WaveletDeltaRecordReader delegate();

  @Override
  public boolean isEmpty() {
    return delegate().isEmpty();
  }

  @Override
  public HashedVersion getLastModifiedVersion() {
    return delegate().getLastModifiedVersion();
  }

  @Override
  public WaveletDeltaRecord getDeltaByStartVersion(long version) throws IOException {
    return delegate().getDeltaByStartVersion(version);
  }

  @Override
  public WaveletDeltaRecord getDeltaByEndVersion(long version) throws IOException {
    return delegate().getDeltaByEndVersion(version);
  }

  @Override
  public WaveletDeltaRecord getDeltaByArbitraryVersion(long version) throws IOException {
    return delegate().getDeltaByArbitraryVersion(version);
  }

  @Override
  public void getDeltasFromVersion(long start, ThrowableReceiver<WaveletDeltaRecord, IOException> receiver) throws IOException {
    delegate().getDeltasFromVersion(start, receiver);
  }
}
