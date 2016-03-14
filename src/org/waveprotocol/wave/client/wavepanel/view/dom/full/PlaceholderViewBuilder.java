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

import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.uibuilder.OutputHelper;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.wavepanel.render.DynamicRendererImpl.Css;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicPlaceholderView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;

/**
 * A PlaceholderViewBuilder to build a placeholder.
 */
public class PlaceholderViewBuilder
    implements UiBuilder, IntrinsicPlaceholderView {

  /**
   * Creates a new PlaceholderViewBuilder.
   */
  public static PlaceholderViewBuilder create() {
    return new PlaceholderViewBuilder(WavePanelResourceLoader.getRender().css());
  }

  private final Css css;

  PlaceholderViewBuilder(Css css) {
    this.css = css;
  }

  // Output

  @Override
  public void outputHtml(SafeHtmlBuilder out) {
    OutputHelper.appendWith(out, null, css.placeholder(), TypeCodes.kind(Type.PLACEHOLDER), null);
  }
}
