/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.wave.common.regexp;

import java.util.List;

/**
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public interface RegExpWrap {
  public List<MatchResultWrap> exec(String query);
}
