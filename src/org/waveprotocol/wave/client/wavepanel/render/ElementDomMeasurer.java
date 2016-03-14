/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.wave.client.wavepanel.render;

import com.google.gwt.dom.client.Element;

import org.waveprotocol.wave.client.wavepanel.view.View;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomUtil;

/**
 * ElementMeasurer implementation for DOM elements.
 * 
 * @author dkonovalchik@gmail.com (Denis Konovalchik)
 */
public class ElementDomMeasurer implements ElementMeasurer<Element> {

  public ElementDomMeasurer() {    
  }
  
  @Override
  public int getTop(Element element) {
    return element.getAbsoluteTop();
  }

  @Override
  public int getHeight(Element element) {
    if (DomUtil.doesElementHaveType(element, View.Type.PLACEHOLDER)) {
      return DomUtil.extractInteger(element.getStyle().getHeight());
    } else {
      return DomUtil.getElementHeight(element);
    }  
  }  
  
  @Override
  public int getBottom(Element element) {
    return getTop(element) + getHeight(element);
  }
}
