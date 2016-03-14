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

package org.waveprotocol.box.server.persistence.blocks.impl;

import org.waveprotocol.box.server.persistence.blocks.ReadableIndexSnapshot;
import org.waveprotocol.box.server.persistence.blocks.SegmentSnapshot;
import org.waveprotocol.box.server.persistence.blocks.ReadableFragment;

import org.waveprotocol.wave.model.raw.RawIndexSnapshot;
import org.waveprotocol.wave.model.id.SegmentId;

import java.util.List;
import java.util.Set;

/**
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class ImmutableIndexSnapshot extends ImmutableSegmentSnapshot<ReadableIndexSnapshot>
    implements ReadableIndexSnapshot {

  public ImmutableIndexSnapshot(SegmentSnapshot snapshot, ReadableFragment fragment, long version) {
    super(snapshot, fragment, version);
  }

  @Override
  public synchronized long getCreationTime() {
    return getProxy().getCreationTime();
  }

  @Override
  public Set<SegmentId> getExistingSegmentIds() {
    return getProxy().getExistingSegmentIds();
  }

  @Override
  public SegmentId getLastModifiedSegmentId() {
    return getProxy().getLastModifiedSegmentId();
  }

  @Override
  public Set<SegmentId> getBeingModifiedSegmentIds() {
    return getProxy().getBeingModifiedSegmentIds();
  }

  @Override
  public synchronized long getCreationVersion(SegmentId segmentId) {
    return getProxy().getCreationVersion(segmentId);
  }

  @Override
  public synchronized long getLastModifiedVersion(SegmentId segmentId) {
    return getProxy().getLastModifiedVersion(segmentId);
  }

  @Override
  public synchronized boolean hasSegment(SegmentId segmentId) {
    return getProxy().hasSegment(segmentId);
  }

  @Override
  public synchronized RawIndexSnapshot getRawSnapshot() {
    return getProxy().getRawSnapshot();
  }
}
