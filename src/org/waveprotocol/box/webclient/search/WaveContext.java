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

package org.waveprotocol.box.webclient.search;

import org.waveprotocol.wave.client.state.BlipReadStateMonitor;
import org.waveprotocol.wave.client.state.InboxStateMonitor;
import org.waveprotocol.wave.client.wavepanel.render.ObservableDynamicRenderer;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.supplement.ObservableSupplementedWave;
import org.waveprotocol.wave.model.wave.opbased.ObservableWaveView;

/**
 * Bundles together strongly-related wave objects.
 *
 * @author hearnden@google.com (David Hearnden)
 */
public final class WaveContext {
  
  private final ObservableWaveView wave;
  private final ObservableConversationView conversations;
  private final ObservableSupplementedWave supplement;
  private final BlipReadStateMonitor blipMonitor;
  private final InboxStateMonitor inboxMonitor;
  private final ObservableDynamicRenderer dynamicRenderer;

  public WaveContext(ObservableWaveView wave, ObservableConversationView conversations,
      ObservableSupplementedWave supplement, BlipReadStateMonitor blipMonitor,
      InboxStateMonitor inboxMonitor, ObservableDynamicRenderer dynamicRenderer) {
    this.wave = wave;
    this.conversations = conversations;
    this.supplement = supplement;
    this.blipMonitor = blipMonitor;
    this.inboxMonitor = inboxMonitor;
    this.dynamicRenderer = dynamicRenderer;
  }

  /**
   * @return the wave
   */
  public ObservableWaveView getWave() {
    return wave;
  }

  /**
   * @return the conversations
   */
  public ObservableConversationView getConversations() {
    return conversations;
  }

  /**
   * @return the supplement
   */
  public ObservableSupplementedWave getSupplement() {
    return supplement;
  }

  /**
   * @return the blip monitor
   */
  public BlipReadStateMonitor getBlipMonitor() {
    return blipMonitor;
  }

  /**
   * @return the inbox monitor
   */
  public InboxStateMonitor getInboxMonitor() {
    return inboxMonitor;
  }
  
  /**
   * @return the dynamic renderer
   */
  public ObservableDynamicRenderer getDynamicRenderer() {
    return dynamicRenderer;
  }
}
