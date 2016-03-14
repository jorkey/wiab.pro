/**
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.waveprotocol.box.server.rpc.render.view.builder;

import com.google.common.annotations.VisibleForTesting;
import org.waveprotocol.box.server.rpc.render.account.impl.ProfileImpl;
import org.waveprotocol.box.server.rpc.render.common.safehtml.EscapeUtils;
import org.waveprotocol.box.server.rpc.render.common.safehtml.SafeHtmlBuilder;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.image;
import org.waveprotocol.box.server.rpc.render.uibuilder.UiBuilder;
import org.waveprotocol.box.server.rpc.render.view.IntrinsicParticipantView;
import org.waveprotocol.box.server.rpc.render.view.TypeCodes;
import org.waveprotocol.box.server.rpc.render.view.View.Type;
import org.waveprotocol.box.server.rpc.render.view.builder.ParticipantsViewBuilder.Css;

/**
 * UiBuilder for a participant.
 *
 */
public final class ParticipantAvatarViewBuilder implements IntrinsicParticipantView, UiBuilder {

  private final Css css;
  private final String id;

  private String avatarUrl;
  private String name;

  @VisibleForTesting
  ParticipantAvatarViewBuilder(String id, Css css) {
    this.id = id;
    this.css = css;
  }

  public static ParticipantAvatarViewBuilder create(WavePanelResources resources, String id) {
    return new ParticipantAvatarViewBuilder(id, resources.getParticipants().css());
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public void setAvatar(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    image(output,
        id,
        css.participant(),
        EscapeUtils.fromString(avatarUrl),
        EscapeUtils.fromString(ProfileImpl.coverName(name)),
        TypeCodes.kind(Type.PARTICIPANT));
  }
}
