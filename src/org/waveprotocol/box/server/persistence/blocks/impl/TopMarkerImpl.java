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
import org.waveprotocol.box.server.persistence.protos.ProtoBlockStore.TopMarkerRecord;

import java.io.IOException;
import java.io.InputStream;

/**
 * Top of version information and links to other versions.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class TopMarkerImpl extends MarkerImpl implements TopMarker {

  /** Offset of VersionInfo in block data section. */
  private final int versionInfoOffset;

  /** Offset in block data section of operation from previous version. */
  private final int fromPreviousVersionOperationOffset;

  static TopMarkerImpl deserialize(InputStream in) {
    try {
      TopMarkerRecord record = TopMarkerRecord.parseFrom(in);
      return deserialize(record);
    } catch (IOException ex) {
      throw new DeserializationBlockException(ex);
    }
  }

  private static TopMarkerImpl deserialize(TopMarkerRecord record) {
    int versionInfoOffset = -1, fromPreviousVersionOperationOffset = -1;
    if (record.hasVersionInfoOffset()) {
      versionInfoOffset = record.getVersionInfoOffset();
    }
    if (record.hasFromPreviousVersionOperationOffset()) {
      fromPreviousVersionOperationOffset = record.getFromPreviousVersionOperationOffset();
    }
    return new TopMarkerImpl(versionInfoOffset, fromPreviousVersionOperationOffset);
  }

  public TopMarkerImpl() {
    this.versionInfoOffset = -1;
    this.fromPreviousVersionOperationOffset = -1;
  }

  public TopMarkerImpl(int versionInfoOffset, int fromPreviousVersionOperationOffset) {
    this.versionInfoOffset = versionInfoOffset;
    this.fromPreviousVersionOperationOffset = fromPreviousVersionOperationOffset;
  }

  @Override
  public int getVersionInfoOffset() {
    return versionInfoOffset;
  }

  @Override
  public int getFromPreviousVersionOperationOffset() {
    return fromPreviousVersionOperationOffset;
  }

  @Override
  public byte[] serialize() {
    TopMarkerRecord.Builder builder = TopMarkerRecord.newBuilder();
    if (versionInfoOffset != -1) {
      builder.setVersionInfoOffset(versionInfoOffset);
    }
    if (fromPreviousVersionOperationOffset != -1) {
      builder.setFromPreviousVersionOperationOffset(fromPreviousVersionOperationOffset);
    }
    return builder.build().toByteArray();
  }
}
