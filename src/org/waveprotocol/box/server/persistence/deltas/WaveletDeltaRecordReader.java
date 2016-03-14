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

package org.waveprotocol.box.server.persistence.deltas;

import org.waveprotocol.wave.model.version.HashedVersion;

import java.io.IOException;
import org.waveprotocol.box.common.ThrowableReceiver;

/**
 * Reads wavelet deltas for a wavelet.
 *
 * @author soren@google.com (Soren Lassen)
 */
public interface WaveletDeltaRecordReader {

  /** @return true if the collection contains no deltas */
  boolean isEmpty();

  /** @return the resulting version of the last delta */
  HashedVersion getLastModifiedVersion();
  
  /** @return the timestamp of the last delta */
  long getLastModifiedTime();

  /** @return the delta applied at this version, if any, otherwise null */
  WaveletDeltaRecord getDeltaByStartVersion(long version) throws IOException;

  /** @return the delta leading to this version, if any, otherwise null */
  WaveletDeltaRecord getDeltaByEndVersion(long version) throws IOException;

  /** @return the delta passing by or leading to this version, if any, otherwise null */
  WaveletDeltaRecord getDeltaByArbitraryVersion(long version) throws IOException;

  /** @return the deltas applied at version */
  void getDeltasFromVersion(long version, ThrowableReceiver<WaveletDeltaRecord, IOException> receiver) throws IOException;
}
