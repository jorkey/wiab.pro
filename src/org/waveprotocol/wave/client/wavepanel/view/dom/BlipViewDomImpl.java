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
import com.google.gwt.dom.client.Style;

import org.waveprotocol.wave.client.wavepanel.view.IntrinsicBlipView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipMetaViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipViewBuilder.Components;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipViewBuilder.Css;

/**
 * BlipViewDomImpl of the blip view.
 *
 */
public final class BlipViewDomImpl implements DomView, IntrinsicBlipView {

  private final static String DELETED_COLOR = "#ffd0d0";

  /** The DOM element of this view. */
  private final Element self;

  /** The HTML id of {@code self}. */
  private final String id;

  /** The CSS classes used to manipulate style based on state changes. */
  private final Css css;

  //
  // UI fields for both intrinsic and structural elements.
  // Element references are loaded lazily and cached.
  //

  private Element topMargin;
  private Element meta;
  private Element replies;
  private Element bottomMargin;

  BlipViewDomImpl(Element self, String id, Css css) {
    this.self = self;
    this.id = id;
    this.css = css;
  }

  public static BlipViewDomImpl of(Element e, Css css) {
    return new BlipViewDomImpl(e, e.getId(), css);
  }

  //
  // Generated code. There is no informative content in the code below.
  //

  public Element getMeta() {
    if (meta == null) {
      meta = DomUtil.findFirstChildElement(self, Type.META);
    }
    return meta;
  }

  //
  // Structural elements are public, in order to export structural control.
  //

  public Element getReplies() {
    if (replies == null) {
      replies = DomViewHelper.load(id, Components.REPLIES);
    }
    return replies;
  }

  public void remove() {
    getElement().removeFromParent();
  }

  @Override
  public void setQuasiDeleted(String title, boolean isRowOwnerDeleted) {
    self.addClassName(css.deleted());
    
    // white margins are shown only if blip doesn't have a deleted row owner blip
    int marginWidthPct = isRowOwnerDeleted ? 0 : 100;
    getTopMargin().getStyle().setWidth(marginWidthPct, Style.Unit.PCT);
    getBottomMargin().getStyle().setWidth(marginWidthPct, Style.Unit.PCT);    
    
    Style metaFrameStyle = DomViewHelper.load(getMeta().getId(),
        BlipMetaViewBuilder.Components.FRAME).getStyle();
    metaFrameStyle.setBackgroundColor(DELETED_COLOR);
    metaFrameStyle.setBorderColor(DELETED_COLOR);
    DomUtil.findFirstChildElement(meta, Type.BLIP_INDICATOR).getStyle().setBorderColor(DELETED_COLOR);    

    self.setTitle(title);
    
    // Unset cursor shape for children
    setAutoCursor(DomUtil.findFirstChildElement(getMeta(), Type.BLIP_CONTINUATION_BAR));
    Element timeAndMenu = DomUtil.findFirstChildElement(getMeta(), Type.META_BAR, Type.TIME_AND_MENU);
    setAutoCursor(timeAndMenu);
    setAutoCursor(DomUtil.findFirstChildElement(timeAndMenu, Type.BLIP_TIME));
    Element menuButton = DomUtil.findFirstChildElement(timeAndMenu, Type.BLIP_MENU_BUTTON);
    setAutoCursor(menuButton);

    // Remove arrow from menu button
    menuButton.getStyle().setColor(DELETED_COLOR);

    DomUtil.setQuasiDeleted(self);
  }

  //
  // DomView nature.
  //

  @Override
  public Element getElement() {
    return self;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public int getZIndex() {
    return Integer.parseInt(self.getStyle().getZIndex());
  }

  @Override
  public void setZIndex(int zIndex) {
    self.getStyle().setZIndex(zIndex);
  }

  @Override
  public void setIndentationLevel(int level) {
    self.getStyle().setMarginLeft(level * BlipViewBuilder.LEVEL_MARGIN_EM, Style.Unit.EM);
  }

  @Override
  public void setMargins(int top, int bottom) {
    getTopMargin().getStyle().setHeight(top, Style.Unit.PX);
    getBottomMargin().getStyle().setHeight(bottom, Style.Unit.PX);
  }

  @Override
  public boolean equals(Object obj) {
    return DomViewHelper.equals(this, obj);
  }

  @Override
  public int hashCode() {
    return DomViewHelper.hashCode(this);
  }

  private static void setAutoCursor(Element element) {
    element.getStyle().setCursor(Style.Cursor.AUTO);
  }
  
  private Element getTopMargin() {
    if (topMargin == null) {
      topMargin = DomViewHelper.load(id, Components.TOP_MARGIN);      
    }
    return topMargin;
  }
  
  private Element getBottomMargin() {
    if (bottomMargin == null) {
      bottomMargin = DomViewHelper.load(id, Components.BOTTOM_MARGIN);
    }
    return bottomMargin;
  }  
}
