/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.wave.client.common.util;

/**
 * Tasks to be executed by key combos.
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public enum KeyComboTask {

  // Text editor
// Text editor
// Text editor
// Text editor
  FORMAT_BOLD,
  FORMAT_ITALIC,
  FORMAT_UNDERLINE,
  FORMAT_STRIKETHROUGH,
  CREATE_LINK,
  CLEAR_LINK,
  SELECT_ALL,
  TEXT_DELETE,
  KEY_BACKSPACE,
  TEXT_CUT,
  TEXT_COPY,
  TEXT_PASTE,
  TEXT_SUGGEST, // suggest variant by first symbols
  DONE_WITH_EDITING,
  CANCEL_EDITING, // finish editing and delete blip if it's empty

  // Wave
  EDIT_BLIP,
  REPLY_TO_BLIP,
  CONTINUE_THREAD,
  DELETE_BLIP,
  DELETE_BLIP_WITHOUT_CONFIRMATION,
  POPUP_LINK,
  ADD_PARTICIPANT,
  ADD_TAG,
  SCROLL_TO_BEGIN,
  SCROLL_TO_END,
  SCROLL_TO_PREVIOUS_PAGE,
  SCROLL_TO_NEXT_PAGE,
  FOCUS_NEXT_BLIP,
  FOCUS_PREVIOUS_BLIP,
  FOCUS_NEXT_UNREAD_BLIP,

  // Unknown
  UNKNOWN
}
