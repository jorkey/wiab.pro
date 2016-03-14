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
import org.waveprotocol.box.server.rpc.render.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.box.server.rpc.render.uibuilder.BuilderHelper.Component;
import org.waveprotocol.box.server.rpc.render.uibuilder.HtmlClosure;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.close;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.open;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.openWith;
import org.waveprotocol.box.server.rpc.render.uibuilder.UiBuilder;
import org.waveprotocol.box.server.rpc.render.view.IntrinsicThreadView;
import org.waveprotocol.box.server.rpc.render.view.TypeCodes;
import org.waveprotocol.box.server.rpc.render.view.View.Type;

/**
 * A implementation of the ThreadView that output content as HTML string. This
 * class should be automatically generated from a template, but the template
 * generator is not ready yet.
 *
 */
public final class RootThreadViewBuilder implements IntrinsicThreadView, UiBuilder {
  
  /** Name of the attribute that stores the total blips. */
  public final static String TOTAL_BLIPS_ATTRIBUTE = "t";
  
  /** Name of the attribute that stores the number of unread blips. */
  public final static String UNREAD_BLIPS_ATTRIBUTE = "u";
  
  /** Resources used by this widget. */
  public interface Resources {
    Css css();
  }

  public interface Css {
    String thread();
  }

  public enum Components implements Component {
    /** Container of the blips container. */
    BLIPS("B"),
    ;
    private final String suffix;

    Components(String suffix) {
      this.suffix = suffix;
    }

    @Override
    public String getDomId(String baseId) {
      return baseId + suffix;
    }
  }

  private final Css css;
  private final String id;

  private int totalBlipCount;
  private int unreadBlipCount;
  
  //
  // Structure.
  //

  private final HtmlClosure blips;
  private final UiBuilder replyBox;

  @VisibleForTesting
  RootThreadViewBuilder(String id, HtmlClosure blips, UiBuilder replyBox, Css css) {
    this.css = css;
    this.id = id;
    this.blips = blips;
    this.replyBox = replyBox;
  }

  public static RootThreadViewBuilder create(WavePanelResources resources, String id,
      HtmlClosure blips, UiBuilder replyBox) {
    return new RootThreadViewBuilder(id, blips, replyBox, resources.getRootThread().css());
  }

  public static RootThreadViewBuilder create(WavePanelResources resources, String id,
      UiBuilder clickToReply) {
    return create(resources, id, HtmlClosure.EMPTY, clickToReply);
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public void setTotalBlipCount(int totalBlipCount) {
    this.totalBlipCount = totalBlipCount;
  }

  @Override
  public void setUnreadBlipCount(int unreadBlipCount) {
    this.unreadBlipCount = unreadBlipCount;
  }
  
  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    String extra = " " + TOTAL_BLIPS_ATTRIBUTE + "='" + totalBlipCount + "'" +
      " " + UNREAD_BLIPS_ATTRIBUTE + "='" + unreadBlipCount + "'";
    
    openWith(output, id, css.thread(), TypeCodes.kind(Type.ROOT_THREAD), extra);
    {
      open(output, Components.BLIPS.getDomId(id), null, null);
      blips.outputHtml(output);
      close(output);
      
      replyBox.outputHtml(output);
    }
    close(output);
  }
}