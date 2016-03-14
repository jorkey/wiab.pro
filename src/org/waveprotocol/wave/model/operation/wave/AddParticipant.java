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

import java.util.Collections;
import java.util.List;

import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.WaveletData;

/**
 * Operation class for the add-participant operation.
 *
 */
public final class AddParticipant extends WaveletOperation {

  /** Participant to add. */
  private final ParticipantId participant;

  /**
   * Creates an add-participant operation.
   *
   * @param context      context of this operation
   * @param participant  participant to add
   */
  public AddParticipant(WaveletOperationContext context, ParticipantId participant) {
    super(context);
    Preconditions.checkNotNull(participant, "Null participant ID");
    this.participant = participant;
  }

  /**
   * Gets the participant to add.
   *
   * @return the participant to add.
   */
  public ParticipantId getParticipantId() {
    return participant;
  }

  @Override
  public void doApply(WaveletData target) throws OperationException {
    if (!target.addParticipant(participant, getContext())) {
      throw new OperationException("Attempt to add a duplicate participant " + participant);
    }
  }

  @Override
  public void acceptVisitor(WaveletOperationVisitor visitor) {
    visitor.visitAddParticipant(this);
  }

  @Override
  public String toString() {
    return "add participant " + participant + " " + suffixForToString();
  }

  @Override
  public List<? extends WaveletOperation> applyAndReturnReverse(WaveletData target)
      throws OperationException {
    WaveletOperationContext reverseContext = new WaveletOperationContext(participant, 
      target.getLastModifiedTime(), target.getVersion(), target.getHashedVersion());
    doApply(target);
    update(target);
    return reverse(reverseContext);
  }

  @Override
  public List<? extends WaveletOperation> reverse(WaveletOperationContext reverseContext) throws OperationException {
    return Collections.singletonList(new RemoveParticipant(reverseContext, participant));
  }

  @Override
  public int hashCode() {
    return participant.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    /*
     * NOTE(user): We're ignoring context in equality comparison. The plan is
     * to remove context from all operations in the future.
     */
    if (!(obj instanceof AddParticipant)) {
      return false;
    }
    AddParticipant other = (AddParticipant) obj;
    return participant.equals(other.participant);
  }
}
