/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.box.server.util.regexp;

import java.util.LinkedList;
import java.util.List;
import org.waveprotocol.wave.common.regexp.MatchResultWrap;
import org.waveprotocol.wave.common.regexp.RegExpWrap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class RegExpWrapImpl implements RegExpWrap {

  private final Pattern regExp;

  public RegExpWrapImpl(Pattern regExp) {
    this.regExp = regExp;
  }

  @Override
  public List<MatchResultWrap> exec(String input) {
    List<MatchResultWrap> results = new LinkedList<MatchResultWrap>();
    Matcher matcher = regExp.matcher(input);
    while (matcher.find()) {
      results.add(new MatchResultWrapImpl(matcher.toMatchResult()));
    }
    return results;
  }
}
