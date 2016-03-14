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

package org.waveprotocol.wave.client.wavepanel.view.dom.full;

import java.util.Arrays;
import java.util.EnumMap;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.ReadableStringMap.ProcV;
import org.waveprotocol.wave.model.util.StringMap;

/**
 * DOM encodings for view types.
 */
public final class TypeCodes {

  private final static EnumMap<Type, String> CODES = new EnumMap<Type, String>(Type.class);
  private final static StringMap<Type> TYPES = CollectionUtils.createStringMap();

  static {
    //
    // These type codes could really be any string that is unique within the
    // space of codes, but in order to retain some pretense of debugability, the
    // codes are letters that closely match the names of the view types.
    //
    // To avoid the need for quotation, the alphabet for these codes is is
    // restricted by the alphabets of:
    // 1. JSO property names,
    // 2. attribute value strings, and
    // 3. Uniqueness of characters across supported browsers (e.g.,
    // ...uppercase/lowercase distinguishability).
    // To keep this property, the alphabet for code values is restricted to
    // lower case alphanumeric characters only.
    //
    TYPES.put("c", Type.ROOT_CONVERSATION);
    TYPES.put("s", Type.PARTICIPANTS);
    TYPES.put("p", Type.PARTICIPANT);
    TYPES.put("a", Type.ADD_PARTICIPANT);
    TYPES.put("sp", Type.SCROLL_PANEL);
    TYPES.put("r", Type.ROOT_THREAD);
    TYPES.put("bb", Type.BLIPS);
    TYPES.put("ph", Type.PLACEHOLDER);
    TYPES.put("cr", Type.CHROME);
    TYPES.put("b", Type.BLIP);
    TYPES.put("to", Type.BLIP_TOP_MARGIN);
    TYPES.put("m", Type.META);
    TYPES.put("mb", Type.META_BAR);
    TYPES.put("tm", Type.TIME_AND_MENU);
    TYPES.put("bm", Type.BLIP_MENU_BUTTON);
    TYPES.put("bt", Type.BLIP_TIME);
    TYPES.put("document", Type.DOCUMENT);    
    TYPES.put("bf", Type.BLIP_FOCUS_FRAME);
    TYPES.put("bi", Type.BLIP_INDICATOR);
    TYPES.put("br", Type.BLIP_REPLIES);
    TYPES.put("bo", Type.BLIP_BOTTOM_MARGIN);
    TYPES.put("d", Type.ANCHOR);
    TYPES.put("g", Type.TOGGLE);
    TYPES.put("it", Type.INLINE_THREAD);
    TYPES.put("is", Type.INLINE_THREAD_STRUCTURE);
    TYPES.put("ic", Type.INLINE_CONVERSATION);
    TYPES.put("ot", Type.OUTLINE_THREAD);
    TYPES.put("rb", Type.REPLY_BOX);
    TYPES.put("bc", Type.BLIP_CONTINUATION_BAR);
    TYPES.put("cb", Type.CONTINUATION_BAR);
    TYPES.put("cu", Type.CONTINUATION_BUTTON);
    TYPES.put("cx", Type.CONTINUATION_TEXT);
    TYPES.put("cl", Type.CONTINUATION_LINE);
    TYPES.put("l", Type.TAGS);
    TYPES.put("j", Type.TAG);
    TYPES.put("o", Type.ADD_TAG);

    TYPES.each(
        new ProcV<Type>() {

          @Override
          public void apply(String code, Type type) {
            CODES.put(type, code);
          }
        });

    assert TYPES.countEntries() == CODES.size() : "not all codes are unique";
    assert CODES.keySet().containsAll(Arrays.asList(Type.values())) : "not all types are coded";
  }

  // Utility class.
  private TypeCodes() {
  }

  public static String kind(Type view) {
    return CODES.get(view);
  }

  public static Type type(String code) {
    return TYPES.get(code);
  }
}