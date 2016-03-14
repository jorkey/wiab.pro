/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.box.server.util.regexp;

import org.waveprotocol.wave.common.regexp.MatchResultWrap;

import java.util.regex.MatchResult;

/**
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class MatchResultWrapImpl implements MatchResultWrap {
  private final MatchResult result;

  public MatchResultWrapImpl(MatchResult result) {
    this.result = result;
  }

  @Override
  public String getGroup(int index) {
    if (result != null) {
      return result.group(index);
    }
    return null;
  }

  @Override
  public int getGroupCount() {
    if (result != null) {
      return result.groupCount();
    }
    return 0;
  }

  @Override
  public String getInput() {
    return result.group();
  }
}
