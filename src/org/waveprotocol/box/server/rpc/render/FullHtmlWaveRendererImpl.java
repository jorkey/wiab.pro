/**
 * Copyright 2011 Google Inc.
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

import org.waveprotocol.box.server.rpc.render.FullHtmlWaveRenderer.DocRefRenderer;
import org.waveprotocol.box.server.rpc.render.account.impl.ProfileManagerImpl;
import org.waveprotocol.box.server.rpc.render.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.box.server.rpc.render.renderer.ReductionBasedRenderer;
import org.waveprotocol.box.server.rpc.render.renderer.RenderingRules;
import org.waveprotocol.box.server.rpc.render.renderer.ShallowBlipRenderer;
import org.waveprotocol.box.server.rpc.render.renderer.WaveRenderer;
import org.waveprotocol.box.server.rpc.render.state.ThreadReadStateMonitor;
import org.waveprotocol.box.server.rpc.render.uibuilder.UiBuilder;
import org.waveprotocol.box.server.rpc.render.view.ViewFactory;
import org.waveprotocol.box.server.rpc.render.view.ViewIdMapper;
import org.waveprotocol.box.server.rpc.render.view.builder.WavePanelResources;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Renders waves into HTML DOM, given a renderer that renders waves as HTML
 * closures.
 *
 */
public final class FullHtmlWaveRendererImpl implements HtmlRenderer {

  private final WaveRenderer<UiBuilder> driver;

  private FullHtmlWaveRendererImpl(WaveRenderer<UiBuilder> driver) {
    this.driver = driver;
  }

  public static HtmlRenderer create(ConversationView wave,
      ProfileManagerImpl profileManager,
      ShallowBlipRenderer shallowRenderer, ViewIdMapper idMapper,
       ThreadReadStateMonitor readMonitor, ViewFactory views,
      DocRefRenderer docRenderer, WavePanelResources resources, String waveletUri) {

    RenderingRules<UiBuilder> rules = new FullHtmlWaveRenderer(
        shallowRenderer, docRenderer, profileManager, idMapper, views, readMonitor, resources, waveletUri);

    return new FullHtmlWaveRendererImpl(ReductionBasedRenderer.of(rules, wave));

  }

  //
  // Temporary invokers. Anti-parser API will remove these methods.
  //

  @Override
  public String renderWave(ConversationView wave) {
    return parseHtml(driver.renderWave(wave));
  }

  @Override
  public String renderConversation(Conversation conversation) {
    return parseHtml(driver.renderConversation(conversation));
  }

  @Override
  public String renderThread(ConversationThread thread) {
    return parseHtml(driver.renderThread(thread));
  }

  @Override
  public String renderBlip(ConversationBlip blip) {
    return parseHtml(driver.renderBlip(blip));
  }

  @Override
  public String renderParticipant(Conversation conversation, ParticipantId participant) {
    return parseHtml(driver.renderParticipant(conversation, participant));
  }

  @Override
  public String renderTag(Conversation conversation, String tag) {
    return parseHtml(driver.renderTag(conversation, tag));
  }

  /** Turns a UiBuilder rendering into  HTML. */
  private String parseHtml(UiBuilder ui) {
    if (ui == null) {
      return null;
    }
    SafeHtmlBuilder html = new SafeHtmlBuilder();
    ui.outputHtml(html);
    return html.toSafeHtml().asString();
  }
}
