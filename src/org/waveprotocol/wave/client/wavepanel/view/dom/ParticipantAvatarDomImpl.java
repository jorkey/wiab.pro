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

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.ImageElement;

import org.waveprotocol.wave.client.wavepanel.view.IntrinsicParticipantView;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.ParticipantsViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.WavePanelResourceLoader;

/**
 * DOM implementation of a participant.
 *
 */
public final class ParticipantAvatarDomImpl implements DomView, IntrinsicParticipantView {
  
  private final ImageElement self;
  private static final ParticipantsViewBuilder.Css css =
      WavePanelResourceLoader.getParticipants().css();  

  ParticipantAvatarDomImpl(Element self) {
    this.self = self.cast();
  }

  public static ParticipantAvatarDomImpl of(Element e) {
    return new ParticipantAvatarDomImpl(e);
  }

  @Override
  public void setAvatar(String url) {
    self.setSrc(url);
  }

  @Override
  public String getParticipantId() {
    return self.getAttribute(PARTICIPANT_ID_ATTRIBUTE);
  }  
  
  @Override
  public void setParticipantId(String participantId) {
    self.setAttribute(PARTICIPANT_ID_ATTRIBUTE, participantId);
  }

  @Override
  public ParticipantState getState() {
    String className = self.getClassName();
    if (className.indexOf(css.added()) != -1) {
      return ParticipantState.ADDED;
    } else if (className.indexOf(css.removed()) != -1) {
      return ParticipantState.REMOVED;
    }
    return ParticipantState.NORMAL;

  }

  @Override
  public void setState(ParticipantState state) {
    String className = css.participant() + " ";
    switch(state) {
      case NORMAL:
        className += css.normal();
        setHint(null);
        break;
      case ADDED:
        className += css.added();
        break;
      case REMOVED:
        className += css.removed();
        break;
    }
    self.setClassName(className);
  }

  @Override
  public String getHint() {
    return extractHint(self.getTitle());
  }

  @Override
  public void setHint(String hint) {
    self.setTitle(composeTitle(getParticipantId(), hint));
  }  
  
  //
  // Structure.
  //

  public void remove() {
    self.removeFromParent();
  }

  //
  // DomView
  //

  @Override
  public Element getElement() {
    return self;
  }

  @Override
  public String getId() {
    return self.getId();
  }

  @Override
  public boolean equals(Object obj) {
    return DomViewHelper.equals(this, obj);
  }

  @Override
  public int hashCode() {
    return DomViewHelper.hashCode(this);
  }
  
  private static String extractName(String title) {
    if (title == null) {
      return null;
    }
    int bracePos = title.indexOf("(");
    return bracePos != -1 ? title.substring(0, bracePos).trim() : title;
  }
  
  private static String extractHint(String title) {
    if (title == null) {
      return null;
    }
    int bracePos1 = title.indexOf("(");
    int bracePos2 = title.indexOf(")");
    if (bracePos1 == -1 || bracePos2 == -1) {
      return null;
    }
    return title.substring(bracePos1+1, bracePos2);
  }
  
  private static String composeTitle(String name, String hint) {
    if (name == null) {
      return null;
    }
    return name + (hint != null ? (" (" + hint + ")") : "");    
  }
}
