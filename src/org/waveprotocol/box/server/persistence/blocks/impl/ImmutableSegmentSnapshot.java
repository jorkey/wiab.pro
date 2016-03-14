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

import org.waveprotocol.box.server.persistence.blocks.ReadableFragment;
import org.waveprotocol.box.server.persistence.blocks.ReadableSegmentSnapshot;
import org.waveprotocol.box.server.persistence.blocks.SegmentSnapshot;
import org.waveprotocol.box.server.persistence.blocks.WritableSegmentSnapshot;
import org.waveprotocol.box.server.persistence.protos.ProtoBlockStore;

import org.waveprotocol.wave.model.raw.RawSnapshot;
import org.waveprotocol.wave.model.util.Preconditions;

/**
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class ImmutableSegmentSnapshot<T extends ReadableSegmentSnapshot> implements ReadableSegmentSnapshot {
  private T proxy;
  private final ReadableFragment fragment;
  private final long version;
  private boolean updated = false;

  public static ImmutableSegmentSnapshot create(SegmentSnapshot snapshot, ReadableFragment fragment, long version) {
    if (snapshot instanceof IndexSnapshot) {
      return new ImmutableIndexSnapshot((IndexSnapshot)snapshot, fragment, version);
    }
    if (snapshot instanceof BlipSnapshot) {
      return new ImmutableBlipSnapshot((BlipSnapshot)snapshot, fragment, version);
    }
    if (snapshot instanceof ParticipantsSnapshot) {
      return new ImmutableParticipantsSnapshot((ParticipantsSnapshot)snapshot, fragment, version);
    }
    Preconditions.illegalArgument("Invalid snapshot type " + snapshot);
    return null;
  }

  protected ImmutableSegmentSnapshot(SegmentSnapshot snapshot, ReadableFragment fragment, long version) {
    snapshot.addUpdateListener(new WritableSegmentSnapshot.UpdateListener() {

      @Override
      public void onBeforeUpdate() {
        synchronized (ImmutableSegmentSnapshot.this) {
          if (!updated) {
            proxy = null;
            updated = true;
          }
        }
      }
    });
    this.proxy = (T)snapshot;
    this.fragment = fragment;
    this.version = version;
  }

  @Override
  public synchronized boolean hasContent() {
    return getProxy().hasContent();
  }

  @Override
  public synchronized SegmentSnapshot duplicate() {
    return getProxy().duplicate();
  }

  @Override
  public synchronized ProtoBlockStore.SegmentSnapshotRecord serialize() {
    return getProxy().serialize();
  }

  @Override
  public synchronized RawSnapshot getRawSnapshot() {
    return getProxy().getRawSnapshot();
  }

  protected synchronized T getProxy() {
    if (proxy == null) {
      proxy = (T)fragment.getSnapshot(version);
    }
    return proxy;
  }
}
