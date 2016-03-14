/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.wave.client.common.regexp;

import com.google.gwt.regexp.shared.MatchResult;
import org.waveprotocol.wave.common.regexp.MatchResultWrap;

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
      return result.getGroup(index);
    }
    return null;
  }

  @Override
  public int getGroupCount() {
    if (result != null) {
      return result.getGroupCount();
    }
    return 0;
  }

  @Override
  public String getInput() {
    return result.getInput();
  }
}
