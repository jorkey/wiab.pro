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

package org.waveprotocol.wave.model.raw;

import com.google.common.collect.ImmutableList;

import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.ReadableSegmentSnapshot;
import org.waveprotocol.box.server.persistence.blocks.SegmentOperation;
import org.waveprotocol.box.server.persistence.blocks.VersionNode;

import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.Preconditions;

import java.util.List;

/**
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class RawFragmentFactory {
  
  public static RawFragment makeRawFragment(SegmentId segmentId, Interval interval, boolean makeSnapshot) {
    RawSnapshot rawSnapshot = null;
    List<SegmentOperation> adjustOperations = CollectionUtils.newLinkedList();
    List<SegmentOperation> diffOperations = CollectionUtils.newLinkedList();
    if (makeSnapshot) {
      if (interval.getRange().from() == interval.getRange().to() && interval.getLastSnapshot() != null) {
        rawSnapshot = interval.getLastSnapshot().getRawSnapshot();
      } else {
        VersionNode nearestSnapshotNode = interval.getNearestSnapshotNode(interval.getRange().from());
        if (nearestSnapshotNode != null) {
          rawSnapshot = nearestSnapshotNode.getSegmentSnapshot().getRawSnapshot();
          if (nearestSnapshotNode.getVersion() != interval.getRange().from()) {
            adjustOperations.addAll(interval.getHistory(nearestSnapshotNode.getVersion(), interval.getRange().from()));
          }
        } else {
          ReadableSegmentSnapshot snapshot = interval.getSnapshot(interval.getRange().from());
          Preconditions.checkNotNull(snapshot, "Can't get snapshot of version " + interval.getRange().from() +
              " of segment " + segmentId.toString());
          rawSnapshot = snapshot.getRawSnapshot();
        }
      }
    }
    if (interval.getRange().from() != interval.getRange().to()) {
      diffOperations.addAll(interval.getHistory(interval.getRange().from(), interval.getRange().to()));
    }
    RawFragment rawFragment = new RawFragment(rawSnapshot,
      toRawOperations(adjustOperations), toRawOperations(diffOperations));
    return rawFragment;
  }
  
  private static ImmutableList<RawOperation> toRawOperations(List<SegmentOperation> operations) {
    ImmutableList.Builder<RawOperation> rawOperations = ImmutableList.builder();
    for (SegmentOperation segmentOperation : operations) {
      rawOperations.add(segmentOperation.getRawOperation());
    }
    return rawOperations.build();
  }
}
