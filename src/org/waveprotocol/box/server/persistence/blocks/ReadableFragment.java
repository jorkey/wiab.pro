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

/**
 * Readable fragment.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public interface ReadableFragment {
  /**
   * Gets segment Id of fragment.
   */
  SegmentId getSegmentId();

  /**
   * Gets block of fragment.
   */
  ReadableBlock getBlock();

  /**
   * Gets previous fragment.
   */
  ReadableFragment getPreviousFragment();

  /**
   * Gets next fragment.
   */
  ReadableFragment getNextFragment();

  /**
   * Gets source version from which fragment started.
   */
  long getStartVersion();

  /**
   * If fragment Gets last target version of fragment.
   */
  long getLastModifiedVersion();

  /**
   * Gets end range value.
   */
  ReadableRangeValue getEndRangeValue();

  /**
   * Gets last snapshot version.
   */
  long getLastStreamSnapshotVersion();

  /**
   * Checks the fragment is first in the segment.
   */
  boolean isFirst();

  /**
   * Checks the fragment is last in the segment.
   */
  boolean isLast();

  /**
   * Gets first node of fragment.
   */
  VersionNode getFirstNode();

  /**
   * Gets last node of fragment.
   */
  VersionNode getLastNode();

  /**
   * Gets timestamp of specified version.
   */
  long getTimestamp(long version);

  /**
   * Gets last segment snapshot.
   */
  ReadableSegmentSnapshot getLastSnapshot();

  /**
   * Gets segment snapshot of specified version.
   */
  ReadableSegmentSnapshot getSnapshot(long version);

  /**
   * Gets node of specified version.
   */
  VersionNode getNode(long version);

  /**
   * Gets nearest node to specified version.
   */
  VersionNode getNearestNode(long version);

  /**
   * Gets nearest node to specified version with snapshot.
   */
  VersionNode getNearestSnapshotNode(long version);

  /**
   * Gets node by top marker offset.
   */
  VersionNode getNodeByTopMarkerOffset(int topMarkerOffset);

  /**
   * Gets node by bottom marker offset.
   */
  VersionNode getNodeByMarkerOffset(int bottomMarkerOffset);

  /**
   * Deserializes version info.
   */
  VersionInfo deserializeVersionInfo(int offset);

  /**
   * Deserializes operation from near previous version.
   */
  SegmentOperation deserializeFromPreviousVersionOperation(int offset, SegmentId segmentId, WaveletOperationContext context);

  /**
   * Deserializes aggregated operation from far previous version.
   */
  SegmentOperation deserializeFromPreviousFarVersionOperation(int offset, SegmentId segmentId, WaveletOperationContext context);

  /**
   * Deserializes snapshot of segment.
   */
  ReadableSegmentSnapshot deserializeSegmentSnapshot(int offset);
}
