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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Multimap;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;

import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.webclient.client.FragmentRequester;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.client.wavepanel.render.RenderUtil.AdjacentPlaceholders;
import org.waveprotocol.wave.client.wavepanel.render.RenderUtil.ElementChange;
import org.waveprotocol.wave.client.wavepanel.render.RenderUtil.PlaceholderInfo;
import org.waveprotocol.wave.client.wavepanel.render.RenderUtil.PlaceholderType;
import org.waveprotocol.wave.client.wavepanel.render.RenderUtil.RenderDirection;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.WavePanelResourceLoader;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.conversation.BlipCluster;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversation;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationBlip;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationThread;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationView;
import org.waveprotocol.wave.model.id.DualIdSerialiser;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.InvalidIdException;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;
import org.waveprotocol.wave.model.util.Interval;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

/**
 * Dynamic renderer implementation.
 *
 * @param <T> type of rendered elements
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public abstract class DynamicRendererImpl<T> implements ObservableDynamicRenderer<T> {

  protected static LoggerBundle LOG = new DomLogger("render");

  /** Resources used by this widget. */
  public interface Resources extends ClientBundle {
    @ClientBundle.Source("Render.css")
    Css css();
  }

  /** CSS for rendering purpose. */
  public interface Css extends CssResource {
    String invisible();
    String nomargins();
    String flap();
    String opaque();
    String transparent();
    String placeholder();
  }

  protected class ConversationListener implements ObservableQuasiConversation.ReplyListener,
      ObservableQuasiConversation.BlipListener, ObservableQuasiConversation.BlipContentListener {

    @Override
    public void onReplyAdded(ObservableQuasiConversationThread thread) {
      ConversationBlip rowOwnerBlip = navigator.getThreadRowOwnerBlip(thread);
      if (rowOwnerBlip != null && !isRendered(rowOwnerBlip)) {
        return;
      }
      if (thread.isInline()) {
        ConversationThread prevThread = navigator.getPreviousThread(thread);
        elementRenderer.insertInlineThread(rowOwnerBlip, thread, prevThread, false);
        initInlineThread(thread, false);
      } else {
        addBlipCluster(RenderUtil.getThreadBlipCluster(thread, navigator));
      }
    }

    @Override
    public void onBeforeReplyRemoved(ObservableQuasiConversationThread thread) {
      ConversationBlip rowOwnerBlip = navigator.getThreadRowOwnerBlip(thread);
      if (rowOwnerBlip != null && !isRendered(rowOwnerBlip)) {
        return;
      }
      removeThread(thread);
      if (thread.isInline()) {
        elementRenderer.removeInlineThread(thread);
      }
    }

    @Override
    public void onBeforeReplyQuasiRemoved(ObservableQuasiConversationThread thread) {}
    
    @Override
    public void onReplyRemoved(ObservableQuasiConversationThread thread) {
      combineAdjacentPlaceholders();
    }

    @Override
    public void onReplyQuasiRemoved(ObservableQuasiConversationThread thread) {}
    
    @Override
    public void onBlipAdded(ObservableQuasiConversationBlip blip) {
      ConversationBlip rowOwnerBlip = navigator.getBlipRowOwnerBlip(blip);
      if (rowOwnerBlip != null && !isRendered(rowOwnerBlip)) {
        return;
      }
      addBlipCluster(new BlipCluster(blip, navigator.getLastBlipInBlipTree(blip, true)) );
    }

    @Override
    public void onBeforeBlipRemoved(ObservableQuasiConversationBlip blip) {
      ConversationBlip rowOwnerBlip = navigator.getBlipRowOwnerBlip(blip);
      if (rowOwnerBlip != null && !isRendered(rowOwnerBlip)) {
        return;
      }
      removeBlipCluster(new BlipCluster(blip, navigator.getLastBlipInBlipTree(blip, true)) );
    }

    @Override
    public void onBeforeBlipQuasiRemoved(ObservableQuasiConversationBlip blip) {}
    
    @Override
    public void onBlipRemoved(ObservableQuasiConversationBlip blip) {
      combineAdjacentPlaceholders();
    }

    @Override
    public void onBlipQuasiRemoved(ObservableQuasiConversationBlip blip) {}
    
    @Override
    public void onBlipContributorAdded(ObservableQuasiConversationBlip blip,
        ParticipantId contributor) {}

    @Override
    public void onBlipContributorRemoved(ObservableQuasiConversationBlip blip,
        ParticipantId contributor) {}

    @Override
    public void onBlipSubmitted(ObservableQuasiConversationBlip blip) {}

    @Override
    public void onBlipTimestampChanged(ObservableQuasiConversationBlip blip, long oldTimestamp,
        long newTimestamp) {}
  }

  private static Css css;

  public static Css getCss() {
    if (css == null) {
      css = WavePanelResourceLoader.getRender().css();
    }
    return css;
  }

  /** Initial size of placeholder in inline threads, in pixels. */
  private final static int SMALL_BLIP_SIZE_PX = 50;

  protected final FragmentRequester fragmentRequester;
  protected final ElementRenderer<T> elementRenderer;
  protected final BlipDocumentRenderer blipDocumentRenderer;
  protected final ConversationNavigator navigator;
  protected final ElementMeasurer<T> measurer;

  protected ObservableQuasiConversation conversation;
  protected ConversationListener conversationListener;

  /** Absolute vertical size of the visible screen. */
  protected Interval visibleScreenSize;
  /** Absolute vertical size of the rendered screen. */
  protected Interval renderScreenSize;

  /** True after first dynamicRendering() call. */
  protected boolean thisWaveHasBeenRendered;
  /** True if dynamic rendering is in process. */
  protected boolean inProcess;

  /** Target blip. */
  protected ConversationBlip targetBlip;
  /** true, if the target blip content is being requested from server. */
  protected boolean targetBlipBeingRequested;

  /** Result of the last phase execution. */
  protected RenderResult lastPhaseResult;
  /** Direction of rendering. */
  protected RenderDirection renderDirection = RenderDirection.DOWN;
  /** The "fixed" blip of rendering (it's visible position shouldn't be changed). */
  protected ConversationBlip renderFixedBlip;

  /** Changes made on blips. */
  protected List<ElementChange<T>> blipChanges = new ArrayList<>();
  /** Changes made on placeholders. */
  protected List<ElementChange<T>> placeholderChanges = new ArrayList<>();

  /** List of placeholders. */
  protected final List<T> placeholders = new ArrayList<>();
  /** Mapping of placeholder to its blip cluster. */
  protected final Map<T, BlipCluster> placeholderToClusters = new HashMap<T, BlipCluster>() {

    @Override
    public BlipCluster get(Object key) {
      return containsKey((T) key) ? super.get(key) : BlipCluster.EMPTY;
    }
  };

  /** Mapping of owner thread id to placeholders of the thread. */
  protected final Multimap<String, T> ownerThreadIdToPlaceholders = ArrayListMultimap.create();
  /** Mapping of placeholder to owner thread id. */
  protected final BiMap<String, T> idToRenderedBlips = HashBiMap.create();
  /** List of adjacent placeholders to be combined together. */
  protected final List<AdjacentPlaceholders<T>> adjacentPlaceholders = new ArrayList<>();

  private final CopyOnWriteSet<Listener> listeners = CopyOnWriteSet.create();

  /** Z-indexer of blips. */
  protected final BlipZIndexer<T> blipZIndexer;

  /** Scroller of the page. */
  protected Scroller scroller;

  protected DynamicRendererImpl(FragmentRequester fragmentRequester,
      ElementRenderer<T> elementRenderer, BlipDocumentRenderer blipDocumentRenderer,
      ConversationNavigator navigator, ElementMeasurer<T> measurer) {
    this.fragmentRequester = fragmentRequester;
    this.elementRenderer = elementRenderer;
    this.blipDocumentRenderer = blipDocumentRenderer;
    this.navigator = navigator;
    this.measurer = measurer;

    blipZIndexer = new BlipZIndexerImpl(navigator, elementRenderer) {

      @Override
      protected boolean isRendered(ConversationBlip blip) {
        return DynamicRendererImpl.this.isRendered(blip);
      }

      @Override
      protected BlipCluster findPlaceholderBlipClusterByBlip(ConversationBlip blip) {
        return placeholderToClusters.get(findPlaceholderByBlip(blip));
      }

      @Override
      protected Object getElementByBlip(ConversationBlip blip) {
        return DynamicRendererImpl.this.getElementByBlip(blip);
      }
    };
  }

  //
  // Public methods
  //

  /**
   * Initializes renderer.
   *
   * @param conversationView conversation view
   */
  public void init(ObservableQuasiConversationView conversationView) {
    conversationView.addListener(new ObservableQuasiConversationView.Listener() {

      @Override
      public void onConversationAdded(ObservableQuasiConversation conversation) {
        if (conversation.isRoot()) {
          observeConversation(conversation);
        }
      }

      @Override
      public void onConversationRemoved(ObservableQuasiConversation conversation) {
        if (conversation.isRoot()) {
          unobserveConversation();
        }
      }
    });

    if (fragmentRequester != null) {
      fragmentRequester.addListener(new FragmentRequester.Listener() {

        @Override
        public void onFragmentsUploaded(Set<SegmentId> segmentIds) {
          for (SegmentId segmentId : segmentIds) {
            ObservableConversationBlip blip = conversation.getBlip(segmentId.getBlipId());

            LOG.trace().log("uploaded blip=" + blip);

            Preconditions.checkNotNull(blip, "Uploaded blip " + segmentId.getBlipId() +
                " is not found in the conversation tree");
            navigator.reindexChildInlineThreads(blip);
          }

          if (targetBlip != null) {
            String targetBlipId = targetBlip.getId();
            for (SegmentId segmentId : segmentIds) {
              if (targetBlipId.equals(segmentId.getBlipId()) ) {
                targetBlipBeingRequested = false;
                break;
              }
            }
          }
          dynamicRendering();
        }
      });
    }

    if (conversationView.getRoot() != null) {
      observeConversation(conversationView.getRoot());
    }
  }

  /**
   * Starts rendering, remembering the initial placeholder.
   *
   * @param startBlipId id of blip to start rendering with.
   */
  public void startRendering(String startBlipId) {
    calculateRenderParameters();

    initPlaceholder(getRootPlaceholder(),
        RenderUtil.getThreadBlipCluster(conversation.getRootThread(), navigator), "", false);

    updateChangedElementsAndScroll();
    completeBlips();

    dynamicRendering(getBlip(startBlipId));
  }

  /** Completes work with this renderer, stopping active tasks and saving current state. */
  public void complete() {
  }

  /** Destroys this renderer, releasing its resources. */
  public void destroy() {
    unobserveConversation();
  }

  //
  // Dynamic renderer
  //

  @Override
  public void dynamicRendering() {
    dynamicRendering((ConversationBlip) null);
  }

  @Override
  public void dynamicRendering(String startBlipId) {
    dynamicRendering(getBlip(startBlipId));
  }

  @Override
  public void dynamicRendering(ConversationBlip targetBlip) {
    if (targetBlip != null && !isRendered(targetBlip)) {
      this.targetBlip = targetBlip;
      targetBlipBeingRequested = false;
    }

    if (inProcess) {
      return;
    }

    inProcess = true;
    thisWaveHasBeenRendered = true;

    triggerOnBeforeRenderingStarted();

    calculateRenderParameters();
    updateChangedElementsAndScroll();
    completeBlips();

    executePhase();
  }

  @Override
  public boolean isBlipReady(String blipId) {
    return isBlipReady(getBlip(blipId));
  }

  @Override
  public boolean isBlipReady(ConversationBlip blip) {
    T blipElement = getElementByBlip(blip);
    if (blipElement == null) {
      return false;
    }
    for (ElementChange<T> change : blipChanges) {
      if (change.getElement() == blipElement) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isBlipVisible(ConversationBlip blip) {
    T blipElement = getElementByBlip(blip);
    if (blipElement == null) {
      return false;
    }
    int top = measurer.getTop(blipElement);
    int bottom = measurer.getBottom(blipElement);
    return visibleScreenSize.contains(top) || visibleScreenSize.contains(bottom);
  }

  @Override
  public T getElementByBlip(ConversationBlip blip) {
    return blip != null ? idToRenderedBlips.get(blip.getId()) : null;
  }

  @Override
  public String getBlipIdByElement(T element) {
    return idToRenderedBlips.inverse().get(element);
  }

  //
  // ObservableDynamicRenderer
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
  // Protected methods
  //

  /**
   * Calculates rendering screen sizes, direction etc.
   */
  protected void calculateRenderParameters() {
    calculateScreenSizes();
  }

  private void observeConversation(ObservableQuasiConversation conversation) {
    Preconditions.checkNotNull(conversation, "conversation cannot be null");
    Preconditions.checkState(conversation.isRoot(), "conversation must be root");

    ConversationListener cl = getConversationListener();
    conversation.addReplyListener(cl);
    conversation.addBlipListener(cl);
    conversation.addBlipContentListener(cl);

    this.conversation = conversation;
  }

  private void unobserveConversation() {
    Preconditions.checkState(conversation == null || conversation.isRoot(),
        "conversation must be root or null");

    if (conversation != null) {
      ConversationListener cl = getConversationListener();
      conversation.removeReplyListener(cl);
      conversation.removeBlipListener(cl);
      conversation.removeBlipContentListener(cl);

      conversation = null;
    }
  }

  private ConversationListener getConversationListener() {
    if (conversationListener == null) {
      conversationListener = createConversationListener();
    }
    return conversationListener;
  }

  protected ConversationListener createConversationListener() {
    return new ConversationListener();
  }

  /** @return the absolute size of the visible screen. */
  protected abstract Interval getVisibleScreenSize();

  /** @return the number of screens to be pre-rendered above the visible screen. */
  protected abstract double getPrerenderedUpperScreenNumber();

  /** @return the number of screens to be pre-rendered below the visible screen. */
  protected abstract double getPrerenderedLowerScreenNumber();

  /** @return root placeholder. */
  protected abstract T getRootPlaceholder();

  protected abstract void scheduleDynamicRendering();

  protected void completeBlips() {
    Iterator<ElementChange<T>> it = blipChanges.iterator();
    while (it.hasNext()) {
      T blipElement = it.next().getElement();
      ConversationBlip blip = getBlipByElement(blipElement);
      setParentBlipHeightsFixed(blip, true, true);

      it.remove();

      completeBlip(blip);

      // At this moment the blip must have been removed from the list.
      triggerOnBlipReady(blip);
    }
  }

  protected void completeBlip(ConversationBlip blip) {
  }

  protected void setParentBlipHeightsFixed(ConversationBlip startBlip, boolean fixed,
      boolean includeStartBlip) {
    ConversationBlip blip = includeStartBlip ? startBlip : navigator.getBlipRowOwnerBlip(startBlip);
    while (blip != null) {
      T blipElement = getElementByBlip(blip);
      elementRenderer.setElementCurrentHeightFixed(blipElement, fixed);
      blip = navigator.getBlipRowOwnerBlip(blip);
    }
  }

  /**
   * @return true, if the blip should be rendered as upper (with back-scrolling)
   * @param element the placeholder of blip element
   * @param blip conversation blip (null for placeholder)
   */
  protected boolean isElementUpper(T element, ConversationBlip blip) {
    if (scroller.isAtBegin()) {
      return false;
    }
    if (scroller.isAtEnd()) {
      return true;
    }

    int top = measurer.getTop(element);
    if (renderFixedBlip != null) {
      // Inner children of the fixed blip affect scrolling when rendering is up!
      if (renderDirection == RenderDirection.UP && blip != null
          && navigator.getBlipRowOwnerBlip(blip) == renderFixedBlip) {
        return true;
      }
      T renderFixedElement = getElementByBlip(renderFixedBlip);
      if (renderFixedElement != null) {
        return top <= measurer.getTop(renderFixedElement);
      }
    }
    if (top >= visibleScreenSize.getEnd()) {
      return false;
    }

    int bottom = measurer.getBottom(element);
    if (bottom <= visibleScreenSize.getBegin()) {
      return true;
    }
    return renderDirection == RenderDirection.UP;
  }

  /**
   * Called after blip is removed.
   *
   * @param blip the removed blip
   */
  protected void terminateBlip(ConversationBlip blip) {}

  //
  // Dynamic rendering
  //

  /** Rendering phase. */
  protected void executePhase() {
    triggerOnBeforePhaseStarted();

    ConversationBlip blipForRendering = null;
    if (targetBlip != null && !targetBlipBeingRequested) {
      blipForRendering = getUpperNonRenderedBlip(targetBlip);
    }
    if (blipForRendering == null) {
      blipForRendering = findBlipForRendering();
    }

    if (blipForRendering != null) {
      if (blipForRendering.hasContent()) {
        renderBlip(blipForRendering);
        lastPhaseResult = RenderResult.PARTIAL;
      } else {
        requestBlipsFromServer();
        lastPhaseResult = RenderResult.DATA_NEEDED;
      }
    } else {
      lastPhaseResult = RenderResult.COMPLETE;
    }

    if (lastPhaseResult == RenderResult.PARTIAL) {
      postRenderBlip();
    } else {
      postDynamicRendering();
    }
  }

  /** Prepares for the next rendering phase. */
  private void postRenderBlip() {
    ensureBlipContentRendered();
    updateChangedElementsAndScroll();
    ensureBlipRendered();
    completePhase();
    startNewPhase();
  }

  protected void ensureBlipContentRendered() {}

  private void updateChangedElementsAndScroll() {
    if (blipChanges.isEmpty() && placeholderChanges.isEmpty()) {
      return;
    }

    // Unfix blip sizes.
    for (ElementChange<T> change : blipChanges) {
      ConversationBlip blip = getBlipByElement(change.getElement());
      setParentBlipHeightsFixed(blip, false, true);
    }

    int scrollPos = scroller.getPosition();
    int deltaHeight = 0;

    for (ElementChange<T> change : blipChanges) {
      T blipElement = change.getElement();
      if (change.isUpper()) {
        deltaHeight += elementRenderer.getBlipPotentialHeight(blipElement);
      }
      elementRenderer.setElementVisible(blipElement, true);
    }

    for (ElementChange<T> change : placeholderChanges) {
      T placeholder = change.getElement();
      if (change.isResizing()) {
        elementRenderer.setElementCurrentHeightFixed(placeholder, false);
        elementRenderer.setElementVisible(placeholder, true);
      } else if (change.isRemoval()) {
        elementRenderer.remove(placeholder);
      }
      if (change.isUpper()) {
        deltaHeight += change.getDeltaHeight();
      }
    }
    placeholderChanges.clear();

    scroller.setPosition(scrollPos + deltaHeight, true);
  }

  protected void ensureBlipRendered() {}

  private void completePhase() {
    completeBlips();

    triggerOnPhaseFinished(lastPhaseResult);
  }

  protected void startNewPhase() {
    executePhase();
  }

  protected void postDynamicRendering() {
    triggerOnPhaseFinished(lastPhaseResult);
    triggerOnRenderingFinished(lastPhaseResult);

    inProcess = false;
  }

  /** @return placeholders to render. */
  public List<PlaceholderInfo<T>> getPlaceholdersToRender() {
    return findPlaceholders(renderScreenSize, PlaceholderType.ANY);
  }

  private ConversationBlip findBlipForRendering() {
    ConversationBlip blip = findBlipForRendering(visibleScreenSize, PlaceholderType.NON_OUTER);
    if (blip == null) {
      blip = findBlipForRendering(renderScreenSize, PlaceholderType.ANY);
    }
    return blip;
  }

  private ConversationBlip findBlipForRendering(Interval interval, int placeholderTypes) {
    List<PlaceholderInfo<T>> foundPlaceholders = findPlaceholders(interval, placeholderTypes);
    if (foundPlaceholders.isEmpty()) {
      return null;
    }

    ConversationBlip bestBlip = null;
    while (!foundPlaceholders.isEmpty()) {
      PlaceholderInfo<T> bestInfo = null;
      int bestTop = 0;
      ListIterator<PlaceholderInfo<T>> it = foundPlaceholders.listIterator();
      while (it.hasNext()) {
        PlaceholderInfo<T> info = it.next();
        T placeholder = info.getPlaceholder();
        int top = measurer.getTop(placeholder);
        if (bestInfo == null
            || renderDirection == RenderDirection.UP && top > bestTop
            || renderDirection == RenderDirection.DOWN && top < bestTop) {
          bestInfo = info;
          bestTop = top;
        }
      }
      if (bestInfo == null) {
        return null;
      }
      bestBlip = findStartBlipByPlaceholder(bestInfo);
      if (bestBlip.hasContent()) {
        return bestBlip;
      }
      foundPlaceholders.remove(bestInfo);
    }
    return bestBlip;
  }

  private void renderBlip(ConversationBlip blip) {
    ConversationBlip rowOwnerBlip = navigator.getBlipRowOwnerBlip(blip);
    if (rowOwnerBlip != null && !isRendered(rowOwnerBlip)) {
      renderBlip(rowOwnerBlip);
    }
    ConversationThread rowOwnerThread = navigator.getBlipRowOwnerThread(blip);
    T placeholder = findPlaceholderByBlip(blip);
    boolean isUpper = isElementUpper(placeholder, blip);
    boolean beforeNeighbor = placeholderToClusters.get(placeholder).isFirstBlip(blip);
    
    T blipElement = elementRenderer.insertBlip(rowOwnerThread, blip, placeholder, beforeNeighbor);
    
    blipZIndexer.applyZIndex(blip, blipElement);
    blipChanges.add(ElementChange.createAddition(blipElement, isUpper));
    
    blipDocumentRenderer.renderDocument(blip);

    // Set placeholder blip cluster to the old placeholder.
    BlipCluster[] newClusters = placeholderToClusters.get(placeholder).subtract(blip, navigator);
    setPlaceholderBlipCluster(placeholder, newClusters[0], rowOwnerThread.getId(), isUpper);

    // Create new placeholder.
    if (newClusters[1] != null) {
      T neighborElement = beforeNeighbor ? placeholder : blipElement;
      T newPlaceholder = elementRenderer.insertPlaceholder(rowOwnerThread, neighborElement, false);
      initPlaceholder(newPlaceholder, newClusters[1], rowOwnerThread.getId(), false);
    }

    initBlip(blip, blipElement, isUpper);

    triggerOnBlipRendered(blip);

    LOG.trace().log("rendered blip: " + blip + ", upper=" + isUpper);

    if (blip == targetBlip) {
      targetBlip = null;
    }
  }

  /** Makes request to the server containing id's of the unloaded blips. */
  private void requestBlipsFromServer() {
    List<ConversationBlip> startBlips = new ArrayList<>();
    if (targetBlip != null) {
      startBlips.add(targetBlip);
      targetBlipBeingRequested = true;
    } else {
      for (PlaceholderInfo<T> info : getPlaceholdersToRender()) {
        startBlips.add(findStartBlipByPlaceholder(info));
      }
    }

    if (startBlips.isEmpty()) {
      return;
    }

    LOG.trace().log("start blips=" + startBlips);

    final WaveletId waveletId;
    try {
      waveletId = DualIdSerialiser.MODERN.deserialiseWaveletId(conversation.getId());
    } catch (InvalidIdException ex) {
      throw new RuntimeException("Deserializing of waveletId " + conversation.getId());
    }
    fragmentRequester.newRequest(waveletId);
    for (ConversationBlip blip : startBlips) {
      if (!blip.hasContent()) {
        fragmentRequester.addSegmentId(waveletId, SegmentId.ofBlipId(blip.getId()), blip.getCreationVersion());
      }
    }
    navigator.findNeighborBlips(startBlips, new Receiver<ConversationBlip>() {

      @Override
      public boolean put(ConversationBlip blip) {
        LOG.trace().log("requested blip=" + blip);
        if (!blip.hasContent()) {
          fragmentRequester.addSegmentId(waveletId, SegmentId.ofBlipId(blip.getId()), blip.getCreationVersion());
        }
        return !fragmentRequester.isFull();
      }
    }, false);
    fragmentRequester.scheduleRequest();
  }

  /**
   * Initializes new placeholder.
   *
   * @param placeholder placeholder
   * @param blipCluster blip cluster
   * @param threadId thread id
   * @param isBig true, if placeholder must be bigger than visible screen
   */
  private void initPlaceholder(T placeholder, BlipCluster blipCluster, String threadId,
      boolean isUpper) {
    if (placeholder != null) {
      setPlaceholderBlipCluster(placeholder, blipCluster, threadId, isUpper);
      placeholders.add(placeholder);
      ownerThreadIdToPlaceholders.put(threadId, placeholder);
    }
  }

  /**
   * Initializes new rendered blip and components inside it.
   *
   * @param blip conversation blip
   * @param blipElement the newly-rendered element of the conversation blip
   * @param isUpper true, if the blip is rendered above visible screen
   */
  protected void initBlip(ConversationBlip blip, T blipElement, boolean isUpper) {
    String blipId = blip.getId();
    idToRenderedBlips.put(blipId, blipElement);

    for (ConversationThread thread : blip.getReplyThreads()) {
      if (thread.isInline()) {
        initInlineThread(thread, isUpper);
      }
    }
  }

  private void initInlineThread(ConversationThread thread, boolean isUpper) {
    initPlaceholder(elementRenderer.getPlaceholderByInlineThread(thread),
        RenderUtil.getThreadBlipCluster(thread, navigator), thread.getId(), isUpper);
  }

  /**
   * @return the blip by its id.
   *
   * @param blipId id to search the blip
   */
  protected ConversationBlip getBlip(String blipId) {
    if (blipId == null || conversation == null) {
      return null;
    }
    switch (blipId) {
      case IdConstants.FIRST_BLIP_ID:
        return navigator.getFirstBlip(conversation.getRootThread());
      case IdConstants.LAST_BLIP_ID:
        return navigator.getLastBlipInThreadTree(conversation.getRootThread(), true);
    }
    return conversation.getBlip(blipId);
  }

  /**
   * @return true, if the given blip is rendered.
   *
   * @param blip the blip to check if it's rendered
   */
  protected boolean isRendered(ConversationBlip blip) {
    return getElementByBlip(blip) != null;
  }

  protected void calculateScreenSizes() {
    visibleScreenSize = getVisibleScreenSize();
    if (visibleScreenSize != null) {
      int height = visibleScreenSize.getLength();
      int prerenderedUpperHeight = (int)(getPrerenderedUpperScreenNumber() * height);
      int prerenderedLowerHeight = (int)(getPrerenderedLowerScreenNumber() * height);
      renderScreenSize = Interval.create(visibleScreenSize.getBegin()- prerenderedUpperHeight,
          visibleScreenSize.getEnd() + prerenderedLowerHeight);
    }
  }

  protected boolean isEditSessionStarted() {
    return false;
  }

  /**
   * @return conversation blip by element.
   *
   * @param blipElement blip element
   */
  protected ConversationBlip getBlipByElement(T blipElement) {
    String blipId = idToRenderedBlips.inverse().get(blipElement);
    return blipId != null ? conversation.getBlip(blipId) : null;
  }

  //
  // Private methods
  //

  /** Sets new blip cluster to the placeholder. */
  private void setPlaceholderBlipCluster(T placeholder, BlipCluster cluster, String ownerThreadId,
      boolean isUpper) {
    if (cluster.isEmpty()) {
      removePlaceholder(placeholder, ownerThreadId, isUpper);
    } else {
      placeholderToClusters.put(placeholder, cluster);
      int oldHeight = measurer.getHeight(placeholder);
      int newHeight = calculatePlaceholderHeight(cluster);
      elementRenderer.setElementHeightFixed(placeholder, oldHeight);
      elementRenderer.setPlaceholderHeight(placeholder, newHeight);
      addPlaceholderChange(ElementChange.createResizing(placeholder, oldHeight, newHeight, isUpper));
    }
  }

  private int calculatePlaceholderHeight(BlipCluster cluster) {
    ConversationBlip firstBlip = cluster.getFirstBlip();
    ConversationBlip lastBlip = cluster.getLastBlip();
    int maxCount = (int) (renderScreenSize.getLength() / SMALL_BLIP_SIZE_PX);
    return navigator.calculateChildBlipCountBetween(firstBlip, lastBlip, maxCount)
        * SMALL_BLIP_SIZE_PX;
  }

  private void removePlaceholder(T placeholder, String ownerThreadId, boolean isUpper) {
    placeholders.remove(placeholder);
    placeholderToClusters.remove(placeholder);
    int height = measurer.getHeight(placeholder);
    addPlaceholderChange(ElementChange.createRemoval(placeholder, height, isUpper));
    ownerThreadIdToPlaceholders.remove(ownerThreadId, placeholder);
  }

  private void removeThread(ConversationThread thread) {
    if (thread.getFirstBlip() != null) {
      removeBlipCluster(RenderUtil.getThreadBlipCluster(thread, navigator));
    }
  }

  private void addBlipCluster(BlipCluster cluster) {
    if (cluster.isEmpty()) {
      return;
    }
    T placeholder;
    ConversationThread rowOwnerThread = navigator.getBlipRowOwnerThread(cluster.getFirstBlip());
    ConversationBlip blipBefore = cluster.getBlipBefore(navigator);
    ConversationBlip blipAfter = cluster.getBlipAfter(navigator);
    if ((blipBefore == null || isRendered(blipBefore))
        && (blipAfter == null || isRendered(blipAfter)) ) {
      // Create new placeholder between two blips.
      boolean insertBefore = blipAfter != null;
      placeholder = elementRenderer.insertPlaceholder(rowOwnerThread,
          getElementByBlip(insertBefore ? blipAfter : blipBefore), insertBefore);
      boolean isUpper = isElementUpper(placeholder, null);
      initPlaceholder(placeholder, cluster, rowOwnerThread.getId(), isUpper);
    } else {
      placeholder = findPlaceholderByBlip(isRendered(blipBefore) ? blipAfter : blipBefore);
      BlipCluster placeholderCluster = placeholderToClusters.get(placeholder);
      BlipCluster newPlaceholderCluster = placeholderCluster;
      if (blipBefore == null || isRendered(blipBefore)
          || blipAfter == null || isRendered(blipAfter)) {
        // Add cluster to the placeholder's cluster.
        newPlaceholderCluster = placeholderCluster.combine(cluster, navigator);
      }
      boolean isUpper = isElementUpper(placeholder, null);
      setPlaceholderBlipCluster(placeholder, newPlaceholderCluster, rowOwnerThread.getId(), isUpper);
    }
  }

  private void removeBlipCluster(BlipCluster cluster) {
    Iterator<ConversationBlip> it = cluster.getIterator(navigator);
    while (it.hasNext()) {
      ConversationBlip blip = it.next();
      if (isRendered(blip)) {
        for (ConversationThread thread : blip.getReplyThreads()) {
          if (thread.isInline()) {
            removeThread(thread);
          }
        }
      } else {
        T placeholder = findPlaceholderByBlip(blip);
        if (placeholder != null) {
          ConversationThread rowOwnerThread = navigator.getBlipRowOwnerThread(blip);
          // Change initial placeholder blip cluster.
          BlipCluster[] newClusters = placeholderToClusters.get(placeholder).subtract(blip, navigator);
          String rowOwnerThreadId = rowOwnerThread.getId();
          boolean isUpper = isElementUpper(placeholder, null);
          setPlaceholderBlipCluster(placeholder, newClusters[0], rowOwnerThreadId, isUpper);
          if (newClusters[1] != null) {
            // Create new placeholder.
            T newPlaceholder = elementRenderer.insertPlaceholder(rowOwnerThread, placeholder, false);
            initPlaceholder(newPlaceholder, newClusters[1], rowOwnerThreadId, false);
            adjacentPlaceholders.add(new AdjacentPlaceholders<>(placeholder, newPlaceholder,
                rowOwnerThreadId));
          }
        }
      }

      if (isRendered(blip)) {
        terminateBlip(blip);

        // Forget rendered blip and its changes.
        T blipElement = getElementByBlip(blip);
        idToRenderedBlips.remove(blip.getId());
        for (ElementChange<T> change : blipChanges) {
          if (change.getElement() == blipElement) {
            blipChanges.remove(change);
            break;
          }
        }
      }
    }
  }

  private void combineAdjacentPlaceholders() {
    for (AdjacentPlaceholders<T> ap : adjacentPlaceholders) {
      T ph1 = ap.getPlaceholder1();
      T ph2 = ap.getPlaceholder2();
      BlipCluster cluster1 = placeholderToClusters.get(ph1);
      BlipCluster cluster2 = placeholderToClusters.get(ph2);
      String threadId = ap.getRowOwnerThreadId();
      if (!cluster1.isEmpty() && !cluster2.isEmpty()) {
        boolean isUpper = isElementUpper(ph1, null) && isElementUpper(ph2, null);
        setPlaceholderBlipCluster(ph1, cluster1.combine(cluster2, navigator), threadId, isUpper);
        setPlaceholderBlipCluster(ph2, BlipCluster.EMPTY, threadId, isUpper);
      }
    }
    adjacentPlaceholders.clear();
  }

  private ConversationBlip getUpperNonRenderedBlip(ConversationBlip blip) {
    ConversationBlip result = null;
    for (ConversationBlip b = blip; b != null; b = navigator.getBlipParentBlip(b)) {
      if (!isRendered(b)) {
        result = b;
      }
    }
    return result;
  }

  private void addPlaceholderChange(ElementChange<T> change) {
    T placeholder = change.getElement();
    for (ListIterator<ElementChange<T>> it = placeholderChanges.listIterator(); it.hasNext(); ) {
      ElementChange<T> ch = it.next();
      if (ch.getElement() == placeholder) {
        it.set(change);
        return;
      }
    }
    placeholderChanges.add(change);
  }

  /**
   * @return placeholders lying within the given interval.
   *
   * @param interval the given interval
   * @param placeholderTypes bit mask composed of needed placeholder type constants
   */
  protected List<PlaceholderInfo<T>> findPlaceholders(Interval interval, int placeholderTypes) {
    List ps = new ArrayList<>();
    for (T placeholder : placeholders) {
      int top = measurer.getTop(placeholder);
      int bottom = measurer.getBottom(placeholder);
      if (top <= interval.getEnd() && bottom >= interval.getBegin()) {
        boolean topInside = interval.contains(top);
        boolean bottomInside = interval.contains(bottom);
        int type = topInside ? (bottomInside ? PlaceholderType.INNER : PlaceholderType.DOWN)
            : (bottomInside ? PlaceholderType.UP : PlaceholderType.OUTER);
        if ((type & placeholderTypes) != 0) {
          ps.add(new PlaceholderInfo<>(placeholder, type));
        }
      }
    }
    return ps;
  }

  /**
   * @return placeholder corresponding to the specified blip.
   *
   * @param blip the blip for which placeholder is looked for
   */
  private T findPlaceholderByBlip(ConversationBlip blip) {
    ConversationThread ownerThread = navigator.getBlipRowOwnerThread(blip);
    Collection<T> threadPlaceholders = ownerThreadIdToPlaceholders.get(ownerThread.getId());

    // Search at the borders of placeholder's blip cluster.
    for (T placeholder : threadPlaceholders) {
      if (placeholderToClusters.get(placeholder).isBorderBlip(blip)) {
        return placeholder;
      }
    }

    // Search within placeholder.
    for (T placeholder : threadPlaceholders) {
      BlipCluster cluster = placeholderToClusters.get(placeholder);
      if (navigator.isBlipBetween(blip, cluster.getFirstBlip(), cluster.getLastBlip()) ) {
        return placeholder;
      }
    }
    return null;
  }

  /**
   * @param info placeholder info
   * @return start blip of placeholder.
   */
  private ConversationBlip findStartBlipByPlaceholder(PlaceholderInfo<T> info) {
    T placeholder = info.getPlaceholder();
    switch (info.getType()) {
      case PlaceholderType.INNER:
        return renderDirection == RenderDirection.UP
            ? RenderUtil.getLastBlipOfPlaceholder(placeholder, placeholderToClusters)
            : RenderUtil.getFirstBlipOfPlaceholder(placeholder, placeholderToClusters);
      case PlaceholderType.UP:
        return RenderUtil.getLastBlipOfPlaceholder(placeholder, placeholderToClusters);
      case PlaceholderType.DOWN:
        return RenderUtil.getFirstBlipOfPlaceholder(placeholder, placeholderToClusters);
      case PlaceholderType.OUTER:
        return RenderUtil.getMiddleBlipOfPlaceholder(placeholder, placeholderToClusters,
            renderScreenSize, measurer, navigator);
    }
    return null;
  }

  //
  // Event triggers
  //

  private void triggerOnBeforeRenderingStarted() {
    for (Listener l : listeners) {
      l.onBeforeRenderingStarted();
    }
  }

  private void triggerOnBeforePhaseStarted() {
    for (Listener l : listeners) {
      l.onBeforePhaseStarted();
    }
  }

  private void triggerOnBlipRendered(ConversationBlip blip) {
    for (Listener l : listeners) {
      l.onBlipRendered(blip);
    }
  }

  private void triggerOnBlipReady(ConversationBlip blip) {
    for (Listener l : listeners) {
      l.onBlipReady(blip);
    }
  }

  protected void triggerOnPhaseFinished(RenderResult result) {
    for (Listener l : listeners) {
      l.onPhaseFinished(result);
    }
  }

  private void triggerOnRenderingFinished(RenderResult result) {
    for (Listener l : listeners) {
      l.onRenderingFinished(result);
    }
  }
}
