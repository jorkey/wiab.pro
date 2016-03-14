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
import org.waveprotocol.box.server.rpc.render.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.box.server.rpc.render.uibuilder.HtmlClosure;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.close;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.openWith;
import org.waveprotocol.box.server.rpc.render.uibuilder.UiBuilder;
import org.waveprotocol.box.server.rpc.render.view.IntrinsicInlineThreadView;
import org.waveprotocol.box.server.rpc.render.view.TypeCodes;
import org.waveprotocol.box.server.rpc.render.view.View.Type;

/**
 * UI builder for an inline thread.
 */
public final class InlineThreadViewBuilder implements IntrinsicInlineThreadView, UiBuilder {

  /**
   * This final HtmlClosure sub class implements the internal structure of the inline thread.
   * It encapsulates the blips in a div container and then creates the continuation indicator
   * as a sibling of the blip container.  The InlineThreadViewBuilder class uses this to marry
   * these two components together before passing the closure off to a CollapsibleBuilder
   * which expects a single entity as its contents.
   */
  final static class InlineThreadStructure implements HtmlClosure {

    /** The closure representing the collection of blips. */
    private final HtmlClosure blips;

    /**
     * Creates a new InlineThreadStructure instance by combining the blips and
     * continuation indicator.
     */
    public static InlineThreadStructure create(HtmlClosure blips) {
      return new InlineThreadStructure(blips);
    }

    @VisibleForTesting
    InlineThreadStructure(HtmlClosure blips) {
      this.blips = blips;
    }

    @Override
    public void outputHtml(SafeHtmlBuilder output) {
      // For whitespace in an inline thread to get click events, it needs zoom:1
      // for some reason.
      String extra = false ? "style='zoom:1' unselectable='on'" : null;
      openWith(output, null, null, null, extra);
      {
        blips.outputHtml(output);
      }  
      close(output);
    }
  }

  /** DOM id. */
  private final String id;

  /** General-purpose collapsible DOM that implements this view. */
  private final CollapsibleBuilder impl;

  /**
   * Creates a UI builder for an inline thread.
   */
  public static InlineThreadViewBuilder create(WavePanelResources resources,
      String id, HtmlClosure blips) {
    // must not contain ', it is especially troublesome because it cause
    // security issues.
    Preconditions.checkArgument(!id.contains("\'"));
    InlineThreadStructure structure = InlineThreadStructure.create(blips);
    return new InlineThreadViewBuilder(id, CollapsibleBuilder.create(
        resources, id, TypeCodes.kind(Type.INLINE_THREAD), structure));
  }

  @VisibleForTesting
  InlineThreadViewBuilder(String id, CollapsibleBuilder impl) {
    this.id = id;
    this.impl = impl;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public void setCollapsed(boolean collapsed) {
    impl.setCollapsed(collapsed);
  }

  @Override
  public boolean isCollapsed() {
    return impl.isCollapsed();
  }

  @Override
  public void setTotalBlipCount(int totalBlipCount) {
    impl.setTotalBlipCount(totalBlipCount);
  }

  @Override
  public void setUnreadBlipCount(int unreadBlipCount) {
    impl.setUnreadBlipCount(unreadBlipCount);
  }

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    impl.outputHtml(output);
  }
}