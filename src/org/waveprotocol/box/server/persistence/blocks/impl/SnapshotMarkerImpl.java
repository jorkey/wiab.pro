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

import org.waveprotocol.box.server.persistence.blocks.*;
import org.waveprotocol.box.server.persistence.protos.ProtoBlockStore.SnapshotMarkerRecord;

import java.io.IOException;
import java.io.InputStream;

/**
 * Top of version information and links to other versions.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class SnapshotMarkerImpl extends MarkerImpl implements SnapshotMarker {

  /** Offset of segment snapshot in block data section. */
  private final int snapshotOffset;

  static SnapshotMarkerImpl deserialize(InputStream in) {
    try {
      SnapshotMarkerRecord record = SnapshotMarkerRecord.parseFrom(in);
      return deserialize(record);
    } catch (IOException ex) {
      throw new DeserializationBlockException(ex);
    }
  }

  private static SnapshotMarkerImpl deserialize(SnapshotMarkerRecord record) {
    return new SnapshotMarkerImpl(record.getSnapshotOffset());
  }

  public SnapshotMarkerImpl() {
    this.snapshotOffset = -1;
  }

  public SnapshotMarkerImpl(int snapshotOffset) {
    this.snapshotOffset = snapshotOffset;
  }

  @Override
  public int getSnapshotOffset() {
    return snapshotOffset;
  }

  @Override
  public byte[] serialize() {
    SnapshotMarkerRecord.Builder builder = SnapshotMarkerRecord.newBuilder();
    if (snapshotOffset != -1) {
      builder.setSnapshotOffset(snapshotOffset);
    }
    return builder.build().toByteArray();
  }
}
