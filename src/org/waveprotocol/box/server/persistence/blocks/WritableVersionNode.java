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

/**
 * Writable version node.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
interface WritableVersionNode {
  /**
   * Sets version info.
   */
  void setVersionInfo(VersionInfo versionInfo);

  /**
   * Sets snapshot.
   */
  void setSegmentSnapshot(ReadableSegmentSnapshot segmentSnapshot);

  /**
   * Sets operation from previous version.
   */
  void setFromPreviousVersionOperation(SegmentOperation operation);

  /**
   * Sets next version node.
   */
  void setNextNode(VersionNode nextNode);

  /**
   * Sets previous version node.
   */
  void setPreviousNode(VersionNode previousNode);

  /**
   * Adds far backward links.
   */
  void addFarBackwardLinks(FarBackwardLinks links);

  /**
   * Adds far forward links.
   */
  void addFarForwardLinks(FarForwardLinks links);
}
