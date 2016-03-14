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

package org.waveprotocol.wave.client.wavepanel.impl.focus;

import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;
import org.waveprotocol.wave.model.util.CollectionUtils;

import java.util.Collections;
import java.util.Map;

/**
 * Selects the blip that should should receive the focus.
 *
 * @author yurize@apache.org (Yuri Zelikov)
 */
public class FocusBlipSelector {

  /** The conversation. */
  private final ConversationView wave;
  /** The navigator. */
  private final ConversationNavigator navigator;
  /** The root blip. */
  private ConversationBlip rootBlip;

  /**
   * Creates a {@link FocusBlipSelector}.
   *
   * @param wave the conversation structure
   * @param navigator the conversation navigator
   * @return the focus blip selector.
   */
  public static FocusBlipSelector create(ConversationView wave, ConversationNavigator navigator) {
    return new FocusBlipSelector(wave, navigator);
  }

  FocusBlipSelector(ConversationView wave, ConversationNavigator navigator) {
    this.wave = wave;
    this.navigator = navigator;
  }

  /**
   * @return the most recently modified blip.
   */
  public ConversationBlip selectMostRecentlyModified() {
    Conversation conversation  = wave.getRoot();
    if (conversation == null) {
      return null;
    } else {
      ConversationBlip startBlip = wave.getRoot().getRootThread().getFirstBlip();
      if (startBlip == null) {
        return null;
      }
      return findMostRecentlyModified(startBlip);
    }
  }

  /**
   * @return the root blip of the currently displayed wave.
   */
  public ConversationBlip getOrFindRootBlip() {
    if (rootBlip == null) {
      Conversation conversation  = wave.getRoot();
      if (conversation == null) {
        return null;
      } else {
        rootBlip = wave.getRoot().getRootThread().getFirstBlip();
      }
    }
    return rootBlip;
  }

  private ConversationBlip findMostRecentlyModified(ConversationBlip start) {
    ConversationBlip blip = start;
    Map<Long, ConversationBlip> blips = CollectionUtils.newHashMap();
    while (blip != null) {
      blips.put(blip.getLastModifiedTime(), blip);
      blip = navigator.getNextBlip(blip);
    }
    long lmt = Collections.max(blips.keySet());
    return blips.get(lmt);
  }
}
