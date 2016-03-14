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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ContextMenuEvent;
import com.google.gwt.event.dom.client.ContextMenuHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.event.dom.client.TouchEndEvent;
import com.google.gwt.event.dom.client.TouchEndHandler;
import com.google.gwt.event.dom.client.TouchStartEvent;
import com.google.gwt.event.dom.client.TouchStartHandler;
import com.google.gwt.user.client.ui.ComplexPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;

import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;
import org.waveprotocol.wave.client.common.util.JsEvents;
import org.waveprotocol.wave.client.common.util.LogicalPanel;
import org.waveprotocol.wave.client.uibuilder.BuilderHelper;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.StringMap;

/**
 * A panel that enables arbitrary event handling using a single DOM event
 * listener.
 * <p>
 * This panel acts as a sink for all DOM events in its subtree, with a single
 * top-level DOM event listener. Application-level event handlers register
 * themselves with this panel against elements of a particular "kind". When a
 * browser-event occurs, this panel traces up the DOM hierarchy from the source
 * of the event, locating the nearest ancestor with a kind for which an event
 * handler is registered, and dispatches the event to that handler. Dispatching
 * continues up the DOM tree to all such element/handler pairs, until this
 * panel's element is reached, or until a handler declares that propagation
 * should stop. This process is analogous to the native browser mechanism of
 * event bubbling.
 * <p>
 * This dispatch mechanism has some specific advantages and disadvantages.<br/>
 * Advantages:
 * <ul>
 * <li>since it uses only a single listener, with contextualization driven by
 * data in the DOM, it is more appropriate for a page with a server-supplied
 * rendering, since it avoids the cost of traversing the entire DOM in order to
 * hook up individual DOM event listeners;</li>
 * <li>it reduces memory overhead; and</li>
 * <li>finally, it allows UI interaction to occur in a GWT application without
 * using Widgets, which are relatively expensive and heavyweight.</li>
 * </ul>
 *  <br/> Disadvantages:
 * <ul>
 * <li>runtime dispatch cost is slower;</li>
 * <li>mixes state and control by injecting kind values into the DOM;</li>
 * <li>event-handling setup requires global context (this object), in order to
 * register event listeners, rather than being able to setup event-handling
 * directly on a Widget.</li>
 * </ul>
 *
 */
//
// Example: (not in Javadoc, because Google's auto-formatter kills it)
//
// <div onclick="handle()"> <-- 2. Bubbling brings the event to this panel
// ..<div>
// ....<div kind="blip"> <-- 3. This panel dispatches to a "blip" handler
// ......<div></div> <-- 1. Click event occurs on this element
// ....</div>
// ..</div>
// </div>
//
public final class EventDispatcherPanel extends ComplexPanel
    implements EventHandlerRegistry, LogicalPanel {

  /**
   * A collection of handlers for a particular event type. This collection
   * registers itself for GWT events, and dispatches them to registered handlers
   * based on kind.
   *
   * @param <E> event type
   * @param <W> wave handler for that event type
   */
  @VisibleForTesting
  static abstract class HandlerCollection<E, W> {
    /** Top element of the panel (where dispatch stops). */
    private final Element top;

    /** Name of the event type, for error reporting. */
    private final String typeName;

    /** Registered handlers, indexed by kind. */
    private final StringMap<W> waveHandlers = CollectionUtils.createStringMap();

    /** Optional global handler for this event type. */
    private W globalHandler;

    /** True iff this collection has registered itself for GWT events. */
    private boolean registered;

    HandlerCollection(Element top, String typeName) {
      this.top = top;
      this.typeName = typeName;
    }

    /**
     * Installs the appropriate GWT event handlers for this event type,
     * forwarding events to {@link #dispatch(Object, Element)}.
     */
    abstract void registerGwtHandler();

    /**
     * Invokes a handler with a given event.
     *
     * @param event event that occurred
     * @param context kind-annotated element associated with the event
     * @param handler kind-registered handler
     * @return true if the event should not propagate to other handlers.
     */
    abstract boolean dispatch(E event, Element context, W handler);

    /**
     * Registers an event handler for elements of a particular kind.
     *
     * @param kind element kind for which events are to be handled, or {@code
     *        null} to handle global events
     * @param handler handler for the events
     */
    void register(String kind, W handler) {
      if (kind == null) {
        // Global handler.
        if (globalHandler != null) {
          throw new IllegalStateException(
              "Feature conflict on UI: " + kind + " with event: " + typeName);
        }
        globalHandler = handler;
      } else {
        if (waveHandlers.containsKey(kind)) {
          throw new IllegalStateException(
              "Feature conflict on UI: " + kind + " with event: " + typeName);
        }
        waveHandlers.put(kind, handler);
      }
      registerInGwt();
    }

    void registerInGwt() {
      if (!registered) {
        registerGwtHandler();
        registered = true;
      }
    }

    /**
     * Dispatches an event through this handler collection.
     *
     * @param event event to dispatch
     * @param target target element of the event
     * @return true if a handled, false otherwise.
     */
    boolean dispatch(E event, Element target) {
      while (target != null) {
        if (target.getNodeType() == Node.ELEMENT_NODE &&
            target.hasAttribute(BuilderHelper.KIND_ATTRIBUTE)) {
          W handler = waveHandlers.get(target.getAttribute(BuilderHelper.KIND_ATTRIBUTE));
          if (handler != null) {
            if (dispatch(event, target, handler)) {
              return true;
            }
          }
        }
        target = !target.equals(top) ? target.getParentElement() : null;
      }
      return dispatchGlobal(event);
    }

    /**
     * Dispatches an event to the global handler for this event type.
     *
     * @param event event to dispatch
     * @return true if handled, false otherwise.
     */
    boolean dispatchGlobal(E event) {
      if (globalHandler != null) {
        return dispatch(event, top, globalHandler);
      } else {
        return false;
      }
    }
  }

  /**
   * Handler collection for click events.
   */
  private final class ClickHandlers // \u2620
      extends HandlerCollection<ClickEvent, WaveClickHandler> implements ClickHandler {
    
    ClickHandlers() {
      super(getElement(), JsEvents.CLICK);
    }

    @Override
    void registerGwtHandler() {
      addDomHandler(this, ClickEvent.getType());
    }

    @Override
    boolean dispatch(ClickEvent event, Element context, WaveClickHandler handler) {
      return handler.onClick(event, context);
    }

    @Override
    public void onClick(ClickEvent event) {
      if (dispatch(event, event.getNativeEvent().getEventTarget().<Element>cast())) {
        event.stopPropagation();
      }
    }
  }

  /**
   * Handler collection for double-click events.
   */
  private final class DoubleClickHandlers // \u2620
      extends HandlerCollection<DoubleClickEvent, WaveDoubleClickHandler>
      implements DoubleClickHandler {
    DoubleClickHandlers() {
      super(getElement(), JsEvents.DOUBLE_CLICK);
    }

    @Override
    void registerGwtHandler() {
      addDomHandler(this, DoubleClickEvent.getType());
    }

    @Override
    boolean dispatch(DoubleClickEvent event, Element context, WaveDoubleClickHandler handler) {
      return handler.onDoubleClick(event, context);
    }

    @Override
    public void onDoubleClick(DoubleClickEvent event) {
      if (dispatch(event, event.getNativeEvent().getEventTarget().<Element>cast())) {
        event.stopPropagation();
      }
    }
  }

  /**
   * Handler collection for mousedown events.
   */
  private final class MouseDownHandlers // \u2620
      extends HandlerCollection<MouseDownEvent, WaveMouseDownHandler> implements MouseDownHandler {
    MouseDownHandlers() {
      super(getElement(), JsEvents.MOUSE_DOWN);
    }

    @Override
    void registerGwtHandler() {
      addDomHandler(this, MouseDownEvent.getType());
    }

    @Override
    boolean dispatch(MouseDownEvent event, Element target) {
      //walkaround of event.getNativeButton() wrong return:
      //remember what button is pressed
      addPressedMouseButtons(event.getNativeButton());

      super.dispatch(event, target);
      return true;
    }

    @Override
    boolean dispatch(MouseDownEvent event, Element context, WaveMouseDownHandler handler) {
      return handler.onMouseDown(event, context);
    }

    @Override
    public void onMouseDown(MouseDownEvent event) {
      if (dispatch(event, event.getNativeEvent().getEventTarget().<Element>cast())) {
        event.stopPropagation();
      }
    }
  }

  /**
   * Handler collection for mouseup events.
   */
  private final class MouseUpHandlers // \u2620
      extends HandlerCollection<MouseUpEvent, WaveMouseUpHandler>
      implements MouseUpHandler {

    MouseUpHandlers() {
      super(getElement(), JsEvents.MOUSE_UP);
    }

    @Override
    void registerGwtHandler() {
      addDomHandler(this, MouseUpEvent.getType());
    }

    @Override
    boolean dispatch(MouseUpEvent event, Element target) {
      //walkaround of event.getNativeButton() wrong return:
      //remember what button is unpressed
      removePressedMouseButtons(event.getNativeButton());

      super.dispatch(event, target);
      return true;
    }

    @Override
    boolean dispatch(MouseUpEvent event, Element context, WaveMouseUpHandler handler) {
      return handler.onMouseUp(event, context);
    }

    @Override
    public void onMouseUp(MouseUpEvent event) {
      if (dispatch(event, event.getNativeEvent().getEventTarget().<Element>cast())) {
        event.stopPropagation();
      }
    }
  }

  /**
   * Handler collection for mouse move events.
   */
  private final class MouseMoveHandlers // \u2620
      extends HandlerCollection<MouseMoveEvent, WaveMouseMoveHandler>
      implements MouseMoveHandler {

    private final MouseComeLeaveHandlers mouseComeLeaveHandlers;
    private MouseMoveEvent prevEvent;
    private Element prevTarget;

    MouseMoveHandlers(MouseComeLeaveHandlers mouseComeLeaveHandlers) {
      super(getElement(), JsEvents.MOUSE_MOVE);

      this.mouseComeLeaveHandlers = mouseComeLeaveHandlers;
      prevEvent = null;
      prevTarget = null;
    }

    @Override
    void registerGwtHandler() {
      addDomHandler(this, MouseMoveEvent.getType());
    }

    @Override
    boolean dispatch(MouseMoveEvent event, Element target) {
      //firing MouseCome and MouseLeave events basing on MouseMove
      if (target != prevTarget) {
        if (prevTarget != null) {
          //create and dispatch new MouseLeaveEvent for previous context
          mouseComeLeaveHandlers.dispatch(
              new MouseLeaveEvent(prevEvent, prevTarget, target), prevTarget);
        }
        if (target != null) {
          //create and dispatch new MouseComeLeaveEvent for current context
          mouseComeLeaveHandlers.dispatch(new MouseComeEvent(event, target, prevTarget), target);
        }
        prevTarget = target;
      }
      prevEvent = event;

      super.dispatch(event, target);

      return true;
    }

    @Override
    boolean dispatch(MouseMoveEvent event, Element context, WaveMouseMoveHandler handler) {
      return handler.onMouseMove(event, context);
    }

    @Override
    public void onMouseMove(MouseMoveEvent event) {
      if (dispatch(event, event.getNativeEvent().getEventTarget().<Element>cast()) ) {
        event.stopPropagation();
      }
    }
  }

  /**
   * Handler collection for touch-start events.
   */
  private final class TouchStartHandlers // \u2620
      extends HandlerCollection<TouchStartEvent, WaveTouchStartHandler>
      implements TouchStartHandler {
    TouchStartHandlers() {
      super(getElement(), "touch-start");
    }

    @Override
    void registerGwtHandler() {
      addDomHandler(this, TouchStartEvent.getType());
    }

    @Override
    boolean dispatch(TouchStartEvent event, Element context, WaveTouchStartHandler handler) {
      return handler.onTouchStart(event, context);
    }

    @Override
    public void onTouchStart(TouchStartEvent event) {
      if (dispatch(event, event.getNativeEvent().getEventTarget().<Element>cast())) {
        event.stopPropagation();
      }
    }
  }  

  /**
   * Handler collection for touch-end events.
   */
  private final class TouchEndHandlers // \u2620
      extends HandlerCollection<TouchEndEvent, WaveTouchEndHandler>
      implements TouchEndHandler {
    TouchEndHandlers() {
      super(getElement(), "touch-end");
    }

    @Override
    void registerGwtHandler() {
      addDomHandler(this, TouchEndEvent.getType());
    }

    @Override
    boolean dispatch(TouchEndEvent event, Element context, WaveTouchEndHandler handler) {
      return handler.onTouchEnd(event, context);
    }

    @Override
    public void onTouchEnd(TouchEndEvent event) {
      if (dispatch(event, event.getNativeEvent().getEventTarget().<Element>cast())) {
        event.stopPropagation();
      }
    }
  }  
  
  /**
   * Handler collection for mouse come events.
   */
  private final class MouseComeLeaveHandlers // \u2620
      extends HandlerCollection<MouseComeLeaveEvent, WaveMouseComeLeaveHandler>
      implements MouseComeLeaveHandler {

    MouseComeLeaveHandlers() {
      super(getElement(), "mousecomeleave");
    }

    @Override
    void registerGwtHandler() {
      //the registering isn't needed because Gwt knows nothing about this event
    }

    @Override
    boolean dispatch(MouseComeLeaveEvent event, Element context, WaveMouseComeLeaveHandler handler) {
      return handler.onMouseComeLeave(event, context);
    }

    @Override
    public void onMouseComeLeave(MouseComeLeaveEvent event) {
      if (dispatch(event, event.getNativeEvent().getEventTarget().<Element>cast()) ) {
        event.stopPropagation();
      }
    }

    /**
     * The MouseComeLeave events shouldn't be dispatched globally.
     */
    @Override
    boolean dispatchGlobal(MouseComeLeaveEvent event) {
      return false;
    }
  }

  /**
   * Handler collection for context menu events.
   */
  private final class ContextMenuHandlers
      extends HandlerCollection<ContextMenuEvent, WaveContextMenuHandler>
      implements ContextMenuHandler {

    ContextMenuHandlers() {
      super(getElement(), JsEvents.CONTEXT_MENU);
    }

    @Override
    void registerGwtHandler() {
      addDomHandler(this, ContextMenuEvent.getType());
    }

    @Override
    boolean dispatch(ContextMenuEvent event, Element context, WaveContextMenuHandler handler) {
      return handler.onContextMenu(event, context);
    }

    @Override
    public void onContextMenu(ContextMenuEvent event) {
      if (dispatch(event, event.getNativeEvent().getEventTarget().<Element>cast()) ) {
        event.stopPropagation();
      }
    }
  }

  /**
   * Handler collection for change events.
   */
  private final class ChangeHandlers
      extends HandlerCollection<ChangeEvent, WaveChangeHandler> implements ChangeHandler {
    ChangeHandlers() {
      super(getElement(), JsEvents.CHANGE);
    }

    @Override
    void registerGwtHandler() {
      addDomHandler(this, ChangeEvent.getType());
    }


    @Override
    boolean dispatch(ChangeEvent event, Element context, WaveChangeHandler handler) {
      Timer timer = null;
      if (Timing.isEnabled()) {
        Timing.enterScope();
        timer = Timing.start("Mouse event dispatch");
      }
      try {
        return handler.onChange(event, context);
      } finally {
        if (timer != null) {
          Timing.stop(timer);
          Timing.exitScope();
        }
      }
    }

    @Override
    public void onChange(ChangeEvent event) {
      if (dispatch(event, event.getNativeEvent().getEventTarget().<Element>cast()) ) {
        event.stopPropagation();
      }
    }
  }

  //pressed mouse buttons support
  //walkaround of wrong MouseEvent.getNativeButton() return
  //(it returns 1 even if no buttons are pressed)
  private static int pressedMouseButtons = 0;

  public static void addPressedMouseButtons(int buttons) {
    pressedMouseButtons |= buttons;
  }

  public static void removePressedMouseButtons(int buttons) {
    pressedMouseButtons -= (pressedMouseButtons & buttons);
  }

  public static boolean areMouseButtonsPressed(int buttons) {
    return (pressedMouseButtons & buttons) == buttons;
  }

  /**
   * Creates an EventDispatcherPanel.
   */
  public static EventDispatcherPanel create() {
    return new EventDispatcherPanel(Document.get().createDivElement());
  }

  /**
   * Creates an EventDispatcherPanel on an existing element. If the element is
   * part of a larger GWT widget structure, consider see
   * {@link #inGwtContext(Element, LogicalPanel)}.
   *
   * @param element element to become the panel
   */
  public static EventDispatcherPanel of(Element element) {
    EventDispatcherPanel panel = new EventDispatcherPanel(element);
    RootPanel.detachOnWindowClose(panel);
    panel.onAttach();
    return panel;
  }

  /**
   * Creates an EventDispatcherPanel on an existing element in an existing GWT
   * widget structure.
   *
   * @param element element to be wrapped
   * @param container panel to adopt the widgetification of {@code element}
   */
  public static EventDispatcherPanel inGwtContext(Element element, LogicalPanel container) {
    Preconditions.checkArgument(container != null);
    EventDispatcherPanel panel = new EventDispatcherPanel(element);
    container.doAdopt(panel);
    return panel;
  }

  private final DoubleClickHandlers doubleClickHandlers;
  private final ClickHandlers clickHandlers;
  private final MouseDownHandlers mouseDownHandlers;
  private final MouseUpHandlers mouseUpHandlers;
  private final MouseMoveHandlers mouseMoveHandlers;
  private final MouseComeLeaveHandlers mouseComeLeaveHandlers;
  private final TouchStartHandlers touchStartHandlers;
  private final TouchEndHandlers touchEndHandlers;  
  private final ContextMenuHandlers contextMenuHandlers;
  private final ChangeHandlers changeHandlers;

  EventDispatcherPanel(Element baseElement) {
    setElement(baseElement);

    // Must construct the handler collections after calling setElement().
    doubleClickHandlers = new DoubleClickHandlers();
    clickHandlers = new ClickHandlers();
    mouseDownHandlers = new MouseDownHandlers();
    mouseUpHandlers = new MouseUpHandlers();
    mouseComeLeaveHandlers = new MouseComeLeaveHandlers();
    mouseMoveHandlers = new MouseMoveHandlers(mouseComeLeaveHandlers);
    touchStartHandlers = new TouchStartHandlers();
    touchEndHandlers = new TouchEndHandlers();
    contextMenuHandlers = new ContextMenuHandlers();
    changeHandlers = new ChangeHandlers();

    //register mouseDownHandlers and mouseUpHandlers -
    //they are needed to walkaround of event.getNativeButton() wrong return
    mouseDownHandlers.registerInGwt();
    mouseUpHandlers.registerInGwt();

    //register mouseMoveHandlers -
    //they are needed to generate MouseComeLeaveEvent
    mouseMoveHandlers.registerInGwt();
  }

  @Override
  public void registerDoubleClickHandler(String kind, WaveDoubleClickHandler handler) {
    doubleClickHandlers.register(kind, handler);
  }

  @Override
  public void registerClickHandler(String kind, WaveClickHandler handler) {
    clickHandlers.register(kind, handler);
  }

  @Override
  public void registerMouseDownHandler(String kind, WaveMouseDownHandler handler) {
    mouseDownHandlers.register(kind, handler);
  }

  @Override
  public void registerMouseUpHandler(String kind, WaveMouseUpHandler handler) {
    mouseUpHandlers.register(kind, handler);
  }

  @Override
  public void registerMouseMoveHandler(String kind, WaveMouseMoveHandler handler) {
    mouseMoveHandlers.register(kind, handler);
  }

  @Override
  public void registerMouseComeLeaveHandler(String kind, WaveMouseComeLeaveHandler handler) {
    mouseComeLeaveHandlers.register(kind, handler);
  }

  @Override
  public void registerTouchStartHandler(String kind, WaveTouchStartHandler handler) {
    touchStartHandlers.register(kind, handler);
  }

  @Override
  public void registerTouchEndHandler(String kind, WaveTouchEndHandler handler) {
    touchEndHandlers.register(kind, handler);
  }
  
  @Override
  public void registerContextMenuHandler(String kind, WaveContextMenuHandler handler) {
    contextMenuHandlers.register(kind, handler);
  }

  @Override
  public void registerChangeHandler(String kind, WaveChangeHandler handler) {
    changeHandlers.register(kind, handler);
  }

  @Override
  public void doAdopt(Widget child) {
    getChildren().add(child);
    adopt(child);
  }

  @Override
  public void doOrphan(Widget child) {
    orphan(child);
    getChildren().remove(child);
  }
}
