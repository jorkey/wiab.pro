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
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversation;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationView;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationViewImpl;
import org.waveprotocol.wave.model.conversation.testing.FakeConversationView;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.supplement.ScreenPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

/**
 * Tests the DynamicRenderer object and its successors.
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class DynamicRendererTest extends TestCase {

  ObservableQuasiConversationView conversationView;
  ObservableQuasiConversation conversation;
  ConversationNavigator navigator;
  DynamicTestRenderer renderer;

  private final Map<String, String> blipIdToNames = new HashMap<>();
  private final Map<String, String> threadIdToNames = new HashMap<>();

  private enum StartPosition {
    BEGIN,
    MIDDLE,
    END
  }

  @Override
  protected void setUp() {
    FakeConversationView baseConversationView = FakeConversationView.builder().build();
    conversationView = QuasiConversationViewImpl.create(null);
    conversationView.initialize(baseConversationView, null);
    conversation = conversationView.createRoot();
    navigator = ConversationNavigator.create(conversationView);
  }

  // Tests for dynamic rendering completeness.

  public void testRootRenderingCompleteness() {
    createRootSample();
    checkRenderingCompleteness();
  }

  public void testOutlineRenderingCompleteness() {
    createOutlineSample();
    checkRenderingCompleteness();
  }

  public void testInlineRenderingCompleteness() {
    createInlineSample();
    checkRenderingCompleteness();
  }

  public void testRecursiveRenderingCompleteness() {
    createRecursiveSample();
    checkRenderingCompleteness();
  }

  // Tests for dynamic rendering order.

  public void testRootRenderingOrder() {
    createRootSample();
    checkRenderingOrder(StartPosition.BEGIN, "A B C D E F G H I J K");
    checkRenderingOrder(StartPosition.MIDDLE, "By Bx Bz Ca Cb Cc Cd Ce Cf Cg Ch Ci");
    checkRenderingOrder(StartPosition.END, "Dv Du Dt Ds Dr Dq Dp Do Dn Dm Dl");
  }

  public void testOutlineRenderingOrder() {
    createOutlineSample();
    checkRenderingOrder(StartPosition.BEGIN, "A AA AB AC AD AE AF AG AH");
    checkRenderingOrder(StartPosition.MIDDLE, "F EJ FA FB FC FD FE FF FG FH");
    checkRenderingOrder(StartPosition.END, "J JJ JI JH JG JF JE JD JC JB");
  }

  public void testInlineRenderingOrder() {
    createInlineSample();
    checkRenderingOrder(StartPosition.BEGIN, "A A1A A1B A1C A1D A1E A2A A2B A2C A2D A2E");
    checkRenderingOrder(StartPosition.MIDDLE, "C C3C C3B C3D C3E C4A C4B C4C C4D C4E C5A C5B");
    checkRenderingOrder(StartPosition.END, "E E4E E4D E4C E4B E4A E5A E5B E5C E5D E5E");
  }

  public void testRecursiveRenderingOrder() {
    createRecursiveSample();
    checkRenderingOrder(StartPosition.BEGIN,
        "A A1A A1A1A A1A1A1A A1A1A1B A1A1A2A A1A1A2B A1A1AA A1A1AB A1A1B A1A1B1A");
    checkRenderingOrder(StartPosition.MIDDLE,
        "B ABBB B1A B1A1A B1A1A1A B1A1A1B B1A1A2A B1A1A2B B1A1AA B1A1AB B1A1B B1A1B1A");
    checkRenderingOrder(StartPosition.END, "B BB BBB BBBB BBAB BBB1A BBB1B BBB2A BBB2B BBBA");
  }

  // Tests for dynamic conversation change processing

  public void testRootChangeProcessing() {
    createRootSample();
    checkChangeProcessing();
  }

  public void testOutlineChangeProcessing() {
    createOutlineSample();
    checkChangeProcessing();
  }

  public void testInlineChangeProcessing() {
    createInlineSample();
    checkChangeProcessing();
  }

  public void testRecursiveChangeProcessing() {
    createRecursiveSample();
    checkChangeProcessing();
  }

  // Private methods

  private void createRootSample() {
    final int BLIP_COUNT = 100;

    ConversationThread rootThread = conversation.getRootThread();
    for (int i = 0; i < BLIP_COUNT; i++) {
      ConversationBlip blip = rootThread.appendBlip();
      fillBlipWithContent(blip);
    }
  }

  private void createOutlineSample() {
    final int ROOT_BLIP_COUNT = 10;
    final int CHILD_BLIP_COUNT = 10;

    ConversationThread rootThread = conversation.getRootThread();
    for (int i = 0; i < ROOT_BLIP_COUNT; i++) {
      ConversationBlip blip = rootThread.appendBlip();
      fillBlipWithContent(blip);
      ConversationThread outlineThread = blip.addReplyThread();
      for (int j = 0; j < CHILD_BLIP_COUNT; j++) {
        outlineThread.appendBlip();
      }
    }
  }

  private void createInlineSample() {
    final int ROOT_BLIP_COUNT = 5;
    final int INLINE_THREAD_COUNT = 5;
    final int CHILD_BLIP_COUNT = 5;

    ConversationThread rootThread = conversation.getRootThread();
    for (int i = 0; i < ROOT_BLIP_COUNT; i++) {
      ConversationBlip blip = rootThread.appendBlip();
      fillBlipWithContent(blip);
      for (int j = 0; j < INLINE_THREAD_COUNT; j++) {
        ConversationThread inlineThread = blip.addReplyThread(blip.getDocument().size()-1);
        for (int k = 0; k < CHILD_BLIP_COUNT; k++) {
          ConversationBlip childBlip = inlineThread.appendBlip();
          fillBlipWithContent(childBlip);
        }
      }
    }
  }

  private void createRecursiveSample() {
    final int ROOT_BLIP_COUNT = 2;

    ConversationThread rootThread = conversation.getRootThread();
    for (int i = 0; i < ROOT_BLIP_COUNT; i++) {
      createBlipForRecursiveSample(0, rootThread);
    }
  }

  private void createBlipForRecursiveSample(int level, ConversationThread parentThread) {
    final int LEVEL_COUNT = 3;
    final int INLINE_THREAD_COUNT = 2;
    final int CHILD_BLIP_COUNT = 2;

    ConversationBlip blip = parentThread.appendBlip();
    fillBlipWithContent(blip);
    if (level < LEVEL_COUNT) {
      for (int j = 0; j < INLINE_THREAD_COUNT; j++) {
        ConversationThread inlineThread = blip.addReplyThread(blip.getDocument().size()-1);
        for (int k = 0; k < CHILD_BLIP_COUNT; k++) {
          createBlipForRecursiveSample(level + 1, inlineThread);
        }
      }
      ConversationThread outlineThread = blip.addReplyThread();
      for (int k = 0; k < CHILD_BLIP_COUNT; k++) {
        createBlipForRecursiveSample(level + 1, outlineThread);
      }
    }
  }

  private void checkRenderingCompleteness() {
    renderer = DynamicTestRenderer.create(0.0, navigator);

    renderer.setScreenDimension(200, 500);
    renderer.init(conversationView);

    renderer.startRendering(null);
    assertTrue(renderer.getPlaceholdersToRender().isEmpty());

    renderer.scrollToEnd();
    renderer.dynamicRendering();
    assertTrue(renderer.getPlaceholdersToRender().isEmpty());
  }

  private void checkRenderingOrder(StartPosition position, String correctRenderingOrder) {

    renderer = DynamicTestRenderer.create(0.0, navigator);
    renderer.setScreenDimension(200, 500);
    renderer.init(conversationView);

    String startBlipId = null;
    switch (position) {
      case BEGIN:   startBlipId = IdConstants.FIRST_BLIP_ID;  break;
      case MIDDLE:  startBlipId = getMiddleBlipId();          break;
      case END:     startBlipId = IdConstants.LAST_BLIP_ID;   break;
    }
    ScreenPositionScrollerImpl sps = ScreenPositionScrollerImpl.create(renderer,
        renderer.getScroller(), renderer.getElementMeasurer());
    sps.initialize(new ScreenPosition(startBlipId), conversationView);
    renderer.startRendering(startBlipId);

    List<ConversationBlip> renderedBlips = renderer.getRenderedBlipList();
    String renderingOrder = blipListToNames(renderedBlips);
    assertEquals(correctRenderingOrder, renderingOrder);
    assertEquals(true, checkZIndexesDescending(conversation.getRootThread()));
  }

  private void checkChangeProcessing() {
    renderer = DynamicTestRenderer.create(0.0, navigator);
    renderer.setScreenDimension(200, 500);
    renderer.init(conversationView);
    renderer.startRendering(null);

    ConversationThread rootThread = conversationView.getRoot().getRootThread();
    ConversationBlip newBeginBlip = rootThread.insertBlip(rootThread.getFirstBlip(), true);
    renderer.dynamicRendering();

    ConversationBlip newEndBlip = rootThread.insertBlip(navigator.getLastBlip(rootThread), false);
    renderer.dynamicRendering();

    assertTrue(renderer.isRendered(newBeginBlip));
    assertFalse(renderer.isRendered(newEndBlip));

    newBeginBlip.delete();
    assertFalse(renderer.isRendered(newBeginBlip));

    renderer.scrollToEnd();
    renderer.dynamicRendering();
    assertTrue(renderer.isRendered(newEndBlip));

    newEndBlip.delete();
    assertFalse(renderer.isRendered(newEndBlip));
  }

  private void fillBlipWithContent(ConversationBlip blip) {
    blip.getDocument().insertText(blip.getDocument().size() - 1, "Blip " + getBlipName(blip));
  }

  private String blipListToNames(List<ConversationBlip> blips) {
    String s = "";
    boolean isFirst = true;
    for (ConversationBlip blip : blips) {
      if (isFirst) {
        isFirst = false;
      } else {
        s += " ";
      }
      s += getBlipName(blip);
    }
    return s;
  }

  private String getMiddleBlipId() {
    List<ConversationBlip> blips = new ArrayList<>();
    for (ConversationBlip blip = navigator.getFirstBlip(conversation.getRootThread()); blip != null;
        blip = navigator.getNextBlip(blip)) {
      blips.add(blip);
    }
    return blips.size() > 0 ? blips.get(blips.size() / 2).getId() : null;
  }

  private String getBlipName(ConversationBlip blip) {
    String blipId = blip.getId();
    String blipName = blipIdToNames.get(blipId);
    if (blipName != null) {
      return blipName;
    }
    ConversationThread parentThread = blip.getThread();
    String s = getThreadName(parentThread) + indexToLetters(navigator.getIndexOfBlipInParentThread(blip));
    blipIdToNames.put(blipId, s);
    return s;
  }

  private String getThreadName(ConversationThread thread) {
    if (thread.isRoot()) {
      return "";
    }

    String threadId = thread.getId();
    String threadName = threadIdToNames.get(threadId);
    if (threadName != null) {
      return threadName;
    }

    ConversationBlip parentBlip = thread.getParentBlip();
    String s = getBlipName(parentBlip);
    if (thread.isInline()) {
      Iterator<? extends ConversationThread> it = parentBlip.getReplyThreads().iterator();
      int index = 1;
      while (it.hasNext()) {
        ConversationThread t = it.next();
        if (t == thread) {
          s += index;
          break;
        }
        index++;
      }
    }
    threadIdToNames.put(threadId, s);
    return s;
  }

  private static String indexToLetters(int index) {
    int n = index;
    String s = "";
    do {
      int letterIndex = n % 26;
      char[] chars = Character.toChars('a' + letterIndex);
      s = chars[0] + s;
      n /= 26;
    } while (n > 0);
    s = capitalize(s.substring(0, 1).toUpperCase() + s.substring(1));
    return s;
  }

  private static String capitalize(String s) {
    String ss = "";
    if (s.length() > 0) {
      ss += s.substring(0, 1).toUpperCase();
      if (s.length() > 1) {
        ss += s.substring(1).toLowerCase();
      }
    }
    return ss;
  }

  private boolean checkZIndexesDescending(ConversationThread rowOwnerThread) {
    int prevZIndex = -1;
    for (ConversationBlip blip = navigator.getFirstBlip(rowOwnerThread);
        blip != null; blip = navigator.getNextBlipInRow(blip)) {
      TestElement blipElement = renderer.getElementByBlip(blip);
      if (blipElement != null) {
        int zIndex = blipElement.getZIndex();
        if (prevZIndex >= 0 && prevZIndex <= zIndex) {
          return false;
        }
        for (ConversationThread reply : blip.getReplyThreads()) {
          if (reply.isInline() && !checkZIndexesDescending(reply)) {
            return false;
          }
        }
        prevZIndex = zIndex;
      }
    }
    return true;
  }
}