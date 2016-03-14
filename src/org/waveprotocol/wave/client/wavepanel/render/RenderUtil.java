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

import org.waveprotocol.wave.client.wavepanel.view.View;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomUtil;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.model.conversation.BlipCluster;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;
import org.waveprotocol.wave.model.util.Interval;

import java.util.Map;

/**
 * Different constants and small classes for rendering.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class RenderUtil {

  /**
   * Direction of rendering.
   */
  public static enum RenderDirection {
    /** From the bottom to the top. */
    UP,
    /** From the top to the bottom. */
    DOWN
  }

  /**
   * Types of placeholders relatively to the screen.
   */
  public static class PlaceholderType {
    /** The screen embraces the placeholder. */
    public static final int INNER = 0b0001;
    /** Only bottom end of the placeholder is within screen. */
    public static final int UP = 0b0010;
    /** Only top end of the placeholder is within screen. */
    public static final int DOWN = 0b0100;
    /** Placeholder embraces the screen. */    
    public static final int OUTER = 0b1000;
    
    /** The screen contains at least one end of the placeholder. */
    public static final int NON_OUTER = INNER | UP | DOWN;
    /** Any placeholder of above types. */
    public static final int ANY = INNER | UP | DOWN | OUTER;
  }  
  
  /**
   * Keeps information about adjacent placeholders to be combined later.
   * 
   * @param <T> type of placeholder
   */
  public static class AdjacentPlaceholders<T> {

    private final T placeholder1;
    private final T placeholder2;
    private final String rowOwnerThreadId;

    public AdjacentPlaceholders(T placeholder1, T placeholder2, String rowOwnerThreadId) {
      this.placeholder1 = placeholder1;
      this.placeholder2 = placeholder2;
      this.rowOwnerThreadId = rowOwnerThreadId;
    }

    public T getPlaceholder1() {
      return placeholder1;
    }

    public T getPlaceholder2() {
      return placeholder2;
    }

    public String getRowOwnerThreadId() {
      return rowOwnerThreadId;
    }
  }  

  /**
   * Placeholder info containing placeholder itself and its type.
   * 
   * @param <T> type of the placeholder
   */
  public static class PlaceholderInfo<T> {
    
    private final T placeholder;
    private final int type;
    
    public PlaceholderInfo(T placeholder, int type) {
      this.placeholder = placeholder;
      this.type = type;
    }
    
    public T getPlaceholder() {
      return placeholder;
    }
    
    public int getType() {
      return type;
    }
  }
  
  /**
   * Change of the element during rendering.
   * 
   * @param <T> type of the element
   */
  public static class ElementChange<T> {

    public static enum ChangeType {
      ADDITION,
      RESIZING,
      REMOVAL
    };

    public static <T> ElementChange createAddition(T element, boolean isUpper) {
      return new ElementChange(element, ChangeType.ADDITION, 0, 0, isUpper);
    }    

    public static <T> ElementChange createResizing(T element, int oldHeight, int newHeight,
        boolean isUpper) {
      return new ElementChange(element, ChangeType.RESIZING, oldHeight, newHeight, isUpper);
    }

    public static <T> ElementChange createRemoval(T element, int oldHeight, boolean isUpper) {
      return new ElementChange(element, ChangeType.REMOVAL, oldHeight, 0, isUpper);
    }

    private final T element;
    private final ChangeType changeType;
    private final int oldHeight;
    private final int newHeight;
    private final boolean isUpper;

    private ElementChange(T element, ChangeType changeType, int oldHeight, int newHeight,
        boolean isUpper) {
      this.element = element;
      this.changeType = changeType;
      this.oldHeight = oldHeight;
      this.newHeight = newHeight;
      this.isUpper = isUpper;
    }

    public T getElement() {
      return element;
    }

    public ChangeType getType() {
      return changeType;
    }

    public boolean isAddition() {
      return ChangeType.ADDITION.equals(changeType);
    }

    public boolean isResizing() {
      return ChangeType.RESIZING.equals(changeType);
    }

    public boolean isRemoval() {
      return ChangeType.REMOVAL.equals(changeType);
    }

    public int getOldHeight() {
      return oldHeight;
    }

    public int getNewHeight() {
      return newHeight;
    }

    public int getDeltaHeight() {
      return newHeight - oldHeight;
    }

    public boolean isUpper() {
      return isUpper;
    }    
  }
  
  //
  // Public methods.
  //
  
  
  
  /**
   * @return first blip of placeholder.
   * @param <T> placeholder type
   * @param placeholder
   * @param placeholderToClusters mapping placeholder to clusters
   */
  public static <T> ConversationBlip getFirstBlipOfPlaceholder(T placeholder,
      Map<T, BlipCluster> placeholderToClusters) {
    return placeholderToClusters.get(placeholder).getFirstBlip();
  }

  /**
   * @return middle blip of placeholder.
   * @param <T> placeholder type
   * @param placeholder
   * @param placeholderToClusters mapping placeholder to clusters
   * @param renderScreenSize render screen size
   * @param measurer element measurer
   * @param navigator conversation navigator
   */  
  public static <T> ConversationBlip getMiddleBlipOfPlaceholder(T placeholder,
      Map<T, BlipCluster> placeholderToClusters, Interval renderScreenSize,
      ElementMeasurer<T> measurer, ConversationNavigator navigator) {
    // Selects the best from front and back blips comparing the space above and under the screen
    int spaceAboveScreen = renderScreenSize.getBegin() - measurer.getTop(placeholder);
    int spaceUnderScreen = measurer.getBottom(placeholder) - renderScreenSize.getEnd();
    boolean preferFrontBlip = spaceAboveScreen < spaceUnderScreen;
    return placeholderToClusters.get(placeholder).getMiddleBlip(preferFrontBlip, navigator);
  }

  /**
   * @return last blip of placeholder.
   * @param <T> placeholder type
   * @param placeholder
   * @param placeholderToClusters mapping placeholder to clusters
   */  
  public static <T> ConversationBlip getLastBlipOfPlaceholder(T placeholder,
      Map<T, BlipCluster> placeholderToClusters) {
    return placeholderToClusters.get(placeholder).getLastBlip();
  }
  
  /**
   * Gets blip cluster by the conversation thread.
   * 
   * @param thread conversation thread
   * @param navigator conversation navigator
   * @return thread's blip cluster
   */
  public static BlipCluster getThreadBlipCluster(ConversationThread thread,
      ConversationNavigator navigator) {
    ConversationBlip firstBlip = navigator.getFirstBlip(thread);
    ConversationBlip lastBlip = navigator.getLastBlipInThreadTree(thread, true);
    return new BlipCluster(firstBlip, lastBlip);
  }  
    
  /**
   * Gets container element for the blips for the row owner thread.
   * 
   * @param rowOwnerThread row owner thread
   * @param modelAsViewProvider model as view provider
   * @return container element
   */
  public static Element getBlipContainer(ConversationThread rowOwnerThread,
      ModelAsViewProvider modelAsViewProvider) {
    if (rowOwnerThread.isRoot()) {
      Element thread = ElementDomRenderer.elementOf(
          modelAsViewProvider.getRootThreadView(rowOwnerThread));
      return DomUtil.findFirstChildElement(thread, View.Type.BLIPS);
    } else {
      Element thread = ElementDomRenderer.elementOf(
          modelAsViewProvider.getInlineThreadView(rowOwnerThread));
      return DomUtil.findFirstChildElement(thread, View.Type.CHROME,
          View.Type.INLINE_THREAD_STRUCTURE);
    }
  }
}
