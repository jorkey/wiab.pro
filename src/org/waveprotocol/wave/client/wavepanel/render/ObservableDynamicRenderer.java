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

import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.wave.SourcesEvents;

/**
 * Observable dynamic renderer.
 *
 * @param <T> type of the rendered element
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public interface ObservableDynamicRenderer<T>
    extends DynamicRenderer<T>, SourcesEvents<ObservableDynamicRenderer.Listener> {

  /** Render result values. */
  public enum RenderResult {
    COMPLETE, /** Everything is rendered. */
    PARTIAL, /** Something isn't rendered. */
    DATA_NEEDED /** Data for the rendering is not enough. */
  }  
  
  /**
   * Listener for rendering process.
   */
  interface Listener {

    /**
     * Called before start of dynamic rendering.
     */
    void onBeforeRenderingStarted();

    /**
     * Called before start of rendering phase.
     */
    void onBeforePhaseStarted();
    
    /**
     * Called after rendering a new blip.
     * 
     * @param blip rendered blip
     */
    void onBlipRendered(ConversationBlip blip);
    
    /**
     * Called after a new blip is ready to interact with.
     * 
     * @param blip ready blip
     */
    void onBlipReady(ConversationBlip blip);    
    
    /**
     * Called after rendering phase is finished.
     * 
     * @param result phase execution result
     */
    void onPhaseFinished(RenderResult result);
    
    /**
     * Called after finish of dynamic rendering.
     * 
     * @param result rendering result
     */
    void onRenderingFinished(RenderResult result);
  }

  /**
   * Empty implementation of listener.
   */
  public static class ListenerImpl implements Listener {

    @Override
    public void onBeforeRenderingStarted() {}

    @Override
    public void onBeforePhaseStarted() {}
    
    @Override
    public void onBlipRendered(ConversationBlip blip) {}
    
    @Override
    public void onBlipReady(ConversationBlip blip) {}

    @Override
    public void onPhaseFinished(RenderResult result) {}    
    
    @Override
    public void onRenderingFinished(RenderResult result) {}
  }
}
