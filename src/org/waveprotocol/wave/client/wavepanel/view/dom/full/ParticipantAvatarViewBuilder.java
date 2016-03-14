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

package org.waveprotocol.wave.client.wavepanel.view.dom.full;

import com.google.common.annotations.VisibleForTesting;

import org.waveprotocol.wave.client.common.safehtml.EscapeUtils;
import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.uibuilder.OutputHelper;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicParticipantView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.ParticipantsViewBuilder.Css;
import org.waveprotocol.wave.model.util.ValueUtils;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * UiBuilder for a participant.
 *
 */
public final class ParticipantAvatarViewBuilder implements IntrinsicParticipantView, UiBuilder {

  public static ParticipantAvatarViewBuilder create(
      String id, String participantId, ParticipantState state, String hint) {
    return new ParticipantAvatarViewBuilder(
        id, participantId, state, hint, WavePanelResourceLoader.getParticipants().css());
  }

  private final String id;
  private String participantId;  
  private String avatarUrl;
  private ParticipantState state;
  private String hint;
  private final Css css;  

  @VisibleForTesting
  private ParticipantAvatarViewBuilder(
      String id, String participantId, ParticipantState state, String hint, Css css) {
    this.id = id;
    this.participantId = participantId;
    this.state = state;
    this.hint = hint;
    this.css = css;
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
  public void setParticipantId(String participantId) {
    this.participantId = participantId;
  }

  @Override
  public String getParticipantId() {
    return participantId;
  }

  @Override
  public ParticipantState getState() {
    return state;
  }

  @Override
  public void setState(ParticipantState state) {
    this.state = state;
  }

  @Override
  public String getHint() {
    return hint;
  }

  @Override
  public void setHint(String hint) {
    this.hint = hint;
  }  
  
  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    String className = css.participant() + " ";
    switch(state) {
      case NORMAL:  className += css.normal();  break;
      case ADDED:   className += css.added();   break;
      case REMOVED: className += css.removed(); break;
    }    
    
    String name = extractName(participantId);
    String title = composeTitle(name, hint);
    OutputHelper.image(output, id, className,
        EscapeUtils.fromString(avatarUrl),
        EscapeUtils.fromString(title),
        TypeCodes.kind(Type.PARTICIPANT),
        " " + PARTICIPANT_ID_ATTRIBUTE + "='" + participantId + "'");
  }
  
  private static String extractName(String participantId) {
    String[] parts = participantId.split(ParticipantId.DOMAIN_PREFIX);
    return ValueUtils.toCapitalCase(parts[0]);
  }
  
  private static String composeTitle(String name, String hint) {
    if (name == null) {
      return null;
    }
    return name + (hint != null ? (" (" + hint + ")") : "");    
  }
}
