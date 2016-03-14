/**
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
package org.waveprotocol.wave.client.wavepanel.view.dom;

import com.google.gwt.dom.client.Element;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicTagsView;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TagsViewBuilder.Components;

/**
 * The DOM implementation of a tag collection.
 *
 */
public final class TagsDomImpl implements DomView, IntrinsicTagsView {

  /** The element to which this view is bound. */
  private final Element self;

  /** The HTML id of {@code self}. */
  private final String id;

  private Element tagContainer;
  private Element tagsCaption;
  private Element simpleMenu;

  TagsDomImpl(Element self, String id) {
    this.self = self;
    this.id = id;
  }

  public static TagsDomImpl of(Element e) {
    return new TagsDomImpl(e, e.getId());
  }

  @Override
  public Element getElement() {
    return self;
  }

  @Override
  public String getId() {
    return id;
  }

  //
  // Structure.
  //

  Element getTagContainer() {
    if (tagContainer == null) {
      tagContainer = DomViewHelper.load(id, Components.CONTAINER);
    }
    return tagContainer;
  }

  Element getTagsCaption() {
    if (tagsCaption == null) {
      tagsCaption = getTagContainer().getFirstChild().cast();
    }
    return tagsCaption;
  }
  
  Element getSimpleMenu() {
    // The two menu elements are always in the tag container, and should
    // always be at the end.
    if (simpleMenu == null) {
      simpleMenu = getTagContainer().getLastChild().getPreviousSibling().cast();
    }
    return simpleMenu;
  }

  void remove() {
    self.removeFromParent();
  }
}
