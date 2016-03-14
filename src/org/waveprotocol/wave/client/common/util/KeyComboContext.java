/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.wave.client.common.util;

/**
 * Context for key combo execution.
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public enum KeyComboContext {

  /** Tasks called by key combos pressed in the text editor. */
  TEXT_EDITOR,

  /** Tasks called by key combos pressed during observing wave. */
  WAVE
}
