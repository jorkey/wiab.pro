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
import com.google.gwt.dom.client.Style;

import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.ThreadView;
import org.waveprotocol.wave.client.wavepanel.view.View;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomUtil;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomView;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.WavePanelResourceLoader;
import org.waveprotocol.wave.client.wavepanel.view.impl.AbstractStructuredView;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;

/**
 * ElementRenderer implementation for DOM elements.
 * 
 * @author dkonovalchik@gmail.com (Denis Konovalchik)
 */
public class ElementDomRenderer implements ElementRenderer<Element> {

  private final ModelAsViewProvider modelAsViewProvider;
  private final DomAsViewProvider domAsViewProvider;
  private final ReplyManager replyManager;
  private final ElementDomMeasurer measurer;
  private final BlipViewBuilder.Css blipCss;

  public ElementDomRenderer(ModelAsViewProvider modelAsViewProvider,
      DomAsViewProvider domAsViewProvider, ReplyManager replyManager, ElementDomMeasurer measurer) {
    this.modelAsViewProvider = modelAsViewProvider;
    this.domAsViewProvider = domAsViewProvider;
    this.replyManager = replyManager;
    this.measurer = measurer;
    blipCss = WavePanelResourceLoader.getBlip().css();
  }

  //
  // Common
  //
  
  @Override
  public void remove(Element element) {
    element.removeFromParent();
  }

  @Override
  public void setElementVisible(Element element, boolean visible) {
    if (DomUtil.doesElementHaveType(element, Type.BLIP)) {
      DomUtil.addOrRemoveClassName(element, blipCss.transparent(), !visible);
      DomUtil.addOrRemoveClassName(element, blipCss.opaque(), visible);
    }
    DomUtil.addOrRemoveClassName(element, DynamicRendererImpl.getCss().invisible(), !visible);
  }

  @Override
  public void setElementHeightFixed(Element element, int fixedHeight) {
    DomUtil.fixElementHeight(element, fixedHeight);
  }

  @Override
  public void setElementCurrentHeightFixed(Element element, boolean fixed) {
    if (fixed) {
      setElementHeightFixed(element, measurer.getHeight(element));
    } else {
      DomUtil.unfixElementHeight(element);
    }
  }  
  
  //
  // Placeholders
  //

  @Override
  public Element insertPlaceholder(ConversationThread rowOwnerThread, Element neighbor,
      boolean beforeNeighbor) {
    Element placeholder = elementOf(viewOf(rowOwnerThread).insertPlaceholder(viewOf(neighbor),
        beforeNeighbor));
    setElementVisible(placeholder, false);
    return placeholder;
  }

  @Override
  public Element getPlaceholderByInlineThread(ConversationThread inlineThread) {
    Element inlineThreadElement = elementOf(modelAsViewProvider.getInlineThreadView(inlineThread));
    return DomUtil.findFirstChildElement(inlineThreadElement,
        Type.CHROME, Type.INLINE_THREAD_STRUCTURE, Type.PLACEHOLDER);
  }

  @Override
  public void setPlaceholderHeight(Element placeholder, int height) {
    placeholder.getStyle().setHeight(height, Style.Unit.PX);
  }  

  /** Returns the DOM element of a view. */
  @SuppressWarnings("unchecked")
  public static Element elementOf(View v) {
    AbstractStructuredView<?, ? extends DomView> asv =
        (AbstractStructuredView<?, ? extends DomView>) v;
    return v == null ? null : asv.getIntrinsic().getElement();
  }  
  
  //
  // Blips
  //

  @Override
  public Element insertBlip(ConversationThread rowOwnerThread, ConversationBlip blip,
      Element neighbor, boolean beforeNeighbor) {    
    ThreadView threadView = viewOf(rowOwnerThread);
    View neighborView = viewOf(neighbor);
    
    BlipView blipView = threadView.insertBlip(blip, neighborView, beforeNeighbor);
    Element blipElement = elementOf(blipView);
    setElementVisible(blipElement, false);
    
    return blipElement;
  }

  @Override
  public Element insertInlineThread(ConversationBlip parentBlip,
      ConversationThread thread, ConversationThread neighborThread, boolean beforeNeighbor) {
    return elementOf(replyManager.present(modelAsViewProvider.getBlipView(parentBlip), thread,
        neighborThread, beforeNeighbor));
  }

  @Override
  public void removeInlineThread(ConversationThread thread) {
    Element threadElement = elementOf(modelAsViewProvider.getInlineThreadView(thread));
    if (threadElement != null) {
      Element threadParent = threadElement.getParentElement();
      if (threadParent != null) {
        threadParent.removeFromParent();
      }
    }
  }

  @Override
  public int getZIndex(Element blipElement) {
    return Integer.parseInt(blipElement.getStyle().getZIndex());
  }

  @Override
  public void setZIndex(Element blipElement, int zIndex) {
    blipElement.getStyle().setZIndex(zIndex);
  }
  
  @Override
  public int getBlipPotentialHeight(Element blip) {
    Element topMargin = DomUtil.findFirstChildElement(blip, Type.BLIP_TOP_MARGIN);
    Element meta = DomUtil.findFirstChildElement(blip, Type.META);
    Element bottomMargin = DomUtil.findFirstChildElement(blip, Type.BLIP_BOTTOM_MARGIN);
    return measurer.getHeight(topMargin) + measurer.getHeight(meta)
        + measurer.getHeight(bottomMargin);
  }  
  
  // Private methods

  public ThreadView viewOf(ConversationThread thread) {
    if (thread == null) {
      return null;
    }
    if (thread.isRoot()) {
      return modelAsViewProvider.getRootThreadView(thread);
    }
    if (thread.isInline()) {
      return modelAsViewProvider.getInlineThreadView(thread);
    }
    return modelAsViewProvider.getOutlineThreadView(thread);
  }

  private View viewOf(Element element) {
    if (element != null) {
      switch (DomUtil.getElementType(element)) {
        case BLIP:
          return domAsViewProvider.asBlip(element);
        case PLACEHOLDER:
          return domAsViewProvider.asPlaceholder(element);
      }
    }
    return null;
  }
}
