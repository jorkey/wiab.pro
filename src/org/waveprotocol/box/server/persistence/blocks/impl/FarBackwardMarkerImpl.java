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
import org.waveprotocol.box.server.persistence.protos.ProtoBlockStore;

import org.waveprotocol.wave.model.util.Preconditions;

import java.io.IOException;
import java.io.InputStream;

/**
 * Link to previous far version.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class FarBackwardMarkerImpl extends MarkerImpl implements FarBackwardMarker {
  /**
   * Distance to previous far version.
   */
  private final long distanceToPreviousFarVersion;
  
  /**
   * Offset of source marker of previous far version top marker.
   */
  private final int previousFarMarkerOffset;

  /**
   * Offset of aggregated operation from previous far version.
   */
  private int fromPreviousFarVersionOperationOffset = -1;

  static FarBackwardMarkerImpl deserialize(InputStream in) {
    try {
      return deserialize(ProtoBlockStore.FarBackwardMarkerRecord.parseFrom(in));
    } catch (IOException ex) {
      throw new DeserializationBlockException(ex);
    }
  }

  static FarBackwardMarkerImpl deserialize(ProtoBlockStore.FarBackwardMarkerRecord serialized) {
    int operationOffset = -1;
    if (serialized.hasFromPreviousFarVersionOperationOffset()) {
      operationOffset = serialized.getFromPreviousFarVersionOperationOffset();
    }
    return new FarBackwardMarkerImpl(serialized.getDistanceToPreviousFarVersion(), 
      serialized.getPreviousFarMarkerOffset(), operationOffset);
  }

  public FarBackwardMarkerImpl(long distanceToPreviousFarVersion, int previousFarMarkerOffset, int fromPreviousFarVersionOperationOffset) {
    this.distanceToPreviousFarVersion = distanceToPreviousFarVersion;
    this.previousFarMarkerOffset = previousFarMarkerOffset;
    this.fromPreviousFarVersionOperationOffset = fromPreviousFarVersionOperationOffset;
  }

  @Override
  public long getDistanceToPreviousFarVersion() {
    return distanceToPreviousFarVersion;
  }

  @Override
  public int getPreviousFarMarkerOffset() {
    return previousFarMarkerOffset;
  }

  @Override
  public int getFromPreviousFarVersionOperationOffset() {
    return fromPreviousFarVersionOperationOffset;
  }

  @Override
  public byte[] serialize() {
    ProtoBlockStore.FarBackwardMarkerRecord.Builder builder = ProtoBlockStore.FarBackwardMarkerRecord.newBuilder();
    Preconditions.checkArgument(distanceToPreviousFarVersion <= Integer.MAX_VALUE, "Version offset too long");
    builder.setDistanceToPreviousFarVersion((int)distanceToPreviousFarVersion);
    builder.setPreviousFarMarkerOffset(previousFarMarkerOffset);
    if (fromPreviousFarVersionOperationOffset != -1) {
      builder.setFromPreviousFarVersionOperationOffset(fromPreviousFarVersionOperationOffset);
    }
    return builder.build().toByteArray();
  }
}
