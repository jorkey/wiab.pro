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

import com.google.api.client.util.Preconditions;
import org.waveprotocol.box.server.persistence.blocks.DeserializationBlockException;
import org.waveprotocol.box.server.persistence.protos.ProtoBlockStore.VersionInfoRecord;
import org.waveprotocol.box.server.persistence.blocks.VersionInfo;
import org.waveprotocol.wave.model.wave.ParticipantId;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;

/**
 * Version info in the block.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class VersionInfoImpl implements VersionInfo {
  /** Version. */
  private final long version;

  /** Author of the version. */
  private final ParticipantId author;

  /** Timestamp of the version. */
  private final long timestamp;

  /** Deserialize form protobuf. */
  static VersionInfoImpl deserialize(byte[] data, int offset, List<ParticipantId> authors) {
    try {
      return deserialize(VersionInfoRecord.parseFrom(data), authors);
    } catch (InvalidProtocolBufferException ex) {
      throw new DeserializationBlockException(ex);
    }
  }

  /** Deserialize form protobuf. */
  static VersionInfoImpl deserialize(VersionInfoRecord record, List<ParticipantId> authors) {
    ParticipantId author = null;
    long timestamp = 0;
    if (record.hasAuthor()) {
      int authorIndex = record.getAuthor();
      Preconditions.checkArgument(authorIndex >= 0 && authorIndex < authors.size(),
        "Invalid index of author " + authorIndex);
      author = authors.get(authorIndex);
    }
    if (record.hasTimestamp()) {
      timestamp = record.getTimestamp();
    }
    return new VersionInfoImpl(record.getVersion(), author, timestamp);
  }

  /** Creates info of version that is created in segment. */
  public VersionInfoImpl(long version) {
    this.version = version;
    this.author = null;
    this.timestamp = 0;
  }

  /** Creates info of version that is created in segment. */
  public VersionInfoImpl(long version, ParticipantId author, long timestamp) {
    this.version = version;
    this.author = author;
    this.timestamp = timestamp;
  }

  /** Gets version. */
  @Override
  public long getVersion() {
    return version;
  }

  /** Gets author. */
  @Override
  public ParticipantId getAuthor() {
    return author;
  }

  /** Gets timestamp. */
  @Override
  public long getTimestamp() {
    return timestamp;
  }

  /** Serialize to protobuf. */
  @Override
  public VersionInfoRecord serialize(List<ParticipantId> authors) {
    VersionInfoRecord.Builder record = VersionInfoRecord.newBuilder();
    record.setVersion(version);
    if (author != null) {
      int authorIndex = authors.indexOf(author);
      Preconditions.checkArgument(authorIndex != -1, "Author " + author.toString() + " is not registered");
      record.setAuthor(authorIndex);
    }
    if (timestamp !=0) {
      record.setTimestamp(timestamp);
    }
    return record.build();
  }
}
