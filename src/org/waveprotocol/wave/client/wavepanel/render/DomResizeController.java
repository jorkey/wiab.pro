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

import org.waveprotocol.wave.model.wave.SourcesEvents;

/**
 * Controller of DOM resize events.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public interface DomResizeController extends SourcesEvents<DomResizeController.Listener> {
  
  /**
   * Starts controlling size of the given element.
   * 
   * @param element the given element
   */
  void install(Element element);
  
  /**
   * Finishes controlling size of the given element.
   * 
   * @param element the given element
   */
  void uninstall(Element element);
  
  /**
   * Finishes controlling size on all elements.
   */
  void uninstall();  
  
  /**
   * @return list of children elements whose sizes are controlled independently
   * from the given element. For blip they are inner threads.
   * 
   * @param element the given element
   */
  Iterable<Element> getIndependentChildren(Element element);  
  
  /**
   * Clears list of children elements whose sizes are controlled independently
   * from the given element.
   * 
   * @param element the given element 
   */
  void clearIndependentChildren(Element element);
  
  /**
   * Listener to observing events.
   */
  public interface Listener {
    
    /**
     * Called when the element is resized.
     * 
     * @param element the resized element
     * @param oldHeight element height before resize
     * @param newHeight element height after resize
     */
    void onElementResized(Element element, int oldHeight, int newHeight);
  }
}
