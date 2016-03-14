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

import org.waveprotocol.box.server.persistence.blocks.FarBackwardLink;
import org.waveprotocol.box.server.persistence.blocks.SegmentOperation;
import org.waveprotocol.box.server.persistence.blocks.VersionNode;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;

/**
 * Backward links to far nodes.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class FarBackwardLinkImpl implements FarBackwardLink {
  private final long distanceToPreviousFarVersion;
  private final VersionNode previousFarNode;
  private final SegmentOperation fromPreviousFarVersionOperation;
  
  private SegmentOperation toPreviousFarVersionOperation;

  public FarBackwardLinkImpl(long distanceToPreviousFarVersion, VersionNode previousFarNode, SegmentOperation fromPreviousFarVersionOperation) {
    this.distanceToPreviousFarVersion = distanceToPreviousFarVersion;
    this.previousFarNode = previousFarNode;
    this.fromPreviousFarVersionOperation = fromPreviousFarVersionOperation;
  }

  @Override
  public long getDistanceToPreviousFarVersion() {
    return distanceToPreviousFarVersion;
  }
  
  @Override
  public VersionNode getPreviousFarNode() {
    return previousFarNode;
  }

  @Override
  public SegmentOperation getFromPreviousFarVersionOperation() {
    return fromPreviousFarVersionOperation;
  }

  @Override
  public SegmentOperation getToPreviousFarVersionOperation() {
    if (toPreviousFarVersionOperation == null) {
      WaveletOperationContext context = new WaveletOperationContext(previousFarNode.getAuthor(),
        previousFarNode.getTimestamp(), previousFarNode.getVersion());
      if (fromPreviousFarVersionOperation != null) {
        toPreviousFarVersionOperation = fromPreviousFarVersionOperation.revert(context);
      }
    }
    return toPreviousFarVersionOperation;
  }
}
