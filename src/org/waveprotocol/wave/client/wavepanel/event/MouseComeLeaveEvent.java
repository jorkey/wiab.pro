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

package org.waveprotocol.wave.client.wavepanel.event;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.event.dom.client.DomEvent;
import com.google.gwt.event.dom.client.MouseEvent;
import com.google.gwt.event.dom.client.MouseMoveEvent;

import org.waveprotocol.wave.client.wavepanel.view.View;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomUtil;

/**
 * Mouse come or leave event.
 */
public class MouseComeLeaveEvent extends MouseEvent<MouseComeLeaveHandler> {

  /**
   * Source event on which this event is based.
   */
  private MouseMoveEvent srcEvent;
  private Element contextElement;
  private Element anotherElement;

  /**
   * Event type for mouse come events. Represents the meta-data associated with
   * this event.
   */
  private static final DomEvent.Type<MouseComeLeaveHandler> TYPE =
      new DomEvent.Type<>("mousecomeleave", new MouseComeLeaveEvent());

  public MouseComeLeaveEvent() {
    srcEvent = null;
  }

  /**
   * Creates new mouse come event basing on the source mouse move event.
   */
  public MouseComeLeaveEvent(MouseMoveEvent srcEvent, Element contextElement,
      Element anotherElement) {
    this.srcEvent = srcEvent;
    this.contextElement = contextElement;
    this.anotherElement = anotherElement;
  }

  /**
   * Gets the event type associated with mouse come events.
   *
   * @return the handler type
   */
  public static DomEvent.Type<MouseComeLeaveHandler> getType() {
    return TYPE;
  }

  @Override
  public final DomEvent.Type<MouseComeLeaveHandler> getAssociatedType() {
    return TYPE;
  }

  /**
   * Gets the target to which the mouse pointer was moved.
   *
   * @return the target to which the mouse pointer was moved
   */
  public EventTarget getRelatedTarget() {
    return getNativeEvent().getRelatedEventTarget();
  }

  @Override
  protected void dispatch(MouseComeLeaveHandler handler) {
    handler.onMouseComeLeave(this);
  }

  public boolean isCome() {
    return false;
  }

  public boolean isLeave() {
    return false;
  }

  @Override
  public int getClientX() {
    return srcEvent.getClientX();
  }

  @Override
  public int getClientY() {
    return srcEvent.getClientY();
  }

  @Override
  public int getNativeButton() {
    return srcEvent.getNativeButton();
  }

  @Override
  public int getRelativeX(Element target) {
    return srcEvent.getRelativeX(target);
  }

  @Override
  public int getRelativeY(Element target) {
    return srcEvent.getRelativeY(target);
  }

  @Override
  public int getScreenX() {
    return srcEvent.getScreenX();
  }

  @Override
  public int getScreenY() {
    return srcEvent.getScreenY();
  }

  @Override
  public int getX() {
    return srcEvent.getX();
  }

  @Override
  public int getY() {
    return srcEvent.getY();
  }

  public Element getContextElement() {
    return contextElement;
  }

  public View.Type getContextElementType() {
    return DomUtil.getElementType(contextElement);
  }

  public Element getAnotherElement() {
    return anotherElement;
  }

  public View.Type getAnotherElementType() {
    return DomUtil.getElementType(anotherElement);
  }
}