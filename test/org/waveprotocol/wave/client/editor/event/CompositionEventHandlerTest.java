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

package org.waveprotocol.wave.client.editor.event;

import org.mockito.InOrder;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.base.Preconditions;

import junit.framework.TestCase;

import org.waveprotocol.wave.client.common.util.JsEvents;
import org.waveprotocol.wave.client.editor.event.CompositionEventHandler.CompositionListener;
import org.waveprotocol.wave.client.scheduler.testing.FakeTimerService;
import org.waveprotocol.wave.common.logging.LoggerBundle;

/**
 * @author danilatos@google.com (Daniel Danilatos)
 *
 */

public class CompositionEventHandlerTest extends TestCase {

  private final Object eventToken = new Object();
  private final Object eventToken2 = new Object();
  private final CompositionListener<Object> listener = mockListener();
  private final FakeTimerService timer = new FakeTimerService();
  private CompositionEventHandler<Object> handler = new CompositionEventHandler<>(
      timer, listener, LoggerBundle.NOP_IMPL, true);
  private final CompositionEventHandler<Object> handler2 = new CompositionEventHandler<>(
      timer, listener, LoggerBundle.NOP_IMPL, false);

  @SuppressWarnings("unchecked")
  private CompositionListener<Object> mockListener() {
    return mock(CompositionListener.class);
  }

  @SuppressWarnings("unchecked")
  private void nothingElseUpToNow() {
    handler.checkAppComposing();
    verifyNoMoreInteractions(listener);
    reset(listener);
  }

  private void tick() {
    timer.tick(handler.compositionEndDelay + 1);
  }

  private void verifyStart(Object ... token) {
    Preconditions.checkArgument(token.length <= 1);
    verify(listener, times(1)).compositionStart(token.length == 1 ? token[0] : eventToken);
    nothingElseUpToNow();
  }
  private void verifyUpdate() {
    verify(listener, times(1)).compositionUpdate();
    nothingElseUpToNow();
  }
  private void verifyEnd() {
    verify(listener, times(1)).compositionEnd();
    nothingElseUpToNow();
  }

  private void doStartUpdateEnd() {
    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_START);
    verifyStart();

    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_UPDATE);
    verifyUpdate();

    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_END);
    nothingElseUpToNow();
  }

  private void verifyDone() {
    timer.tick(500);
    nothingElseUpToNow();
    assertFalse(handler.delayAfterTextInput);
  }

  private void doSomeMoreTestsToCheckSaneState() {
    testBasicTiming();
    testMergesInterleavedSecondCycle();
    testDoesntMergeSecondCycleAfterTextInput();
    testDoesntEndWithTextInput();
  }

  public void testBasicTiming() {
    doStartUpdateEnd();

    tick();
    verifyEnd();

    verifyDone();
  }

  public void testMergesInterleavedSecondCycle() {
    doStartUpdateEnd();

    handler.handleCompositionEvent(eventToken2, JsEvents.COMPOSITION_START);
    nothingElseUpToNow();

    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_END);
    nothingElseUpToNow();

    tick();
    verifyEnd();

    verifyDone();
  }

  public void testDoesntMergeSecondCycleAfterTextInput() {
    assertFalse(handler.delayAfterTextInput);
    doStartUpdateEnd();

    assertFalse(handler.delayAfterTextInput);
    handler.handleCompositionEvent(eventToken, JsEvents.TEXT_INPUT);
    assertTrue(handler.delayAfterTextInput);
    nothingElseUpToNow();


    handler.handleCompositionEvent(eventToken2, JsEvents.COMPOSITION_START);
    assertFalse(handler.delayAfterTextInput);
    InOrder ordered = inOrder(listener);
    ordered.verify(listener).compositionEnd();
    ordered.verify(listener).compositionStart(eventToken2);
    nothingElseUpToNow();

    handler.handleCompositionEvent(eventToken2, JsEvents.COMPOSITION_END);
    nothingElseUpToNow();

    tick();
    verifyEnd();

    verifyDone();
  }

  public void testHandlesInterleavedOtherEvent() {
    doStartUpdateEnd();

    handler.handleOtherEvent();
    verifyEnd();

    handler.handleOtherEvent();

    verifyDone();
  }

  public void testIgnoresOtherEventDuringComposition() {
    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_START);
    verifyStart();

    handler.handleOtherEvent();
    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_UPDATE);
    verifyUpdate();

    handler.handleOtherEvent();
    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_END);
    nothingElseUpToNow();

    tick();
    verify(listener).compositionEnd();

    handler.handleOtherEvent();

    verifyDone();
  }

  public void testDealsWithDupCompositionStartEvents() {
    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_START);
    verifyStart();

    handler.handleOtherEvent();
    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_UPDATE);
    verifyUpdate();

    // dup start doesn't notify
    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_START);
    nothingElseUpToNow();

    handler.handleOtherEvent();
    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_UPDATE);
    verifyUpdate();

    handler.handleOtherEvent();
    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_END);
    nothingElseUpToNow();

    tick();
    verifyEnd();

    handler.handleOtherEvent();

    doSomeMoreTestsToCheckSaneState();
  }

  public void testDealsWithDupCompositionEndEvents() {
    doStartUpdateEnd();
    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_END);
    nothingElseUpToNow();

    tick();
    verifyEnd();

    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_END);
    nothingElseUpToNow();

    doSomeMoreTestsToCheckSaneState();
  }

  public void testDoesntEndWithTextInput() {
    doStartUpdateEnd();

    handler.handleCompositionEvent(eventToken, JsEvents.TEXT_INPUT);
    assertTrue(handler.delayAfterTextInput);
    nothingElseUpToNow();

    tick();
    verifyEnd();

    // Non-composition text input is ignored
    assertFalse(handler.handleCompositionEvent(eventToken, JsEvents.TEXT_INPUT));
    assertFalse(handler.delayAfterTextInput);

    verifyDone();
  }

  // This might be the way webkit does things in the future
  public void testHandlesTextInputBeforeCompositionEnd() {
    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_START);
    verifyStart();

    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_UPDATE);
    verifyUpdate();

    assertFalse(handler.handleCompositionEvent(eventToken, JsEvents.TEXT_INPUT));
    nothingElseUpToNow();
    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_END);
    tick();
    verifyEnd();

    verifyDone();
  }

  public void testIgnoresTimerDuringComposition() {
    doStartUpdateEnd();

    handler.handleCompositionEvent(eventToken2, JsEvents.COMPOSITION_START);
    nothingElseUpToNow();

    tick();
    nothingElseUpToNow();

    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_END);
    nothingElseUpToNow();

    tick();
    verifyEnd();

    verifyDone();
  }

  public void testSimpleMode() {
    handler = handler2;
    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_START);
    verifyStart();

    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_UPDATE);
    verifyUpdate();

    handler.handleCompositionEvent(eventToken, JsEvents.COMPOSITION_END);
    verifyEnd();
    verifyDone();
  }
}
