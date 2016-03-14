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

package org.waveprotocol.wave.client.render;


import junit.framework.TestCase;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicParticipantView.ParticipantState;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicTagView.TagState;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.conversation.testing.FakeConversationView;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.document.util.XmlStringBuilder;
import org.waveprotocol.wave.model.util.IdentityMap;
import org.waveprotocol.wave.model.util.StringMap;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Tests the equivalence between using the direct rendering mechanism (
 * {@link ReductionBasedRenderer}) versus the indirect one (
 * {@link ConversationRenderer} and {@link ReducingRendererHelper}).
 *
 */

public final class ReductionRuleRenderHelperEquivalenceTest extends TestCase {

  static class FakeRules implements RenderingRules<String> {

    @Override
    public String render(ConversationBlip blip, IdentityMap<ConversationThread, String> replies) {
      return "Document";
    }

    @Override
    public String render(ConversationBlip blip, String document,
        IdentityMap<ConversationThread, String> anchors) {
      StringBuilder r = new StringBuilder("Blip [");

      r.append(" id: " + blip.getId());
      r.append("; content: " + document);
      r.append("; inlineThreads: [");
      for (ConversationThread reply : blip.getReplyThreads()) {
        r.append(" ");
        r.append(anchors.get(reply));
      }
      r.append(" ]");

      return r.toString();
    }

    @Override
    public String render(
        ConversationThread thread, IdentityMap<ConversationBlip, String> blips, boolean renderBlips) {
      StringBuilder r = new StringBuilder("Thread [");

      r.append(" id: " + thread.getId());
      r.append("; blips: [");
      for (ConversationBlip blip : thread.getBlips()) {
        r.append(" ");
        r.append(blips.get(blip));
      }
      r.append(" ]");

      return r.toString();
    }

    @Override
    public String render(
        Conversation conversation, String participants, String thread, String tags) {
      return "Conversation [ id: " + conversation.getId() + "; participants: " + participants
          + "; tags: " + tags
          + "; thread: " + thread + " ]";
    }

    @Override
    public String render(
        Conversation conversation, ParticipantId participant, ParticipantState state, String hint) {
      return "Participant [ id: " + participant + " ]";
    }

    @Override
    public String renderTags(Conversation conversation, StringMap<String> tags) {
      StringBuilder r = new StringBuilder("Tags [");
      for (String tag : conversation.getTags()) {
        r.append(" ");
        r.append(tags.get(tag));
      }
      r.append(" ]");
      return r.toString();
    }

    @Override
    public String renderTag(Conversation conversation, String tag, TagState state, String hint) {
      return "Tag [ " + tag + " ]";
    }

    @Override
    public String render(Conversation conversation, StringMap<String> participants) {
      StringBuilder r = new StringBuilder("Participants [");
      for (ParticipantId participant : conversation.getParticipantIds()) {
        r.append(" ");
        r.append(participants.get(participant.getAddress()));
      }
      r.append(" ]");

      return r.toString();
    }

    @Override
    public String render(ConversationView wave, IdentityMap<Conversation, String> conversations) {
      return "Wave [ main: " + conversations.get(wave.getRoot()) + " ]";
    }

    @Override
    public String render(ConversationThread thread, String threadR) {
      return "Anchor [ id: " + thread.getId() + (threadR != null ? "; thread: " + threadR : "")
          + " ]";
    }

    @Override
    public String renderPlaceholder() {
      return "";
    }
  }

  private ConversationView wave;
  private FakeRules rules;
  private ConversationThread rootThread;
  private ConversationBlip rootBlip;

  @Override
  protected void setUp() {
    rules = new FakeRules();
    wave = FakeConversationView.builder().build();
    Conversation c = wave.createRoot();
    rootThread = c.getRootThread();
    rootBlip = rootThread.appendBlip();
  }

  /**
   * Only inline threads are added to the given blip.
   */
  public void testInlineReplySample() {
//    createInlineReplySample(rootBlip);
//    checkEquivalence();
  }

  /**
   * Only outline threads are added to the given blip.
   */
  public void testOutlineReplySample() {
//    createOutlineReplySample(rootBlip);
//    checkEquivalence();
  }

  /**
   * Blip with inline sample and blip with outline sample are added to the given blip.
   */
  public void testBiggerSample() {
//    createBiggerSample(rootBlip);
//    checkEquivalence();
  }

  /**
   * Bigger samples are added to the given blip.
   */
  public void testBiggestSample() {
//    createBiggestSample(rootBlip);
//    checkEquivalence();
  }

  /**
   * Biggest samples are added to the given blip.
   */
  public void testComplexSample() {
//    createComplexSample(rootThread);
//    checkEquivalence();
  }

  private void checkEquivalence() {
    String direct = ReductionBasedRenderer.of(rules, wave).render(wave);
    ReducingRendererHelper<String> adapter = ReducingRendererHelper.of(rules);
    adapter.begin();
    ConversationRenderer.renderWith(adapter, wave);
    adapter.end();
    String indirect = adapter.getResult();
    assertEquals(direct, indirect);
  }

  private static void createInlineReplySample(ConversationBlip blip) {
    sampleContent(blip.getDocument());
    ConversationThread thread = blip.addReplyThread(5);
    thread.appendBlip();
    thread.appendBlip();
  }

  private static void createOutlineReplySample(ConversationBlip blip) {
    sampleContent(blip.getDocument());
    ConversationThread thread = blip.addReplyThread();
    thread.appendBlip();
    thread.appendBlip();
  }

  private static void createBiggerSample(ConversationBlip blip) {
    ConversationThread thread = blip.addReplyThread();
    createInlineReplySample(thread.appendBlip());
    createOutlineReplySample(thread.appendBlip());
    thread.appendBlip();
  }

  private static void createBiggestSample(ConversationBlip blip) {
    ConversationThread thread = blip.addReplyThread();
    createBiggerSample(thread.appendBlip());
    createBiggerSample(thread.appendBlip());
    thread.appendBlip();
    thread.appendBlip();
  }

  private static void createComplexSample(ConversationThread root) {
    createInlineReplySample(root.appendBlip());
    root.appendBlip();
    root.appendBlip();
    createBiggerSample(root.appendBlip());
    root.appendBlip();
    root.appendBlip();
    createBiggestSample(root.appendBlip());
    root.appendBlip();
    createBiggerSample(root.appendBlip());
    createOutlineReplySample(root.appendBlip());
  }

  private static void sampleContent(Document d) {
    d.emptyElement(d.getDocumentElement());
    d.appendXml(XmlStringBuilder.createFromXmlString("<body><line></line>Hello World</body>"));
  }
}
