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

import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.operation.OperationException;

import com.google.common.collect.ImmutableSet;

/**
 * Defines the readable abstract data type used to implement a blip's state.
 */
public interface ReadableBlipData {

  /**
   * Gets the wave participant that created this blip.
   *
   * @return the creator of this blip.
   */
  ParticipantId getAuthor();

  /**
   * Gets an immutable set of contributors, in order of {@code addContributor()}.
   *
   * @return this blip's contributors.
   */
  ImmutableSet<ParticipantId> getContributors();

  /**
   * Gets the epoch time of the creation of this blip.
   *
   * @return the creation time of this blip.
   */
  long getCreationTime();

  /**
   * Gets the wavelet version of the creation of this blip.
   *
   * @return the creation version of this blip.
   */
  long getCreationVersion();

  /**
   * Gets the epoch time of the last modification to this blip.
   *
   * @return the last-modified time of this blip.
   */
  long getLastModifiedTime();

  /**
   * Gets the wavelet version of the last modification to this blip.
   *
   * @return the last-modified version of this blip.
   */
  long getLastModifiedVersion();

  /**
   * Checks is blip has complete history.
   */
  boolean isConsistent();

  /**
   * Checks is blip has content.
   */
  boolean hasContent();
  
  /**
   * Checks is content is initialized.
   */
  boolean isContentInitialized();

  /**
   * Initializes snapshot.
   */
  void initalizeSnapshot();

  /**
   * Processes diff operations.
   */
  void processDiffs() throws OperationException;

  /**
   * Gets the document content of this blip.
   */
  <T extends DocumentOperationSink> T getContent();

  /**
   * Gets an identifier for this blip, which is unique within the wavelet.
   *
   * @return an identifier for this blip, which is unique within the wavelet.
   */
  String getId();
}
