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

package org.waveprotocol.box.server.util;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.schema.SchemaCollection;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.data.WaveletData;
import org.waveprotocol.wave.model.wave.data.impl.EmptyWaveletSnapshot;
import org.waveprotocol.wave.model.wave.data.impl.ObservablePluggableMutableDocument;
import org.waveprotocol.wave.model.wave.data.impl.WaveletDataImpl;

/**
 * Utility methods for {@link WaveletData}.
 *
 * @author ljvderijk@google.com (Lennard de Rijk)
 */
public final class WaveletDataUtil {

  // TODO(ljvderijk): Schemas should be enforced, see issue 109.
  private static final ObservableWaveletData.Factory<?> WAVELET_FACTORY =
      WaveletDataImpl.Factory.create(
          ObservablePluggableMutableDocument.createFactory(SchemaCollection.empty()));

  private WaveletDataUtil() {
  }

  /**
   * Returns the {@link WaveletName} for the given wavelet.
   *
   * @param wavelet the wavelet to get the name for
   */
  public static WaveletName waveletNameOf(ReadableWaveletData wavelet) {
    return WaveletName.of(wavelet.getWaveId(), wavelet.getWaveletId());
  }

  /**
   * Apply a delta to the given wavelet. Rolls back the operation if it fails.
   *
   * @param delta delta to apply.
   * @param wavelet the wavelet to apply the operations to.
   *
   * @throws OperationException if the operations fail to apply (and are
   *         successfully rolled back).
   * @throws IllegalStateException if the operations have failed and can not be
   *         rolled back.
   */
  public static void applyWaveletDelta(TransformedWaveletDelta delta, WaveletData wavelet)
      throws OperationException {
    Preconditions.checkState(wavelet != null, "wavelet may not be null");
    Preconditions.checkState(delta.getAppliedAtVersion() == wavelet.getVersion(),
        "Delta's version %s doesn't apply to wavelet at %s", delta.getAppliedAtVersion(),
        wavelet.getVersion());

    List<WaveletOperation> reverseOps = new ArrayList<WaveletOperation>();
    WaveletOperation lastOp = null;
    int opsApplied = 0;
    try {
      for (WaveletOperation op : delta) {
        lastOp = op;
        List<? extends WaveletOperation> reverseOp = op.applyAndReturnReverse(wavelet);
        reverseOps.addAll(reverseOp);
        opsApplied++;
      }
    } catch (OperationException e) {
      // Deltas are atomic, so roll back all operations that were successful
      rollbackWaveletOperations(wavelet, reverseOps);
      throw new OperationException("Only applied " + opsApplied + " of "
          + delta.size() + " operations at version " + wavelet.getVersion()
          + ", rolling back, failed op was " + lastOp, e);
    }
  }

  /**
   * Like applyWaveletOperations, but throws an {@link IllegalStateException}
   * when ops fail to apply. Is used for rolling back operations.
   *
   * @param ops to apply for rollback
   */
  private static void rollbackWaveletOperations(WaveletData wavelet, List<WaveletOperation> ops) {
    for (int i = ops.size() - 1; i >= 0; i--) {
      try {
        ops.get(i).apply(wavelet);
      } catch (OperationException e) {
        throw new IllegalStateException(
            "Failed to roll back operation with inverse " + ops.get(i), e);
      }
    }
  }

  /**
   * Creates an empty wavelet.
   *
   * @param waveletName the name of the wavelet.
   * @param author the author of the wavelet.
   * @param creationTimestamp the time at which the wavelet is created.
   */
  public static ObservableWaveletData createEmptyWavelet(WaveletName waveletName,
      ParticipantId author, HashedVersion version, long creationTimestamp) {
    return copyWavelet(new EmptyWaveletSnapshot(waveletName.waveId, waveletName.waveletId, author,
        version, creationTimestamp));
  }

  /**
   * Constructs the wavelet state after the application of the first delta.
   *
   * @param waveletName the name of the wavelet.
   * @param delta first delta to apply at version zero.
   */
  public static ObservableWaveletData buildWaveletFromFirstDelta(WaveletName waveletName,
      TransformedWaveletDelta delta) throws OperationException {
    Preconditions.checkArgument(delta.getAppliedAtVersion() == 0,
        "first delta has non-zero version: %s", delta.getAppliedAtVersion());
    ObservableWaveletData wavelet =
        createEmptyWavelet(
            waveletName,
            delta.getAuthor(), // creator
            HashedVersion.unsigned(0), // garbage hash, is overwritten by first delta below
            delta.getApplicationTimestamp()); // creation time
    applyWaveletDelta(delta, wavelet);
    return wavelet;
  }

  /**
   * Reads all deltas from the given iterator and constructs the end
   * wavelet state by successive application of all deltas beginning
   * from the empty wavelet.
   *
   * @param waveletName the name of the wavelet.
   * @param deltas non-empty, contiguous sequence of non-empty deltas beginning
   *        from version zero.
   */
  public static ObservableWaveletData buildWaveletFromDeltas(WaveletName waveletName,
      Iterator<TransformedWaveletDelta> deltas) throws OperationException {
    Preconditions.checkArgument(deltas.hasNext(), "empty deltas");
    ObservableWaveletData wavelet = buildWaveletFromFirstDelta(waveletName, deltas.next());
    while (deltas.hasNext()) {
      TransformedWaveletDelta delta = deltas.next();
      applyWaveletDelta(delta, wavelet);
    }
    return wavelet;
  }

  /**
   * Copies a wavelet.
   *
   * @param wavelet the wavelet to copy.
   * @return A mutable copy.
   */
  public static ObservableWaveletData copyWavelet(ReadableWaveletData wavelet) {
    Timer timer = Timing.start("WaveletDataUtil.copyWavelet");
    try {
      return WAVELET_FACTORY.create(wavelet);
    } finally {
      Timing.stop(timer);
    }
  }

  /**
   * @return true if the wave has conversational root wavelet.
   */
  public static boolean hasConversationalRootWavelet(@Nullable WaveViewData wave) {
    if (wave == null) {
      return false;
    }
    for (ReadableWaveletData waveletData : wave.getWavelets()) {
      WaveletId waveletId = waveletData.getWaveletId();
      if (IdUtil.isConversationRootWaveletId(waveletId)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if the user has access to the wavelet.
   *
   * @param participants the participants of wavelet.
   * @param user the user that wants to access the wavelet.
   * @param sharedDomainParticipantId the shared domain participant id.
   * @return true if the user has access to the wavelet.
   */
  public static boolean checkAccessPermission(Set<ParticipantId> participants, ParticipantId user,
      ParticipantId sharedDomainParticipantId) {
    return user != null && (participants.isEmpty()
        || participants.contains(user)
        || (sharedDomainParticipantId != null
        && participants.contains(sharedDomainParticipantId)));
  }
}
