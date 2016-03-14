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

import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.supplement.ScreenPosition;

/**
 * Scrolls the wave to the given start position after all necessary rendering is done.
 * 
 * @param <T> type of the rendered element.
 * 
 * @author dyukon@gmail.com
 */
public class ScreenPositionScrollerImpl<T> implements ScreenPositionScroller {
  
  /**
   * Creates the screen position scroller.
   * 
   * @param renderer the dynamic renderer
   * @param scroller
   * @param measurer element measurer
   * @return new screen position scroller
   */
  public static ScreenPositionScrollerImpl create(ObservableDynamicRenderer renderer,
      Scroller scroller, ElementMeasurer measurer) {
    return new ScreenPositionScrollerImpl(renderer, scroller, measurer);
  }  
  
  protected final ObservableDynamicRenderer<T> renderer;  
  protected final Scroller scroller;
  private final ElementMeasurer<T> measurer;
  
  private final ObservableDynamicRenderer.Listener rendererListener =
      new ObservableDynamicRenderer.ListenerImpl() {

    @Override
    public void onPhaseFinished(ObservableDynamicRenderer.RenderResult result) {
      doScrollAttempt();
    }
  };
  
  protected ConversationView conversationView;
  protected ScreenPosition positionToScroll;  
    
  protected ScreenPositionScrollerImpl(ObservableDynamicRenderer renderer, Scroller scroller,
      ElementMeasurer measurer) {
    this.renderer = renderer;
    this.scroller = scroller;
    this.measurer = measurer;
    
    renderer.addListener(rendererListener);
  }

  public void initialize(ScreenPosition startPosition, ConversationView conversationView) {
    this.positionToScroll = startPosition;
    this.conversationView = conversationView;    
  }
  
  @Override
  public void scrollToBlip(ConversationBlip blip) {
    throw new UnsupportedOperationException("Not supported.");
  }
  
  @Override
  public void scrollToScreenPosition(ScreenPosition screenPosition) {    
    if (screenPosition != null) {
      String blipId = screenPosition.getBlipId();
      if (!renderer.isBlipReady(blipId)) {
        positionToScroll = screenPosition;        
        renderer.dynamicRendering(blipId);
        return;
      } 
      int scrollPosition = screenPositionToScrollPosition(screenPosition);
      if (isScrollPositionAchievable(scrollPosition)) {
        immediateScrollToScrollPosition(scrollPosition);
        positionToScroll = null;        
      } else {
        positionToScroll = screenPosition;
      }
    }
  }

  @Override
  public ScreenPosition getScreenPosition() {
    throw new UnsupportedOperationException("Not supported.");
  }
  
  //
  // Protected methods
  //
  
  protected void doScrollAttempt() {
    if (positionToScroll != null) {
      scrollToScreenPosition(positionToScroll);
    }
  }
  
  protected int screenPositionToScrollPosition(ScreenPosition screenPosition) {
    int scrollPosition = -1;
    String blipId = screenPosition.getBlipId();
    switch (blipId) {
      case IdConstants.FIRST_BLIP_ID:
        scrollPosition = 0;
        break;
      case IdConstants.LAST_BLIP_ID:
        scrollPosition = scroller.getMaxPosition();
        break;
      default:
        ConversationBlip blip = conversationView.getRoot().getBlip(blipId);
        if (blip != null) {
          T blipElement = renderer.getElementByBlip(blip);
          if (blipElement != null) {
            scrollPosition = measurer.getTop(blipElement) + scroller.getPosition()
                - scroller.getPanelTop();
          }  
        }
    }
    return scrollPosition;
  }
  
  //
  // Private methods
  //
  
  private boolean isScrollPositionAchievable(int scrollPosition) {
    return scroller.getScrollHeight() >= scrollPosition + scroller.getPanelHeight();
  }
  
  private void immediateScrollToScrollPosition(int scrollPosition) {
    if (scrollPosition >= 0) {
      scroller.setPosition(scrollPosition, true);
    }
  }
}
