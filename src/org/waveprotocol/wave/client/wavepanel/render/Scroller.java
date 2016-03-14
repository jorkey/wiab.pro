/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.wave.client.wavepanel.render;

/**
 * Scroller interface.
 * 
 * @author dyukon@gmail.com
 */
public interface Scroller {
  
  /**
   * @return total height of the scrolled content.
   */
  int getScrollHeight();
  
  /**
   * @return maximum value of scroll position.
   */
  int getMaxPosition();  
  
  /**
   * @return scroll position.
   */  
  int getPosition();
  
  /**
   * Sets scroll position.
   * 
   * @param value new scroll position value
   * @param silent true, if the trigger shouldn't be activated
   */
  void setPosition(int value, boolean silent);
  
  /**
   * Moves scroll position.
   * 
   * @param delta delta of the scroll position
   * @param silent true, if the trigger shouldn't be activated
   */
  void movePosition(int delta, boolean silent);
  
  /**
   * @return top coordinate of the scroll panel.
   */
  int getPanelTop();
  
  /**
   * @return height of the scroll panel.
   */
  int getPanelHeight();
  
  /**
   * @return true, if the scroller is scrolled to the begin.
   */
  boolean isAtBegin();
  
  /**
   * @return true, if the scroller is scrolled to the end.
   */
  boolean isAtEnd();
  
  /**
   * Primitive Scroller implementation.
   */
  public abstract class Impl implements Scroller {
    
    @Override
    public int getMaxPosition() {
      int maxPosition = getScrollHeight() - getPanelHeight();
      return maxPosition < 0 ? 0 : maxPosition;
    }
    
    @Override
    public void movePosition(int delta, boolean silent) {
      if (delta != 0) {
        setPosition(getPosition() + delta, silent);
      }
    }
    
    @Override
    public boolean isAtBegin() {
      return getPosition() == 0;
    }

    @Override
    public boolean isAtEnd() {
      int position = getPosition();
      return position == getMaxPosition() && position > 0;
    }    
  }
}
