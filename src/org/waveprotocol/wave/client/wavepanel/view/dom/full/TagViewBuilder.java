/**
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
package org.waveprotocol.wave.client.wavepanel.view.dom.full;

import com.google.common.annotations.VisibleForTesting;
import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.close;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.openWith;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicTagView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TagsViewBuilder.Css;

/**
 * UiBuilder for a tag.
 *
 */
public final class TagViewBuilder
    implements IntrinsicTagView, UiBuilder {

  public static TagViewBuilder create(
      String id, String name, TagState state, String hint) {
    return new TagViewBuilder(
        id, name, state, hint, WavePanelResourceLoader.getTags().css());
  }  
  
  private final String id;
  private String name;
  private TagState state;
  private String hint;
  private final Css css;    

  @VisibleForTesting
  private TagViewBuilder(
      String id, String name, TagState state, String hint, Css css) {
    this.id = id;
    this.name = name;
    this.state = state;
    this.hint = hint;
    this.css = css;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public TagState getState() {
    return state;
  }
  
  @Override
  public void setState(TagState state) {
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
    String className = css.tag() + " ";
    switch(state) {
      case NORMAL:  className += css.normal();  break;
      case ADDED:   className += css.added();   break;
      case REMOVED: className += css.removed(); break;
    }
    
    openWith(output, id, className, TypeCodes.kind(Type.TAG), null, hint, null);
    {
      output.appendEscaped(name);
    }  
    close(output);
  }
}