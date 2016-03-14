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

package org.waveprotocol.wave.client.wavepanel.view.dom;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.DOM;

import org.waveprotocol.wave.client.common.util.MeasurerInstance;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.client.uibuilder.BuilderHelper;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TypeCodes;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.model.util.Interval;
import org.waveprotocol.wave.model.util.Rect;

/**
 * Utilities for working with DOM objects.
 */
public class DomUtil {

  protected static LoggerBundle LOG = new DomLogger("render");  
  
  private static Boolean isTouchDevice = null;
  
  //
  // Constants
  //
  
  /** Main element id. */
  private static final String MAIN_ELEMENT_ID = "main";  
  
  /** Quasi-deleted element boolean attribute. */
  private static final String DELETED_ATTRIBUTE = "deleted";
  
  /** The "cursor" property of the element style. */
  private static final String STYLE_CURSOR_PROPERTY = "cursor";
  
  /** The "min-height" property of the element style. */
  private static final String STYLE_MIN_HEIGHT_PROPERTY = "minHeight";

  /** The "max-height" property of the element style. */
  private static final String STYLE_MAX_HEIGHT_PROPERTY = "maxHeight";
  
  /** The default (arrow-shaped) cursor. */
  private static final String STYLE_CURSOR_DEFAULT_VALUE = "default";

  /** The progress (arrow and glass-shaped) cursor. */
  private static final String STYLE_CURSOR_PROGRESS_VALUE = "progress";
  
  /** String represenation of the boolean true value. */
  private static final String BOOLEAN_TRUE_VALUE = Boolean.toString(true);
  
  //
  // Public methods
  //
  
  /**
   * Adds or removes class name to/from the element.
   * 
   * @param element
   * @param className
   * @param toAdd true - if class name should be added; false - if removed
   */
  public static void addOrRemoveClassName(Element element, String className, boolean toAdd) {
    if (toAdd) {
      element.addClassName(className);
    } else {
      element.removeClassName(className);
    }
  }

  /**
   * Sets boolean attribute to the element.
   * 
   * @param element
   * @param attribute attribute name
   * @param value the boolean value to be set to the attribute; if false, the attribute is removed
   */
  public static void setElementBooleanAttribute(Element element, String attribute, boolean value) {
    if (element != null && attribute != null) {
      if (value) {
        element.setAttribute(attribute, BOOLEAN_TRUE_VALUE);
      } else {
        element.removeAttribute(attribute);
      }  
    }
  }

  /**
   * Gets boolean value from the element.
   * 
   * @param element
   * @param attribute attribute name
   * @return true, if the attribute is found
   */
  public static boolean getElementBooleanAttribute(Element element, String attribute) {
    return element != null && attribute != null && element.hasAttribute(attribute);
  }

  /**
   * Sets quasi-deletion attribute to the element.
   * 
   * @param element 
   */
  public static void setQuasiDeleted(Element element) {
    setElementBooleanAttribute(element, DELETED_ATTRIBUTE, true);
  }

  /**
   * Checks quas-deletion attribute on the element and its parents.
   * 
   * @param element
   * @return true, if the element or some its parent has quasi-deletion attribute
   */
  public static boolean isQuasiDeleted(Element element) {
    while (element != null) {
      if (getElementBooleanAttribute(element, DELETED_ATTRIBUTE)) {
        return true;
      }
      element = element.getParentElement();
    }
    return false;
  }

  /**
   * Gets element type.
   * 
   * @param element
   * @return element type
   */  
  public static Type getElementType(Element element) {
    String typeString = getElementTypeString(element);
    if (typeString == null) {
      return null;
    }
    return TypeCodes.type(typeString);
  }  
  
  /**
   * Sets element type.
   * 
   * @param element
   * @param type 
   */
  public static void setElementType(Element element, Type type) {
    if (element != null) {
      element.setAttribute(BuilderHelper.KIND_ATTRIBUTE, TypeCodes.kind(type));
    }    
  }

  /**
   * @return true, if the element has the specified type.
   * 
   * @param element
   * @param type
   */
  public static boolean doesElementHaveType(Element element, Type type) {
    return doesElementHaveTypeString(element, TypeCodes.kind(type));
  }

  /**
   * Finds parent element with the specified type, starting with given element.
   * 
   * @param startElement
   * @param parentType
   * @return found parent element, or null, if it doesn't exist
   */
  public static Element findParentElement(Element startElement, Type parentType) {
    Element element = startElement;
    if (element != null) {
      String parentTypeString = TypeCodes.kind(parentType);
      element = element.getParentElement();
      while (element != null && !doesElementHaveTypeString(element, parentTypeString)) {
        element = element.getParentElement();
      }
    }
    return element;
  }

  public static Element findFirstChildElement(Element parent, Type childType) {
    return findChildElementExclusive(parent, TypeCodes.kind(childType), null, null, +1);
  }

  public static Element findFirstChildElement(Element parent, Type... childTypes) {
    Element element = parent;
    for (Type childType : childTypes) {
      element = findChildElementExclusive(element, TypeCodes.kind(childType), null, null, +1);
    }
    return element;
  }

  public static Element findFirstSiblingElement(Element element, Type siblingType) {
    if (element != null) {
      return findChildElementExclusive(element.getParentElement(), TypeCodes.kind(siblingType),
          element, null, +1);
    }
    return null;
  }

  public static void setMainElement(Element element) {
    element.setId(MAIN_ELEMENT_ID);    
  }
  
  /**
   * @return element with "main" id.
   */
  public static Element getMainElement() {
    return Document.get().getElementById(MAIN_ELEMENT_ID);
  }

  /**
   * @return element containing elements for blips which don't have inline parents.
   */
  public static Element getRootBlipContainer() {
    return findFirstChildElement(getMainElement(), Type.ROOT_CONVERSATION, Type.SCROLL_PANEL,
        Type.ROOT_THREAD, Type.BLIPS);
  }

  /**
   * @return height of the given element in pixels.
   * 
   * @param element the given element
   */  
  public static int getElementHeight(Element element) {
    return (int) MeasurerInstance.get().height(element);
  }
  
  /**
   * @return absolute coordinates of the element as a rectangle.
   */
  public static Rect getElementAbsoluteRect(Element element) {
    return new Rect(getElementAbsoluteHorizontalSize(element),
        getElementAbsoluteVerticalSize(element));
  }  
  
  /**
   * @return absolute horizontal size of the element.
   */
  public static Interval getElementAbsoluteHorizontalSize(Element element) {
    return Interval.create(element.getAbsoluteLeft(), element.getAbsoluteRight());
  }  
  
  /**
   * @return absolute vertical size of the element.
   */
  public static Interval getElementAbsoluteVerticalSize(Element element) {
    return Interval.create(element.getAbsoluteTop(), element.getAbsoluteBottom());
  }

  /**
   * Fixes element's height with the given value.
   */
  public static void fixElementHeight(Element element, int fixedHeight) {
    Style style = element.getStyle();
    style.setPropertyPx(STYLE_MIN_HEIGHT_PROPERTY, fixedHeight);
    style.setPropertyPx(STYLE_MAX_HEIGHT_PROPERTY, fixedHeight);
  }

  /**
   * Unfixes element's height.
   */
  public static void unfixElementHeight(Element element) {
    Style style = element.getStyle();
    style.clearProperty(STYLE_MIN_HEIGHT_PROPERTY);
    style.clearProperty(STYLE_MAX_HEIGHT_PROPERTY);
  }  
  
  /**
   * Sets default (arrow-shaped) cursor to the element.
   */
  public static void setDefaultCursor(Element element) {
    setCursor(element, STYLE_CURSOR_DEFAULT_VALUE);
  }
  
  /**
   * Sets progress (arrow with glass-shaped) cursor to the element.
   */
  public static void setProgressCursor(Element element) {
    setCursor(element, STYLE_CURSOR_PROGRESS_VALUE);
  }  

  /**
   * Puts cover element on the covered element.
   * The cover and covered elements have the same position and size.
   * 
   * @param cover the cover element
   * @param covered the covered element
   */
  public static void putCover(Element cover, Element covered) {
      Element parent = covered.getParentElement();
      Rect coveredRect = DomUtil.getElementAbsoluteRect(covered);
      Rect parentRect = DomUtil.getElementAbsoluteRect(parent);
      Style coverStyle = cover.getStyle();
      coverStyle.setPosition(Style.Position.ABSOLUTE);      
      coverStyle.setTop(coveredRect.getTop() - parentRect.getTop(), Style.Unit.PX);
      coverStyle.setLeft(coveredRect.getLeft() - parentRect.getLeft(), Style.Unit.PX);
      coverStyle.setRight(parentRect.getRight() - coveredRect.getRight(), Style.Unit.PX);
      coverStyle.setBottom(parentRect.getBottom() - coveredRect.getBottom(), Style.Unit.PX);      
      parent.appendChild(cover);    
  }
  
  /**
   * @return integer extracted from the string representation containing non-digit symbols
   */
  public static int extractInteger(String strValue) {
    String src = strValue.trim();
    String s = "";
    for (int i = 0; i < src.length(); i++) {
      char ch = src.charAt(i);
      if (ch >= '0' && ch <= '9') {
        s += src.substring(i, i+1);
      }
    }
    return s.length() > 0 ? Integer.parseInt(s) : 0;
  }  
  
  /**
   * Gets parent element on the given level.
   * 
   * @param startElement element to start with
   * @param level level of the parent to be found: 1 => parent, 2 => grandparent, and so on)
   * @return requested parent, or null, if it doesn't exist
   */
  public static Element getParentElement(Element startElement, int level) {
    Element e = startElement;
    for (int i = 0; i < level && e != null; i++) {
      e = e.getParentElement();
    }
    return e;
  }  
  
  /**
   * @return visible child containing given vertical position using binary search.
   * @param parentElement parent element
   * @param y given vertical position
   */
  public static Element findChildElementContainingY(Element parentElement, int y) {
    if (parentElement.getAbsoluteTop() > y || parentElement.getAbsoluteBottom() < y) {
      return null;
    }
    int beginIndex = 0;
    int endIndex = DOM.getChildCount(parentElement) - 1;
    while (endIndex >= beginIndex) {
      int middleIndex = beginIndex + (endIndex - beginIndex) / 2;
      Element element = DOM.getChild(parentElement, middleIndex);
      int elementTop = element.getAbsoluteTop();
      if (elementTop > y) {
        endIndex = middleIndex - 1;
        continue;
      }
      int elementBottom = element.getAbsoluteBottom();
      if (elementBottom < y) {
        beginIndex = middleIndex + 1;
        continue;
      }
      return element;
    }
    return null;
  }  
  
  /**
   * @return true, if the client device supports a touch screen.
   */
  public static boolean isTouchDevice() {
    if (isTouchDevice == null) {
      isTouchDevice = isTouchDeviceNative();
      
      if (LOG.trace().shouldLog()) {
        LOG.trace().log("DomUtil.isTouchDevice(): " + isTouchDevice);
      }
    }
    return isTouchDevice;
  }
  
  //
  // Private methods
  //

  /**
   * Gets string representation of the element type
   * 
   * @param element
   * @return string representation of the element type
   */
  private static String getElementTypeString(Element element) {
    if (element == null || !element.hasAttribute(BuilderHelper.KIND_ATTRIBUTE)) {
      return null;
    }
    return element.getAttribute(BuilderHelper.KIND_ATTRIBUTE);
  }  

  /**
   * Returns true, if the element has given type's string representation.
   * 
   * @param element
   * @param typeString
   */
  private static boolean doesElementHaveTypeString(Element element, String typeString) {
    if (element == null) {
      return false;
    }
    if (typeString == null) {
      return true;
    }
    return typeString.equals(getElementTypeString(element));
  }  
  
  /**
   * Returns the specified child element of the given element.
   * 
   * @param parent parent element
   * @param childTypeString string representaton of the child element
   * @param toExclude element to be excluded from the search
   * @param startFrom element to start search with
   * @param step step to change the child index
   */
  private static Element findChildElementExclusive(Element parent, String childTypeString,
      Element toExclude, Element startFrom, int step) {
    if (parent != null) {
      NodeList<Node> children = parent.getChildNodes();
      int begin;
      int end;
      if (step == +1) {
        begin = 0;
        end = children.getLength();
      } else {
        begin = children.getLength()-1;
        end = -1;
      }
      boolean startFound = false;
      for (int i = begin; i != end; i += step) {
        Node child = children.getItem(i);
        if (child instanceof Element) {
           Element e = (Element) child;
           if (doesElementHaveTypeString(e, childTypeString)) {
             if (e != toExclude) {
               if (startFrom != null && !startFound) {
                 startFound = true;
               } else {
                 return e;
               }
             }
           }
         }
      }
    }
    return null;
  }  
  
  /** 
   * Sets specified cursor to the element.
   * 
   * @param element
   * @param cursorName name of the cursor
   * 
   * Walkaround of Chrome bug when cursor on the element needs mouse move to be actually changed.
   * http://code.google.com/p/chromium/issues/detail?id=26723#c87
   */
  private static void setCursor(Element element, String cursorName) {
    String currentCursorName = element.getStyle().getCursor();
    if (!currentCursorName.equals(cursorName)) {
      Element wkch = Document.get().createDivElement();
      com.google.gwt.dom.client.Style wkchStyle = wkch.getStyle();
      wkchStyle.setOverflow(Style.Overflow.HIDDEN);
      wkchStyle.setPosition(Style.Position.ABSOLUTE);
      wkchStyle.setLeft(0, Style.Unit.PX);
      wkchStyle.setTop(0, Style.Unit.PX);
      wkchStyle.setWidth(100, Style.Unit.PCT);
      wkchStyle.setHeight(100, Style.Unit.PCT);

      Element wkch2 = Document.get().createDivElement();
      com.google.gwt.dom.client.Style wkch2Style = wkch2.getStyle();
      wkch2Style.setWidth(200, Style.Unit.PCT);
      wkch2Style.setHeight(200, Style.Unit.PCT);
      wkch.appendChild(wkch2);

      element.appendChild(wkch);
      element.getStyle().setProperty(STYLE_CURSOR_PROPERTY, cursorName);
      wkch.setScrollLeft(1);
      wkch.setScrollLeft(0);
      element.removeChild(wkch);
    }
  }
  
  /**
   * @return true, if the client device supports a touch screen.
   * 
   * See http://stackoverflow.com/a/14283643
   */
  private static native boolean isTouchDeviceNative() /*-{
    var isTouchDevice = function() {  return 'ontouchstart' in window || 'onmsgesturechange' in window; };
    return window.screenX == 0 || isTouchDevice() ? true : false;
  }-*/;  
}