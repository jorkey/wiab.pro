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
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;

import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.uibuilder.BuilderHelper;
import org.waveprotocol.wave.client.uibuilder.BuilderHelper.Component;
import org.waveprotocol.wave.client.uibuilder.OutputHelper;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicBlipView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;

/**
 * A implementation of the BlipView that output content as HTML string. This
 * class should be automatically generated from a template, but the template
 * generator is not ready yet.
 *
 */
public class BlipViewBuilder implements UiBuilder, IntrinsicBlipView {

  /** An enum for all the components of a blip view. */
  public enum Components implements Component {

    /** Top margin. */
    TOP_MARGIN("T"),
    
    /** Container for default anchors of reply threads. */
    REPLIES("R"),

    /** Container for nested conversations. */
    PRIVATE_REPLIES("P"),
    
    /** Bottom margin. */
    BOTTOM_MARGIN("B");

    private final String postfix;

    Components(String postfix) {
      this.postfix = postfix;
    }

    @Override
    public String getDomId(String baseId) {
      return baseId + postfix;
    }
  }

  /** Resources used by this widget. */
  public interface Resources extends ClientBundle {
    /** CSS */
    @Source("Blip.css")
    Css css();

    @Source("menu_button.png")
    ImageResource menuButton();
  }

  /** CSS for this widget. */
  public interface Css extends CssResource {
    /** The topmost blip element. */
    String blip();
    String opaque();
    String transparent();
    String deleted();
    String margin();
    String meta();
    String metaWithTopBorder();
    String indicator();
    String read();
    String unread();
    String avatar();
    String metabar();
    String metaline();
    String dummy();
    String timeArrow();
    String time();
    String arrow();
    String blipMenuButton();
    String contentContainer();
    String replies();
    String privateReplies();
    String frame();
    String frameCorner();
    String frameVertical();
    String frameVerticalWithoutRnd();
    String frameCornerWithoutRnd();
    String blipContinuationBar();
    String draftModeIndicator();
    String draftModeControls();
  }

  /** Additional margin for each new level, in em's. */
  public static double LEVEL_MARGIN_EM = 4.5;

  /**
   * Creates a new blip view builder with the given id.
   *
   * @param id unique id for this builder
   * @param innerThreads collection of non-inline innerThreads
   */
  public static BlipViewBuilder create(String id, UiBuilder meta, UiBuilder innerThreads) {
    // must not contain ', because it cause security issues.
    Preconditions.checkArgument(!id.contains("\'"));
    return new BlipViewBuilder(id, BuilderHelper.nonNull(meta), BuilderHelper.nonNull(innerThreads),
        WavePanelResourceLoader.getBlip().css());
  }

  /** A unique id for this builder. */
  private final String id;

  private final Css css;

  /** Blip number in the z-order. */
  private int zIndex;

  /** Blip level in the hierarchy. */
  private int level;

  /** Blip margins, in pixels. */
  private int topMargin;
  private int bottomMargin;

  //
  // Structural components.
  //

  private final UiBuilder meta;
  private final UiBuilder innerThreads;

  @VisibleForTesting
  BlipViewBuilder(String id, UiBuilder meta, UiBuilder innerThreads, Css css) {
    this.id = id;
    this.meta = meta;
    this.innerThreads = innerThreads;
    this.css = css;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public int getZIndex() {
    return zIndex;
  }

  @Override
  public void setZIndex(int zIndex) {
    this.zIndex = zIndex;
  }

  @Override
  public void setIndentationLevel(int level) {
    this.level = level;
  }

  @Override
  public void setMargins(int top, int bottom) {
    topMargin = top;
    bottomMargin = bottom;
  }

  @Override
  public void setQuasiDeleted(String title, boolean isRowOwnerDeleted) {
    // blip can't be made quasi-deleted before diffs receiving, so do nothing here
  }

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    OutputHelper.openWith(output, id, css.blip(), TypeCodes.kind(Type.BLIP),
        "style='z-index: " + zIndex + ";" +
        " margin-left: " + level * LEVEL_MARGIN_EM + "em;'");
    {
      // Top margin
      OutputHelper.appendWith(output, Components.TOP_MARGIN.getDomId(id), css.margin(),
          TypeCodes.kind(Type.BLIP_TOP_MARGIN), "style='height=" + topMargin + "px;'");
      
      // Meta (no wrapper).
      meta.outputHtml(output);

      // Inner threads
      OutputHelper.open(output, Components.REPLIES.getDomId(id), css.replies(),
          TypeCodes.kind(Type.BLIP_REPLIES));
      {
        innerThreads.outputHtml(output);
      }
      OutputHelper.close(output);
      
      // Bottom margin
      OutputHelper.appendWith(output, Components.BOTTOM_MARGIN.getDomId(id), css.margin(),
          TypeCodes.kind(Type.BLIP_BOTTOM_MARGIN), "style='height=" + bottomMargin + "px;'");
    }
    OutputHelper.close(output);
  }
}
