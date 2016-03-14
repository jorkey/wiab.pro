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
 */
package org.waveprotocol.box.server.rpc.render;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.waveprotocol.box.server.rpc.render.account.Profile;
import org.waveprotocol.box.server.rpc.render.account.ProfileManager;
import org.waveprotocol.box.server.rpc.render.common.safehtml.EscapeUtils;
import org.waveprotocol.box.server.rpc.render.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.box.server.rpc.render.renderer.RenderingRules;
import org.waveprotocol.box.server.rpc.render.renderer.ShallowBlipRenderer;
import org.waveprotocol.box.server.rpc.render.state.ThreadReadStateMonitor;
import org.waveprotocol.box.server.rpc.render.uibuilder.HtmlClosure;
import org.waveprotocol.box.server.rpc.render.uibuilder.HtmlClosureCollection;
import org.waveprotocol.box.server.rpc.render.uibuilder.UiBuilder;
import org.waveprotocol.box.server.rpc.render.view.ViewFactory;
import org.waveprotocol.box.server.rpc.render.view.ViewIdMapper;
import org.waveprotocol.box.server.rpc.render.view.builder.AnchorViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.BlipMetaViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.BlipViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.ContinuationIndicatorViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.InlineThreadViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.ParticipantAvatarViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.ParticipantsViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.ReplyBoxViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.RootThreadViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.TagViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.TagsViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.WavePanelResources;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.IdentityMap;
import org.waveprotocol.wave.model.util.IdentityMap.ProcV;
import org.waveprotocol.wave.model.util.IdentityMap.Reduce;
import org.waveprotocol.wave.model.util.StringMap;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Renders conversational objects with UiBuilders.
 *
 */
public final class FullHtmlWaveRenderer implements RenderingRules<UiBuilder> {

  public interface DocRefRenderer {
    UiBuilder render(ConversationBlip blip,
        IdentityMap<ConversationThread, UiBuilder> replies);

    public static final DocRefRenderer EMPTY = new DocRefRenderer() {
      @Override
      public UiBuilder render(ConversationBlip blip,
          IdentityMap<ConversationThread, UiBuilder> replies) {
        return UiBuilder.Constant.of(EscapeUtils.fromSafeConstant("<div></div>"));
      }
    };
  }

  public interface ParticipantsRenderer {
    UiBuilder render(Conversation c);

    ParticipantsRenderer EMPTY = new ParticipantsRenderer() {
      @Override
      public UiBuilder render(Conversation c) {
        return UiBuilder.Constant.of(EscapeUtils.fromSafeConstant("<div></div>"));
      }
    };
  }

  private final ShallowBlipRenderer blipPopulator;
  private final DocRefRenderer docRenderer;
  private final ViewIdMapper viewIdMapper;
  private final ViewFactory viewFactory;
  private final ProfileManager profileManager;
  private final ThreadReadStateMonitor readMonitor;
  private final WavePanelResources resources;
  private final String waveUri;

  public FullHtmlWaveRenderer(ShallowBlipRenderer blipPopulator,
      DocRefRenderer docRenderer, ProfileManager profileManager,
      ViewIdMapper viewIdMapper, ViewFactory viewFactory,
      ThreadReadStateMonitor readMonitor,
      WavePanelResources resources, String waveletUri) {
    this.blipPopulator = blipPopulator;
    this.docRenderer = docRenderer;
    this.profileManager = profileManager;
    this.viewIdMapper = viewIdMapper;
    this.viewFactory = viewFactory;
    this.readMonitor = readMonitor;
    this.resources = resources;
    this.waveUri = waveletUri;
  }

  @Override
  public UiBuilder renderConversations(ConversationView wave,
      IdentityMap<Conversation, UiBuilder> conversations) {
    // return the first conversation in the view.
    // TODO(hearnden): select the 'best' conversation.
    return conversations.isEmpty() ? null : getFirstConversation(conversations);
  }

  public UiBuilder getFirstConversation(IdentityMap<Conversation, UiBuilder> conversations) {
    return conversations.reduce(null, new Reduce<Conversation, UiBuilder, UiBuilder>() {
      @Override
      public UiBuilder apply(UiBuilder soFar, Conversation key, UiBuilder item) {
        // Pick the first rendering (any will do).
        return soFar == null ? item : soFar;
      }
    });
  }

  @Override
  public UiBuilder renderConversation(Conversation conversation,
  UiBuilder threadUi, UiBuilder participantsUi, UiBuilder tagsUi) {
    String id = viewIdMapper.conversationOf(conversation);
    boolean isTop = !conversation.hasAnchor();
    return isTop ?
        viewFactory.createTopConversationView(id, threadUi, participantsUi, tagsUi)
        :
        viewFactory.createInlineConversationView(id, threadUi, participantsUi);
  }

  @Override
  public UiBuilder renderParticipants(
      Conversation conversation, StringMap<UiBuilder> participantUis) {
    HtmlClosureCollection participantsUi = new HtmlClosureCollection();
    for (ParticipantId participant : conversation.getParticipantIds()) {
      participantsUi.add(participantUis.get(participant.getAddress()));
    }
    String id = viewIdMapper.participantsOf(conversation);
    return ParticipantsViewBuilder.create(resources, id, participantsUi);
  }

  @Override
  public UiBuilder renderParticipant(
      Conversation conversation, ParticipantId participant) {
    Profile profile = profileManager.getProfile(participant);
    String id = viewIdMapper.participantOf(conversation, participant);
    
    //for participant's name instead of avatar use ParticipantNameVewBuilder
    final ParticipantAvatarViewBuilder participantUi = 
      ParticipantAvatarViewBuilder.create(resources, id);
    participantUi.setAvatar(profile.getImageUrl());
    participantUi.setName(profile.getFullName());
    return participantUi;
  }

  @Override
  public UiBuilder renderThread(final ConversationThread thread,
      final IdentityMap<ConversationBlip, UiBuilder> blipUis) {
    HtmlClosure blipsUi = new HtmlClosure() {
      @Override
      public void outputHtml(SafeHtmlBuilder out) {
        for (ConversationBlip blip : thread.getBlips()) {
          UiBuilder blipUi = blipUis.get(blip);
          // Not all blips are rendered.
          if (blipUi != null) {
            blipUi.outputHtml(out);
          }
        }
      }
    };

    UiBuilder builder;    
    
    String threadId = viewIdMapper.threadOf(thread);
    if (thread.isRoot()) {
      String replyBoxId = viewIdMapper.replyBoxOf(thread);
      ReplyBoxViewBuilder replyBoxBuilder = ReplyBoxViewBuilder.create(resources, replyBoxId);
      builder = RootThreadViewBuilder.create(resources, threadId, blipsUi, replyBoxBuilder);
    } else {
      InlineThreadViewBuilder inlineBuilder = InlineThreadViewBuilder.create(resources, threadId,
          blipsUi);
      int read = readMonitor.getReadCount(thread);
      int unread = readMonitor.getUnreadCount(thread);
      inlineBuilder.setTotalBlipCount(read + unread);
      inlineBuilder.setUnreadBlipCount(unread);
      builder = inlineBuilder;
    }
    return builder;
  }

  @Override
  public UiBuilder renderBlip(final ConversationBlip blip, UiBuilder document,
      final IdentityMap<ConversationThread, UiBuilder> anchorUis,
      final IdentityMap<Conversation, UiBuilder> nestedConversations) {
    UiBuilder threadsUi = new UiBuilder() {
      @Override
      public void outputHtml(SafeHtmlBuilder out) {
        for (ConversationThread thread : blip.getReplyThreads()) {
          anchorUis.get(thread).outputHtml(out);
        }
      }
    };

    UiBuilder convsUi = new UiBuilder() {
      @Override
      public void outputHtml(SafeHtmlBuilder out) {
        // Order by conversation id. Ideally, the sort key would be creation
        // time, but that is not exposed in the conversation API.
        final List<Conversation> ordered = CollectionUtils.newArrayList();
        nestedConversations.each(new ProcV<Conversation, UiBuilder>() {
          @Override
          public void apply(Conversation conv, UiBuilder ui) {
            ordered.add(conv);
          }
        });
        Collections.sort(ordered, new Comparator<Conversation>() {
          @Override
          public int compare(Conversation o1, Conversation o2) {
            return o1.getId().compareTo(o2.getId());
          }
        });
        for (Conversation conv : ordered) {
          nestedConversations.get(conv).outputHtml(out);
        }
      }
    };
    final BlipMetaViewBuilder metaUi = BlipMetaViewBuilder.create(
        resources, viewIdMapper.metaOf(blip), document);
    metaUi.setBlipUri(waveUri + "/" + blip.getId());
    blipPopulator.render(blip, metaUi);
    
    final String continuationIndicatorId =
        viewIdMapper.continuationIndicatorOf(blip);
    final ContinuationIndicatorViewBuilder indicatorBuilder =
        ContinuationIndicatorViewBuilder.create(
        resources, continuationIndicatorId, getMainAvatarUrl());
    return BlipViewBuilder.create(resources,
        viewIdMapper.blipOf(blip), metaUi, threadsUi, convsUi, indicatorBuilder);
  }

  /**
   */
  @Override
  public UiBuilder renderNamedDocument(
      ConversationBlip blip, IdentityMap<ConversationThread, UiBuilder> replies) {
    return docRenderer.render(blip, replies);
  }

  @Override
  public UiBuilder renderDefaultAnchor(ConversationThread thread, UiBuilder threadR) {
    final String id = EscapeUtils.htmlEscape(viewIdMapper.defaultAnchorOf(thread));
    return AnchorViewBuilder.create(id, threadR);
  }
  
  @Override
  public UiBuilder renderTags(Conversation conversation,
  final StringMap<UiBuilder> tagUis) {
    final HtmlClosureCollection tagsUi = new HtmlClosureCollection();
    for (String tag : conversation.getTags()) {
        tagsUi.add(tagUis.get(tag));
    }

    final String id = viewIdMapper.tagsOf(conversation);
    return TagsViewBuilder.create(resources, id, tagsUi);
  }

  @Override
  public UiBuilder renderTag(Conversation conversation, String tag) {
    final String id = viewIdMapper.tagOf(conversation, tag);
    final TagViewBuilder tagUi = TagViewBuilder.create(resources, id);
    tagUi.setName(tag);
    return tagUi;
  }
  
  private String getMainAvatarUrl() {
    return null;
/***    
    final String mainAddress = Session.get().getAddress();
    final ParticipantId mainId = new ParticipantId(mainAddress);
    final Profile mainProfile = profileManager.getProfile(mainId);
    return mainProfile.getImageUrl();            
***/
  }  
}
