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

package org.waveprotocol.wave.client.wavepanel.view.dom;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;

import org.waveprotocol.wave.client.wavepanel.view.AnchorView;
import org.waveprotocol.wave.client.wavepanel.view.BlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.ConversationView;
import org.waveprotocol.wave.client.wavepanel.view.InlineThreadView;
import org.waveprotocol.wave.client.wavepanel.view.OutlineThreadView;
import org.waveprotocol.wave.client.wavepanel.view.ParticipantView;
import org.waveprotocol.wave.client.wavepanel.view.ParticipantsView;
import org.waveprotocol.wave.client.wavepanel.view.RootThreadView;
import org.waveprotocol.wave.client.wavepanel.view.TagView;
import org.waveprotocol.wave.client.wavepanel.view.TagsView;
import org.waveprotocol.wave.client.wavepanel.view.ThreadView;
import org.waveprotocol.wave.client.wavepanel.view.View;
import org.waveprotocol.wave.client.wavepanel.view.ViewIdMapper;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Get the view associated with the given model element.
 *
 */
public class ModelAsViewProviderImpl implements ModelAsViewProvider {
  private final DomAsViewProvider viewProvider;
  private final ViewIdMapper viewIdMapper;

  /**
   */
  public ModelAsViewProviderImpl(ViewIdMapper viewIdMapper, DomAsViewProvider viewProvider) {
    this.viewProvider = viewProvider;
    this.viewIdMapper = viewIdMapper;
  }

  @Override
  public BlipView getBlipView(ConversationBlip source) {
    if (source != null) {
      Element e = Document.get().getElementById(viewIdMapper.blipOf(source));
      return viewProvider.asBlip(e);
    }
    return null;
  }

  @Override
  public BlipMetaView getBlipMetaView(ConversationBlip source) {
    if (source != null) {
      Element e = Document.get().getElementById(viewIdMapper.metaOf(source));
      return viewProvider.asBlipMeta(e);
    }
    return null;
  }

  @Override
  public RootThreadView getRootThreadView(ConversationThread source) {
    if (source != null) {
      Element e = getRootThreadElement();
      return viewProvider.asRootThread(e);
    }
    return null;
  }

  @Override
  public InlineThreadView getInlineThreadView(ConversationThread source) {
    if (source != null) {
      Element e = Document.get().getElementById(viewIdMapper.threadOf(source));
      return viewProvider.asInlineThread(e);
    }
    return null;
  }

  @Override
  public OutlineThreadView getOutlineThreadView(ConversationThread source) {
    if (source != null) {
      Element e;
      ConversationBlip parentBlip = source.getParentBlip();
      if (parentBlip == null) {
        e = DomUtil.getRootBlipContainer();
      } else {
        Element parentBlipElement = Document.get().getElementById(viewIdMapper.blipOf(parentBlip));
        e = parentBlipElement.getParentElement();
      }
      return viewProvider.asOutlineThread(e);
    }
    return null;
  }

  @Override
  public AnchorView getDefaultAnchor(ConversationThread source) {
    if (source != null) {
      Element e = Document.get().getElementById(viewIdMapper.defaultAnchorOf(source));
      return viewProvider.asAnchor(e);
    }
    return null;
  }

  @Override
  public AnchorView getInlineAnchor(ConversationThread source) {
    if (source != null) {
      final String domId = viewIdMapper.inlineAnchorOf(source.getParentBlip(), source.getId());
      final Element e = Document.get().getElementById(domId);
      return viewProvider.asAnchor(e);
    }
    return null;
  }

  @Override
  public ParticipantView getParticipantView(Conversation conv, ParticipantId source) {
    if (conv != null && source != null) {
      Element e = Document.get().getElementById(viewIdMapper.participantOf(conv, source));
      return viewProvider.asParticipant(e);
    }
    return null;
  }

  @Override
  public ParticipantsView getParticipantsView(Conversation conv) {
    if (conv != null) {
      Element e = Document.get().getElementById(viewIdMapper.participantsOf(conv));
      return viewProvider.asParticipants(e);
    }
    return null;
  }

  @Override
  public TagView getTagView(Conversation conv, String tag) {
    if (conv != null && tag != null) {
      Element e = Document.get().getElementById(viewIdMapper.tagOf(conv, tag));
      return viewProvider.asTag(e);
    }
    return null;
  }

  @Override
  public TagsView getTagsView(Conversation conv) {
    if (conv != null) {
      Element e = Document.get().getElementById(viewIdMapper.tagsOf(conv));
      return viewProvider.asTags(e);
    }
    return null;
  }

  @Override
  public ConversationView getConversationView(Conversation conv) {
    if (conv != null) {
      Element e = Document.get().getElementById(viewIdMapper.conversationOf(conv));
      return viewProvider.asConversation(e);
    }
    return null;
  }

  @Override
  public ConversationBlip getBlip(BlipView blipView) {
    return blipView != null ? viewIdMapper.blipOf(blipView.getId()) : null;
  }

  @Override
  public ConversationThread getThread(ThreadView threadView) {
    return threadView != null ? viewIdMapper.threadOf(threadView.getId()) : null;
  }

  @Override
  public Conversation getConversation(ParticipantsView participantsView) {
    return participantsView != null ? viewIdMapper.participantsOf(participantsView.getId()) : null;
  }

  @Override
  public Pair<Conversation, ParticipantId> getParticipant(ParticipantView participantView) {
    return participantView != null ? viewIdMapper.participantOf(participantView.getId()) : null;
  }

  @Override
  public Conversation getTags(TagsView tagsView) {
    return tagsView != null ? viewIdMapper.tagsOf(tagsView.getId()) : null;
  }

  @Override
  public Pair<Conversation, String> getTag(TagView tagView) {
    return tagView != null ? viewIdMapper.tagOf(tagView.getId()) : null;
  }

  private static Element getRootThreadElement() {
    Element mainElement = DomUtil.getMainElement();
    return DomUtil.findFirstChildElement(
        mainElement, View.Type.ROOT_CONVERSATION, View.Type.SCROLL_PANEL, View.Type.ROOT_THREAD);
  }
}
