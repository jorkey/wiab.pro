/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.wave.common.regexp;

/**
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public interface RegExpWrapFactory {
  RegExpWrap create(String regex);
}
