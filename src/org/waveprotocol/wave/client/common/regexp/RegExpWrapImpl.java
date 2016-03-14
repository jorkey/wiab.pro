/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.wave.client.common.regexp;

import org.waveprotocol.wave.common.regexp.MatchResultWrap;
import org.waveprotocol.wave.common.regexp.RegExpWrap;

import com.google.gwt.regexp.shared.MatchResult;
import com.google.gwt.regexp.shared.RegExp;

import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class RegExpWrapImpl implements RegExpWrap {

  private final RegExp regExp;

  public RegExpWrapImpl(RegExp regExp) {
    this.regExp = regExp;
  }


  @Override
  public List<MatchResultWrap> exec(String input) {
    List<MatchResultWrap> results = new LinkedList<MatchResultWrap>();
    for (;;) {
      MatchResult result = regExp.exec(input);
      if (result == null) {
        break;
      }
      results.add(new MatchResultWrapImpl(result));
    }
    return results;
  }
}
