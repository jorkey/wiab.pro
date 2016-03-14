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

import org.waveprotocol.wave.model.id.SegmentId;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Writable block.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public interface WritableBlock {
  /**
   * Creates new fragment.
   *
   * @param segmentId the Id of segment.
   * @param first fragment is first in the segment.
   */
  Fragment createFragment(SegmentId segmentId, boolean first);

  /**
   * Writes version info,
   */
  int writeVersionInfo(VersionInfo versionInfo);

  /**
   * Writes segment snapshot.
   */
  int writeSegmentSnapshot(ReadableSegmentSnapshot segmentSnapshot);

  /**
   * Writes segment operation.
   */
  int writeSegmentOperation(SegmentOperation segmentOperation);

  /**
   * Adds block observer.
   */
  void addObserver(FragmentObserver actualizer);

  /** Gets write lock for block and its content.  */
  ReentrantReadWriteLock.WriteLock getWriteLock();

  /** Notify that fragment is modified. */
  void writeAccessNotify(Fragment fragment);
}
