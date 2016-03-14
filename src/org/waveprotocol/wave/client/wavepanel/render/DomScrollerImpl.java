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

import com.google.gwt.dom.client.Element;

import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.client.render.undercurrent.ScreenController;
import org.waveprotocol.wave.client.render.undercurrent.ScreenControllerImpl;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomUtil;
import org.waveprotocol.wave.common.logging.LoggerBundle;

/**
 * DOM scroller implementation.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class DomScrollerImpl extends Scroller.Impl {

  protected static LoggerBundle LOG = new DomLogger("render");
  
  private ScreenController screen;
  private Element scrollPanel;
  
  public DomScrollerImpl() {    
  }
    
  public void upgrade(ScreenController screen) {
    this.screen = screen;
  }
  
  @Override
  public int getScrollHeight() {
    return getScrollPanel().getScrollHeight();
  }  
  
  @Override
  public int getPosition() {
    return getScrollPanel().getScrollTop();    
  }
  
  @Override
  public void setPosition(int value, boolean silent) {
    LOG.trace().log("DomScrollerImpl.setPosition: value=" + value + ", silent=" + silent);
    
    if (screen != null) {
      screen.setScrollPosition(value, silent);
    } else {
      getScrollPanel().setScrollTop(value);
    }    
  }
  
  @Override
  public int getPanelTop() {
    return getScrollPanel().getAbsoluteTop();
  }

  @Override
  public int getPanelHeight() {
    return DomUtil.getElementHeight(getScrollPanel());
  }
  
  //
  // Private methods
  //

  private Element getScrollPanel() {
    if (scrollPanel == null) {
      scrollPanel = ScreenControllerImpl.getScrollPanel();
    }
    return scrollPanel;
  }  
}
