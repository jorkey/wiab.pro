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

package org.waveprotocol.box.server.frontend;

import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveserver.WaveServerException;

import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.util.CollectionUtils;

import java.util.List;
import java.util.Set;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
class WaveView {

  public static WaveView create(WaveletProvider waveletProvider, WaveId waveId, IdFilter waveletIdFilter) throws WaveServerException {
    WaveletId rootWaveletId = null, userDataWaveletId = null;
    Set<WaveletId> otherWaveletIds = CollectionUtils.newHashSet();
    for (WaveletId waveletId : waveletProvider.getWaveletIds(waveId)) {
      if (IdFilter.accepts(waveletIdFilter, waveletId)) {
        if (IdUtil.isConversationRootWaveletId(waveletId)) {
          rootWaveletId = waveletId;
        } else if (IdUtil.isUserDataWavelet(waveletId)) {
          userDataWaveletId = waveletId;
        } else {
          otherWaveletIds.add(waveletId);
        }
      }
    }
    return new WaveView(rootWaveletId, userDataWaveletId, otherWaveletIds);
  }

  private final WaveletId rootWaveletId;
  private final WaveletId userDataWaveletId;
  private final Set<WaveletId> otherWaveletIds;

  private WaveView(WaveletId rootWaveletId, WaveletId userDataWaveletId, Set<WaveletId> otherWaveletIds) {
    this.rootWaveletId = rootWaveletId;
    this.userDataWaveletId = userDataWaveletId;
    this.otherWaveletIds = otherWaveletIds;
  }

  public boolean isEmpty() {
    return rootWaveletId == null && userDataWaveletId == null && otherWaveletIds.isEmpty();
  }

  public WaveletId getRootWaveletId() {
    return rootWaveletId;
  }

  public WaveletId getUserDataWaveletId() {
    return userDataWaveletId;
  }

  public Set<WaveletId> getOtherWaveletIds() {
    return otherWaveletIds;
  }

  public List<WaveletId> getWaveletIds() {
    List<WaveletId> waveletIds = CollectionUtils.newLinkedList();
    if (userDataWaveletId != null) {
      waveletIds.add(userDataWaveletId);
    }
    if (rootWaveletId != null) {
      waveletIds.add(rootWaveletId);
    }
    waveletIds.addAll(otherWaveletIds);
    return waveletIds;
  }
}
