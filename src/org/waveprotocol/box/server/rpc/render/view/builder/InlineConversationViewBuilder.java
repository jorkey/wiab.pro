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
import static org.waveprotocol.box.server.rpc.render.uibuilder.BuilderHelper.compose;
import org.waveprotocol.box.server.rpc.render.uibuilder.UiBuilder;
import org.waveprotocol.box.server.rpc.render.view.IntrinsicInlineConversationView;
import org.waveprotocol.box.server.rpc.render.view.TypeCodes;
import org.waveprotocol.box.server.rpc.render.view.View.Type;

/**
 * UI builder for an inline thread.
 *
 */
public final class InlineConversationViewBuilder implements IntrinsicInlineConversationView,
    UiBuilder {
  /** General-purpose collapsible DOM that implements this view. */
  private final CollapsibleBuilder impl;

  /**
   * Creates a UI builder for an inline thread.
   */
  public static InlineConversationViewBuilder create(WavePanelResources resources, String id,
      UiBuilder participants, UiBuilder thread) {
    // must not contain ', it is especially troublesome because it cause
    // security issues.
    Preconditions.checkArgument(!id.contains("\'"));
    return new InlineConversationViewBuilder(CollapsibleBuilder.create(resources, id,
        TypeCodes.kind(Type.INLINE_CONVERSATION), compose(participants, thread)));
  }

  @VisibleForTesting
  InlineConversationViewBuilder(CollapsibleBuilder impl) {
    this.impl = impl;
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
  public void outputHtml(SafeHtmlBuilder output) {
    impl.outputHtml(output);
  }
}
