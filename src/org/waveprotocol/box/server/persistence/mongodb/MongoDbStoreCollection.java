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

package org.waveprotocol.box.server.persistence.mongodb;

import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.box.server.persistence.deltas.DeltaStore;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.common.ThrowableReceiver;

import java.io.IOException;
import java.util.Collection;

/**
 * A MongoDB based Delta Access implementation using a simple <b>deltas</b>
 * collection, storing a delta record per each MongoDb document.
 *
 * @author pablojan@gmail.com (Pablo Ojanguren)
 *
 * See release in Apache Wiab.
 */
public class MongoDbStoreCollection implements DeltaStore.DeltaAccess {

  @Override
  public WaveletName getWaveletName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEmpty() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HashedVersion getLastModifiedVersion() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getLastModifiedTime() {
    throw new UnsupportedOperationException();
  }

  @Override
  public WaveletDeltaRecord getDeltaByStartVersion(long version) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public WaveletDeltaRecord getDeltaByEndVersion(long version) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public WaveletDeltaRecord getDeltaByArbitraryVersion(long version) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void getDeltasFromVersion(long version, ThrowableReceiver<WaveletDeltaRecord, IOException> receiver) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void append(Collection<WaveletDeltaRecord> deltas) throws PersistenceException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void close() throws IOException {
    throw new UnsupportedOperationException();
  }

  }
