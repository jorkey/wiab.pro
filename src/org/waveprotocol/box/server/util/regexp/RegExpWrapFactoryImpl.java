/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.box.server.util.regexp;

import org.waveprotocol.wave.common.regexp.RegExpWrapFactory;
import org.waveprotocol.wave.common.regexp.RegExpWrap;

import java.util.regex.Pattern;

/**
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class RegExpWrapFactoryImpl implements RegExpWrapFactory {
  @Override
  public RegExpWrap create(String regex) {
    return new RegExpWrapImpl(Pattern.compile(regex));
  }
}
