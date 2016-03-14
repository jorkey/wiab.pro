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
import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.uibuilder.HtmlClosure;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicThreadView;

/**
 * UI builder for an inline thread.
 */
public final class OutlineThreadViewBuilder implements IntrinsicThreadView, UiBuilder {

  /** DOM id. */
  private final String id;

  /** General-purpose collapsible DOM that implements this view. */
  private final HtmlClosure impl;

  /**
   * Creates a UI builder for an inline thread.
   */
  public static OutlineThreadViewBuilder create(
      String id,
      HtmlClosure blips) {
    return new OutlineThreadViewBuilder(
        id,
        blips);
  }

  @VisibleForTesting
  OutlineThreadViewBuilder(String id, HtmlClosure impl) {
    this.id = id;
    this.impl = impl;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    impl.outputHtml(output);
  }

  @Override
  public void setTotalBlipCount(int totalBlipCount) {
  }  
  
  @Override
  public void setUnreadBlipCount(int unreadBlipCount) {
  }  
}