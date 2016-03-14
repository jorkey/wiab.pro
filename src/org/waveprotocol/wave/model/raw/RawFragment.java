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

package org.waveprotocol.wave.model.raw;

import com.google.common.collect.ImmutableList;

/**
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class RawFragment {
  final RawSnapshot snapshot;
  final ImmutableList<RawOperation> adjustOperations;
  final ImmutableList<RawOperation> diffOperations;

  public RawFragment(RawSnapshot snapshot, ImmutableList<RawOperation> adjustOperations,
      ImmutableList<RawOperation> diffOperations) {
    this.snapshot = snapshot;
    this.adjustOperations = adjustOperations;
    this.diffOperations = diffOperations;
  }

  public boolean hasSnapshot() {
    return snapshot != null;
  }

  public RawSnapshot getSnapshot() {
    return snapshot;
  }

  public RawIndexSnapshot getIndexSnapshot() {
    return (RawIndexSnapshot)snapshot;
  }

  public RawParticipantsSnapshot getParticipantsSnapshot() {
    return (RawParticipantsSnapshot)snapshot;
  }

  public RawBlipSnapshot getBlipSnapshot() {
    return (RawBlipSnapshot)snapshot;
  }

  public boolean hasAdjustOperations() {
    return !adjustOperations.isEmpty();
  }

  public ImmutableList<RawOperation> getAdjustOperations() {
    return adjustOperations;
  }

  public boolean hasDiffOperations() {
    return !diffOperations.isEmpty();
  }

  public ImmutableList<RawOperation> getDiffOperations() {
    return diffOperations;
  }

  public boolean isEmpty() {
    return hasSnapshot() || hasAdjustOperations() || hasDiffOperations();
  }
}
