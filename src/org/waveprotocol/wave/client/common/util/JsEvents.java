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

/**
 * Names of native JS events.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class JsEvents {
 
  // Mouse
  public static final String CLICK = "click";
  public static final String CONTEXT_MENU = "contextmenu";
  public static final String DOUBLE_CLICK = "dblclick";
  public static final String MOUSE_DOWN = "mousedown";
  public static final String MOUSE_MOVE = "mousemove";
  public static final String MOUSE_OUT = "mouseout";
  public static final String MOUSE_OVER = "mouseover";
  public static final String MOUSE_UP = "mouseup";
  public static final String MOUSE_WHEEL = "mousewheel";
  public static final String SELECT_START = "selectstart";
  
  // Key
  public static final String KEY_DOWN = "keydown";
  public static final String KEY_PRESS = "keypress";
  public static final String KEY_UP = "keyup";  
  
  // Input
  /** IME composition commencement event. */  
  public static final String COMPOSITION_START = "compositionstart";  
  /** IME composition completion event. */  
  public static final String COMPOSITION_END = "compositionend";
  /** DOM3 composition update event. */  
  public static final String COMPOSITION_UPDATE = "compositionupdate";
  /** Firefox composition update event. */  
  public static final String TEXT = "text";
  /** Poorly supported DOM3 event. */  
  public static final String TEXT_INPUT = "textInput";  
  
  // DOM
  public static final String DOM_ATTRIBUTE_MODIFIED = "DOMAttrModified";
  public static final String DOM_ATTRIBUTE_NAME_CHANGED = "DOMAttributeNameChanged";
  public static final String DOM_CHARACTER_DATA_MODIFIED = "DOMCharacterDataModified";  
  public static final String DOM_ELEMENT_NAME_CHANGED = "DOMElementNameChanged";
  public static final String DOM_MOUSE_SCROLL = "DOMMouseScroll";
  public static final String DOM_NODE_INSERTED = "DOMNodeInserted";
  public static final String DOM_NODE_REMOVED = "DOMNodeRemoved";
  public static final String DOM_NODE_REMOVED_FROM_DOCUMENT = "DOMNodeRemovedFromDocument";  
  public static final String DOM_NODE_INSERTED_INTO_DOCUMENT = "DOMNodeInsertedIntoDocument";  
  public static final String DOM_SUBTREE_MODIFIED = "DOMSubtreeModified";
  
  // Focus
  public static final String BEFORE_EDIT_FOCUS = "beforeeditfocus";
  public static final String BLUR = "blur";  
  public static final String FOCUS = "focus";  
  
  // Drag and drop
  public static final String DRAG = "drag";
  public static final String DRAG_START = "dragstart";
  public static final String DRAG_ENTER = "dragenter";
  public static final String DRAG_OVER = "dragover";
  public static final String DRAG_LEAVE = "dragleave";
  public static final String DRAG_END = "dragend";
  public static final String DROP = "drop";
  
  // Frames
  public static final String ABORT = "abort";
  public static final String BEFORE_UNLOAD = "beforeunload";
  public static final String ERROR = "error";
  public static final String LOAD = "load";
  public static final String RESIZE = "resize";
  public static final String SCROLL = "scroll";
  public static final String STOP = "stop";
  public static final String UNLOAD = "unload";
  
  // Forms
  public static final String CHANGE = "change";
  public static final String RESET = "reset";
  public static final String SELECT = "select";
  public static final String SUBMIT = "submit";
  
  // UI
  public static final String DOM_ACTIVATE = "domactivate";
  public static final String DOM_FOCUS_IN = "domfocusin";
  public static final String DOM_FOCUS_OUT = "domfocusout";
  
  // Clipboard
  public static final String BEFORE_COPY = "beforecopy";
  public static final String BEFORE_CUT = "beforecut";
  public static final String BEFORE_PASTE = "beforepaste"; 
  public static final String COPY = "copy";
  public static final String CUT = "cut";
  public static final String PASTE = "paste";  
  
  // Data binding
  public static final String AFTER_UPDATE = "afterupdate";
  public static final String BEFORE_UPDATE = "beforeupdate";
  public static final String CELL_CHANGE = "cellchange";
  public static final String DATA_AVAILABLE = "dataavailable";
  public static final String DATA_SET_CHANGED = "datasetchanged";
  public static final String DATA_SET_COMPLETE = "datasetcomplete";
  public static final String ERROR_UPDATE = "errorupdate";
  public static final String ROW_ENTER = "rowenter";
  public static final String ROW_EXIT = "rowexit";
  public static final String ROW_INSERTED = "rowinserted";
  public static final String ROWS_DELETE = "rowsdelete";
  
  // Marquee
  public static final String START = "start";  
  public static final String BOUNCE = "bounce";
  public static final String FINISH = "finish";  
  
  // Print
  public static final String BEFORE_PRINT = "beforeprint";
  public static final String AFTER_PRINT = "afterprint";
  
  // Misc
  public static final String FILTER_CHANGE = "filterchange";
  public static final String HELP = "help";
  public static final String LOSE_CAPTURE = "losecapture";
  public static final String PROPERTY_CHANGE = "propertychange";
  public static final String READY_STATE_CHANGE = "readystatechange";
}
