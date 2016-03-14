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

import org.waveprotocol.wave.client.account.ProfileManager;
import org.waveprotocol.wave.client.common.safehtml.EscapeUtils;
import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.render.RenderingRules;
import org.waveprotocol.wave.client.uibuilder.HtmlClosure;
import org.waveprotocol.wave.client.uibuilder.HtmlClosureCollection;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicParticipantView.ParticipantState;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicTagView.TagState;
import org.waveprotocol.wave.client.wavepanel.view.ViewIdMapper;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.AnchorViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipMetaViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.ContinuationIndicatorViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.InlineThreadViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.OutlineThreadViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.ParticipantAvatarViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.ParticipantsViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.PlaceholderViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.ReplyBoxViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.RootThreadViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TagViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TagsViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.ViewFactory;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.util.IdentityMap;
import org.waveprotocol.wave.model.util.IdentityMap.Reduce;
import org.waveprotocol.wave.model.util.StringMap;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Renders conversational objects with UiBuilders.
 */
public final class FullDomRenderer implements RenderingRules<UiBuilder> {

  private final ViewIdMapper viewIdMapper;
  private final ViewFactory viewFactory;
  private final ProfileManager profileManager;

  public FullDomRenderer(
      ProfileManager profileManager,
      ViewIdMapper viewIdMapper,
      ViewFactory viewFactory) {
    this.profileManager = profileManager;
    this.viewIdMapper = viewIdMapper;
    this.viewFactory = viewFactory;
  }

  @Override
  public UiBuilder render(final ConversationBlip blip, UiBuilder document,
      final IdentityMap<ConversationThread, UiBuilder> anchorUis) {
    UiBuilder threadsUi = new UiBuilder() {

      @Override
      public void outputHtml(SafeHtmlBuilder out) {
        for (ConversationThread thread : blip.getReplyThreads()) {
          if (thread.isInline()) {
            anchorUis.get(thread).outputHtml(out);
          }  
        }
      }
    };

    BlipMetaViewBuilder metaUi = BlipMetaViewBuilder.create(viewIdMapper.metaOf(blip), document);
    return BlipViewBuilder.create(viewIdMapper.blipOf(blip), metaUi, threadsUi);
  }

  @Override
  public UiBuilder render(final ConversationBlip blip, IdentityMap<ConversationThread,
      UiBuilder> replies) {
    return new UiBuilder() {
      @Override
        public void outputHtml(SafeHtmlBuilder out) {
          // Documents are rendered blank, and filled in later when they get paged in.
          out.append(EscapeUtils.fromSafeConstant("<div></div>"));
      }
    };
  }

  @Override
  public UiBuilder render(ConversationThread thread, UiBuilder threadR) {
    String id = EscapeUtils.htmlEscape(viewIdMapper.defaultAnchorOf(thread));
    return AnchorViewBuilder.create(id, threadR);
  }

  @Override
  public UiBuilder render(
      ConversationView wave, IdentityMap<Conversation, UiBuilder> conversations) {
    // return the first conversation in the view.
    // TODO(hearnden): select the 'best' conversation.
    return conversations.isEmpty() ? null : getFirstConversation(conversations);
  }

  @Override
  public UiBuilder render(Conversation conversation, UiBuilder participants,
      UiBuilder thread, UiBuilder tags) {
    String id = viewIdMapper.conversationOf(conversation);
    return !conversation.hasAnchor()
        ? viewFactory.createTopConversationView(id, participants, thread, tags)
        : viewFactory.createInlineConversationView(id, participants, thread);
  }

  @Override
  public UiBuilder render(Conversation conversation,
      StringMap<UiBuilder> participantMap) {
    HtmlClosureCollection participantCollection = new HtmlClosureCollection();
    for (ParticipantId participant : conversation.getParticipantIds()) {
      participantCollection.add(participantMap.get(participant.getAddress()));
    }
    return ParticipantsViewBuilder.create(
        viewIdMapper.participantsOf(conversation), participantCollection);
  }

  @Override
  public UiBuilder render(Conversation conversation, ParticipantId participant,
      ParticipantState state, String hint) {
    ParticipantAvatarViewBuilder participantUi = ParticipantAvatarViewBuilder.create(
        viewIdMapper.participantOf(conversation, participant), participant.getAddress(),
        state, hint);
    participantUi.setAvatar(profileManager.getProfile(participant).getImageUrl());
    return participantUi;
  }

  @Override
  public UiBuilder render(final ConversationThread thread, final IdentityMap<ConversationBlip,
      UiBuilder> blipUis, final boolean renderBlips) {
    HtmlClosure blipsUi;
    if (renderBlips) {
      blipsUi = new HtmlClosure() {
        @Override
        public void outputHtml(SafeHtmlBuilder out) {
          if (renderBlips) {
            for (ConversationBlip blip : thread.getBlips()) {
              UiBuilder blipUi = blipUis.get(blip);
              // Not all blips are rendered.
              if (blipUi != null) {
                blipUi.outputHtml(out);
              }
            }
          }
        }
      };
    } else {
      boolean isEmpty = thread.getFirstBlip() == null;
      blipsUi = isEmpty ? HtmlClosure.EMPTY : PlaceholderViewBuilder.create();
    }

    UiBuilder builder;
    String threadId = viewIdMapper.threadOf(thread);
    if (thread.isRoot()) {
      // root thread
      String replyBoxId = viewIdMapper.replyBoxOf(thread);
      ReplyBoxViewBuilder replyBoxViewBuilder = ReplyBoxViewBuilder.create(replyBoxId);
      ContinuationIndicatorViewBuilder continuationIndicatorViewBuilder =
          ContinuationIndicatorViewBuilder.create();
      builder = RootThreadViewBuilder.create(
          threadId, blipsUi, replyBoxViewBuilder, continuationIndicatorViewBuilder);
    } else if (thread.isInline()) {
      builder = InlineThreadViewBuilder.create(threadId, blipsUi);
    } else {
      builder = OutlineThreadViewBuilder.create(threadId, blipsUi);
    }
    return builder;
  }

  @Override
  public UiBuilder renderTags(Conversation conversation, final StringMap<UiBuilder> tagMap) {
    HtmlClosureCollection tagCollection = new HtmlClosureCollection();
    for (String tag : conversation.getTags()) {
      tagCollection.add(tagMap.get(tag));
    }

    String id = viewIdMapper.tagsOf(conversation);
    return TagsViewBuilder.create(id, tagCollection);
  }

  @Override
  public UiBuilder renderTag(Conversation conversation, String tag, TagState state, String hint) {
    String id = viewIdMapper.tagOf(conversation, tag);
    return TagViewBuilder.create(id, tag, state, hint);
  }

  @Override
  public UiBuilder renderPlaceholder() {
    return PlaceholderViewBuilder.create(); ///return PlaceholderViewBuilder.create(true);
  }

  private UiBuilder getFirstConversation(IdentityMap<Conversation, UiBuilder> conversations) {
    return conversations.reduce(null, new Reduce<Conversation, UiBuilder, UiBuilder>() {
      @Override
      public UiBuilder apply(UiBuilder soFar, Conversation key, UiBuilder item) {
        // Pick the first rendering (any will do).
        return soFar == null ? item : soFar;
      }
    });
  }
}
