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

import com.google.gwt.user.client.Command;

import org.waveprotocol.wave.client.wavepanel.render.RenderUtil.RenderDirection;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationView;
import org.waveprotocol.wave.model.util.Interval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DynamicRenderer implementation for rendering wave into test elements.
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class DynamicTestRenderer extends DynamicRendererImpl<TestElement> {

  private static class ElementTestMeasurer implements ElementMeasurer<TestElement> {

    @Override
    public int getTop(TestElement element) {
      return element.getAbsoluteTop();
    }

    @Override
    public int getHeight(TestElement element) {
      return element.getHeight();
    }

    @Override
    public int getBottom(TestElement element) {
      return getTop(element) + getHeight(element);
    }
  }

  private static class ElementTestRenderer implements ElementRenderer<TestElement> {

    private final ConversationNavigator navigator;
    private final ElementTestMeasurer measurer;
    private final Map<String, TestElement> idToThreadElements = new HashMap<>();
    private final Map<String, TestElement> idToBlipElements = new HashMap<>();

    private RootThreadTestElement rootThreadElement;

    public ElementTestRenderer(ConversationNavigator navigator, ElementTestMeasurer measurer) {
      this.navigator = navigator;
      this.measurer = measurer;
    }

    //
    // Elements
    //

    @Override
    public void remove(TestElement element) {
      element.remove();
    }

    @Override
    public void setElementHeightFixed(TestElement element, int fixedHeight) {
      // do nothing here
    }

    @Override
    public void setElementCurrentHeightFixed(TestElement element, boolean fixed) {
      // do nothing here
    }

    //
    // Placeholder elements
    //

    @Override
    public TestElement insertPlaceholder(ConversationThread rowOwnerThread,
        TestElement neighbor, boolean beforeNeighbor) {
      TestElement placeholder = renderPlaceholder();
      TestElement parentElement = idToThreadElements.get(rowOwnerThread.getId());
      parentElement.insertChild(placeholder, neighbor, beforeNeighbor);
      return placeholder;
    }

    @Override
    public TestElement getPlaceholderByInlineThread(ConversationThread inlineThread) {
      TestElement inlineThreadElement = idToThreadElements.get(inlineThread.getId());
      return inlineThreadElement.getChildren().get(0);
    }

    @Override
    public void setPlaceholderHeight(TestElement placeholder, int pixelHeight) {
      if (placeholder instanceof PlaceholderTestElement) {
        ((PlaceholderTestElement) placeholder).setPixelHeight(pixelHeight);
      }
    }

    //
    // Blip elements
    //

    @Override
    public TestElement insertBlip(ConversationThread rowOwnerThread, ConversationBlip blip,
        TestElement neighbor, boolean beforeNeighbor) {
      TestElement blipElement = renderBlip(blip);
      TestElement parentElement = idToThreadElements.get(rowOwnerThread.getId());
      parentElement.insertChild(blipElement, neighbor, beforeNeighbor);
      rootThreadElement.adjustSize();
      return blipElement;
    }

    @Override
    public TestElement insertInlineThread(ConversationBlip parentBlip,
        ConversationThread thread, ConversationThread neighborThread, boolean beforeNeighbor) {
      TestElement blipElement = idToBlipElements.get(parentBlip.getId());
      TestElement threadElement = renderInlineThread(thread);
      TestElement prevThreadElement = idToThreadElements.get(neighborThread.getId());
      blipElement.insertChild(threadElement, prevThreadElement, beforeNeighbor);
      return threadElement;
    }

    @Override
    public void removeInlineThread(ConversationThread thread) {
      TestElement threadElement = idToThreadElements.get(thread.getId());
      threadElement.remove();
    }

    @Override
    public void setElementVisible(TestElement element, boolean visible) {
      // do nothing here
    }

    @Override
    public int getZIndex(TestElement blipElement) {
      return blipElement.getZIndex();
    }

    @Override
    public void setZIndex(TestElement blipElement, int zIndex) {
      blipElement.setZIndex(zIndex);
    }

    @Override
    public int getBlipPotentialHeight(TestElement blip) {
      return measurer.getHeight(blip);
    }

    //
    // Public methods
    //

    public RootThreadTestElement renderConversation(Conversation conversation) {
      ConversationThread rootThread = conversation.getRootThread();
      renderRootThread(rootThread);
      return rootThreadElement;
    }

    //
    // Private render methods
    //

    private void renderRootThread(ConversationThread thread) {
      rootThreadElement = new RootThreadTestElement(thread);
      rootThreadElement.appendChild(renderPlaceholder());
      idToThreadElements.put(thread.getId(), rootThreadElement);
    }

    private InlineThreadTestElement renderInlineThread(ConversationThread thread) {
      InlineThreadTestElement threadElement = new InlineThreadTestElement(thread);
      threadElement.appendChild(renderPlaceholder());
      idToThreadElements.put(thread.getId(), threadElement);
      return threadElement;
    }

    private PlaceholderTestElement renderPlaceholder() {
      return new PlaceholderTestElement();
    }

    private BlipTestElement renderBlip(ConversationBlip blip) {
      BlipTestElement blipElement = new BlipTestElement(blip, navigator.getBlipIndentationLevel(blip));
      for (ConversationThread thread : blip.getReplyThreads()) {
        if (thread.isInline()) {
          blipElement.appendChild(renderInlineThread(thread));
        }
      }
      idToBlipElements.put(blip.getId(), blipElement);
      return blipElement;
    }
  }

  /**
   * Creates dynamic test renderer.
   *
   * @param prerenderedScreenNumber prerendered screen number
   * @param navigator conversation navigator
   * @return new dynamic test renderer
   */
  public static DynamicTestRenderer create(double prerenderedScreenNumber,
      ConversationNavigator navigator) {
    ElementTestMeasurer elementMeasurer = new ElementTestMeasurer();
    ElementTestRenderer elementRenderer = new ElementTestRenderer(navigator, elementMeasurer);
    DynamicTestRenderer renderer = new DynamicTestRenderer(elementRenderer,
        prerenderedScreenNumber, navigator, elementMeasurer);
    return renderer;
  }

  private final ElementTestRenderer elementTestRenderer;
  private final double prerenderedScreenNumber;

  private RootThreadTestElement rootThreadElement;
  private int screenWidth;
  private int screenHeight;
  private int scrollPosition;

  private final List<ConversationBlip> renderedBlipList = new ArrayList<>();

  private DynamicTestRenderer(ElementTestRenderer elementTestRenderer,
      double prerenderedScreenNumber, ConversationNavigator navigator,
      ElementTestMeasurer elementTestMeasurer) {
    super(null, elementTestRenderer, BlipDocumentRenderer.EMPTY, navigator, elementTestMeasurer);
    this.elementTestRenderer = elementTestRenderer;
    this.prerenderedScreenNumber = prerenderedScreenNumber;

    scroller = new Scroller.Impl() {

      @Override
      public int getScrollHeight() {
        return rootThreadElement.getHeight();
      }

      @Override
      public int getPosition() {
        return scrollPosition;
      }

      @Override
      public void setPosition(int value, boolean silent) {
        int maxPosition = getMaxPosition();
        if (value > maxPosition) {
          value = maxPosition;
        }
        if (value < 0) {
          value = 0;
        }
        scrollPosition = value;

        calculateScreenSizes();
      }

      @Override
      public int getPanelTop() {
        return 0;
      }

      @Override
      public int getPanelHeight() {
        return screenHeight;
      }
    };

    observeThis();
  }

  public void setScreenDimension(int screenWidth, int screenHeight) {
    this.screenWidth = screenWidth;
    if (rootThreadElement != null) {
      rootThreadElement.setPageWidth(screenWidth);
    }
    this.screenHeight = screenHeight;
  }

  public String getSnapshot() {
    String s = "Screen(" + scrollPosition + "," + (scrollPosition + screenHeight) + ") ";
    s += toStringWithChildren(rootThreadElement);
    return s;
  }

  public List<ConversationBlip> getRenderedBlipList() {
    return renderedBlipList;
  }

  public void clearRenderedBlipList() {
    renderedBlipList.clear();
  }

  public Scroller getScroller() {
    return scroller;
  }

  public ElementMeasurer getElementMeasurer() {
    return measurer;
  }

  public void scrollToBegin() {
    scroller.setPosition(0, false);
    renderDirection = RenderDirection.DOWN;
  }

  public void scrollToEnd() {
    scroller.setPosition(scroller.getMaxPosition(), false);
    renderDirection = RenderDirection.UP;
  }

  //
  // DynamicRenderer
  //


  @Override
  public void init(ObservableQuasiConversationView conversationView) {
    super.init(conversationView);

    rootThreadElement = elementTestRenderer.renderConversation(conversation);
    rootThreadElement.setPageWidth(screenWidth);
  }

  @Override
  protected Interval getVisibleScreenSize() {
    return Interval.create(scrollPosition, scrollPosition + screenHeight);
  }

  @Override
  protected double getPrerenderedUpperScreenNumber() {
    return prerenderedScreenNumber;
  }

  @Override
  protected double getPrerenderedLowerScreenNumber() {
    return prerenderedScreenNumber;
  }

  @Override
  protected TestElement getRootPlaceholder() {
    return rootThreadElement.getChildren().get(0);
  }

  @Override
  protected void scheduleDynamicRendering() {
  }

  //
  // Private methods
  //

  private String toStringWithChildren(TestElement element) {
    String s = "";
    switch (element.getKind()) {
      case BLIP:
        s += element.getAbsoluteTop();
        break;
      case PLACEHOLDER:
        s += element.getShortName() + "(" + element.getAbsoluteTop() + "," +
            element.getAbsoluteBottom() + ")";
        break;
      case ROOT_THREAD:
      case INLINE_THREAD:
        s += element.getShortName();
        break;
    }
    if (!element.getChildren().isEmpty()) {
      s += "[";
      boolean isFirst = true;
      for (TestElement child : element.getChildren()) {
        if (isFirst) {
          isFirst = false;
        } else {
          s += " ";
        }
        s += toStringWithChildren(child);
      }
      s += "]";
    }
    return s;
  }

  private void observeThis() {
    addListener(new ListenerImpl() {

      @Override
      public void onBeforeRenderingStarted() {
        rootThreadElement.adjustSize();
      }

      @Override
      public void onBlipRendered(ConversationBlip blip) {
        rootThreadElement.adjustSize();
      }

      @Override
      public void onBlipReady(ConversationBlip blip) {
        renderedBlipList.add(blip);
      }

      @Override
      public void onPhaseFinished(RenderResult result) {
        inProcess = false;
      }
    });
  }
}
