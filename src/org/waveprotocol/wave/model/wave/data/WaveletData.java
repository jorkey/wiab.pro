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

package org.waveprotocol.wave.model.wave.data;

import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.Collection;

public interface WaveletData extends ReadableWaveletData {

  /**
   * Factory for constructing wavelet data copies.
   *
   * @param <T> type constructed by this factory.
   */
  interface Factory<T extends WaveletData> extends ReadableWaveletData.Factory<T> {}

  /**
   * Gets a document and its metadata from this wavelet.
   *
   * @return the requested document container, or null if it doesn't exist
   */
  @Override
  BlipData getBlip(String documentId);

  /**
   * Creates a document in this wavelet.
   *
   * @param id                   identifier of the document
   * @param author               author of the new document
   * @param contributors         participants who have contributed to the document
   * @param content              initial content of the document
   * @param creationTime         the creation time
   * @param creationVersion      the creation version
   * @param lastModifiedVersion  version of last worthy modification
   * @param lastModifiedTime     epoch time of last worthy modification
   * @return the created document
   * @throws IllegalStateException if this wavelet already has a document
   *         with the requested id
   */
  BlipData createBlip(String id, ParticipantId author,
      Collection<ParticipantId> contributors, DocInitialization content,
      long creationTime, long creationVersion,
      long lastModifiedTime, long lastModifiedVersion);

  /**
   * Adds a participant to this wavelet, ensuring it is in the participants
   * collection. The new participant is added to the end of the collection if it
   * was not already present.
   *
   * @param participant participant to add
   * @param opContext wavelet operation context
   * @return false if the given participant was already a participant of this
   *         wavelet.
   */
  boolean addParticipant(ParticipantId participant, WaveletOperationContext opContext);

  /**
   * Adds a participant to this wavelet at a specified position in the
   * participant list, ensuring it is in the participants collection.
   * Implementations of this interface may assume that this is an infrequently
   * used method and implement it inefficiently.
   *
   * @param participant participant to add
   * @param position position in participant list where to add the participant
   * @param opContext wavelet operation context
   * @return false if the given participant was already a participant of this
   *         wavelet, in which case its position is unaffected.
   * @throws IndexOutOfBoundsException if {@code position < 0} or
   *   {@code position > getParticipants.size()}.
   */
  boolean addParticipant(
      ParticipantId participant, int position, WaveletOperationContext opContext);

  /**
   * Removes a participant from this wavelet, ensuring it is no longer reflected
   * in the participants collection.
   *
   * @param participant participant to remove
     @param opContext wavelet operation context
   * @return false if the given participant was not a participant of this
   *         wavelet.
   */
  boolean removeParticipant(ParticipantId participant, WaveletOperationContext opContext);

  /**
   * Sets the distinct version of this wavelet.
   *
   * @param newHashedVersion new distinct version
   * @return the old distinct version
   */
  HashedVersion setHashedVersion(HashedVersion newHashedVersion);

  /**
   * Sets the last-modified time of this wavelet.
   *
   * @param newTime  new last-modified time
   * @return the old last-modified time
   */
  long setLastModifiedTime(long newTime);
}
