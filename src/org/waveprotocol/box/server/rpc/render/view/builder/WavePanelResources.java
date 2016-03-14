/**
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.waveprotocol.box.server.rpc.render.view.builder;

/**
 * Pojo resources interface for the Wave Panel xView/xViewBuilder classes.
 * Enables server and client sides to provide their own implementations.
 * 
 * @see GwtWavePanelResourceLoader.
 * 
 * @author yurize@apache.org (Yuri Zelikov)
 */
public interface WavePanelResources {
  
  /**
   * Pojo adaptor that mirrors the GWT {@link ImageResource} counterpart.
   */
  public interface WaveImageResource {
    
    /**
     * Returns the height of the image.
     */
    int getHeight();

    /**
     * Returns the horizontal position of the image within the composite image.
     */
    int getLeft();

    /**
     * Returns the vertical position of the image within the composite image.
     */
    int getTop();

    /**
     * Returns the URL for the composite image that contains the ImageResource.
     */
    String getURL();

    /**
     * Returns the width of the image.
     */
    int getWidth();

    /**
     * Return <code>true</code> if the image contains multiple frames.
     */
    boolean isAnimated();
  }

  BlipViewBuilder.Resources getBlip();

  CollapsibleBuilder.Resources getCollapsible();

  RootThreadViewBuilder.Resources getRootThread();

  ReplyBoxViewBuilder.Resources getReplyBox();

  ContinuationIndicatorViewBuilder.Resources getContinuationIndicator();

  TopConversationViewBuilder.Resources getConversation();

  ParticipantsViewBuilder.Resources getParticipants();
  
  TagsViewBuilder.Resources getTags();
}
