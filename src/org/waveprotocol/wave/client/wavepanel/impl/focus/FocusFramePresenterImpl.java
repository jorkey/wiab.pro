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

import com.google.common.annotations.VisibleForTesting;

import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.client.scheduler.Scheduler.Task;
import org.waveprotocol.wave.client.scheduler.SchedulerInstance;
import org.waveprotocol.wave.client.scheduler.TimerService;
import org.waveprotocol.wave.client.scroll.SmartScroller;
import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.render.DynamicRenderer;
import org.waveprotocol.wave.client.wavepanel.render.ObservableDynamicRenderer;
import org.waveprotocol.wave.client.wavepanel.view.BlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.FocusFrameView;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.FocusFrame;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;
import org.waveprotocol.wave.model.conversation.focus.ObservableFocusFramePresenter;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversation;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationBlip;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationThread;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationView;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.supplement.SupplementedWave;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;
import org.waveprotocol.wave.model.util.Preconditions;

/**
 * Presents the focus frame, and exposes an API for controlling it.
 */
public final class FocusFramePresenterImpl implements ObservableFocusFramePresenter {

  protected static LoggerBundle LOG = new DomLogger("focus");  
    
  /**
   * Installs the focusFramePresenter frame feature.
   *
   * @param wavePanel wave panel to hold the feature
   * @param smartScroller smart scroller
   * @return the feature.
   */
  public static FocusFramePresenterImpl install(WavePanel wavePanel,
      SmartScroller<? super BlipView> smartScroller) {
    FocusFrameView focusFrameView = FocusFrame.create();
    return new FocusFramePresenterImpl(wavePanel, focusFrameView, smartScroller);
  }

  /** Focus frame UI. */
  private final FocusFrameView view;
  
  /** Thing that provides models by views. */
  private ModelAsViewProvider modelAsViewProvider;  
  
  /** Conversation. */
  private ObservableQuasiConversation conversation;
  
  /** Navigator. */
  private ConversationNavigator navigator;
  
  /** Focus move validator. */
  private FocusMoveValidator focusMoveValidator;
  
  /** Supplement. */
  private SupplementedWave supplement;
  
  /** Wavelet id. */
  private WaveletId waveletId;

  /** Start blip id. */
  private String startBlipId;
  
  /** Dynamic renderer. */
  private DynamicRenderer dynamicRenderer;
  
  /** Listeners. */
  private final CopyOnWriteSet<Listener> listeners = CopyOnWriteSet.create();

  /** Order generator, optionally installed. */
  private FocusOrder order;

  /** Blip that currently has the focusFramePresenter frame. May be {@code null}. */
  private ConversationBlip focusedBlip;
  
  /** Delay of supplement update. */
  private final static int SUPPLEMENT_UPDATE_DELAY_MS = 1000;
  
  private final Task supplementUpdateTask = new Task() {

    @Override
    public void execute() {
      updateSupplement();
    }
  };
  
  ObservableQuasiConversation.ReplyListener replyListener =
      new ObservableQuasiConversation.ReplyListener.Impl() {

    @Override
    public void onBeforeReplyRemoved(ObservableQuasiConversationThread thread) {
      // Handle deletion of the focused blip.
      if (navigator.isThreadPredecessorOfBlip(thread, focusedBlip)) {
        clearFocus();
      }
    }
  };  

  ObservableQuasiConversation.BlipListener blipListener =
      new ObservableQuasiConversation.BlipListener.Impl() {
    
    @Override
    public void onBeforeBlipRemoved(ObservableQuasiConversationBlip blip) {
      // Handle deletion of the focused blip.
      if (blip == focusedBlip || navigator.isBlipPredecessorOfBlip(blip, focusedBlip)) {
        clearFocus();
      }
    }
  };
  
  /**
   * Creates a focusFramePresenter-frame presenter.
   */
  @VisibleForTesting
  FocusFramePresenterImpl(WavePanel wavePanel, FocusFrameView view,
      SmartScroller<? super BlipView> scroller) {
    this.view = view;
    
    wavePanel.addListener(new WavePanel.LifecycleListener.Impl() {

      @Override
      public void onReset() {
        focusedBlip = null;
      }
    });
  }

  public void upgrade(ModelAsViewProvider modelAsViewProvider,
      final ObservableQuasiConversationView conversationView, ConversationNavigator navigator,
      SupplementedWave supplement, WaveletId waveletId, String startBlipId,
      DynamicRenderer dynamicRenderer) {
    this.modelAsViewProvider = modelAsViewProvider;
    this.conversation = conversationView.getRoot();
    this.navigator = navigator;    
    this.supplement = supplement;
    this.waveletId = waveletId;
    this.startBlipId = startBlipId;
    this.dynamicRenderer = dynamicRenderer;
    
    conversationView.addListener(new ObservableQuasiConversationView.Listener() {

      @Override
      public void onConversationAdded(ObservableQuasiConversation conversation) {
        if (conversation.isRoot()) {
          focusedBlip = getSavedFocusedBlip();
          observeConversation(conversation);
        }        
      }

      @Override
      public void onConversationRemoved(ObservableQuasiConversation conversation) {
        if (conversation.isRoot()) {
          focusedBlip = null;
          unobserveConversation();
        }        
      }
    });
    
    if (conversationView.getRoot() != null) {
      observeConversation(conversationView.getRoot());
    }  
    
    focusedBlip = getSavedFocusedBlip();
  }

  public void upgrade(FocusMoveValidator focusMoveValidator) {
    this.focusMoveValidator = focusMoveValidator;
  }
  
  public void complete() {
    getTimer().cancel(supplementUpdateTask);
    updateSupplement();
  }
  
  //
  // Listeners
  //
  
  public ObservableDynamicRenderer.Listener getRendererListener() {
    
    return new ObservableDynamicRenderer.ListenerImpl() {

      @Override
      public void onBlipReady(ConversationBlip blip) {
        if (blip == focusedBlip) {
          focusInner(blip);
        }
      }  
    };
  }
  
  //
  // FocusFramePresenter
  //

  @Override
  public ConversationBlip getFocusedBlip() {
    return focusedBlip;
  }
  
  @Override
  public void focus(ConversationBlip blip) {
    if (blip != focusedBlip) {
      focusInner(blip);
    }  
  }

  @Override
  public void clearFocus() {
    setChrome(focusedBlip, false);
    focusedBlip = null;
  }

  @Override
  public ConversationBlip getNeighborBlip(boolean next) {
    if (focusedBlip != null) {
      return next ? navigator.getNextBlip(focusedBlip) : navigator.getPreviousBlip(focusedBlip);
    }
    return null;
  }
  
  @Override
  public ConversationBlip getNextUnreadBlip() {      
    if (order != null) {
      ConversationBlip startBlip = focusedBlip != null ? focusedBlip : conversation.getRootThread().getFirstBlip();        
      ConversationBlip nextBlip = order.getNextUnread(startBlip);
      if (nextBlip != null) {
        return nextBlip;
      } else {
        return getPreviousUnreadBlip();
      }
    }
    return null;
  }

  @Override
  public void setOrder(FocusOrder order) {
    this.order = order;
  }

  @Override
  public void setEditing(boolean editing) {
    view.setEditing(editing);
  }

  //
  // Listeners
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

  /**
   * @return previous unread blip as defined by an attached
   * {@link #setOrder(FocusOrder) ordering}, if there is one.
   */
  private ConversationBlip getPreviousUnreadBlip() {
    if (order != null) {
      ConversationBlip startBlip = focusedBlip != null ? focusedBlip : conversation.getRootThread().getFirstBlip();
      ConversationBlip previousBlip = order.getPreviousUnread(startBlip);
      if (previousBlip != null) {
        return previousBlip;
      }
    }
    return null;
  }
  
  private void focusInner(ConversationBlip blip) {
    if (focusedBlip != null) {
      
      // Is focus move valid?
      if (focusMoveValidator != null) {
        if (!focusMoveValidator.canMoveFocus(focusedBlip, blip)) {
          return;
        }
      }
      
      if (blip != focusedBlip && dynamicRenderer.isBlipReady(focusedBlip)) {
        setChrome(focusedBlip, false);

        ConversationBlip oldFocusedBlip = focusedBlip;
        focusedBlip = null;
        fireOnFocusOut(oldFocusedBlip);
      }
    }

    focusedBlip = blip;    
    
    if (blip != null && dynamicRenderer.isBlipReady(blip)) {
      setChrome(blip, true);

      fireOnFocusIn(blip);
    }
        
    getTimer().scheduleDelayed(supplementUpdateTask, SUPPLEMENT_UPDATE_DELAY_MS);
  }  
  
  private void setChrome(ConversationBlip blip, boolean on) {
    if (blip != null) {
      BlipMetaView meta = modelAsViewProvider.getBlipMetaView(blip);
      if (meta != null) {
        meta.setFocusChrome(view, on);
      }
    }
  }
  
  private ConversationBlip getSavedFocusedBlip() {
    if (conversation == null) {
      return null;
    }
    
    String focusedBlipId = null;
    if (startBlipId != null) {
      focusedBlipId = startBlipId;
    } else if (supplement != null) {
      focusedBlipId = supplement.getFocusedBlipId(waveletId);
    }
    return focusedBlipId != null ? conversation.getBlip(focusedBlipId) : null;
  }
  
  private void updateSupplement() {
    if (supplement != null && waveletId != null) {
      supplement.setFocusedBlipId(waveletId, focusedBlip != null ? focusedBlip.getId() : null);
    }  
  }
  
  private TimerService getTimer() {
    return SchedulerInstance.getLowPriorityTimer();
  }
  
  private void observeConversation(ObservableQuasiConversation conversation) {
    Preconditions.checkNotNull(conversation, "conversation cannot be null");
    Preconditions.checkState(conversation.isRoot(), "conversation must be root");
    
    conversation.addBlipListener(blipListener);
    conversation.addReplyListener(replyListener);
    this.conversation = conversation;
  }

  private void unobserveConversation() {
    Preconditions.checkNotNull(conversation, "conversation cannot be null");
    Preconditions.checkState(conversation.isRoot(), "conversation must be root");    
    
    conversation.removeBlipListener(blipListener);
    conversation.removeReplyListener(replyListener);
    conversation = null;
  }  
  
  //
  // Listener events
  //

  private void fireOnFocusOut(ConversationBlip blip) {
    Preconditions.checkNotNull(blip, "blip must not be null");
    Preconditions.checkState(dynamicRenderer.isBlipReady(blip), "blip must be ready");
    
    for (Listener listener : listeners) {
      listener.onFocusOut(blip);
    }
  }
  
  private void fireOnFocusIn(ConversationBlip blip) {
    Preconditions.checkNotNull(blip, "blip must not be null");
    Preconditions.checkState(dynamicRenderer.isBlipReady(blip), "blip must be ready");    
    
    for (Listener listener : listeners) {
      listener.onFocusIn(blip);
    }
  }
}
