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

package org.waveprotocol.wave.client.wavepanel.view.fake;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.Map;
import org.waveprotocol.wave.client.common.util.LinkedSequence;
import org.waveprotocol.wave.client.render.ReductionBasedRenderer;
import org.waveprotocol.wave.client.render.RenderingRules;
import org.waveprotocol.wave.client.render.WaveRenderer;
import org.waveprotocol.wave.client.wavepanel.view.AnchorView;
import org.waveprotocol.wave.client.wavepanel.view.BlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.InlineThreadView;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicParticipantView.ParticipantState;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicTagView.TagState;
import org.waveprotocol.wave.client.wavepanel.view.OutlineThreadView;
import org.waveprotocol.wave.client.wavepanel.view.ParticipantView;
import org.waveprotocol.wave.client.wavepanel.view.ParticipantsView;
import org.waveprotocol.wave.client.wavepanel.view.RootThreadView;
import org.waveprotocol.wave.client.wavepanel.view.TagView;
import org.waveprotocol.wave.client.wavepanel.view.TagsView;
import org.waveprotocol.wave.client.wavepanel.view.ThreadView;
import org.waveprotocol.wave.client.wavepanel.view.TopConversationView;
import org.waveprotocol.wave.client.wavepanel.view.View;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.util.IdentityMap;
import org.waveprotocol.wave.model.util.IdentityMap.Reduce;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.util.StringMap;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * A wave renderer that renders waves into fake view objects.
 */
public final class FakeRenderer implements WaveRenderer<View>, ModelAsViewProvider {

  /** Factory and registry of fake views. */
  class ViewStore {
    final BiMap<ConversationBlip, FakeBlipView> blipUis = HashBiMap.create();
    final BiMap<Conversation, FakeConversationView> convUis = HashBiMap.create();
    final BiMap<ConversationThread, FakeRootThreadView> rootThreadUis = HashBiMap.create();
    final BiMap<ConversationThread, FakeInlineThreadView> inlineThreadUis = HashBiMap.create();
    final BiMap<ConversationThread, FakeOutlineThreadView> outlineThreadUis = HashBiMap.create();
    final BiMap<ConversationThread, FakeAnchor> defaultAnchorUis = HashBiMap.create();
    final BiMap<ConversationThread, FakeAnchor> inlineAnchorUis = HashBiMap.create();

    FakeBlipView createBlipView(ConversationBlip blip, LinkedSequence<FakeAnchor> anchors) {
      return put(blipUis, blip, new FakeBlipView(FakeRenderer.this, anchors));
    }

    FakeConversationView createTopConversationView(Conversation conv, FakeRootThreadView thread) {
      return put(convUis, conv, new FakeTopConversationView(thread));
    }

    FakeConversationView createInlineConversationView(
        Conversation conv, FakeRootThreadView thread) {
      return put(convUis, conv, new FakeInlineConversationView(thread));
    }

    FakeThreadView createRootThreadView(
        ConversationThread thread, LinkedSequence<FakeBlipView> blipUis) {
      return put(rootThreadUis, thread,
          new FakeRootThreadView(FakeRenderer.this, blipUis));
    }

    FakeThreadView createInlineThreadView(
        ConversationThread thread, LinkedSequence<FakeBlipView> blipUis) {
      return put(inlineThreadUis, thread,
          new FakeInlineThreadView(FakeRenderer.this, blipUis));
    }

    FakeThreadView createOutlineThreadView(
        ConversationThread thread, LinkedSequence<FakeBlipView> blipUis) {
      return put(outlineThreadUis, thread,
          new FakeOutlineThreadView(FakeRenderer.this, blipUis));
    }

    FakeAnchor createDefaultAnchorView(ConversationThread thread) {
      return put(defaultAnchorUis, thread, new FakeAnchor());
    }

    FakeAnchor createInlineAnchorView(ConversationThread thread) {
      return put(inlineAnchorUis, thread, new FakeAnchor());
    }

    /** Puts a value in a map and returns it. */
    private <K, V> V put(Map<? super K, ? super V> map, K key, V value) {
      map.put(key, value);
      return value;
    }
  }

  class Rules implements RenderingRules<View> {

    @Override
    public View render(
        ConversationBlip blip, IdentityMap<ConversationThread, View> replies) {
      return new FakeDocumentView(blip.getDocument().toXmlString());
    }

    @Override
    public FakeBlipView render(ConversationBlip blip, View document,
        IdentityMap<ConversationThread, View> defaultAnchors) {
      LinkedSequence<FakeAnchor> anchorsUi = LinkedSequence.create();
      for (ConversationThread reply : blip.getReplyThreads()) {
        if (reply.isInline()) {
          anchorsUi.append((FakeAnchor) defaultAnchors.get(reply));
        }  
      }
      FakeBlipView blipUi = views.createBlipView(blip, anchorsUi);
      blipUi.getMeta().setContent((FakeDocumentView) document);
      return blipUi;
    }

    @Override
    public FakeAnchor render(ConversationThread thread, View threadUi) {
      FakeAnchor anchor = views.createDefaultAnchorView(thread);
      anchor.attach((InlineThreadView) threadUi);
      return anchor;
    }

    @Override
    public TopConversationView render(
        ConversationView wave, IdentityMap<Conversation, View> conversations) {
      // Pick the first one.
      return conversations.isEmpty() ? null :
        conversations.reduce(null,
          new Reduce<Conversation, View, TopConversationView>() {

            @Override
            public TopConversationView apply(
                TopConversationView soFar, Conversation key, View item) {
              return soFar != null ? soFar : (TopConversationView) item;
            }
          });
    }

    @Override
    public FakeConversationView render(
        Conversation conversation, View participants, View thread, View tags) {
      if (!conversation.hasAnchor()) {
        return views.createTopConversationView(conversation, (FakeRootThreadView) thread);
      } else {
        return views.createInlineConversationView(conversation, (FakeRootThreadView) thread);
      }
    }

    @Override
    public View render(
        Conversation conversation, ParticipantId participant, ParticipantState state, String hint) {
      // Ignore participants; not yet exercised by tests.
      return null;
    }

    @Override
    public View render(Conversation conversation, StringMap<View> participants) {
      // Ignore participants; not yet exercised by tests.
      return null;
    }

    @Override
    public FakeThreadView render(
        ConversationThread thread, IdentityMap<ConversationBlip, View> blips,
        boolean renderBlips) {
      LinkedSequence<FakeBlipView> blipUis = LinkedSequence.create();
      for (ConversationBlip blip : thread.getBlips()) {
        blipUis.append((FakeBlipView) blips.get(blip));
      }
      if (thread.isRoot()) {
        return views.createRootThreadView(thread, blipUis);
      } else if (thread.isInline()) {
        return views.createInlineThreadView(thread, blipUis);
      } else {
        return views.createOutlineThreadView(thread, blipUis);
      }
    }

    @Override
    public View renderTag(Conversation conversation, String tag, TagState state, String hint) {
      // Ignore tags; not yet exercised by tests.
      return null;
    }

    @Override
    public View renderTags(Conversation conversation, StringMap<View> tags) {
      // Ignore tags; not yet exercised by tests.
      return null;
    }

    @Override
    public View renderPlaceholder() {
      // Ignore placeholder; not yet exercised by tests.
      return null;
    }
  }

  private final ViewStore views = new ViewStore();
  private final WaveRenderer<View> renderer;

  private FakeRenderer(ConversationView wave) {
    this.renderer = ReductionBasedRenderer.of(new Rules(), wave);
  }

  /**
   * Creates a renderer of fake views.
   */
  public static FakeRenderer create(ConversationView wave) {
    return new FakeRenderer(wave);
  }

  // TODO: Expose view store, so that fake views can remove themselves after
  // destruction, so that view lookups below do not report spurious results.
  // This code path is unique to this fake renderer, because in a DOM renderer,
  // cleanup occurs implicitly by virtue of lookups being based on
  // Document.getElementById().

  public FakeAnchor createInlineAnchor(ConversationThread thread) {
    return views.createInlineAnchorView(thread);
  }

  // Delegate wave-rendering to the internal driver.

  @Override
  public View render(Conversation conversation, ParticipantId participant,
      ParticipantState state, String hint) {
    return renderer.render(conversation, participant, state, hint);
  }

  @Override
  public View render(Conversation conversation) {
    return renderer.render(conversation);
  }

  @Override
  public View render(ConversationBlip blip) {
    return renderer.render(blip);
  }

  @Override
  public View renderPlaceholder() {
    return renderer.renderPlaceholder();
  }

  @Override
  public View renderParticipants(Conversation conversation) {
    return renderer.renderParticipants(conversation);
  }

  @Override
  public View renderRootThread(ConversationThread rootThread) {
    return renderer.renderRootThread(rootThread);
  }

  @Override
  public View render(ConversationThread thread) {
    return renderer.render(thread);
  }

  @Override
  public View render(ConversationView wave) {
    return renderer.render(wave);
  }

  @Override
  public View renderTags(Conversation conversation) {
    return renderer.renderTags(conversation);
  }

  @Override
  public View render(Conversation conversation, String tag, TagState state, String hint) {
    return renderer.render(conversation, tag, state, hint);
  }

  // Delegate view lookup to view store.

  @Override
  public BlipView getBlipView(ConversationBlip blip) {
    return views.blipUis.get(blip);
  }

  @Override
  public RootThreadView getRootThreadView(ConversationThread thread) {
    return views.rootThreadUis.get(thread);
  }

  @Override
  public InlineThreadView getInlineThreadView(ConversationThread thread) {
    return views.inlineThreadUis.get(thread);
  }

  @Override
  public OutlineThreadView getOutlineThreadView(ConversationThread thread) {
    return views.outlineThreadUis.get(thread);
  }

  @Override
  public org.waveprotocol.wave.client.wavepanel.view.ConversationView getConversationView(
      Conversation conv) {
    return views.convUis.get(conv);
  }

  @Override
  public BlipMetaView getBlipMetaView(ConversationBlip blip) {
    BlipView blipUi = getBlipView(blip);
    return blipUi != null ? blipUi.getMeta() : null;
  }

  @Override
  public AnchorView getDefaultAnchor(ConversationThread thread) {
    return views.defaultAnchorUis.get(thread);
  }

  @Override
  public AnchorView getInlineAnchor(ConversationThread thread) {
    return views.inlineAnchorUis.get(thread);
  }

  @Override
  public ParticipantsView getParticipantsView(Conversation conv) {
    // Participant views not supported.
    return null;
  }

  @Override
  public ParticipantView getParticipantView(Conversation conv, ParticipantId source) {
    return null;
  }

  // Inverse lookup.

  @Override
  public ConversationBlip getBlip(BlipView blipUi) {
    return views.blipUis.inverse().get(blipUi);
  }

  @Override
  public ConversationThread getThread(ThreadView threadUi) {
    ConversationThread inline = views.inlineThreadUis.inverse().get(threadUi);
    return inline != null ? inline : views.rootThreadUis.inverse().get(threadUi);
  }

  @Override
  public Pair<Conversation, ParticipantId> getParticipant(ParticipantView participantUi) {
    return null;
  }

  @Override
  public Conversation getConversation(ParticipantsView participantsUi) {
    return null;
  }

  @Override
  public TagView getTagView(Conversation conv, String tag) {
    return null;
  }

  @Override
  public TagsView getTagsView(Conversation conv) {
    return null;
  }

  @Override
  public Pair<Conversation, String> getTag(TagView tagUi) {
    return null;
  }

  @Override
  public Conversation getTags(TagsView tagsUi) {
    return null;
  }
}
