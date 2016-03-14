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

import org.waveprotocol.box.server.persistence.protos.ProtoBlockIndex;

import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.version.HashedVersion;

import java.util.Set;
import org.waveprotocol.wave.model.util.Pair;

/**
 * Map of placing segments to blocks.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public interface ReadableBlockIndex {
  /**
   * Checks is format version of storage compatible with current version.
   */
  boolean isFormatCompatible();

  /**
   * Index consistent with deltas.
   */
  boolean isConsistent();
  
  /**
   * Gets block Ids of specified range of segment.
   */
  Set<String> getBlockIds(SegmentId segmentId, VersionRange range, boolean strictly);

  /**
   * Gets last modified version and time.
   */
  Pair<HashedVersion, Long> getLastModifiedVersionAndTime();

  /**
   * Checks is index is empty.
   */
  boolean isEmpty();

  /**
   * Serializes index.
   */
  ProtoBlockIndex.BlockIndex serialize();
}
