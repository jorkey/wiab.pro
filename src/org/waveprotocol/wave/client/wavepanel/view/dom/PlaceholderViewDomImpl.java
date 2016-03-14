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

package org.waveprotocol.wave.client.wavepanel.view.dom;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;

import org.waveprotocol.wave.client.wavepanel.view.IntrinsicPlaceholderView;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TopConversationViewBuilder;

/**
 * DOM implementation of the placeholder view.
 */
public final class PlaceholderViewDomImpl
    implements DomView, IntrinsicPlaceholderView {

  public static PlaceholderViewDomImpl of(Element e, TopConversationViewBuilder.Css css) {
    return new PlaceholderViewDomImpl(e, css);
  }

  /** The DOM element of this view. */
  private final Element self;

  /** The CSS classes used to manipulate style based on state changes. */
  private final TopConversationViewBuilder.Css css;

  PlaceholderViewDomImpl(Element self, TopConversationViewBuilder.Css css) {
    this.self = self;
    this.css = css;
  }

  public void remove() {
    getElement().removeFromParent();
  }

  // DomView nature.

  @Override
  public Element getElement() {
    return self;
  }

  @Override
  public String getId() {
    return self.getId();
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj;
  }

  @Override
  public int hashCode() {
    return DomViewHelper.hashCode(this);
  }
}
