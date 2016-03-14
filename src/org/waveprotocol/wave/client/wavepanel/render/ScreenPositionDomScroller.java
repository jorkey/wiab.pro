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

import org.waveprotocol.wave.client.editor.content.ContentDocument;
import org.waveprotocol.wave.client.editor.content.ContentElement;
import org.waveprotocol.wave.client.editor.content.ContentNode;
import org.waveprotocol.wave.client.editor.impl.NodeManager;
import org.waveprotocol.wave.client.scroll.SmartScroller;
import org.waveprotocol.wave.client.wave.DiffContentDocument;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.View;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomUtil;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.document.util.LineContainers;
import org.waveprotocol.wave.model.document.util.Point;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.supplement.ScreenPosition;
import org.waveprotocol.wave.model.util.Pair;

/**
 * DOM implementation of ScreenPositionScroller
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class ScreenPositionDomScroller extends ScreenPositionScrollerImpl<Element> {
  
  /**
   * Creates the screen position scroller.
   * 
   * @param renderer the dynamic renderer
   * @param smartScroller smart scroller
   * @param modelAsViewProvider model as view provider
   * @param scroller
   * @param measurer element measurer
   * @return new screen position scroller
   */
  public static ScreenPositionDomScroller create(ObservableDynamicRenderer renderer,
      SmartScroller<? super BlipView> smartScroller, ModelAsViewProvider modelAsViewProvider,
      Scroller scroller, ElementMeasurer measurer) {
    return new ScreenPositionDomScroller(renderer, smartScroller, modelAsViewProvider, scroller,
        measurer);
  }  
  
  private final ModelAsViewProvider modelAsViewProvider;
  private final SmartScroller<? super BlipView> smartScroller;
  
  private ConversationBlip blipToScroll;  
  
  private ScreenPositionDomScroller(ObservableDynamicRenderer renderer,
      SmartScroller<? super BlipView> smartScroller, ModelAsViewProvider modelAsViewProvider,
      Scroller scroller, ElementMeasurer measurer) {
    super(renderer, scroller, measurer);
    
    this.smartScroller = smartScroller;
    this.modelAsViewProvider = modelAsViewProvider;
  }

  @Override
  public void scrollToBlip(ConversationBlip blip) {
    blipToScroll = null;
    positionToScroll = null;    
    if (blip != null) {
      if (renderer.isBlipReady(blip)) {
        BlipView blipView = modelAsViewProvider.getBlipView(blip);
        if (blipView != null) {
          smartScroller.moveTo(blipView);
        }        
      } else {
        blipToScroll = blip;
        renderer.dynamicRendering(blip);        
      }
    }
  }  

  @Override
  public void scrollToScreenPosition(ScreenPosition position) {
    blipToScroll = null;
    
    super.scrollToScreenPosition(position);
  }
  
  @Override
  public ScreenPosition getScreenPosition() {
    String topBlipId = null;
    int paragraphOffset = -1;
    double relativeOffset = 0;
    if (scroller.isAtBegin()) {
      topBlipId = IdConstants.FIRST_BLIP_ID;
    } else if (scroller.isAtEnd()) {
      topBlipId = IdConstants.LAST_BLIP_ID;
    } else {
      ConversationThread rootThread = conversationView.getRoot().getRootThread();
      ConversationBlip topBlip = findScreenPositionBlip(rootThread, scroller.getPanelTop());
      if (topBlip == null) {
        topBlip = rootThread.getFirstBlip();
      }
      if (topBlip != null) {
        topBlipId = topBlip.getId();
        Pair<Integer, Double> offsets = findScreenPositionOffsets(topBlip, scroller.getPanelTop());
        paragraphOffset = offsets.getFirst();
        relativeOffset = offsets.getSecond();
      }
    }
    return new ScreenPosition(topBlipId, paragraphOffset, relativeOffset);
  }
  
  //
  // Protected methods
  //

  @Override
  protected void doScrollAttempt() {
    if (blipToScroll != null) {
      scrollToBlip(blipToScroll);
    } else if (positionToScroll != null) {
      scrollToScreenPosition(positionToScroll);
    }
  }
  
  @Override
  protected int screenPositionToScrollPosition(ScreenPosition position) {
    String blipId = position.getBlipId();
    if (blipId.equals(IdConstants.FIRST_BLIP_ID) || blipId.equals(IdConstants.LAST_BLIP_ID)) {
      return super.screenPositionToScrollPosition(position);
    }
    
    int scrollPosition = -1;
    ConversationBlip blip = conversationView.getRoot().getBlip(blipId);
    if (blip != null) {
      Element baseElement = findScreenPositionElement(blip, position.getParagraphOffset());
      double relativeOffset = position.getRelativeOffset();
      int pixelOffset = (int) Math.round(relativeOffset * DomUtil.getElementHeight(baseElement));    
      scrollPosition = baseElement.getAbsoluteTop() + scroller.getPosition() - scroller.getPanelTop()
          + pixelOffset;
    }
    return scrollPosition;
  }
  
  //
  // Private methods
  //
  
  /**
   * Gets screen position blip.
   * 
   * @param rowOwnerThread row owner thread to find the blip in
   * @param yPos screen vertical position
   * @return screen position blip
   */
  private ConversationBlip findScreenPositionBlip(ConversationThread rowOwnerThread, int yPos) {
    Element topElement = DomUtil.findChildElementContainingY(
        RenderUtil.getBlipContainer(rowOwnerThread, modelAsViewProvider), yPos);
    if (topElement == null || !View.Type.BLIP.equals(DomUtil.getElementType(topElement)) ) {
      return null;
    }

    String topId = renderer.getBlipIdByElement(topElement);
    ConversationBlip topBlip = topId != null ? conversationView.getRoot().getBlip(topId) : null;
    if (topBlip == null) {
      return null;
    }

    // Find top visible blip in inline threads of the found blip.
    for (ConversationThread thread : topBlip.getReplyThreads()) {
      if (thread.isInline()) {
        ConversationBlip innerTopBlip = findScreenPositionBlip(thread, yPos);
        if (innerTopBlip != null) {
          return innerTopBlip;
        }
      }
    }
    
    return topBlip;
  }

  /**
   * Gets screen position offsets - paragraph offset and relative offset.
   * 
   * @param blip the blip at the screen position
   * @param yPos screen vertical position
   * @return pair containing paragraph offset and relative offset
   */
  private Pair<Integer, Double> findScreenPositionOffsets(ConversationBlip blip, int yPos) {
    int paragraphOffset = -1;
    double relativeOffset = 0;
    Element blipElement = renderer.getElementByBlip(blip);
    if (blipElement != null) {
      Element docElement = DomUtil.findFirstChildElement(blipElement, View.Type.META,
          View.Type.DOCUMENT);
      Element ulElement = docElement.getFirstChildElement().getFirstChildElement()
          .getFirstChildElement();
      Element paragraphElement = DomUtil.findChildElementContainingY(ulElement, yPos);
      Element baseElement = blipElement;
      if (paragraphElement != null) {
        ContentDocument doc = ((DiffContentDocument)blip.getContent()).getDocument();
        ContentElement contentElement = NodeManager.getBackReference(paragraphElement);
        paragraphOffset = doc.getLocationMapper().getLocation(contentElement);
        baseElement = paragraphElement;
      }
      double absoluteOffset = yPos - baseElement.getAbsoluteTop();    
      double baseHeight = DomUtil.getElementHeight(baseElement);
      relativeOffset = absoluteOffset / baseHeight;
    }
    return new Pair<>(paragraphOffset, relativeOffset);
  }
  
  /**
   * Gets DOM element by the given screen position.
   * 
   * @param blip conversation blip
   * @param paragraphOffset paragraph offset in the document
   * @return screen position DOM element
   */
  private Element findScreenPositionElement(ConversationBlip blip, int paragraphOffset) {
    if (paragraphOffset != -1) {
      ContentDocument doc = ((DiffContentDocument)blip.getContent()).getDocument();
      Point<ContentNode> location = null;
      try {
        location = doc.getLocationMapper().locate(paragraphOffset);
      } catch (IndexOutOfBoundsException e) {          
      }
      if (location != null) {
        ContentElement paragraphElement = null;
        if (location.isInTextNode()) {
          ContentNode textNode = location.getContainer();
          if (textNode != null) {                    
            paragraphElement = textNode.getParentElement();
          }
        } else {
          ContentNode locationNode = location.getNodeAfter();
          if (locationNode != null) {
            ContentElement locationElement = locationNode.asElement();            
            if (locationElement != null) {
              switch (locationElement.getTagName()) {
                case LineContainers.LINE_TAGNAME: // Text line.
                  paragraphElement = locationElement.getPreviousSibling().asElement();
                  break;
                default:  // Attachment or gadget.
                  paragraphElement = locationElement.getParentElement();
                  break;
              }    
            }  
          }          
        }
        if (paragraphElement != null) {
          return paragraphElement.getImplNodelet();
        }
      }  
    }
    return renderer.getElementByBlip(blip);
  }  
}
