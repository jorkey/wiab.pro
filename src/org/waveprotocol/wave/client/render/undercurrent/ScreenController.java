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

package org.waveprotocol.wave.client.render.undercurrent;

import org.waveprotocol.wave.model.wave.SourcesEvents;

/**
 * Screen controller reacting for scroll and resize events.
 */
public interface ScreenController extends SourcesEvents<ScreenController.Listener> {

  /**
   * Sources of the screen change.
   */
  public static enum ChangeSource {
    /** Resize of parent panel or window. */
    PARENT_RESIZE,
    /** Move of scrollbar by mouse. */
    SCROLLBAR_MOUSE_MOVE,
    /** Mouse up on scrollbar. */
    SCROLLBAR_MOUSE_UP,
    /** Programmatical setting scroll position. */
    PROGRAM,
    /** Any other source. */
    OTHER
  }  
  
  /**
   * Directions of scrolling.
   */
  public static enum ScrollDirection {
    /** Scrolling up. */
    UP,
    /** Scrolling down. */
    DOWN,
    /** No scrolling at all. */
    NONE
  }
  
  /**
   * Kinds of scrolling speed change.
   */
  public static enum ScrollSpeedChange {
    /** Scrolling has been accelerated. */
    ACCELERATED,
    /** Scrolling has been slowed down. */
    SLOWED_DOWN,
    /** Scrolling speed hasn't been changed. */
    NONE
  }
  
  /**
   * Listens to screen changes.
   */
  interface Listener {

    /**
     * Is called when the visible screen coordinates are changed.
     */
    void onScreenChanged();
  }
  
  /**
   * Completes working when the wave is about to close.
   */
  void complete();
  
  /**
   * Disconnects listeners and destroys the object.
   */
  void destroy();  
  
  //
  // Getters
  //
  
  /**
   * @return the last screen change source
   */
  ChangeSource getLastChangeSource();
  
  /**
   * @return scrolling position value
   */
  int getScrollPosition();
  
  /**
   * @return true, if the scrolling is going on
   */
  boolean isScrolling();  
  
  /**
   * @return current scrolling direction value
   */
  ScrollDirection getScrollDirection();

  /**
   * @return last scrolling direction value
   */
  ScrollDirection getLastScrollDirection();  

  /**
   * @return last scrolling direction value
   */
  ScrollDirection getPreviousScrollDirection();  
  
  /**
   * @return last absolute value of scrolling speed in pixels per second
   */
  double getLastAbsoluteScrollSpeed();

  /**
   * @return previous absolute value of scrolling speed in pixels per second
   */
  double getPreviousAbsoluteScrollSpeed();  
  
  /**
   * @return last scrolling speed change value
   */
  ScrollSpeedChange getLastScrollSpeedChange();
  
  /**
   * @return true, if left mouse button is pressed
   */
  boolean isLeftMouseButtonPressed();
  
  /**
   * Sets scrolling position programmatically.
   * 
   * @param scrollPosition new scrolling position
   * @param silent true, if the scroll position shouldn't trigger event
   */
  void setScrollPosition(int scrollPosition, boolean silent);
}
