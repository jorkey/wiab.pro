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

package org.waveprotocol.wave.client.editor.constants;

import org.waveprotocol.wave.client.common.util.JsEvents;

/**
 * List of all events.
 * Events we don't care about handling in the editor are commented out.
 *
 * Add more events if they become known, even if we don't need to handle them.
 * Uncomment in order to handle an event.
 *
 * Only harmless events should be commented - harmful ones that we don't know
 * how to properly handle need to be cancelled.
 *
 * @author danilatos@google.com (Daniel Danilatos)
 */
public class BrowserEvents {
  /**
   * Array of events the editor listens for.
   */
  public static final String[] HANDLED_EVENTS = new String[] {

  // Category: mouse
      JsEvents.CLICK,
      JsEvents.DOUBLE_CLICK,
      JsEvents.MOUSE_DOWN, // need to flush on mouse down, for typex assumptions
      JsEvents.MOUSE_UP,
      // JsEvents.MOUSE_OVER,
      // JsEvents.MOUSE_MOVE,
      // JsEvents.MOUSE_OUT,
      // JsEvents.MOUSE_WHEEL,
      JsEvents.CONTEXT_MENU,
      // JsEvents.SELECT_START,

  // Category: key
      JsEvents.KEY_PRESS,
      JsEvents.KEY_DOWN,
      JsEvents.KEY_UP,

  // Category: input
      JsEvents.COMPOSITION_START,  // IME events
      JsEvents.COMPOSITION_END,    // IME events
      JsEvents.COMPOSITION_UPDATE, // IME events
      JsEvents.TEXT,               // IME events
      JsEvents.TEXT_INPUT,         // In supported browsers, fired both for IME and non-IME input


  // Category: mutation
      //TODO(danilatos): Omit these for IE
      JsEvents.DOM_SUBTREE_MODIFIED,
      JsEvents.DOM_NODE_INSERTED,
      JsEvents.DOM_NODE_REMOVED,
      JsEvents.DOM_NODE_REMOVED_FROM_DOCUMENT,
      JsEvents.DOM_NODE_INSERTED_INTO_DOCUMENT,
      JsEvents.DOM_ATTRIBUTE_MODIFIED,
      JsEvents.DOM_CHARACTER_DATA_MODIFIED,
      JsEvents.DOM_ELEMENT_NAME_CHANGED,
      JsEvents.DOM_ATTRIBUTE_NAME_CHANGED,
      JsEvents.DOM_MOUSE_SCROLL,

  // Category: focus
      // JsEvents.FOCUS,
      // JsEvents.BLUR,
      // JsEvents.BEFORE_EDIT_FOCUS,


  // Category: dragdrop
      // TODO(danilatos): Handle drop events and protect the DOM
      // JsEvents.DRAG,
      // JsEvents.DRAG_START,
      // JsEvents.DRAG_ENTER,
      // JsEvents.DRAG_OVER,
      // JsEvents.DRAG_LEAVE,
      // JsEvents.DRAG_END,
      // JsEvents.DROP,

  // Category: frame/object
      // JsEvents.LOAD,
      // JsEvents.UNLOAD,
      // JsEvents.ABORT,
      // JsEvents.ERROR,
      // JsEvents.RESIZE,
      // JsEvents.SCROLL,
      // JsEvents.BEFORE_UNLOAD,
      // JsEvents.STOP,

  // Category: form
      // JsEvents.SELECT,
      JsEvents.CHANGE,
      JsEvents.SUBMIT,
      JsEvents.RESET,

  // Category: ui
      JsEvents.DOM_FOCUS_IN,
      JsEvents.DOM_FOCUS_OUT,
      JsEvents.DOM_ACTIVATE,

  // Category: clipboard
      JsEvents.CUT,
      JsEvents.COPY,
      JsEvents.PASTE,
      JsEvents.BEFORE_CUT,
      JsEvents.BEFORE_COPY,
      JsEvents.BEFORE_PASTE,

  // Category: data binding
      JsEvents.AFTER_UPDATE,
      JsEvents.BEFORE_UPDATE,
      JsEvents.CELL_CHANGE,
      JsEvents.DATA_AVAILABLE,
      JsEvents.DATA_SET_CHANGED,
      JsEvents.DATA_SET_COMPLETE,
      JsEvents.ERROR_UPDATE,
      JsEvents.ROW_ENTER,
      JsEvents.ROW_EXIT,
      JsEvents.ROWS_DELETE,
      JsEvents.ROW_INSERTED,

  // Category: misc
      // JsEvents.HELP,

      // JsEvents.START,
      // JsEvents.FINISH,
      // JsEvents.BOUNCE,

      // JsEvents.BEFORE_PRINT,
      // JsEvents.AFTER_PRINT,

      // JsEvents.PROPERTY_CHANGE,
      // JsEvents.FILTER_CHANGE,
      // JsEvents.READY_STATE_CHANGE,
      // JsEvents.LOSE_CAPTURE
  };


  private BrowserEvents() {}
}
