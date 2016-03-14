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
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicTagView;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TagsViewBuilder.Css;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.WavePanelResourceLoader;

/**
 * DOM implementation of a tag.
 *
 */
public final class TagDomImpl implements DomView, IntrinsicTagView {
  
  private final Element self;
  private static final Css css = WavePanelResourceLoader.getTags().css();

  TagDomImpl(Element self) {
    this.self = self.cast();
  }

  static TagDomImpl of(Element e) {
    return new TagDomImpl(e);
  }

  @Override
  public void setName(String name) {
    self.setInnerText(name);
  }

  @Override
  public String getName() {
    return self.getInnerText();
  }
  
  @Override
  public TagState getState() {
    String className = self.getClassName();
    if (className.indexOf(css.added()) != -1) {
      return TagState.ADDED;
    } else if (className.indexOf(css.removed()) != -1) {
      return TagState.REMOVED;
    }
    return TagState.NORMAL;
  }

  @Override
  public void setState(TagState state) {
    String className = css.tag() + " ";
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
    return self.getTitle();
  }
  
  @Override
  public void setHint(String hint) {
    self.setTitle(hint);
  }
  
  //
  // Structure.
  //

  void remove() {
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
}
