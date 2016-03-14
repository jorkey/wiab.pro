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

import org.waveprotocol.wave.model.conversation.BlipCluster;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;

/**
 * Implementation of BlipZIndexer.
 * 
 * @param <T> rendered element type
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public abstract class BlipZIndexerImpl<T> implements BlipZIndexer<T> {

  /** Value of the z-index of the first blip in the row. */
  private static final int FIRST_ROW_BLIP_ZINDEX = 900000; // Continuation indicator's zIndex=999999.
  /** Step of z-index between two neighbor blips. */
  private static final int BLIP_ZINDEX_BIG_STEP = 32;
  /** Step of z-index to avoid collision. */
  private static final int BLIP_ZINDEX_SMALL_STEP = 4;
  
  /** Conversation navigator. */
  private final ConversationNavigator navigator;
  /** Element renderer. */
  private final ElementRenderer elementRenderer;
  
  public BlipZIndexerImpl(ConversationNavigator navigator, ElementRenderer elementRenderer) {
    this.navigator = navigator;
    this.elementRenderer = elementRenderer;
  }
  
  //
  // BlipZIndexer
  //
  
  @Override
  public void applyZIndex(ConversationBlip blip, T blipElement) {
    int zIndex = calculateZIndex(blip);
    elementRenderer.setZIndex(blipElement, zIndex);

    // Avoid z-index collisions with previous blips.
    int prevZIndex, nextZIndex;
    nextZIndex = zIndex;
    for (ConversationBlip prevBlip = navigator.getPreviousBlipInRow(blip);
        prevBlip != null && isRendered(prevBlip);
        prevBlip = navigator.getPreviousBlipInRow(prevBlip)) {
      T prevBlipElement = getElementByBlip(prevBlip);
      prevZIndex = elementRenderer.getZIndex(prevBlipElement);
      if (nextZIndex < prevZIndex) {
        break;
      }
      prevZIndex  = nextZIndex + BLIP_ZINDEX_SMALL_STEP;
      elementRenderer.setZIndex(prevBlipElement, prevZIndex);
      nextZIndex = prevZIndex;
    }

    // avoid z-index collisions with next blips
    prevZIndex = zIndex;
    for (ConversationBlip nextBlip = navigator.getNextBlipInRow(blip);
        nextBlip != null && isRendered(nextBlip);
        nextBlip = navigator.getNextBlipInRow(nextBlip)) {
      T nextBlipElement = getElementByBlip(nextBlip);
      nextZIndex = elementRenderer.getZIndex(nextBlipElement);
      if (nextZIndex < prevZIndex) {
        break;
      }
      nextZIndex = prevZIndex - BLIP_ZINDEX_SMALL_STEP;
      elementRenderer.setZIndex(nextBlipElement, nextZIndex);
      prevZIndex = nextZIndex;
    }
  }
  
  //
  // Protected methods to be implemented in successor
  //
  
  /**
   * @return true, if the blip is rendered.
   * 
   * @param blip conversation blip
   */
  protected abstract boolean isRendered(ConversationBlip blip);
  
  /**
   * @return placeholder's blip cluster containing the given blip
   * 
   * @param blip conversation blip
   */
  protected abstract BlipCluster findPlaceholderBlipClusterByBlip(ConversationBlip blip);
  
  /**
   * @return rendered blip element
   * 
   * @param blip conversation blip
   */
  protected abstract T getElementByBlip(ConversationBlip blip);
  
  //
  // Private methods
  //
      
  private int calculateZIndex(ConversationBlip blip) {
    ConversationBlip prevBlip = navigator.getPreviousBlipInRow(blip);
    ConversationBlip nextBlip = navigator.getNextBlipInRow(blip);
    T prevBlipElement = getElementByBlip(prevBlip);
    T nextBlipElement = getElementByBlip(nextBlip);

    // Use previous and next z-indexes.
    if (prevBlipElement != null && nextBlipElement != null) {
      int prevZIndex = elementRenderer.getZIndex(prevBlipElement);
      int nextZIndex = elementRenderer.getZIndex(nextBlipElement);
      return (prevZIndex + nextZIndex) / 2;
    }

    // Use previous z-index.
    if (prevBlipElement != null) {
      return elementRenderer.getZIndex(prevBlipElement) - BLIP_ZINDEX_BIG_STEP;
    }

    // Use next z-index.
    if (nextBlipElement != null) {
      return elementRenderer.getZIndex(nextBlipElement) + BLIP_ZINDEX_BIG_STEP;
    }

    // It's the first blip.
    if (prevBlip == null) {
      return FIRST_ROW_BLIP_ZINDEX;
    }

    // It's the last blip.
    if (nextBlip == null) {
      return FIRST_ROW_BLIP_ZINDEX / 2;
    }

    // find prev and next base z-indexes (before and after blip's placeholder) and use them
    ConversationBlip prevBaseBlip = findBlipNearPlaceholder(prevBlip, true);
    T prevBaseBlipElement = getElementByBlip(prevBaseBlip);
    int prevBaseZIndex = prevBaseBlipElement != null
        ? elementRenderer.getZIndex(prevBaseBlipElement) : FIRST_ROW_BLIP_ZINDEX;
    ConversationBlip nextBaseBlip = findBlipNearPlaceholder(nextBlip, false);
    T nextBaseBlipElement = getElementByBlip(nextBaseBlip);
    int nextBaseZIndex = nextBaseBlipElement != null
        ? elementRenderer.getZIndex(nextBaseBlipElement) : FIRST_ROW_BLIP_ZINDEX / 2;
    return (prevBaseZIndex + nextBaseZIndex) / 2;
  }
  
  private ConversationBlip findBlipNearPlaceholder(ConversationBlip startBlip, boolean before) {
    BlipCluster blipCluster = findPlaceholderBlipClusterByBlip(startBlip);
    return before ? blipCluster.getBlipBefore(navigator) : blipCluster.getBlipAfter(navigator);
  }
}
