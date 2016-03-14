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

package org.waveprotocol.wave.model.operation.wave;

import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.Constants;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.BlipData;
import org.waveprotocol.wave.model.wave.data.WaveletData;
import org.waveprotocol.wave.model.operation.OperationException;

import java.util.Collections;
import java.util.List;

/**
 * This is pretty much like a no-op except it updates the version information.
 * It also contains a doc id when it wants to update the meta data of a
 * document.
 *
 * This operation has simple identity transformation like no-op.
 *
 * The constructors are purposely package private.
 *
 * @author zdwang@google.com (David Wang)
 */
final public class VersionUpdateOp extends WaveletOperation {

  /**
   * The document that should also have it's version information updated.
   * This field is optional.
   */
  private final String docId;

  /**
   * Constructs a VersionUpdateOp.
   *
   * @param docId the doc also to update the version
   */
  VersionUpdateOp(ParticipantId creator, String docId, long segmentVersion, HashedVersion hashedVersion) {
    super(new WaveletOperationContext(creator, Constants.NO_TIMESTAMP, segmentVersion, hashedVersion));
    this.docId = docId;
  }

  /**
   * Updates the blips metadata/version.
   *
   * Wavelet version and timestamp are expected to be updated by the universal
   * application logic in {@link WaveletBlipOperation#apply(WaveletData)}
   */
  @Override
  protected void doApply(WaveletData wave) throws OperationException {
    doInternalApply(wave);
  }

  private VersionUpdateOp doInternalApply(WaveletData wavelet) throws OperationException {
    HashedVersion oldHashedVersion = wavelet.getHashedVersion();
    if (docId != null) {
      BlipData blip = wavelet.getBlip(docId);
      long oldDocVersion = blip.setLastModifiedVersion(context.getSegmentVersion());
      return new VersionUpdateOp(context.getCreator(), docId, oldDocVersion, oldHashedVersion);
    } else {
      return new VersionUpdateOp(context.getCreator(), null, oldHashedVersion.getVersion(), oldHashedVersion);
    }
  }

  @Override
  public void acceptVisitor(WaveletOperationVisitor visitor) {
    visitor.visitVersionUpdateOp(this);
  }

  @Override
  public String toString() {
    return "version update op, blip id " + docId;
  }

  @Override
  public List<? extends WaveletOperation> reverse(WaveletOperationContext context) throws OperationException {
    return Collections.singletonList(new VersionUpdateOp(context.getCreator(), docId, context.getSegmentVersion(), 
      context.getHashedVersion()));
  }

  @Override
  public List<? extends WaveletOperation> applyAndReturnReverse(WaveletData target) throws OperationException {
    List<? extends WaveletOperation> ret = Collections.singletonList(doInternalApply(target));
    update(target);
    return ret;
  }

  @Override
  public int hashCode() {
    return ((docId == null) ? 0 : docId.hashCode());
  }

  @Override
  public boolean equals(Object obj) {
    /*
     * NOTE(user): We're ignoring context in equality comparison. The plan is
     * to remove context from all operations in the future.
     */
    if (!(obj instanceof VersionUpdateOp)) {
      return false;
    }
    VersionUpdateOp other = (VersionUpdateOp) obj;
    return docId.equals(other.docId);
  }
}
