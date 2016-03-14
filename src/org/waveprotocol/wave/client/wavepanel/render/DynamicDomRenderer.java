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

import com.google.gwt.core.client.Duration;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptException;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Command;

import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.webclient.client.FragmentRequester;
import org.waveprotocol.box.webclient.flags.Flags;
import org.waveprotocol.wave.client.doodad.diff.DiffAnnotationHandler;
import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.editor.content.DiffHighlightingFilter;
import org.waveprotocol.wave.client.render.undercurrent.ScreenController;
import org.waveprotocol.wave.client.render.undercurrent.ScreenController.ScrollDirection;
import org.waveprotocol.wave.client.render.undercurrent.ScreenController.ScrollSpeedChange;
import org.waveprotocol.wave.client.render.undercurrent.ScreenControllerImpl;
import org.waveprotocol.wave.client.scheduler.Scheduler.Task;
import org.waveprotocol.wave.client.scheduler.SchedulerInstance;
import org.waveprotocol.wave.client.scheduler.TimerService;
import org.waveprotocol.wave.client.wave.DiffContentDocument;
import org.waveprotocol.wave.client.wave.InteractiveDocument;
import org.waveprotocol.wave.client.wavepanel.impl.edit.EditSession;
import org.waveprotocol.wave.client.wavepanel.render.RenderUtil.ElementChange;
import org.waveprotocol.wave.client.wavepanel.render.RenderUtil.PlaceholderType;
import org.waveprotocol.wave.client.wavepanel.render.RenderUtil.RenderDirection;
import org.waveprotocol.wave.client.wavepanel.render.i18n.RenderMessages;
import org.waveprotocol.wave.client.wavepanel.view.BlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.View;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomUtil;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.widget.dialog.DialogBox;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationBlip;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationThread;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationView;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationBlip;
import org.waveprotocol.wave.model.operation.SilentOperationSink;
import org.waveprotocol.wave.model.util.Interval;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DynamicRenderer implementation for rendering wave into DOM elements.
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class DynamicDomRenderer extends DynamicRendererImpl<Element> {

  protected class ConversationDomListener extends ConversationListener {

    @Override
    public void onReplyAdded(ObservableQuasiConversationThread thread) {
      super.onReplyAdded(thread);

      if (thread.isInline()) {
        clearResizeControllerIndependentChildren(thread.getParentBlip());
      }
      if (thisWaveHasBeenRendered) {
        if (!thread.isInline()) {
          addNeighborsToRerender(navigator.getFirstBlip(thread), true, false);
          addNeighborsToRerender(navigator.getLastBlip(thread), false, true);
          rerenderBlips();
        }
        dynamicRendering();
      }
    }

    @Override
    public void onBeforeReplyRemoved(ObservableQuasiConversationThread thread) {
      super.onBeforeReplyRemoved(thread);

      if (thisWaveHasBeenRendered && !thread.isInline()) {
        addNeighborsToRerender(navigator.getFirstBlip(thread), true, false);
        addNeighborsToRerender(navigator.getLastBlip(thread), false, true);
      }
    }    
    
    @Override
    public void onReplyRemoved(ObservableQuasiConversationThread thread) {
      super.onReplyRemoved(thread);

      if (thread.isInline()) {
        clearResizeControllerIndependentChildren(thread.getParentBlip());
      }

      if (thisWaveHasBeenRendered) {
        rerenderBlips();
        dynamicRendering();
      }
    }

    @Override
    public void onReplyQuasiRemoved(ObservableQuasiConversationThread thread) {
      super.onReplyQuasiRemoved(thread);

      for (ConversationBlip blip : thread.getBlips()) {
        deletifyBlipAndChildren(blip);
      }
    }

    @Override
    public void onBlipAdded(ObservableQuasiConversationBlip blip) {
      super.onBlipAdded(blip);
      
      if (thisWaveHasBeenRendered) {
        addNeighborsToRerender(blip, true, true);
        rerenderBlips();
        dynamicRendering();
      }
    }

    @Override
    public void onBeforeBlipRemoved(ObservableQuasiConversationBlip blip) {
      super.onBeforeBlipRemoved(blip);

      resizeController.uninstall(getMetaElementByBlip(blip));
      
      if (thisWaveHasBeenRendered) {
        addNeighborsToRerender(blip, true, true);
      }
    }

    @Override
    public void onBlipRemoved(ObservableQuasiConversationBlip blip) {
      super.onBlipRemoved(blip);
      
      clearResizeControllerIndependentChildren(blip);

      if (thisWaveHasBeenRendered) {
        rerenderBlips();
        dynamicRendering();
      }
    }

    @Override
    public void onBlipQuasiRemoved(ObservableQuasiConversationBlip blip) {
      super.onBlipQuasiRemoved(blip);
      
      deletifyBlipAndChildren(blip);

      if (thisWaveHasBeenRendered) {
        rerenderBlips();
        dynamicRendering();
      }
    }    
    
    @Override
    public void onBlipContributorAdded(ObservableQuasiConversationBlip blip,
        ParticipantId contributor) {
      profileRenderer.monitorContribution(blip, contributor);
      renderContributors(blip);
    }

    @Override
    public void onBlipContributorRemoved(ObservableQuasiConversationBlip blip,
        ParticipantId contributor) {
      profileRenderer.unmonitorContribution(blip, contributor);
      renderContributors(blip);
    }

    @Override
    public void onBlipTimestampChanged(ObservableQuasiConversationBlip blip, long oldTimestamp,
        long newTimestamp) {
      BlipView blipUi = modelAsViewProvider.getBlipView(blip);
      BlipMetaView metaUi = blipUi != null ? blipUi.getMeta() : null;
      if (metaUi != null) {
        shallowBlipRenderer.renderTime(blip, metaUi);
      }
    }

    @Override
    public void onBlipSubmitted(ObservableQuasiConversationBlip blip) {}

    //
    // Private methods
    //

    private void renderContributors(ObservableConversationBlip blip) {
      BlipView blipUi = modelAsViewProvider.getBlipView(blip);
      BlipMetaView metaUi = blipUi != null ? blipUi.getMeta() : null;
      if (metaUi != null) {
        shallowBlipRenderer.renderContributors(blip, metaUi);
      }
    }

    private void clearResizeControllerIndependentChildren(ConversationBlip blip) {
      if (isResizeControllerSupported && isBlipReady(blip)) {
        Element metaElement = getMetaElementByBlip(blip);
        if (metaElement != null) {
          resizeController.clearIndependentChildren(metaElement);
        }
      }
    }    
  }

  /**
   * Creation of DOM dynamic renderer.
   * 
   * @param modelAsViewProvider model as view provider
   * @param shallowBlipRenderer shallow blip renderer
   * @param fragmentRequester fragment requester
   * @param blipRenderer blip renderer
   * @param navigator conversation navigator
   * @param profileRenderer profile renderer
   * @param supplementRenderer supplement renderer
   * @param elementRenderer element renderer
   * @param measurer element measurer
   * @return new dynamic renderer for a conversation view
   */
  public static DynamicDomRenderer create(
      ModelAsViewProvider modelAsViewProvider,
      ShallowBlipRenderer shallowBlipRenderer,
      FragmentRequester fragmentRequester,
      BlipDocumentRenderer blipRenderer,
      ConversationNavigator navigator,
      LiveProfileRenderer profileRenderer,
      LiveSupplementRenderer supplementRenderer,
      ElementRenderer elementRenderer,
      ElementMeasurer measurer) {
    return new DynamicDomRenderer(modelAsViewProvider, shallowBlipRenderer, profileRenderer,
        supplementRenderer, fragmentRequester, elementRenderer, blipRenderer, navigator, measurer);
  }

  private static final RenderMessages messages = GWT.create(RenderMessages.class);  
  
  /** Delay of fixing all rendering blips sizes (in ms). */
  private final static int FIX_ALL_RENDERED_BLIP_SIZES_MS = 1000;

  /**
   * Delay before hiding flap panel.
   * Must be equal to the transition value of the .flap class in Render.css
   */
  private final static int HIDE_FLAP_PANEL_DELAY_MS = 200;

  /** Symbolic time value meaning impossible moment of time. */
  private final static long NEVER = -1;

  private final ModelAsViewProvider modelAsViewProvider;
  private final ShallowBlipRenderer shallowBlipRenderer;
  private final LiveSupplementRenderer supplementRenderer;
  private final LiveProfileRenderer profileRenderer;

  /** Set of blips whose appearance should be updated. */
  private final Set<ConversationBlip> blipsToRerender = new HashSet<>();

  private ScreenController screen;
  private EditSession editSession;
  private boolean editSessionStarted;
  private Element scrollPanel;
  private Element rootThreadPanel;
  private Element replyBox;
  private long planRenderTime = NEVER;

  /** The flap panel shown above the whole page during opening mode. */
  private Element flapPanel;
  private boolean flapPanelVisible = false;

  private boolean isResizeControllerSupported = true;
  private final DomResizeController resizeController = new DomResizeControllerImpl() {

    @Override
    protected Iterable<Element> requestIndependentChildren(Element metaElement) {
      Element blipElement = metaElement.getParentElement();
      ConversationBlip blip = blipElement != null ? getBlipByElement(blipElement) : null;
      if (blip == null) {
        return Collections.EMPTY_LIST;
      }
      List<Element> children = new ArrayList<>();
      for (ConversationThread reply : blip.getReplyThreads()) {
        if (reply.isInline()) {
          children.add(getChromeElementByThread(reply));
        }
      }
      return children;
    }
  };

  private final Task hideFlapPanelTask = new Task() {

    @Override
    public void execute() {
      DomUtil.setDefaultCursor(flapPanel);
      flapPanel.removeFromParent();
      flapPanel = null;
    }
  };

  private final Task dynamicRenderingTask = new Task() {

    @Override
    public void execute() {
      dynamicRendering();
      planRenderTime = NEVER;
    }
  };

  private final Task executePhaseTask = new Task() {

    @Override
    public void execute() {
      executePhase();
    }
  };

  private boolean areAllRenderedBlipSizeFixed = true;

  private final Task fixAllRenderedBlipSizeTask = new Task() {

    @Override
    public void execute() {
      setAllRenderedBlipsHeightsFixed(true);
    }
  };

  private DynamicDomRenderer(
      ModelAsViewProvider modelAsViewProvider,
      ShallowBlipRenderer shallowBlipRenderer,
      LiveProfileRenderer profileRenderer,
      LiveSupplementRenderer supplementRenderer,
      FragmentRequester blipRequester,
      ElementRenderer elementRenderer,
      BlipDocumentRenderer blipDocumentRenderer,
      ConversationNavigator navigator,
      ElementMeasurer measurer) {
    super(blipRequester, elementRenderer, blipDocumentRenderer, navigator, measurer);
    
    this.modelAsViewProvider = modelAsViewProvider;
    this.shallowBlipRenderer = shallowBlipRenderer;
    this.profileRenderer = profileRenderer;
    this.supplementRenderer = supplementRenderer;
    
    scroller = new DomScrollerImpl();
  }

  public void upgrade(ScreenController screenController, EditSession editSession) {
    this.screen = screenController;
    this.editSession = editSession;
    
    ((DomScrollerImpl) scroller).upgrade(screen);
  }  
  
  //
  // Listeners
  //

  /** @return listener to the screen controller. */
  public ScreenController.Listener getScreenListener() {
    return new ScreenController.Listener() {

      @Override
      public void onScreenChanged() {
        // If parent window size is changed => unfix all blips' heights, and schedule their fixing.
        if (screen.getLastChangeSource() == ScreenController.ChangeSource.PARENT_RESIZE) {
          setAllRenderedBlipsHeightsFixed(false);
          getTimer().scheduleDelayed(fixAllRenderedBlipSizeTask, FIX_ALL_RENDERED_BLIP_SIZES_MS);
        }

        boolean shouldRender = !screen.isLeftMouseButtonPressed() && !placeholders.isEmpty();
        if (shouldRender) {
          calculateRenderParameters();
          double currentTime = Duration.currentTimeMillis();    

          // Renders immediately when acceleration is negative (scrolling is slowed)
          // and the scrolling speed is small for two times in a row.
          boolean shouldRenderImmediately = !screen.isScrolling() ||
              (screen.getLastScrollSpeedChange() == ScrollSpeedChange.SLOWED_DOWN
              && screen.getPreviousAbsoluteScrollSpeed() <= Flags.get().immediateRenderScrollSpeedPxS()
              && screen.getLastAbsoluteScrollSpeed() <= Flags.get().immediateRenderScrollSpeedPxS());

          if (!shouldRenderImmediately && planRenderTime != NEVER && currentTime > planRenderTime) {
            shouldRenderImmediately = true;
          }

          if (shouldRenderImmediately) {
            if (!inProcess) {
              dynamicRendering();
              planRenderTime = NEVER;
              getTimer().cancel(dynamicRenderingTask);
            }
          } else {
            // Addition to timer to make rendering at equal time intervals during scroll down.
            if (screen.getLastScrollDirection() == ScrollDirection.DOWN) {
              if (planRenderTime == NEVER) {
                planRenderTime = (long) (currentTime) + Flags.get().afterScrollRenderDelayMs();
              }
            }
            getTimer().scheduleDelayed(dynamicRenderingTask, Flags.get().afterScrollRenderDelayMs());
          }
        }
      }
    };
  }

  /**
   * @return listener to the edit session.
   */
  public EditSession.Listener getEditSessionListener() {
    return new EditSession.Listener() {

      @Override
      public void onSessionStart(Editor e, ConversationBlip blip) {
        editSessionStarted = true;
        setParentBlipHeightsFixed(blip, false, true);
        renderFixedBlip = blip;
      }

      @Override
      public void onSessionEnd(Editor e, ConversationBlip blip) {
        setParentBlipHeightsFixed(blip, true, true);
        editSessionStarted = false;
        renderFixedBlip = null;
      }  
    };
  }

  //
  // DynamicRenderer
  //

  @Override
  public void init(ObservableQuasiConversationView conversationView) {
    super.init(conversationView);

    profileRenderer.init();
    if (conversation != null) {
      // Note: blip contributions are only monitored once a blip is paged in.
      for (ParticipantId participant : conversation.getParticipantIds()) {
        profileRenderer.monitorParticipation(conversation, participant);
      }
    }
    supplementRenderer.init();

    resizeController.addListener(new DomResizeController.Listener() {

      @Override
      public void onElementResized(Element metaElement, int oldHeight, int newHeight) {
        Element blipElement = metaElement.getParentElement();
        if (blipElement == null) {
          return;
        }
        ConversationBlip blip = getBlipByElement(blipElement);
        if (blip == null) {
          return;
        }

        setParentBlipHeightsFixed(blip, false, true);
        // Compensates scroll position for the non-edited upper blip.
        // If the wave is scrolled to the end, no negative compensation is needed.
        int deltaHeight = newHeight - oldHeight;
        if (!isBlipEdited(blip) && isElementUpper(blipElement, blip) &&
            !(scroller.isAtEnd() && deltaHeight < 0)) {
          scroller.movePosition(deltaHeight, true);
        }
        setParentBlipHeightsFixed(blip, true, true);
      }
    });
  }

  @Override
  public void startRendering(String startBlipId) {
    setFlapPanelVisible(true);    
    
    super.startRendering(startBlipId);
  }

  @Override
  public void calculateRenderParameters() {
    super.calculateRenderParameters();

    renderDirection = calculateRenderDirection();
  }

  private RenderDirection calculateRenderDirection() {
    // Checks scrolling position.
    if (scroller.isAtBegin()) {
      return RenderDirection.DOWN;
    }
    if (scroller.isAtEnd()) {
      return RenderDirection.UP;
    }

    // Checks scrolling direction.
    if (screen != null) {
      switch (screen.getScrollDirection()) {
        case UP:
          return RenderDirection.UP;
        case DOWN:
          return RenderDirection.DOWN;
      }
    }

    // Checks if are there any placeholders in the top/bottom of the visible screen.
    Interval visibleBlipsSize = getVisibleBlipsSize();
    int top = visibleBlipsSize.getBegin();
    int bottom = visibleBlipsSize.getEnd();
    boolean topPlaceholdersFound = !findPlaceholders(Interval.create(top, top+1),
        PlaceholderType.ANY).isEmpty();
    boolean bottomPlaceholdersFound = !findPlaceholders(Interval.create(bottom-1, bottom),
        PlaceholderType.ANY).isEmpty();
    if (topPlaceholdersFound && !bottomPlaceholdersFound) {
      return RenderDirection.UP;
    }
    if (!topPlaceholdersFound && bottomPlaceholdersFound) {
      return RenderDirection.DOWN;
    }

    // Default value.
    return RenderDirection.DOWN;
  }

  @Override
  protected ConversationListener createConversationListener() {
    return new ConversationDomListener();
  }
  
  @Override
  public void complete() {
    cancelTasks();

    super.complete();
  }

  @Override
  public void destroy() {
    complete();
    removeListeners();
    resizeController.uninstall();

    super.destroy();
  }

  @Override
  protected void initBlip(ConversationBlip blip, Element blipElement, boolean upper) {
    if (((QuasiConversationBlip) blip).isQuasiDeleted()) {
      makeBlipVoid(blip);
    }

    super.initBlip(blip, blipElement, upper);

    // Render blip's meta according to the model.
    BlipView blipView = modelAsViewProvider.getBlipView(blip);
    BlipMetaView metaView = modelAsViewProvider.getBlipMetaView(blip);
    if (blipView != null && metaView != null) {
      shallowBlipRenderer.render(blip, blipView, metaView);
    }

    // Listen to the contributors on the blip.
    for (ParticipantId contributor : blip.getContributorIds()) {
      profileRenderer.monitorContribution(blip, contributor);
    }
  }

  @Override
  protected Interval getVisibleScreenSize() {
    Element sp = getScrollPanel();
    return Interval.create(sp.getAbsoluteTop(), sp.getAbsoluteBottom());
  }

  @Override
  protected double getPrerenderedUpperScreenNumber() {
    return Flags.get().prerenderedUpperScreenNumber();
  }

  @Override
  protected double getPrerenderedLowerScreenNumber() {
    return Flags.get().prerenderedLowerScreenNumber();
  }

  @Override
  protected Element getRootPlaceholder() {
    return DomUtil.findFirstChildElement(getRootThreadPanel(), View.Type.BLIPS, View.Type.PLACEHOLDER);
  }

  @Override
  protected void scheduleDynamicRendering() {
    getTimer().schedule(dynamicRenderingTask);
  }

  @Override
  protected void ensureBlipContentRendered() {
    for (ElementChange<Element> change : blipChanges) {
      Element meta = DomUtil.findFirstChildElement(change.getElement(), Type.META);
      // getHeight() call on unrendered meta div calls its immediate rendering by browser.
      DomUtil.getElementHeight(meta);
    }
  }

  @Override
  protected void ensureBlipRendered() {
    for (ElementChange<Element> change : blipChanges) {
      // getHeight() call on unrendered blip div calls its immediate rendering by browser.
      DomUtil.getElementHeight(change.getElement());
    }
  }

  @Override
  protected void startNewPhase() {
    getTimer().schedule(executePhaseTask);
  }

  @Override
  protected void postDynamicRendering() {
    super.postDynamicRendering();
    
    // Hide flap panel.
    setFlapPanelVisible(false);
  }

  @Override
  protected void completeBlip(ConversationBlip blip) {
    if (isResizeControllerSupported) {
      try {
        resizeController.install(getMetaElementByBlip(blip));
      } catch (JavaScriptException ex) {
        DialogBox.information(messages.javaScriptError(ex.getMessage()) + "\n" +
            messages.upgradeBrowser());
        isResizeControllerSupported = false;
      }
    }    
  }
  
  @Override
  protected void terminateBlip(ConversationBlip blip) {
    Preconditions.checkArgument(isRendered(blip), "not rendered blip");
    
    for (ParticipantId contributor : blip.getContributorIds()) {
      profileRenderer.unmonitorContribution(blip, contributor);
    }    

    // Deals with focus and editing.
    BlipView blipView = modelAsViewProvider.getBlipView(blip);
    if (blipView != null) {
      // TODO(user): Hide parent thread if it becomes empty.
      if (blipView.isFocused()) {
        if (!blipView.isBeingEdited() && blip.hasContent()) {
          InteractiveDocument doc = blip.getContent();
          doc.clearDiffs(true);
        }
      }
      blipView.remove();
    }
  }

  @Override
  protected boolean isEditSessionStarted() {
    return editSessionStarted;
  }  
  
  //
  // Private methods
  //

  /** Marks the blip and all its child blips as quasi-deleted. */
  private void deletifyBlipAndChildren(ConversationBlip startBlip) {
    final String deleteTitle = DiffAnnotationHandler.formatOperationContext(
        DiffHighlightingFilter.DIFF_DELETE_KEY,
        ((QuasiConversationBlip) startBlip).getQuasiDeletionContext());
    navigator.breadthFirstSearchFromBlip(startBlip, new Receiver<ConversationBlip>() {

      @Override
      public boolean put(ConversationBlip blip) {
        if (isRendered(blip)) {
          elementRenderer.setElementCurrentHeightFixed(getElementByBlip(blip), false);

          makeBlipVoid(blip);
          if (isBlipEdited(blip)) {
            editSession.stopEditing();
          }

          InteractiveDocument document = blip.getContent();
          document.clearDiffs(false);

          // Render the blip as quasi-deleted.
          BlipView blipView = modelAsViewProvider.getBlipView(blip);
          if (blipView != null) {
            ConversationBlip rowOwnerBlip = navigator.getBlipRowOwnerBlip(blip);
            boolean isRowOwnerDeleted = rowOwnerBlip != null &&
                ((QuasiConversationBlip) rowOwnerBlip).isQuasiDeleted();
            blipView.setQuasiDeleted(deleteTitle, isRowOwnerDeleted);
          }
        }
        return true;
      }
    }, false);
  }

  /** Prevents blip from emitting operations outside. */
  private void makeBlipVoid(ConversationBlip blip) {
    if (blip.hasContent()) {
      DiffContentDocument document = blip.getContent();
      document.getDocument().replaceOutgoingSink(SilentOperationSink.Void.get());
    }
  }

  private Element getScrollPanel() {
    if (scrollPanel == null) {
      scrollPanel = ScreenControllerImpl.getScrollPanel();
    }
    return scrollPanel;
  }

  private Element getRootThreadPanel() {
    if (rootThreadPanel == null) {
      Element sp = getScrollPanel();
      rootThreadPanel = sp != null ? DomUtil.findFirstChildElement(sp, Type.ROOT_THREAD) : null;
    }
    return rootThreadPanel;
  }

  private Element getReplyBox() {
    if (replyBox == null) {
      Element rtp = getRootThreadPanel();
      replyBox = rtp != null ? DomUtil.findFirstChildElement(rtp, Type.REPLY_BOX) : null;
    }
    return replyBox;
  }

  /**
   * @return size of the visible blips.
   * Takes into account visible screen size and coordinates of reply box lying under all blips.
   */
  private Interval getVisibleBlipsSize() {
    int top = visibleScreenSize.getBegin();
    int bottom = visibleScreenSize.getEnd();
    if (getReplyBox() != null) {
      int replyBoxTop = getReplyBox().getAbsoluteTop();
      if (replyBoxTop < bottom) {
        bottom = replyBoxTop;
      }
    }
    return Interval.create(top, bottom);
  }
    
  private TimerService getTimer() {
    return SchedulerInstance.getLowPriorityTimer();
  }

  private void cancelTasks() {
    getTimer().cancel(hideFlapPanelTask);
    getTimer().cancel(dynamicRenderingTask);
    getTimer().cancel(fixAllRenderedBlipSizeTask);
    getTimer().cancel(executePhaseTask);
  }

  private void removeListeners() {
    supplementRenderer.destroy();
    profileRenderer.destroy();
  }

  private void setFlapPanelVisible(boolean visible) {
    if (flapPanelVisible != visible) {
      if (flapPanel == null) {
        flapPanel = Document.get().createDivElement();
      }
      flapPanel.setClassName(getCss().flap() + " " +
          (visible ? getCss().opaque() : getCss().transparent()) );
      if (visible) {
        DomUtil.putCover(flapPanel, getScrollPanel());
        DomUtil.setProgressCursor(flapPanel);
      } else {
        getTimer().scheduleDelayed(hideFlapPanelTask, HIDE_FLAP_PANEL_DELAY_MS);
      }
      flapPanelVisible = visible;
    }
  }

  private void setAllRenderedBlipsHeightsFixed(boolean fixed) {
    if (areAllRenderedBlipSizeFixed != fixed) {
      for (Element blip : idToRenderedBlips.inverse().keySet()) {
        elementRenderer.setElementCurrentHeightFixed(blip, fixed);
      }
      areAllRenderedBlipSizeFixed = fixed;
    }
  }

  private Element getChromeElementByThread(ConversationThread thread) {
    Element inlineThread = ElementDomRenderer.elementOf(modelAsViewProvider.getInlineThreadView(thread));
    return inlineThread != null ? inlineThread.getFirstChildElement() : null;
  }

  private Element getMetaElementByBlip(ConversationBlip blip) {
    Element blipElement = getElementByBlip(blip);
    return blipElement != null ? DomUtil.findFirstChildElement(blipElement, Type.META) : null;
  }

  /**
   * Adds the given blip's neighbors into the set to be re-rendered.
   *
   * @param blip the given blip
   * @param takeUpper true, if upper neighbors should be taken
   * @param takeLower true, if lower neighbors should be taken
   */
  private void addNeighborsToRerender(ConversationBlip blip, boolean takeUpper, boolean takeLower) {
    if (blip == null) {
      return;
    }
    if (takeUpper) {
      addBlipToRerender(navigator.getPreviousBlipInParentThread(blip));
    }
    if (takeLower) {
      addBlipToRerender(navigator.getNextBlipInParentThread(blip));
    }
    ConversationBlip parent = navigator.getBlipParentBlip(blip);
    if (parent != null) {
      if (takeUpper) {
        addBlipToRerender(parent);
      }
      if (takeLower) {
        addBlipToRerender(navigator.getNextBlipInParentThread(parent));
      }
    }
  }

  private void addBlipToRerender(ConversationBlip blip) {
    if (blip != null && isRendered(blip)) {
      blipsToRerender.add(blip);
    }
  }

  /** Re-renders all blips that could be changed. */
  private void rerenderBlips() {
    for (ConversationBlip blip : blipsToRerender) {
      BlipView blipView = modelAsViewProvider.getBlipView(blip);
      BlipMetaView metaView = modelAsViewProvider.getBlipMetaView(blip);
      if (blipView != null && metaView != null) {
        setParentBlipHeightsFixed(blip, false, true);
        shallowBlipRenderer.renderStyle(blip, blipView);
        shallowBlipRenderer.renderFrame(blip, metaView);
        setParentBlipHeightsFixed(blip, true, true);
      }
    }
    blipsToRerender.clear();
  }

  private boolean isBlipEdited(ConversationBlip blip) {
    return editSession != null && editSession.getBlip() == blip;
  }
}
