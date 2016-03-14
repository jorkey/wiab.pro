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

import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.box.server.serialize.OperationSerializer;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletState;
import org.waveprotocol.box.server.waveletstate.block.BlockWaveletState;
import org.waveprotocol.box.server.waveserver.DeltaWaveletStateMap.DeltaWaveletStateAccessor;

import org.waveprotocol.wave.federation.Proto.ProtocolAppliedWaveletDelta;
import org.waveprotocol.wave.federation.Proto.ProtocolSignature;
import org.waveprotocol.wave.federation.Proto.ProtocolSignedDelta;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.TransformException;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.util.logging.Log;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.concurrent.Executor;

/**
 * A local wavelet may be updated by submits. The local wavelet will perform
 * operational transformation on the submitted delta and assign it the latest
 * version of the wavelet.
 */
class LocalWaveletContainerImpl extends WaveletContainerImpl implements LocalWaveletContainer {

  private static final Log LOG = Log.get(LocalWaveletContainerImpl.class);

  private Object submitLock = new Object();

  public LocalWaveletContainerImpl(WaveletName waveletName, String waveDomain, WaveletNotificationSubscriber notifiee,
        DeltaWaveletStateAccessor deltaStateAccessor, ListenableFuture<? extends SegmentWaveletState> segmentWaveletStateFuture,
        Executor storageIndexingExecutor, Executor storageContinuationExecutor) {
    super(waveletName, waveDomain, notifiee, deltaStateAccessor, segmentWaveletStateFuture,
        storageIndexingExecutor, storageContinuationExecutor);
  }

  @Override
  public WaveletDeltaRecord submitRequest(WaveletName waveletName, ProtocolSignedDelta signedDelta)
      throws OperationException, InvalidProtocolBufferException, InvalidHashException,
      PersistenceException, WaveletStateException, TransformException {
    awaitLoad();
    synchronized (submitLock) {
      checkStateOk();
      try {
        HashedVersion before = getLastModifiedVersion();
        WaveletDeltaRecord result = transformAndApplyLocalDelta(signedDelta);
        HashedVersion after = getLastModifiedVersion();
        // Only publish and persist the delta if it wasn't transformed away
        // (right now it never is since the current OT algorithm doesn't transform ops away)
        // and wasn't a duplicate of a previously applied delta.
        if (!after.equals(before)) {
          Preconditions.checkState(!result.isEmpty());
          Preconditions.checkState(result.getAppliedAtVersion().equals(before));
          ImmutableSet<String> domainsToNotify = ImmutableSet.of(waveletName.waveId.getDomain());
          // TODO (A. Kaplanov): Append federation support, commented:
          //ImmutableSet<String> domainsToNotify = domainsOf(Iterables.concat(
          //    accessSnapshot().getParticipants(),
          //    participantsRemovedBy(result.getTransformedDelta())));
          notifyOfDeltas(ImmutableList.of(result), domainsToNotify);
          // We always persist a local delta immediately after it's applied
          // and after it's broadcast on the wave bus and to remote servers.
          persistDelta(result.getResultingVersion(), domainsToNotify);
        }
        return result;
      } catch (OperationException | InvalidProtocolBufferException | InvalidHashException | TransformException ex) {
        state = State.CORRUPTED;
        throw ex;
      }
    }
  }

  /**
   * Apply a signed delta to a local wavelet. This assumes the caller has
   * validated that the delta is at the correct version and can be applied to
   * the wavelet. Must be called with writelock held.
   *
   * @param signedDelta the delta that is to be applied to wavelet.
   * @return the transformed and applied delta.
   * @throws OperationException if an error occurs during transformation or
   *         application
   * @throws InvalidProtocolBufferException if the signed delta did not contain a valid delta
   * @throws InvalidHashException if delta hash sanity checks fail
   */
  private WaveletDeltaRecord transformAndApplyLocalDelta(ProtocolSignedDelta signedDelta)
      throws OperationException, InvalidProtocolBufferException, InvalidHashException,
      PersistenceException, TransformException, WaveletStateException {
    ProtocolWaveletDelta protocolDelta =
        ByteStringMessage.parseProtocolWaveletDelta(signedDelta.getDelta()).getMessage();

    Preconditions.checkArgument(protocolDelta.getOperationCount() > 0, "empty delta");

    WaveletDelta transformed = maybeTransformSubmittedDelta(OperationSerializer.deserialize(protocolDelta));

    // TODO(ljvderijk): a Clock needs to be injected here (Issue 104)
    long applicationTimestamp = System.currentTimeMillis();

    HashedVersion currentVersion = getLastModifiedVersion();

    if (transformed.size() == 0) {
      Preconditions.checkState(currentVersion.getVersion() != 0,
          "currentVersion can not be 0 if delta was transformed");
      Preconditions.checkState(
          transformed.getTargetVersion().getVersion() <= currentVersion.getVersion());
      // The delta was transformed away. That's OK but we don't call either
      // applyTransformedWaveletDelta(), because that will throw IllegalArgumentException, or
      // commitAppliedDelta(), because empty deltas cannot be part of the delta history.
      TransformedWaveletDelta emptyDelta = new TransformedWaveletDelta(transformed.getAuthor(),
          transformed.getTargetVersion(), applicationTimestamp, transformed);
      return new WaveletDeltaRecord(transformed.getTargetVersion(), null, emptyDelta);
    }

    if (!transformed.getTargetVersion().equals(currentVersion)) {
      Preconditions.checkState(
          transformed.getTargetVersion().getVersion() < currentVersion.getVersion());
      // The delta was a duplicate of an existing server delta.
      // We duplicate-eliminate it (don't apply it to the wavelet state and don't store it in
      // the delta history) and return the server delta which it was a duplicate of
      // (so delta submission becomes idempotent).
      ByteStringMessage<ProtocolAppliedWaveletDelta> existingDeltaBytes =
          lookupAppliedDelta(transformed.getTargetVersion());
      TransformedWaveletDelta dupDelta = lookupTransformedDelta(transformed.getTargetVersion());
      LOG.info("Duplicate delta " + dupDelta + " for wavelet " + getWaveletName());
      // TODO(anorth): Replace these comparisons with methods on delta classes.
      Preconditions.checkState(dupDelta.getAuthor().equals(transformed.getAuthor()),
          "Duplicate delta detected but mismatched author, expected %s found %s",
          transformed.getAuthor(), dupDelta.getAuthor());
      Preconditions.checkState(Iterables.elementsEqual(dupDelta, transformed),
          "Duplicate delta detected but mismatched ops, expected %s found %s",
          transformed, dupDelta);

      return new WaveletDeltaRecord(transformed.getTargetVersion(), existingDeltaBytes, dupDelta);
    }

    // Build the applied delta to commit
    ByteStringMessage<ProtocolAppliedWaveletDelta> appliedDelta =
        AppliedDeltaUtil.buildAppliedDelta(signedDelta, transformed.getTargetVersion(),
            transformed.size(), applicationTimestamp);

    return applyDelta(appliedDelta, transformed);
  }

  @Override
  public boolean isDeltaSigner(HashedVersion version, ByteString signerId) throws WaveletStateException {
    ByteStringMessage<ProtocolAppliedWaveletDelta> appliedDelta =
        lookupAppliedDeltaByEndVersion(version);
    if (appliedDelta == null) {
      return false;
    }
    ProtocolSignedDelta signedDelta = appliedDelta.getMessage().getSignedOriginalDelta();
    for (ProtocolSignature signature : signedDelta.getSignatureList()) {
      if (signature.getSignerId().equals(signerId)) {
        return true;
      }
    }
    return false;
  }
}
