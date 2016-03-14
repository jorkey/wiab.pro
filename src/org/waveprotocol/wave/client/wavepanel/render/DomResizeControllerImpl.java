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

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;

import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomUtil;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * DomMutationObserver implementation.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public abstract class DomResizeControllerImpl implements DomResizeController {

  /**
   * Controlled element's info.
   */
  private class ElementInfo {
    
    private final JavaScriptObject mutationObserver;
    private int previousHeight = -1;
    private Iterable<Element> independentChildren;
    
    public ElementInfo(JavaScriptObject mutationObserver) {
      this.mutationObserver = mutationObserver;
    }
    
    public JavaScriptObject getMutationObserver() {
      return mutationObserver;
    }
    
    public void setPreviousHeight(int previousHeight) {
      this.previousHeight = previousHeight;
    }
    
    public int getPreviousHeight() {
      return previousHeight;
    }
    
    public void setIndependentChildren(Iterable<Element> independentChildren) {
      this.independentChildren = independentChildren;
    }
    
    public Iterable<Element> getIndependentChildren() {
      return independentChildren;
    }

    @Override
    public String toString() {
      return "mutationObserver=" + mutationObserver +
          ", previousHeight=" + previousHeight;
    }
  }
  
  protected static LoggerBundle LOG = new DomLogger("render");  
  
  private final CopyOnWriteSet<Listener> listeners = CopyOnWriteSet.create();
  private final Map<Element, ElementInfo> elementToInfo = new HashMap<>();
  
  //
  // DomResizeController
  //
  
  @Override
  public void install(Element element) {
    assert !elementToInfo.containsKey(element);
    ElementInfo info = new ElementInfo(installMutationObserver(element));
    elementToInfo.put(element, info);
    info.setPreviousHeight(getElementPureHeight(element));
  }

  @Override
  public void uninstall(Element element) {
    ElementInfo info = elementToInfo.get(element);
    if (info != null) {
      uninstallMutationObserver(info.getMutationObserver());
      elementToInfo.remove(element);
    }  
  }

  @Override
  public void uninstall() {
    for (ElementInfo info : elementToInfo.values()) {
      uninstallMutationObserver(info.getMutationObserver());
    }
    elementToInfo.clear();
  }

  @Override
  public void clearIndependentChildren(Element element) {
    ElementInfo info = elementToInfo.get(element);
    if (info != null) {
      info.setIndependentChildren(null);
    }  
  }

  @Override
  public Iterable<Element> getIndependentChildren(Element element) {
    ElementInfo info = elementToInfo.get(element);
    assert info != null;
    Iterable<Element> independentChildren = info.getIndependentChildren();
    if (independentChildren == null) {
      independentChildren = requestIndependentChildren(element);
      info.setIndependentChildren(independentChildren);
    }    
    return independentChildren;
  }
  
  //
  // SourcesEvents
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
   * @return independent children requested for the given element from the outside object.
   * 
   * @param element the given element
   */
  protected abstract Iterable<Element> requestIndependentChildren(Element element);  
  
  //
  // Private methods
  //
  
  private native JavaScriptObject installMutationObserver(Element element) /*-{
    var _this = this;
    var target = element;
    var observer = new MutationObserver(function(mutations) {
      _this.@org.waveprotocol.wave.client.wavepanel.render.DomResizeControllerImpl::
          checkElementSize(Lcom/google/gwt/dom/client/Element;)(target);
    });
    var config = { attributes: true, childList: true, characterData: true, subtree: true }
    observer.observe(target, config);
    return observer;
  }-*/;
  
  private native void uninstallMutationObserver(JavaScriptObject observer) /*-{
    var nativeObserver = observer;
    nativeObserver.disconnect();
  }-*/;
      
  protected void checkElementSize(Element element) {
    ElementInfo info = elementToInfo.get(element);
    assert info != null;
    int previousHeight = info.getPreviousHeight();
    int height = getElementPureHeight(element);
    if (height != previousHeight) {
      triggerOnElementResized(element, previousHeight, height);
      info.setPreviousHeight(height);
    }
  }
  
  /**
   * @return height of the element without sum of its independent children's heights
   */
  private int getElementPureHeight(Element element) {
    int height = getHeight(element);
    int childHeight = 0;

    Iterator<Element> it = getIndependentChildren(element).iterator();
    while (it.hasNext()) {
      childHeight += getHeight(it.next());
    }
    height -= childHeight;
    return height;
  }
  
  private static int getHeight(Element element) {
    return DomUtil.getElementHeight(element);
  }
  
  private void triggerOnElementResized(Element element, int oldHeight, int newHeight) {
    for (Listener l : listeners) {
      l.onElementResized(element, oldHeight, newHeight);
    }
  }
}
