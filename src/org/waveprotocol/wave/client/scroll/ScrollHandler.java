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

package org.waveprotocol.wave.client.scroll;

import org.waveprotocol.wave.client.common.util.KeyCombo;
import org.waveprotocol.wave.client.common.util.KeyComboContext;
import org.waveprotocol.wave.client.common.util.KeyComboTask;
import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.event.KeySignalHandler;

import java.util.EnumSet;

/**
 * Translates UI gestures to scroll actions.
 *
 * @author hearnden@google.com (David Hearnden)
 */
public final class ScrollHandler implements KeySignalHandler {

  private final ScrollController controller;

  /**
   * Creates a scroll controller.
   */
  private ScrollHandler(ScrollController controller) {
    this.controller = controller;
  }

  public static ScrollHandler install(WavePanel panel, ScrollPanel<?> scroller) {
    ScrollHandler c = new ScrollHandler(new ScrollController(scroller));
    panel.getKeyRouter().registerTasks(KeyComboContext.WAVE, EnumSet.of(
        KeyComboTask.SCROLL_TO_BEGIN, KeyComboTask.SCROLL_TO_END,
        KeyComboTask.SCROLL_TO_PREVIOUS_PAGE, KeyComboTask.SCROLL_TO_NEXT_PAGE), c);
    return c;
  }

  @Override
  public boolean onKeySignal(KeyCombo key) {
    switch (key.getAssignedTask(KeyComboContext.WAVE)) {
      case SCROLL_TO_PREVIOUS_PAGE:
        controller.pageUp();
        return true;
      case SCROLL_TO_NEXT_PAGE:
        controller.pageDown();
        return true;
      case SCROLL_TO_BEGIN:
        controller.home();
        return true;
      case SCROLL_TO_END:
        controller.end();
        return true;
    }
    throw new AssertionError("unknown key: " + key.getHint());
  }
}
