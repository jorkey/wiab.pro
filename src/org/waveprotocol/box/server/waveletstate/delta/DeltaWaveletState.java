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
package org.waveprotocol.box.server.waveletstate.delta;

import org.waveprotocol.box.server.persistence.deltas.DeltaStore.DeltaAccess;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.waveserver.ByteStringMessage;
import org.waveprotocol.wave.federation.Proto.ProtocolAppliedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.id.WaveletName;

import com.google.common.util.concurrent.ListenableFuture;
import org.waveprotocol.box.common.ThrowableReceiver;
import org.waveprotocol.box.server.waveletstate.WaveletState;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.wave.model.util.Pair;

/**
 * The state of a wavelet, including its delta history. Combines persisted and
 * not-yet-persisted delta history.
 *
 * Implementations of this interface are not thread safe. The callers must
 * serialize all calls to an instance of this interface.
 *
 * @author soren@google.com (Soren Lassen)
 */
public interface DeltaWaveletState extends WaveletState {
  interface GuiceFactory {
    DeltaWaveletState create(WaveletName waveletName);
  }

  /**
   * @return The last persisted hashed version.
   */
  HashedVersion getLastPersistedVersion();

  /**
   * @return the hashed version at the given version, if the version is at a
   *         delta boundary, otherwise null.
   */
  HashedVersion getHashedVersion(long version);

  /**
   * @return the hashed version at the given version, or at nearest less version.
   */
  HashedVersion getNearestHashedVersion(long version);

  /**
   * Returns the current version.
   */
  HashedVersion getLastModifiedVersion();

  /**
   * Returns the current timestamp.
   */
  long getLastModifiedTime();

  /**
   * @return last modified version and time.
   */
  Pair<HashedVersion, Long> getLastModifiedVersionAndTime();

  /**
   * @return the transformed delta applied at the given version, if it exists,
   *         otherwise null.
   */
  TransformedWaveletDelta getTransformedDelta(HashedVersion beginVersion);

  /**
   * @return the transformed delta with the given resulting version, if it
   *         exists, otherwise null.
   */
  TransformedWaveletDelta getTransformedDeltaByEndVersion(HashedVersion endVersion);

  /**
   * @return the applied delta applied at the given version, if it exists,
   *         otherwise null.
   */
  ByteStringMessage<ProtocolAppliedWaveletDelta> getAppliedDelta(HashedVersion beginVersion);

  /**
   * @return the applied delta with the given resulting version, if it exists,
   *         otherwise null.
   */
  ByteStringMessage<ProtocolAppliedWaveletDelta> getAppliedDeltaByEndVersion(
      HashedVersion endVersion);

  /**
   * @return the delta record applied at the given version, if it exists,
   *         otherwise null.
   */
  WaveletDeltaRecord getDeltaRecord(HashedVersion beginVersion);

  /**
   * @return the delta record with the given resulting version, if it exists,
   *         otherwise null.
   */
  WaveletDeltaRecord getDeltaRecordByEndVersion(HashedVersion endVersion);

  /**
   * Gets the applied deltas from the one applied at the given start version
   * until the one resulting in the given end version or receiver will interrupt.
   */
   void getDeltaHistory(HashedVersion startVersion, HashedVersion endVersion,
      ThrowableReceiver<WaveletDeltaRecord, WaveletStateException> receiver) throws WaveletStateException;

  /**
   * Appends the delta to the in-memory delta history.
   *
   * <p>
   * The caller must make a subsequent call to {@link #persist(HashedVersion)}
   * to persist the appended delta.
   */
  void appendDelta(WaveletDeltaRecord deltaRecord);

  /**
   * Initiates persistence of all in-memory deltas up to the one which
   * results in the given version. This call is non-blocking.
   *
   * <p>
   * If the deltas up to the given version are already persisted, this call does
   * nothing and returns a future which is already done.
   *
   * @param version Must be the resulting version of some delta in the delta
   *        history.
   * @return a future which is done when the version is persisted, or the attempt
   *         to persist fails (in which case the future raises an exception).
   */
  ListenableFuture<Void> persist(HashedVersion version);

  /** Flushes persisted delta from memory. */
  void flush(HashedVersion version);

  /**
   * Returns delta assess.
   */
  DeltaAccess getDeltaAccess();
}
