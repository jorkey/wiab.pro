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

import org.waveprotocol.wave.client.wavepanel.view.IntrinsicParticipantView.ParticipantState;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicTagView.TagState;
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
public final class ReductionBasedRenderer<R> implements WaveRenderer<R> {

  /** Nesting structure of conversations. */
  private final ConversationStructure structure;

  /** Production rules. */
  private final RenderingRules<R> builders;

  /** Creates a rendering builder. */
  private ReductionBasedRenderer(RenderingRules<R> builders, ConversationStructure structure) {
    this.builders = builders;
    this.structure = structure;
  }

  /** @return a renderer of {@code wave}, using {@code builders}. */
  public static <R> ReductionBasedRenderer<R> of(
      RenderingRules<R> builders, ConversationView wave) {
    return new ReductionBasedRenderer<R>(builders, ConversationStructure.of(wave));
  }

  @Override
  public R render(ConversationView wave) {
    IdentityMap<Conversation, R> conversations = CollectionUtils.createIdentityMap();
    Conversation c = structure.getMainConversation();
    if (c != null) {
      conversations.put(c, render(c));
    }
    return builders.render(wave, conversations);
  }

  @Override
  public R render(Conversation conversation) {
    R participants = renderParticipants(conversation);
    R thread = renderRootThread(conversation.getRootThread());
    R tags = renderTags(conversation);

    return builders.render(conversation, participants, thread, tags);
  }

  @Override
  public R render(Conversation conversation, ParticipantId participant, ParticipantState state,
      String hint) {
    return builders.render(conversation, participant, state, hint);
  }

  private R renderInner(ConversationThread thread) {
    return builders.render(thread, CollectionUtils.<ConversationBlip, R>emptyIdentityMap(), false);
  }

  @Override
  public R render(ConversationThread thread) {
    return builders.render(thread, renderInner(thread));
  }

  @Override
  public R render(ConversationBlip blip) {
    // Inline threads
    IdentityMap<ConversationThread, R> inlineThreadRs = null;
    for (ConversationThread reply : blip.getReplyThreads()) {
      if (reply.isInline()) {
        if (inlineThreadRs == null) {
          inlineThreadRs = CollectionUtils.createIdentityMap();
        }
        inlineThreadRs.put(reply, renderInner(reply));
      }  
    }
    inlineThreadRs = nonNull(inlineThreadRs);

    // Document
    R documentR = builders.render(blip, inlineThreadRs);

    // Anchors for inline threads
    IdentityMap<ConversationThread, R> anchorRs = null;
    for (ConversationThread reply : blip.getReplyThreads()) {
      if (reply.isInline()) {
        if (anchorRs == null) {
          anchorRs = CollectionUtils.createIdentityMap();
        }
        anchorRs.put(reply, builders.render(reply, inlineThreadRs.get(reply)) );
      }  
    }
    anchorRs = nonNull(anchorRs);

    // Render blip
    return builders.render(blip, documentR, anchorRs);
  }

  private static <K, V> IdentityMap<K, V> nonNull(IdentityMap<K, V> source) {
    return source != null ? source : CollectionUtils.<K, V>emptyIdentityMap();
  }

  @Override
  public R renderParticipants(Conversation conversation) {
    StringMap<R> participants = CollectionUtils.createStringMap();
    for (ParticipantId participant : conversation.getParticipantIds()) {
      String name = participant.getAddress();
      participants.put(name, render(conversation, participant, ParticipantState.NORMAL, null));
    }
    return builders.render(conversation, participants);
  }

  @Override
  public R renderRootThread(ConversationThread thread) {
    return renderInner(thread);
  }

  @Override
  public R renderTags(Conversation conversation) {
    StringMap<R> tags = CollectionUtils.createStringMap();
    for (String tag : conversation.getTags()) {
      tags.put(tag, render(conversation, tag, TagState.NORMAL, null));
    }
    return builders.renderTags(conversation, tags);
  }

  @Override
  public R render(Conversation conversation, String tag, TagState state, String hint) {
    return builders.renderTag(conversation, tag, state, hint);
  }

  @Override
  public R renderPlaceholder() {
    return builders.renderPlaceholder();
  }
}
