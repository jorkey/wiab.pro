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
package org.waveprotocol.wave.client.wavepanel.impl.focus;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;

import org.waveprotocol.wave.client.common.util.KeyCombo;
import org.waveprotocol.wave.client.common.util.KeyComboContext;
import org.waveprotocol.wave.client.common.util.KeyComboTask;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.event.EventHandlerRegistry;
import org.waveprotocol.wave.client.wavepanel.event.KeySignalHandler;
import org.waveprotocol.wave.client.wavepanel.event.KeySignalRouter;
import org.waveprotocol.wave.client.wavepanel.event.WaveMouseDownHandler;
import org.waveprotocol.wave.client.wavepanel.render.ScreenPositionScroller;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TypeCodes;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.focus.ObservableFocusFramePresenter;

import java.util.EnumSet;

/**
 * Interpets user gestures as focus-frame actions.
 *
 */
public final class FocusFrameController implements WaveMouseDownHandler, KeySignalHandler {

  /**
   * Installs the focus-frame feature in a wave panel.
   * 
   * @param focus focus frame presenter
   * @param panel wave panel
   * 
   * @return new focus-frame feature
   */
  public static FocusFrameController create(ObservableFocusFramePresenter focus, WavePanel panel) {
    FocusFrameController controller = new FocusFrameController(focus, panel.getViewProvider());
    controller.install(panel.getHandlers(), panel.getKeyRouter());
    return controller;
  }
  
  protected static LoggerBundle LOG = new DomLogger("render");  
  
  private final ObservableFocusFramePresenter focus;
  private final DomAsViewProvider panel;
  
  private ScreenPositionScroller scroller;
  private ModelAsViewProvider modelAsViewProvider;

  /**
   * Creates a focus controller.
   */
  private FocusFrameController(ObservableFocusFramePresenter focus, DomAsViewProvider panel) {
    this.focus = focus;
    this.panel = panel;
  }

  private void install(EventHandlerRegistry handlers, KeySignalRouter keys) {
    handlers.registerMouseDownHandler(TypeCodes.kind(Type.BLIP), this);
    keys.registerTasks(KeyComboContext.WAVE, EnumSet.of(
        KeyComboTask.FOCUS_PREVIOUS_BLIP,
        KeyComboTask.FOCUS_NEXT_BLIP,
        KeyComboTask.FOCUS_NEXT_UNREAD_BLIP), this);
  }

  public void upgrade(ScreenPositionScroller scroller, ModelAsViewProvider modelAsViewProvider) {
    this.scroller = scroller;
    this.modelAsViewProvider = modelAsViewProvider;
  }
  
  //
  // WaveMouseDownHandler
  //
  
  @Override
  public boolean onMouseDown(MouseDownEvent event, Element source) {
    if (event.getNativeButton() != NativeEvent.BUTTON_LEFT) {
      return false;
    }
    BlipView blipView = panel.asBlip(source);
    ConversationBlip blip = modelAsViewProvider.getBlip(blipView);
    focus.focus(blip);    
    return true;
  }

  //
  // KeySignalHandler
  //
  
  @Override
  public boolean onKeySignal(KeyCombo key) {
    ConversationBlip blip = null;
    boolean keyProcessed = false;
    switch (key.getAssignedTask(KeyComboContext.WAVE)) {
      case FOCUS_PREVIOUS_BLIP:
        blip = focus.getNeighborBlip(false);
        keyProcessed = true;
        break;
      case FOCUS_NEXT_BLIP:
        blip = focus.getNeighborBlip(true);
        keyProcessed = true;
        break;
      case FOCUS_NEXT_UNREAD_BLIP:
        blip = focus.getNextUnreadBlip();
        keyProcessed = true;
        break;
    }
    if (blip != null) {
      focus.focus(blip);
      if (scroller != null) {
        scroller.scrollToBlip(blip);
      }
    }
    return keyProcessed;
  }  
}
