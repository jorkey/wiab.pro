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

package org.waveprotocol.box.server.persistence.blocks.impl.aggregator;

import org.waveprotocol.box.server.persistence.blocks.FarBackwardLinks;
import org.waveprotocol.box.server.persistence.blocks.FarForwardLinks;
import org.waveprotocol.box.server.persistence.blocks.ReadableSegmentSnapshot;
import org.waveprotocol.box.server.persistence.blocks.ReadableVersionNode;
import org.waveprotocol.box.server.persistence.blocks.SegmentOperation;
import org.waveprotocol.box.server.persistence.blocks.VersionInfo;
import org.waveprotocol.box.server.persistence.blocks.VersionNode;
import org.waveprotocol.box.server.persistence.blocks.impl.ImmutableSegmentSnapshot;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class VersionNodeStub implements VersionNode {
  private final long version;
  private final ParticipantId author;
  private final long timestamp;

  private VersionNode previousNode;
  private VersionNode nextNode;

  private SegmentOperation fromPreviousVersionOperation;
  private FarBackwardLinks farBackwardLinks;
  private FarForwardLinks farForwardLinks;

  public VersionNodeStub(long version, ParticipantId author, long timestamp) {
    this.version = version;
    this.author = author;
    this.timestamp = timestamp;
  }

  @Override
  public long getVersion() {
    return version;
  }

  @Override
  public ParticipantId getAuthor() {
    return author;
  }

  @Override
  public long getTimestamp() {
    return timestamp;
  }

  @Override
  public VersionInfo getVersionInfo() {
    throw new UnsupportedOperationException();
  }

  @Override
  public VersionNode getNextNode() {
    return nextNode;
  }

  @Override
  public VersionNode getPreviousNode() {
    return previousNode;
  }

  @Override
  public ImmutableSegmentSnapshot getSegmentSnapshot() {
    throw new UnsupportedOperationException();
  }

  @Override
  public SegmentOperation getFromPreviousVersionOperation() {
    return fromPreviousVersionOperation;
  }

  @Override
  public SegmentOperation getToPreviousVersionOperation(ReadableVersionNode previousNode) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setVersionInfo(VersionInfo versionInfo) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setSegmentSnapshot(ReadableSegmentSnapshot segmentSnapshot) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setFromPreviousVersionOperation(SegmentOperation operation) {
    this.fromPreviousVersionOperation = operation;
  }

  @Override
  public void setNextNode(VersionNode nextNode) {
    this.nextNode = nextNode;
  }

  @Override
  public void setPreviousNode(VersionNode previousNode) {
    this.previousNode = previousNode;
  }

  @Override
  public FarForwardLinks getFarForwardLinks() {
    return farForwardLinks;
  }

  @Override
  public FarBackwardLinks getFarBackwardLinks() {
    return farBackwardLinks;
  }

  @Override
  public void addFarBackwardLinks(FarBackwardLinks links) {
    farBackwardLinks = links;
  }

  @Override
  public void addFarForwardLinks(FarForwardLinks links) {
    farForwardLinks = links;
  }
}

