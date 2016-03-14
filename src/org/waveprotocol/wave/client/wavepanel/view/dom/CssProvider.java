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

import org.waveprotocol.wave.client.wavepanel.render.DynamicRendererImpl;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.CollapsibleBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.ContinuationIndicatorViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.ParticipantsViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.ReplyBoxViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.RootThreadViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TagsViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TopConversationViewBuilder;
import org.waveprotocol.wave.client.widget.dialog.Dialog;

/**
 * Provides access to the CSS class names for all supported view sections,
 * an immutable tuple storing the custom version of each collection of classes.
 */
public final class CssProvider {
  private final BlipViewBuilder.Css blipCss;
  private final CollapsibleBuilder.Css collapsibleCss;
  private final RootThreadViewBuilder.Css rootThreadCss;
  private final ReplyBoxViewBuilder.Css replyBoxCss;
  private final ContinuationIndicatorViewBuilder.Css continuationIndicatorCss;
  private final TopConversationViewBuilder.Css conversationCss;
  private final ParticipantsViewBuilder.Css participantsCss;
  private final TagsViewBuilder.Css tagsCss;
  private final Dialog.Css dialogCss;
  private final DynamicRendererImpl.Css renderCss;

  /** Inject all members of the tuple, split by view section. */
  public CssProvider(
      BlipViewBuilder.Css blipCss,
      CollapsibleBuilder.Css collapsibleCss,
      ContinuationIndicatorViewBuilder.Css continuationIndicatorCss,
      TopConversationViewBuilder.Css conversationCss,
      ParticipantsViewBuilder.Css participantsCss,
      ReplyBoxViewBuilder.Css replyBoxCss,
      RootThreadViewBuilder.Css rootThreadCss,
      TagsViewBuilder.Css tagsCss,
      Dialog.Css dialogCss,
      DynamicRendererImpl.Css renderCss) {
    this.blipCss = blipCss;
    this.collapsibleCss = collapsibleCss;
    this.continuationIndicatorCss = continuationIndicatorCss;
    this.conversationCss = conversationCss;
    this.participantsCss = participantsCss;
    this.replyBoxCss = replyBoxCss;
    this.rootThreadCss = rootThreadCss;
    this.tagsCss = tagsCss;
    this.dialogCss = dialogCss;
    this.renderCss = renderCss;
  }

  public BlipViewBuilder.Css getBlipCss() {
    return blipCss;
  }

  public CollapsibleBuilder.Css getCollapsibleCss() {
    return collapsibleCss;
  }

  public ContinuationIndicatorViewBuilder.Css getContinuationIndicatorCss() {
    return continuationIndicatorCss;
  }

  public TopConversationViewBuilder.Css getConversationCss() {
    return conversationCss;
  }

  public ParticipantsViewBuilder.Css getParticipantsCss() {
    return participantsCss;
  }

  public ReplyBoxViewBuilder.Css getReplyBoxCss() {
    return replyBoxCss;
  }

  public RootThreadViewBuilder.Css getRootThreadCss() {
    return rootThreadCss;
  }

  public TagsViewBuilder.Css getTagsCss() {
    return tagsCss;
  }

  public Dialog.Css getDialogCss() {
    return dialogCss;
  }

  public DynamicRendererImpl.Css getRenderCss() {
    return renderCss;
  }
}
