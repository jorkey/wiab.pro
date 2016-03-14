/**
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package org.waveprotocol.box.server.rpc.render.renderer;

import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationStructure;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.IdentityMap;
import org.waveprotocol.wave.model.util.StringMap;
import org.waveprotocol.wave.model.wave.ParticipantId;


/**
 * Builds a rendering of conversations in a wave, by folding together renderings
 * from production rules in {@link RenderingRules}.
 *
 * @see ConversationRenderer for an alternative (SAX) style of producing a
 *      rendering.
 */
public final class ReductionBasedRenderer<R>
    implements WaveRenderer<R> {

  /** Nesting conversationStructure of conversations. */
  private final ConversationStructure conversationStructure;

  /** Production rules. */
  private final RenderingRules<R> renderingRules;

  /** Creates a rendering builder. */
  private ReductionBasedRenderer(
      RenderingRules<R> renderingRules,
      ConversationStructure conversationStructure) {
    this.renderingRules = renderingRules;
    this.conversationStructure = conversationStructure;
  }

  /** @return a renderer of {@code wave}, using {@code renderingRules}. */
  public static <R> ReductionBasedRenderer<R> of(
      RenderingRules<R> builders, ConversationView wave) {
    return new ReductionBasedRenderer<R>(
        builders, ConversationStructure.of(wave));
  }

  @Override
  public R renderWave(ConversationView wave) {
    IdentityMap<Conversation, R> conversations =
        CollectionUtils.createIdentityMap();
    Conversation c = conversationStructure.getMainConversation();
    if (c != null) {
      conversations.put(c, renderConversation(c));
    }
    return renderingRules.renderConversations(wave, conversations);
  }

  @Override
  public R renderConversation(Conversation conversation) {
    StringMap<R> participants = CollectionUtils.createStringMap();
    for (ParticipantId participant : conversation.getParticipantIds()) {
      participants.put(participant.getAddress(),
          renderParticipant(conversation, participant));
    }

    StringMap<R> tags = CollectionUtils.createStringMap();
    for (String tag : conversation.getTags()) {
        tags.put(tag, renderTag(conversation, tag));
    }

    return renderingRules.renderConversation(conversation,
        renderInner(conversation.getRootThread()),
        renderingRules.renderParticipants(conversation, participants),
        renderingRules.renderTags(conversation, tags));
  }

  @Override
  public R renderParticipant(
      Conversation conversation, ParticipantId participant) {
    return renderingRules.renderParticipant(conversation, participant);
  }

  @Override
  public R renderTag(Conversation conversation, String tag) {
    return renderingRules.renderTag(conversation, tag);
  }

  /** @return the rendering of {@code thread}, without a surrounding anchor. */
  private R renderInner(ConversationThread thread) {
    IdentityMap<ConversationBlip, R> blips = null;
    for (ConversationBlip blip : thread.getBlips()) {
      if (blips == null) {
        blips = CollectionUtils.createIdentityMap();
      }
      blips.put(blip, renderBlip(blip));
    }
    return renderingRules.renderThread(thread, nonNull(blips));
  }

  @Override
  public R renderThread(ConversationThread thread) {
    return renderingRules.renderDefaultAnchor(thread, renderInner(thread));
  }

  @Override
  public R renderBlip(ConversationBlip blip) {
    // Threads.
    IdentityMap<ConversationThread, R> threadRs = null;
    for (ConversationThread reply : blip.getReplyThreads()) {
      if (threadRs == null) {
        threadRs = CollectionUtils.createIdentityMap();
      }
      threadRs.put(reply, renderInner(reply));
    }
    threadRs = nonNull(threadRs);

    // Nested conversations.
    IdentityMap<Conversation, R> nestedRs = null;
    for (Conversation conversation :
        conversationStructure.getAnchoredConversations(blip)) {
      if (nestedRs == null) {
        nestedRs = CollectionUtils.createIdentityMap();
      }
      nestedRs.put(conversation, renderConversation(conversation));
    }
    nestedRs = nonNull(nestedRs);

    // Document.
    R documentR = renderingRules.renderNamedDocument(blip, threadRs);

    // Default-anchored threads.
    IdentityMap<ConversationThread, R> defaultAnchorRs = null;
    for (ConversationThread reply : blip.getReplyThreads()) {
      if (defaultAnchorRs == null) {
        defaultAnchorRs = CollectionUtils.createIdentityMap();
      }
      defaultAnchorRs.put(reply, renderingRules.renderDefaultAnchor(
          reply, threadRs.get(reply)) );
    }
    defaultAnchorRs = nonNull(defaultAnchorRs);

    // Render blip.
    return renderingRules.renderBlip(blip, documentR, defaultAnchorRs, nestedRs);
  }

  private static <K, V> IdentityMap<K, V> nonNull(IdentityMap<K, V> source) {
    return source != null ? source : CollectionUtils.<K, V>emptyIdentityMap();
  }
}
