/**
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.waveprotocol.box.server.rpc.render.view.builder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.waveprotocol.box.server.rpc.render.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.box.server.rpc.render.uibuilder.BuilderHelper.Component;
import org.waveprotocol.box.server.rpc.render.uibuilder.UiBuilder;
import org.waveprotocol.box.server.rpc.render.view.IntrinsicReplyBoxView;

/**
 * This class is the view builder for the reply box that exists at the end of a
 * root thread.
 */
public final class ReplyBoxViewBuilder implements UiBuilder, IntrinsicReplyBoxView {

  public interface Resources {
    Css css();
  }

  public interface Css {
    /** The main reply box container. */
    String replyBox();
    
    /** The avatar image. */
    String avatar();
  }

  /** An enum for all the components of a reply box view. */
  public enum Components implements Component {
    /** The avatar element. */
    AVATAR("A");

    private final String postfix;

    Components(String postfix) {
      this.postfix = postfix;
    }

    @Override
    public String getDomId(String baseId) {
      return baseId + postfix;
    }
  }

  /** A unique id for this builder. */
  private final String id;
  
  //
  // Intrinsic state.
  //

  /**
   * Creates a new reply box view builder with the given id.
   * 
   * @param id unique id for this builder, it must only contains alphanumeric
   *        characters
   */
  public static ReplyBoxViewBuilder create(WavePanelResources resources, String id) {
    return new ReplyBoxViewBuilder(resources.getReplyBox().css(), id);
  }

  @VisibleForTesting
  ReplyBoxViewBuilder(Css css, String id) {
    // must not contain ', it is especially troublesome because it cause
    // security issues.
    Preconditions.checkArgument(!id.contains("\'"));
    this.id = id;
  }

  @Override
  public void setAvatarImageUrl(String avatarUrl) {
  }

  //
  // DomImpl nature.
  //

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
  }

  @Override
  public String getId() {
    return id;
  }
}