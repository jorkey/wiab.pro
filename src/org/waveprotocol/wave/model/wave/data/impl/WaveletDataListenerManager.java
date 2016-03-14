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
import org.waveprotocol.wave.model.util.CopyOnWriteSet;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.BlipData;
import org.waveprotocol.wave.model.wave.data.WaveletData;
import org.waveprotocol.wave.model.wave.data.WaveletDataListener;

/**
 * Manages a set of wavelet data listeners, forwarding each event on to a set
 * of client listeners.
 *
 * @author anorth@google.com (Alex North)
 */
public class WaveletDataListenerManager implements WaveletDataListener {

  /** Set of listeners for change events. */
  private final CopyOnWriteSet<WaveletDataListener> listeners = CopyOnWriteSet.create();

  @Override
  public void onBlipDataAdded(BlipData blip) {
    for (WaveletDataListener l : listeners) {
      l.onBlipDataAdded(blip);
    }
  }

  @Override
  public void onBlipDataTimestampModified(BlipData blip, long oldTime, long newTime) {
    for (WaveletDataListener l : listeners) {
      l.onBlipDataTimestampModified(blip, oldTime, newTime);
    }
  }

  @Override
  public void onBlipDataVersionModified(BlipData blip, long oldVersion, long newVersion) {
    for (WaveletDataListener l : listeners) {
      l.onBlipDataVersionModified(blip, oldVersion, newVersion);
    }
  }

  @Override
  public void onBlipDataContributorAdded(BlipData blip, ParticipantId contributor) {
    for (WaveletDataListener l : listeners) {
      l.onBlipDataContributorAdded(blip, contributor);
    }
  }

  @Override
  public void onBlipDataContributorRemoved(BlipData blip, ParticipantId contributor) {
    for (WaveletDataListener l : listeners) {
      l.onBlipDataContributorRemoved(blip, contributor);
    }
  }

  @Override
  public void onBlipDataSubmitted(BlipData blip) {
    for (WaveletDataListener l : listeners) {
      l.onBlipDataSubmitted(blip);
    }
  }

  @Override
  public void onLastModifiedTimeChanged(long oldTime, long newTime) {
    for (WaveletDataListener l : listeners) {
      l.onLastModifiedTimeChanged(oldTime, newTime);
    }
  }

  @Override
  public void onVersionChanged(long oldVersion, long newVersion) {
    for (WaveletDataListener l : listeners) {
      l.onVersionChanged(oldVersion, newVersion);
    }
  }

  @Override
  public void onHashedVersionChanged(HashedVersion oldHashedVersion,
      HashedVersion newHashedVersion) {
    for (WaveletDataListener l : listeners) {
      l.onHashedVersionChanged(oldHashedVersion, newHashedVersion);
    }
  }

  @Override
  public void onParticipantAdded(ParticipantId participant, WaveletOperationContext opContext) {
    for (WaveletDataListener l : listeners) {
      l.onParticipantAdded(participant, opContext);
    }
  }

  @Override
  public void onParticipantRemoved(ParticipantId participant, WaveletOperationContext opContext) {
    for (WaveletDataListener l : listeners) {
      l.onParticipantRemoved(participant, opContext);
    }
  }

  @Override
  public void onTagAdded(String tag, WaveletOperationContext opContext) {
    for (WaveletDataListener l : listeners) {
      l.onTagAdded(tag, opContext);
    }
  }

  @Override
  public void onTagRemoved(String tag, WaveletOperationContext opContext) {
    for (WaveletDataListener l : listeners) {
      l.onTagRemoved(tag, opContext);
    }
  }
  
  @Deprecated
  @Override
  public void onRemoteBlipDataContentModified(BlipData blip) {
    for (WaveletDataListener l : listeners) {
      l.onRemoteBlipDataContentModified(blip);
    }
  }
  
  /**
   * Adds a new client listener to receive events received here. Adding the
   * same listener twice has no effect.
   *
   * @param listener a wavelet data listener
   */
  public void addListener(WaveletDataListener listener) {
    listeners.add(listener);
  }

  /**
   * Removes a client listener, so it will no longer receive events. Removing
   * an unregistered listener has no effect.
   *
   * @param listener wavelet data listener to remove
   */
  public void removeListener(WaveletDataListener listener) {
    listeners.remove(listener);
  }
}
