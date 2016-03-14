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

package org.waveprotocol.box.server.persistence.blocks;

import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.raw.RawOperation;
import org.waveprotocol.wave.communication.Blob;

import com.google.common.collect.ImmutableList;

/**
 * Wavelet operations on segment that change version.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public interface SegmentOperation {

  public ImmutableList<? extends WaveletOperation> getOperations();

  public boolean isWorthy();

  public long getTargetVersion();

  public long getTimestamp();

  public SegmentOperation revert(WaveletOperationContext context);

  public RawOperation getRawOperation();

  public Blob serialize();
}
