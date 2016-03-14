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

package org.waveprotocol.box.server.persistence.blocks;

import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Readable version node.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public interface ReadableVersionNode {
  /**
   * Gets version of node.
   */
  long getVersion();

  /**
   * Gets author of version.
   */
  ParticipantId getAuthor();

  /**
   * Gets version timestamp.
   */
  long getTimestamp();

  /**
   * Gets version info.
   */
  VersionInfo getVersionInfo();

  /**
   * Gets next node.
   */
  VersionNode getNextNode();

  /**
   * Gets previous node.
   */
  VersionNode getPreviousNode();

  /**
   * Gets segment snapshot of this version.
   */
  ReadableSegmentSnapshot getSegmentSnapshot();

  /**
   * Gets operation from previous version.
   */
  SegmentOperation getFromPreviousVersionOperation();

  /**
   * Gets rollback operation to previous version.
   */
  SegmentOperation getToPreviousVersionOperation(ReadableVersionNode previousNode);

  /**
   * Gets far backward links.
   */
  ReadableFarBackwardLinks getFarBackwardLinks();

  /**
   * Gets far forward links.
   */
  ReadableFarForwardLinks getFarForwardLinks();
}
