/**
 * Copyright 2010 Google Inc.
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

package org.waveprotocol.box.server.rpc.render.view.builder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.waveprotocol.box.server.rpc.render.common.safehtml.EscapeUtils;
import org.waveprotocol.box.server.rpc.render.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.box.server.rpc.render.uibuilder.BuilderHelper.Component;
import static org.waveprotocol.box.server.rpc.render.uibuilder.BuilderHelper.nonNull;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.close;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.image;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.open;
import org.waveprotocol.box.server.rpc.render.uibuilder.UiBuilder;
import org.waveprotocol.box.server.rpc.render.view.IntrinsicBlipMetaView;
import org.waveprotocol.box.server.rpc.render.view.TypeCodes;
import org.waveprotocol.box.server.rpc.render.view.View.Type;

/**
 */
public final class BlipMetaViewBuilder implements UiBuilder, IntrinsicBlipMetaView {

  /** An enum for all the components of a blip view. */
  public enum Components implements Component {
    /** The read status indicator. */
    INDICATOR("I"),    
    /** The avatar element. */
    AVATAR("A"),
    /** The text inside the information bar. */
    METALINE("M"),
    /** The element for the information bar. */
    METABAR("B"),
    /** The element containing the time text. */
    TIME("T"),
    /** The element containing the document. */
    CONTENT("C"),
    /** The element containing menu options. */
    BLIPMENUBUTTON("U");

    private final String postfix;

    Components(String postfix) {
      this.postfix = postfix;
    }

    @Override
    public String getDomId(String baseId) {
      return baseId + postfix;
    }
  }

  /**
   * A unique id for this builder.
   */
  private final String id;
  private final BlipViewBuilder.Css css;

  //
  // Intrinsic state.
  //

  private String time;
  private String metaline;
  private String avatarUrl;
  private String authorName;  

  //
  // Structural components.
  //

  private final UiBuilder content;

  /**
   * Creates a new blip view builder with the given id.
   *
   * @param id unique id for this builder, it must only contains alphanumeric
   *        characters
   */
  public static BlipMetaViewBuilder create(WavePanelResources resources,
      String id, UiBuilder content) {
    return new BlipMetaViewBuilder(resources.getBlip().css(),
        id, nonNull(content));
  }

  @VisibleForTesting
  BlipMetaViewBuilder(BlipViewBuilder.Css css, String id, UiBuilder content) {
    // must not contain ', it is especially troublesome because it cause
    // security issues.
    Preconditions.checkArgument(!id.contains("\'"));
    this.css = css;
    this.id = id;
    this.content = content;
  }

  @Override
  public void setAvatar(String avatarUrl, String authorName) {
    this.avatarUrl = avatarUrl;
    this.authorName = authorName;
  }

  @Override
  public void setTime(String time) {
    this.time = time;
  }

  @Override
  public void setMetaline(String metaline) {
    this.metaline = metaline;
  }

  @Override
  public void setRead(boolean read) {
  }

  //
  // DomImpl nature.
  //

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    // HACK HACK HACK
    // This code should be automatically generated from UiBinder template, not
    // hand written.

    open(output, id, css.meta(), TypeCodes.kind(Type.META));
    {      
      // Author avatar
      image(output, Components.AVATAR.getDomId(id), css.avatar(),
          EscapeUtils.fromString(avatarUrl),
          EscapeUtils.fromPlainText(authorName), null);

      // Metabar
      open(output, Components.METABAR.getDomId(id), css.metabar(), null);
      {
        // Metaline
        open(output, Components.METALINE.getDomId(id), css.metaline(), null);
        {
          if (metaline != null) {
            output.appendEscaped(metaline);
          }
        }  
        close(output);        
        
        // Time
        open(output, Components.TIME.getDomId(id), css.time(),
            TypeCodes.kind(Type.BLIP_TIME));
        {
          if (time != null) {
            output.appendEscaped(time);
          }
        }  
        close(output);

      }
      close(output);

      // Content
      open(output, Components.CONTENT.getDomId(id), css.contentContainer(),
          "document");
      {
        content.outputHtml(output);
      }  
      close(output);
    }
    close(output);
  }

  @Override
  public void setBlipUri(String blipUri) {
  }
}
