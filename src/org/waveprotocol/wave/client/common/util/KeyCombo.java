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

package org.waveprotocol.wave.client.common.util;

public enum KeyCombo {

  // No modification
  SPACE,
  TAB,
  UP,
  DOWN,
  LEFT,
  RIGHT,
  HOME,
  END,
  PAGE_UP,
  PAGE_DOWN,
  ESC,
  ENTER,
  BACKSPACE,
  DELETE,
  INSERT,

  // Shift
  SHIFT_SPACE,
  SHIFT_TAB,
  SHIFT_ENTER,
  SHIFT_BACKSPACE,
  SHIFT_DELETE,
  SHIFT_INSERT,

  // Ctrl
  CTRL_SPACE,
  CTRL_ENTER,
  CTRL_EQUALS,
  CTRL_INSERT,
  CTRL_A,
  CTRL_B,
  CTRL_C,
  CTRL_D,
  CTRL_E,
  CTRL_F,
  CTRL_G,
  CTRL_H,
  CTRL_I,
  CTRL_J,
  CTRL_K,
  CTRL_L,
  CTRL_N,
  CTRL_O,
  CTRL_P,
  CTRL_Q,
  CTRL_R,
  CTRL_T,
  CTRL_U,
  CTRL_V,
  CTRL_W,
  CTRL_X,
  CTRL_Z,

  // Ctrl+Shift
  CTRL_SHIFT_SPACE,
  CTRL_SHIFT_K,
  CTRL_SHIFT_L,
  CTRL_SHIFT_R,
  CTRL_SHIFT_V,
  CTRL_SHIFT_1,
  CTRL_SHIFT_2,
  CTRL_SHIFT_3,
  CTRL_SHIFT_5,

  // Ctrl+Alt
  CTRL_ALT_D,
  CTRL_ALT_F,
  CTRL_ALT_G,
  CTRL_ALT_S,

  // Ctrl+Alt+Shift

  CTRL_ALT_SHIFT_V,

  // Meta
  META_A,
  META_B,
  META_C,
  META_D,
  META_F,
  META_G,
  META_I,
  META_J,
  META_K,
  META_L,
  META_N,
  META_O,
  META_P,
  META_Q,
  META_R,
  META_T,
  META_U,
  META_V,
  META_W,
  META_X,
  META_Z,

  // Meta+Shift
  META_SHIFT_K,
  META_SHIFT_R,
  META_SHIFT_V,
  META_SHIFT_5,

  // Meta+Alt+Shift
  META_ALT_SHIFT_V,

  // Meta
  META_LEFT,
  META_RIGHT,
  META_HOME,

  OTHER;  // Unknown keycombo

  public String getHint() {
    return EventWrapper.getKeyComboHint(this);
  }

  public KeyComboTask getAssignedTask(KeyComboContext context) {
    return KeyComboManager.getTaskByKeyCombo(context, this);
  }
}
