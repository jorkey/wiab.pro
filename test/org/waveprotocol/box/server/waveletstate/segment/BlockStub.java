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

package org.waveprotocol.box.server.waveletstate.segment;

import org.waveprotocol.box.server.persistence.blocks.Block;
import org.waveprotocol.box.server.persistence.blocks.Fragment;
import org.waveprotocol.box.server.persistence.blocks.FragmentObserver;
import org.waveprotocol.box.server.persistence.blocks.ReadableSegmentSnapshot;
import org.waveprotocol.box.server.persistence.blocks.SegmentOperation;
import org.waveprotocol.box.server.persistence.blocks.VersionInfo;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import java.io.OutputStream;

/**
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
class BlockStub implements Block {

  private final String blockId;
  private final FragmentStub fragment;
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  public BlockStub(String blockId, long startVersion, long endVersion, boolean last) {
    this.blockId = blockId;
    fragment = new FragmentStub(this, startVersion, endVersion, last);
  }

  @Override
  public String getBlockId() {
    return blockId;
  }

  @Override
  public int getSize() {
    return 0;
  }

  @Override
  public long getLastModifiedVersion() {
    return 1;
  }

  @Override
  public Fragment getFragment(SegmentId segmentId) {
    return fragment;
  }

  @Override
  public VersionInfo deserializeVersionInfo(int offset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ReadableSegmentSnapshot deserializeSegmentSnapshot(int offset, SegmentId segmentId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SegmentOperation deserializeSegmentOperation(int offset, SegmentId segmentId, WaveletOperationContext context) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void serialize(OutputStream out) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ReentrantReadWriteLock.ReadLock getReadLock() {
    return lock.readLock();
  }

  @Override
  public Fragment createFragment(SegmentId segmentId, boolean first) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int writeVersionInfo(VersionInfo versionInfo) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int writeSegmentSnapshot(ReadableSegmentSnapshot segmentSnapshot) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int writeSegmentOperation(SegmentOperation segmentOperation) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addObserver(FragmentObserver actualizer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ReentrantReadWriteLock.WriteLock getWriteLock() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void writeAccessNotify(Fragment fragment) {
    throw new UnsupportedOperationException();
  }
}
