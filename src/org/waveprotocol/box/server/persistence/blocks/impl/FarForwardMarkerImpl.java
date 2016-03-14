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

import java.io.IOException;
import java.io.InputStream;
import org.waveprotocol.wave.model.util.Preconditions;

/**
 * Link to future far version.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class FarForwardMarkerImpl extends MarkerImpl implements FarForwardMarker {
  /**
   * Distance to next far version.
   */
  private final long distanceToNextFarVersion;

  /**
   * Offset of next far version top marker.
   */
  private final int nextFarMarkerOffset;

  static FarForwardMarkerImpl deserialize(InputStream in) {
    try {
      return deserialize(ProtoBlockStore.FarForwardMarkerRecord.parseFrom(in));
    } catch (IOException ex) {
      throw new DeserializationBlockException(ex);
    }
  }

  static FarForwardMarkerImpl deserialize(ProtoBlockStore.FarForwardMarkerRecord serialized) {
    return new FarForwardMarkerImpl(serialized.getDistanceToNextFarVersion(), serialized.getNextFarMarkerOffset());
  }

  public FarForwardMarkerImpl(long distanceToNextFarVersion) {
    this.distanceToNextFarVersion = distanceToNextFarVersion;
    this.nextFarMarkerOffset = -1;
  }

  public FarForwardMarkerImpl(long distanceToNextFarVersion, int nextFarMarkerOffset) {
    this.distanceToNextFarVersion = distanceToNextFarVersion;
    this.nextFarMarkerOffset = nextFarMarkerOffset;
  }

  @Override
  public boolean isEmpty() {
    return nextFarMarkerOffset == -1;
  }

  @Override
  public long getDistanceToNextFarVersion() {
    return distanceToNextFarVersion;
  }

  @Override
  public int getNextFarMarkerOffset() {
    return nextFarMarkerOffset;
  }

  @Override
  public byte[] serialize() {
    ProtoBlockStore.FarForwardMarkerRecord.Builder builder = ProtoBlockStore.FarForwardMarkerRecord.newBuilder();
    Preconditions.checkArgument(distanceToNextFarVersion <= Integer.MAX_VALUE, "Version offset too long");
    builder.setDistanceToNextFarVersion((int)distanceToNextFarVersion);
    builder.setNextFarMarkerOffset(nextFarMarkerOffset);
    return builder.build().toByteArray();
  }
}
