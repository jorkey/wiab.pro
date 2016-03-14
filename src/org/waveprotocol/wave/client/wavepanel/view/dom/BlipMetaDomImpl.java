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
import com.google.gwt.dom.client.ImageElement;

import org.waveprotocol.wave.client.common.util.StringSequence;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicBlipMetaView;
import static org.waveprotocol.wave.client.wavepanel.view.dom.DomViewHelper.load;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipMetaViewBuilder.Components;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipViewBuilder;

/**
 * BlipViewDomImpl of the blip view.
 *
 */
public final class BlipMetaDomImpl implements DomView, IntrinsicBlipMetaView {

  /**
   * The INLINE_LOCATOR_ATTRIBUTE is the dom element attribute that contains the
   * serialized string of the inline locator.
   * The INLINE_LOCATOR_PROPERTY is a the dom element property that contains the deserialized
   * inline locator object.
   * The two must be different since in IE, property and attribute are not namespaced separately.
   */
  private static final String INLINE_LOCATOR_PROPERTY = "inlineSequence";
  public static final String INLINE_LOCATOR_ATTRIBUTE = "inline";

  /** The DOM element of this view. */
  private final Element self;

  /** The HTML id of {@code self}. */
  private final String id;

  /** The CSS classes used to manipulate style based on state changes. */
  private final BlipViewBuilder.Css css;

  //
  // UI fields for both intrinsic and structural elements.
  // Element references are loaded lazily and cached.
  //

  private Element time;
  private Element contentContainer;
  private ImageElement avatar;
  private Element metaline;
  private Element indicator;
  private Element frame;
  private Element draftModeControls;

  private StringSequence inlineLocators;

  BlipMetaDomImpl(Element self, String id, BlipViewBuilder.Css css) {
    this.self = self;
    this.id = id;
    this.css = css;
  }

  public static BlipMetaDomImpl of(Element e, BlipViewBuilder.Css css) {
    return new BlipMetaDomImpl(e, e.getId(), css);
  }

  @Override
  public void setTime(String time) {
    getTime().setInnerText(time == null ? null : (" " + time));
  }

  @Override
  public void setAvatar(String avatarUrl, String authorName) {
    getAvatar().setSrc(avatarUrl);
    getAvatar().setAlt(authorName);
    getAvatar().setTitle(authorName);
  }

  @Override
  public void setMetaline(String metaline) {
    getMetaline().setInnerText(metaline);
  }

  @Override
  public void setRead(boolean read) {
    // The entire set of class names is always replaced, because
    // server-generated and client-generated classes do not mix.
    getIndicator().setClassName(css.indicator() + " " +
        (read ? css.read() : css.unread()) );
  }

  @Override
  public void setBorders(boolean top, boolean right, boolean bottom, boolean left, boolean isFirst) {
    self.setClassName(css.meta() + (top  ? (" " + css.metaWithTopBorder()) : ""));


  /*    .frameCorner => the rounded shadow on the left and bottom;
   *    .frameVertical => the rounded shadow on the left;
   *    .frameVerticalWithoutRnd => the left shadow without rounding;
   *    .frameCornerWithoutRnd => the left shadow without rounding and the rounded shadow on the bottom.
   */

    getFrame().setClassName(css.frame() +
       ((left && !bottom && ((!top) || (top && isFirst))) ? (" " + css.frameVertical()) : "") +
       ((left &&  bottom && ((!top) || (top && isFirst))) ? (" " + css.frameCorner()) : "") +
       ((left && !bottom &&  top && !isFirst) ? (" " + css.frameVerticalWithoutRnd()) : "") +
       ((left &&  bottom &&  top && !isFirst) ? (" " + css.frameCornerWithoutRnd()): ""));
  }

  public void clearContent() {
    getInlineLocators().clear();
    getContentContainer().getFirstChildElement().setInnerHTML("");
  }

  public void setContent(Element document) {
    // Server-side document rendering is not correct - it leaves off the crucial
    // "document" class.
    document.addClassName("document");
    getContentContainer().getFirstChildElement().appendChild(document);
  }

  public StringSequence getInlineLocators() {
    if (inlineLocators == null) {
      Element content = getContentContainer().getFirstChildElement();
      if (content != null) {
        inlineLocators = (StringSequence) content.getPropertyObject(INLINE_LOCATOR_PROPERTY);
        if (inlineLocators == null) {
          // Note: getAttribute() of a missing attribute does not return null on
          // all browsers.
          if (content.hasAttribute(INLINE_LOCATOR_ATTRIBUTE)) {
            String serial = content.getAttribute(INLINE_LOCATOR_ATTRIBUTE);
            inlineLocators = StringSequence.create(serial);
          } else {
            inlineLocators = StringSequence.create();
          }
          content.setPropertyObject(INLINE_LOCATOR_PROPERTY, inlineLocators);
        }
      } else {
        // Leave inlineLocators as null, since the document is not here yet.
      }
    }
    return inlineLocators;
  }

  //
  // Generated code. There is no informative content in the code below.
  //

  private Element getTime() {
    if (time == null) {
      time = DomViewHelper.load(id, Components.TIME);
    }
    return time;
  }

  private ImageElement getAvatar() {
    if (avatar == null) {
      avatar = DomViewHelper.load(id, Components.AVATAR).cast();
    }
    return avatar;
  }

  private Element getMetaline() {
    if (metaline == null) {
      metaline = DomViewHelper.load(id, Components.METALINE);
    }
    return metaline;
  }

  private Element getIndicator() {
    if (indicator == null) {
      indicator = DomViewHelper.load(id, Components.INDICATOR);
    }
    return indicator;
  }

  private Element getFrame() {
    if (frame == null) {
      frame = DomViewHelper.load(id, Components.FRAME);
    }
    return frame;
  }
  
  //
  // Structural elements are public, in order to export structural control.
  //

  public Element getContentContainer() {
    if (contentContainer == null) {
      contentContainer = DomViewHelper.load(id, Components.CONTENT);
    }
    return contentContainer;
  }

  public Element getDraftModeControls() {
    if (draftModeControls == null)
      draftModeControls = load(id, Components.DRAFTMODECONTROLS);
    
    return draftModeControls;
  }

  public void remove() {
    getElement().removeFromParent();
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

  //
  // DOM-specific structural knowledge.
  //

  Element getInlineAnchorAfter(Element ref) {
    String id = ref != null ? ref.getId() : null;
    String nextId = getInlineLocators().getNext(id);
    return Document.get().getElementById(nextId);
  }

  Element getInlineAnchorBefore(Element ref) {
    String id = ref != null ? ref.getId() : null;
    String nextId = getInlineLocators().getPrevious(id);
    return Document.get().getElementById(nextId);
  }

  void insertInlineLocatorBefore(Element ref, Element x) {
    String id = ref != null ? ref.getId() : null;
    getInlineLocators().insertBefore(id, x.getId());
  }

  void removeInlineLocator(Element x) {
    getInlineLocators().remove(x.getId());
  }

  @Override
  public boolean equals(Object obj) {
    return DomViewHelper.equals(this, obj);
  }

  @Override
  public int hashCode() {
    return DomViewHelper.hashCode(this);
  }
}