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

package org.waveprotocol.wave.model.conversation.navigator;

import org.waveprotocol.wave.model.conversation.Manifest;
import org.waveprotocol.wave.model.conversation.ManifestThread;
import org.waveprotocol.wave.model.conversation.ManifestBlip;

import java.util.Iterator;

/**
 * Adapter for manifest structure.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 * @author dyukon@gmail.com (D. Konovalchik)
 */
public class ManifestAdapter implements Adapter<ManifestThread, ManifestBlip> {

  private final Manifest manifest;

  public ManifestAdapter(Manifest manifest) {
    this.manifest = manifest;
  }

  @Override
  public void addListener(Listener<ManifestThread, ManifestBlip> listener) {
  }

  @Override
  public ManifestThread getRootThread() {
    return manifest.getRootThread();
  }

  @Override
  public Iterator<? extends ManifestBlip> getBlips(ManifestThread thread) {
    return thread.getBlips().iterator();
  }

  @Override
  public Iterator<? extends ManifestThread> getThreads(ManifestBlip blip) {
    return blip.getReplies().iterator();
  }

  @Override
  public boolean isThreadInline(ManifestThread thread) {
    return thread.isInline();
  }
}
