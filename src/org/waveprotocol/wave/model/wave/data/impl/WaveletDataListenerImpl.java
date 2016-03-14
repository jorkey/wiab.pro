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

package org.waveprotocol.wave.model.wave.data.impl;

import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.BlipData;
import org.waveprotocol.wave.model.wave.data.WaveletDataListener;

/**
 * Vacuous implementation of a {@link WaveletDataListener}.
 *
 */
public class WaveletDataListenerImpl
    implements WaveletDataListener {

  @Override
  public void onBlipDataAdded(BlipData blip) {}

  @Override
  public void onBlipDataSubmitted(BlipData blip) {}

  @Override
  public void onBlipDataTimestampModified(BlipData b, long oldTime, long newTime) {}

  @Override
  public void onBlipDataVersionModified(BlipData blip, long oldVersion, long newVersion) {}

  @Override
  public void onBlipDataContributorAdded(BlipData blip, ParticipantId contributor) {}

  @Override
  public void onBlipDataContributorRemoved(BlipData blip, ParticipantId contributor) {}

  @Override
  public void onParticipantAdded(ParticipantId participant, WaveletOperationContext opContext) {}

  @Override
  public void onParticipantRemoved(ParticipantId participant, WaveletOperationContext opContext) {}

  @Override
  public void onTagAdded(String tag, WaveletOperationContext opContext) {}

  @Override
  public void onTagRemoved(String tag, WaveletOperationContext opContext) {}

  @Override
  public void onLastModifiedTimeChanged(long oldTime, long newTime) {}

  @Override
  public void onVersionChanged(long oldVersion, long newVersion) {}

  @Override
  public void onHashedVersionChanged(HashedVersion oldHashedVersion, HashedVersion newHashedVersion) {}

  @Override
  public void onRemoteBlipDataContentModified(BlipData blip) {}
}
