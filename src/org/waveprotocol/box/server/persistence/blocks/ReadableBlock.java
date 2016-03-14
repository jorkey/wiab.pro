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
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;

import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Readable block.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public interface ReadableBlock {
  /**
   * Gets block Id.
   */
  String getBlockId();

  /**
   * Checks size of block.
   */
  int getSize();

  /**
   * Gets last version.
   */
  long getLastModifiedVersion();

  /**
   * Gets fragment.
   *
   * @param segmentId the Id of segment.
   */
  Fragment getFragment(SegmentId segmentId);
  
  /**
   * Deserializes version info.
   *
   * @param offset block data offset.
   */
  VersionInfo deserializeVersionInfo(int offset);

  /**
   * Deserializes snapshot of segment.
   *
   * @param offset block data offset.
   * @param segmentId id of segment.
   */
  ReadableSegmentSnapshot deserializeSegmentSnapshot(int offset, SegmentId segmentId);

  /**
   * Deserializes segment operation.
   *
   * @param offset block data offset.
   * @param segmentId id of segment.
   * @param context context to apply to operation.
   */
  SegmentOperation deserializeSegmentOperation(int offset, SegmentId segmentId, WaveletOperationContext context);

  /**
   * Serializes block.
   */
  void serialize(OutputStream out);

  /** Gets read lock for block and its content.  */
  ReentrantReadWriteLock.ReadLock getReadLock();
}
