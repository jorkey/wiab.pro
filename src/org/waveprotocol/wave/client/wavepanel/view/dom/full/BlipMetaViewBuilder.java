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
import com.google.common.base.Preconditions;
import org.waveprotocol.wave.client.common.safehtml.EscapeUtils;
import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.uibuilder.BuilderHelper.Component;
import static org.waveprotocol.wave.client.uibuilder.BuilderHelper.nonNull;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.append;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.close;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.closeSpan;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.image;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.open;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.openSpan;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicBlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;

/**
 * An implementation of the BlipMetaView that output content as HTML string.
 */
public final class BlipMetaViewBuilder implements UiBuilder, IntrinsicBlipMetaView {

  /** An enum for all the components of a blip meta view. */
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

    /** The element containing blip menu. */
    BLIPMENUBUTTON("U"),

    /** The frame around meta. */
    FRAME("F"),
    
    /** Editor mode indicator. */
    DRAFTMODECONTROLS("E");

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

  private final BlipViewBuilder.Css css;

  private final static String DOWN_ARROW = "\u25bc";

  private final static String MENU_BUTTON_TEXT = " " + DOWN_ARROW;

  //
  // Intrinsic state.
  //

  private String time;
  private String metaline;
  private String avatarUrl;
  private String authorName;
  private boolean read = true;
  private boolean topBorder;
  private boolean bottomBorder;
  private boolean leftBorder;
  private boolean isFirst;

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
  public static BlipMetaViewBuilder create(String id, UiBuilder content) {
    return new BlipMetaViewBuilder(id, nonNull(content), WavePanelResourceLoader.getBlip().css());
  }

  @VisibleForTesting
  BlipMetaViewBuilder(String id, UiBuilder content, BlipViewBuilder.Css css) {
    // must not contain ', because it cause security issues.
    Preconditions.checkArgument(!id.contains("\'"));
    this.id = id;
    this.content = content;
    this.css = css;
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
    this.read = read;
  }

  @Override
  public void setBorders(boolean top, boolean right, boolean bottom, boolean left,
      boolean isFirstBlip) {
    topBorder = top;
    bottomBorder = bottom;
    leftBorder = left;
    isFirst = isFirstBlip;
  }

  //
  // DomImpl nature.
  //

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    // Meta
    String metaClass = css.meta() + (topBorder? (" " + css.metaWithTopBorder()) : "");
    open(output, id, metaClass, TypeCodes.kind(Type.META));
    {
      // Read status indicator
      append(output, Components.INDICATOR.getDomId(id),
          css.indicator() + " " + (read ? css.read() : css.unread()),
          TypeCodes.kind(Type.BLIP_INDICATOR));

      // Author avatar
      image(output, Components.AVATAR.getDomId(id), css.avatar(), EscapeUtils.fromString(avatarUrl),
          EscapeUtils.fromPlainText(authorName), null, null);

      // Metabar
      open(output, Components.METABAR.getDomId(id), css.metabar(), TypeCodes.kind(Type.META_BAR));
      {
        // Metaline with author
        open(output, Components.METALINE.getDomId(id), css.metaline(), null);
        {
          if (metaline != null) {
            output.appendEscaped(metaline);
          }
        }
        close(output);

        // Time and menu button
        open(output, null, css.timeArrow(), TypeCodes.kind(Type.TIME_AND_MENU));
        {
          // Time
          openSpan(output, Components.TIME.getDomId(id), css.time(), TypeCodes.kind(Type.BLIP_TIME));
          {
            if (time != null) {
              output.appendEscaped(" " + time);
            }
          }
          closeSpan(output);

          // Menu button
          openSpan(output, Components.BLIPMENUBUTTON.getDomId(id), css.arrow(),
              TypeCodes.kind(Type.BLIP_MENU_BUTTON));
          {
            output.appendPlainText(MENU_BUTTON_TEXT);
          }
          closeSpan(output);
        }
        close(output);

        //Dummy (walkaround for problem with selection of the first text's line)
        append(output, null, css.dummy(), null);
      }
      close(output);

      // Content
      open(output, Components.CONTENT.getDomId(id), css.contentContainer(), TypeCodes.kind(Type.DOCUMENT));
      {
        content.outputHtml(output);
      }
      close(output);
      
      open(output, Components.DRAFTMODECONTROLS.getDomId(id), css.draftModeControls(), null);
      close(output);
      // Frame 
      
      /* .frameCorner => the rounded shadow on the left and bottom;
       * .frameVertical => the rounded shadow on the left;
       * .frameVerticalWithoutRnd => the left shadow without rounding;
       * .frameCornerWithoutRnd => the left shadow without rounding
       * and the rounded shadow on the bottom.
       */
      String frameClass = css.frame() +
          ((leftBorder && !bottomBorder && ((!topBorder) ||
              (topBorder && isFirst))) ? (" " + css.frameVertical()) : "") +
          ((leftBorder && bottomBorder && ((!topBorder) ||
              (topBorder && isFirst))) ? (" " + css.frameCorner()) : "") +
          ((leftBorder && !bottomBorder && topBorder && !isFirst) ?
              (" " + css.frameVerticalWithoutRnd()) : "") +
          ((leftBorder &&  bottomBorder && topBorder && !isFirst) ?
              (" " + css.frameCornerWithoutRnd()): "");
      append(output, Components.FRAME.getDomId(id), frameClass, null);

      // Blip continuation bar
      append(output, null, css.blipContinuationBar(), TypeCodes.kind(Type.BLIP_CONTINUATION_BAR));
    }
    close(output);
  }
}