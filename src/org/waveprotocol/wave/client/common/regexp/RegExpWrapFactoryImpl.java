/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.wave.client.common.regexp;

import org.waveprotocol.wave.common.regexp.RegExpWrapFactory;
import org.waveprotocol.wave.common.regexp.RegExpWrap;

import com.google.gwt.regexp.shared.RegExp;

/**
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class RegExpWrapFactoryImpl implements RegExpWrapFactory {
  @Override
  public RegExpWrap create(String regex) {
    return new RegExpWrapImpl(RegExp.compile(regex, "g"));
  }
}
