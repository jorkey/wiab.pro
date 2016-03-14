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

import com.google.gwt.core.client.Duration;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.Window;

import org.waveprotocol.box.webclient.flags.Flags;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.client.scheduler.Scheduler.IncrementalTask;
import org.waveprotocol.wave.client.scheduler.SchedulerInstance;
import org.waveprotocol.wave.client.scheduler.TimerService;
import org.waveprotocol.wave.client.wavepanel.render.ScreenPositionScroller;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomUtil;
import org.waveprotocol.wave.client.widget.common.ImplPanel;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.supplement.ScreenPosition;
import org.waveprotocol.wave.model.supplement.SupplementedWave;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;
import org.waveprotocol.wave.model.util.Preconditions;

/**
 * Screen controller implementation.
 */
public class ScreenControllerImpl implements ScreenController {

  protected static LoggerBundle LOG = new DomLogger("render");  
  
  /** The maximum delta time (in ms) after which scrolling is treated as finished. */
  private final static int SCROLL_FINISH_DELTA_TIME_MS = 100;  
  
  public static ScreenControllerImpl create(ImplPanel waveHolder, ScreenPositionScroller scroller,
      SupplementedWave supplement, WaveletId waveletId) {
    return new ScreenControllerImpl(getScrollPanel(), waveHolder, scroller, supplement, waveletId);
  }

  public static Element getScrollPanel() {
    return DomUtil.findFirstChildElement(DomUtil.getMainElement(), Type.ROOT_CONVERSATION,
        Type.SCROLL_PANEL);
  }
  
  private final Element scrollPanel;
  private final ImplPanel waveHolder;
  private final ScreenPositionScroller scroller;
  private final SupplementedWave supplement;
  private final WaveletId waveletId;
  private final HandlerRegistration windowResizeRegistration;
  
  private final CopyOnWriteSet<Listener> listeners = CopyOnWriteSet.create();
  
  private double lastChangeTime;
  private double previousChangeTime;
  private int lastScrollPosition;
  private int previousScrollPosition;
  private double lastAbsoluteScrollSpeed;
  private double previousAbsoluteScrollSpeed;
  private ChangeSource lastChangeSource = ChangeSource.OTHER;
  private ScrollDirection lastScrollDirection = ScrollDirection.NONE;
  private ScrollDirection previousScrollDirection = ScrollDirection.NONE;  
  private ScrollSpeedChange lastScrollSpeedChange = ScrollSpeedChange.NONE;
  
  private boolean leftMouseButtonPressed;
  private boolean scrollBarScrolling;
  private Integer silentScrollPosition;

  private boolean needsSupplementUpdate;
  
  private final IncrementalTask supplementUpdateTask = new IncrementalTask() {

    @Override
    public boolean execute() {
      updateSupplement();
      return true;
    }
  };  
  
  private final ImplPanel.Listener waveHolderListener = new ImplPanel.Listener() {

    @Override
    public void onResize() {
      processScreenChange(ChangeSource.PARENT_RESIZE);
    }
  };
  
  private ScreenControllerImpl(final Element scrollPanel, ImplPanel waveHolder,
      ScreenPositionScroller scroller, SupplementedWave supplement, WaveletId waveletId) {
    Preconditions.checkArgument(waveHolder != null, "Wave holder mustn't be null");
    
    this.scrollPanel = scrollPanel;
    this.waveHolder = waveHolder;
    this.scroller = scroller;
    this.supplement = supplement;
    this.waveletId = waveletId;
    
    if (scrollPanel != null) {
      DOM.sinkEvents(scrollPanel, Event.ONSCROLL | Event.ONMOUSEDOWN | Event.ONMOUSEUP);
      DOM.setEventListener(scrollPanel, new EventListener() {
        
        @Override
        public void onBrowserEvent(Event event) {
          switch (DOM.eventGetType(event)) {
            case Event.ONSCROLL:
              if (leftMouseButtonPressed) {
                scrollBarScrolling = true;
              }              
              ChangeSource changeSource = ChangeSource.OTHER;
              if (silentScrollPosition != null &&
                  silentScrollPosition == scrollPanel.getScrollTop()) {
                changeSource = ChangeSource.PROGRAM;
                silentScrollPosition = null;
              } else if (scrollBarScrolling) {
                changeSource = ChangeSource.SCROLLBAR_MOUSE_MOVE;
              }
              processScreenChange(changeSource);
              break;
              
            case Event.ONMOUSEDOWN:
              if (event.getButton() == Event.BUTTON_LEFT) {
                leftMouseButtonPressed = true;
              }
              break;
              
            case Event.ONMOUSEUP:
              if (event.getButton() == Event.BUTTON_LEFT) {
                leftMouseButtonPressed = false;                
                if (scrollBarScrolling) {
                  processScreenChange(ChangeSource.SCROLLBAR_MOUSE_UP);
                  scrollBarScrolling = false;
                }
              }              
              break;              
          }
        }
      });
    }  

    waveHolder.addListener(waveHolderListener);
    windowResizeRegistration = Window.addResizeHandler(new ResizeHandler() {
    
      @Override
      public void onResize(ResizeEvent event) {
        processScreenChange(ChangeSource.PARENT_RESIZE);
      }
    });
    
    int delay = Flags.get().supplementUpdateDelayMs();
    getTimer().scheduleRepeating(supplementUpdateTask, delay, delay);    
  }

  //
  // ScreenController
  //

  @Override
  public void complete() {
    getTimer().cancel(supplementUpdateTask);
    updateSupplement();
  }

  @Override
  public void destroy() {
    waveHolder.removeListener(waveHolderListener);
    windowResizeRegistration.removeHandler();
  }
  
  @Override
  public ChangeSource getLastChangeSource() {
    return lastChangeSource;
  }
  
  @Override
  public int getScrollPosition() {
    return lastScrollPosition;
  }

  @Override
  public boolean isScrolling() {
    return leftMouseButtonPressed ||
        (lastChangeSource != ChangeSource.PARENT_RESIZE &&
        Duration.currentTimeMillis() - lastChangeTime < SCROLL_FINISH_DELTA_TIME_MS);
  }
  
  @Override
  public ScrollDirection getScrollDirection() {
    return isScrolling() ? lastScrollDirection : ScrollDirection.NONE;
  }

  @Override
  public ScrollDirection getLastScrollDirection() {
    return lastScrollDirection;
  }

  @Override
  public ScrollDirection getPreviousScrollDirection() {
    return previousScrollDirection;
  }
  
  @Override
  public double getLastAbsoluteScrollSpeed() {
    return lastAbsoluteScrollSpeed;
  }

  @Override
  public double getPreviousAbsoluteScrollSpeed() {
    return previousAbsoluteScrollSpeed;
  }
  
  @Override
  public ScrollSpeedChange getLastScrollSpeedChange() {
    return lastScrollSpeedChange;
  }

  @Override
  public boolean isLeftMouseButtonPressed() {
    return leftMouseButtonPressed;
  }

  @Override
  public void setScrollPosition(int scrollPosition, boolean silent) {
    silentScrollPosition = silent ? scrollPosition : null;
    scrollPanel.setScrollTop(scrollPosition);
  }
  
  //
  // SourcesEvents
  //
  
  @Override
  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  //
  // Private methods
  //
  
  private void processScreenChange(ChangeSource changeSource) {
    if (scrollPanel == null) {
      return;
    }
    
    lastChangeTime = Duration.currentTimeMillis();
    lastChangeSource = changeSource;
    lastScrollPosition = scrollPanel.getScrollTop();
    lastScrollDirection = calculateScrollDirection();        
    lastAbsoluteScrollSpeed = calculateAbsoluteScrollSpeed();    
    lastScrollSpeedChange = calculateScrollSpeedChange();    
    
    if (!ChangeSource.PROGRAM.equals(changeSource)) {      
      triggerOnScreenChanged();
      
      needsSupplementUpdate = true;
    }  
    
    previousChangeTime = lastChangeTime;
    previousScrollPosition = lastScrollPosition;
    previousAbsoluteScrollSpeed = lastAbsoluteScrollSpeed;
    previousScrollDirection = lastScrollDirection;
  }
  
  private ScrollDirection calculateScrollDirection() {
    int delta = lastScrollPosition - previousScrollPosition;
    if (delta > 0) {
      return ScrollDirection.DOWN;
    }
    if (delta < 0) {
      return ScrollDirection.UP;
    }
    return ScrollDirection.NONE;
  }
  
  private double calculateAbsoluteScrollSpeed() {
    return Math.abs(lastScrollPosition - previousScrollPosition) /
        (lastChangeTime - previousChangeTime) * 1000;    
  }
  
  private ScrollSpeedChange calculateScrollSpeedChange() {
    double delta = lastAbsoluteScrollSpeed - previousAbsoluteScrollSpeed;
    if (delta > 0) {
      return ScrollSpeedChange.ACCELERATED;
    }
    if (delta < 0) {
      return ScrollSpeedChange.SLOWED_DOWN;
    }
    return ScrollSpeedChange.NONE;
  }
  
  /** Saves current screen position to the supplement. */
  private void updateSupplement() {
    if (needsSupplementUpdate) {
      ScreenPosition screenPosition = scroller.getScreenPosition();
      
      LOG.trace().log("ScreenControllerImpl.updateSupplement: screenPosition=" + screenPosition);
      
      supplement.setScreenPosition(waveletId, screenPosition);
      needsSupplementUpdate = false;
    }
  }  
  
  private TimerService getTimer() {
    return SchedulerInstance.getLowPriorityTimer();
  }  
  
  //
  // Listeners
  //
  
  private void triggerOnScreenChanged() {
    for (Listener l : listeners) {
      l.onScreenChanged();
    }
  }
}  
