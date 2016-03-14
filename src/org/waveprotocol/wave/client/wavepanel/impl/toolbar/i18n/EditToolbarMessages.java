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

package org.waveprotocol.wave.client.wavepanel.impl.toolbar.i18n;

import com.google.gwt.i18n.client.Messages;
import com.google.gwt.i18n.client.Messages.DefaultMessage;

/**
 *
 * @author dkonovalchik (Denis Konovalchik)
 */
public interface EditToolbarMessages extends Messages {
  @DefaultMessage("Bold")
  String boldHint();
  
  @DefaultMessage("Italic")
  String italicHint();  
  
  @DefaultMessage("Underline")
  String underlineHint();

  @DefaultMessage("Strike through")
  String strikethroughHint();
  
  @DefaultMessage("Superscript")
  String superscriptHint();
  
  @DefaultMessage("Subscript")
  String subscriptHint();
  
  @DefaultMessage("Font size")
  String fontSizeHint();
  
  @DefaultMessage("Font family")
  String fontFamilyHint();
  
  @DefaultMessage("Heading")
  String headingHint();
  
  @DefaultMessage("Font color")
  String fontColorHint();

  @DefaultMessage("Background color")
  String fontBackcolorHint();

  @DefaultMessage("Indent")
  String indentHint();

  @DefaultMessage("Outdent")
  String outdentHint();

  @DefaultMessage("Unordered list")
  String unorderedListHint();
  
  @DefaultMessage("Ordered list")
  String orderedListHint();
  
  @DefaultMessage("Align to left")
  String alignLeftHint();

  @DefaultMessage("Align to center")
  String alignCenterHint();  
  
  @DefaultMessage("Align to right")
  String alignRightHint();

  @DefaultMessage("Clear formatting")
  String clearFormattingHint();  
  
  @DefaultMessage("Insert link")
  String insertLinkHint();
  
  @DefaultMessage("Remove link")
  String removeLinkHint();
  
  @DefaultMessage("Insert gadget")
  String insertGadgetHint();

  @DefaultMessage("Insert attachment")
  String insertAttachmentHint();  
}