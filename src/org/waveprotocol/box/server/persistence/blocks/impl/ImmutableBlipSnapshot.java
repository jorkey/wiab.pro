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

import org.waveprotocol.box.server.persistence.blocks.ReadableBlipSnapshot;
import org.waveprotocol.box.server.persistence.blocks.SegmentSnapshot;
import org.waveprotocol.box.server.persistence.blocks.ReadableFragment;

import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.raw.RawBlipSnapshot;
import org.waveprotocol.wave.model.wave.ParticipantId;

import com.google.common.collect.ImmutableSet;

/**
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class ImmutableBlipSnapshot extends ImmutableSegmentSnapshot<ReadableBlipSnapshot>
    implements ReadableBlipSnapshot {

  public ImmutableBlipSnapshot(SegmentSnapshot snapshot, ReadableFragment fragment, long version) {
    super(snapshot, fragment, version);
  }
  
  @Override
  public synchronized ParticipantId getAuthor() {
    return getProxy().getAuthor();
  }

  @Override
  public synchronized ImmutableSet<ParticipantId> getContributors() {
    return getProxy().getContributors();
  }

  @Override
  public synchronized long getCreationTime() {
    return getProxy().getCreationTime();
  }

  @Override
  public synchronized long getCreationVersion() {
    return getProxy().getCreationVersion();
  }

  @Override
  public synchronized long getLastModifiedTime() {
    return getProxy().getLastModifiedTime();
  }

  @Override
  public synchronized long getLastModifiedVersion() {
    return getProxy().getLastModifiedVersion();
  }

  @Override
  public synchronized DocInitialization getContent() {
    return getProxy().getContent();
  }

  @Override
  public synchronized String getId() {
    return getProxy().getId();
  }

  @Override
  public synchronized RawBlipSnapshot getRawSnapshot() {
    return getProxy().getRawSnapshot();
  }
}
