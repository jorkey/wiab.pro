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

package org.waveprotocol.wave.client.wavepanel.view.impl;

import org.waveprotocol.wave.client.wavepanel.view.IntrinsicPlaceholderView;
import org.waveprotocol.wave.client.wavepanel.view.PlaceholderView;
import org.waveprotocol.wave.client.wavepanel.view.ThreadView;
import org.waveprotocol.wave.client.wavepanel.view.View;

/**
 * Implements a blip view by delegating primitive state matters to a view
 * object, and structural state matters to a helper. The intent is that the
 * helper is a flyweight handler.
 *
 * @param <I> intrinsic blip implementation
 */
public final class PlaceholderViewImpl<I extends IntrinsicPlaceholderView>
    extends AbstractStructuredView<PlaceholderViewImpl.Helper<? super I>, I>
    implements PlaceholderView {

  /**
   * Handles structural queries on blip views.
   *
   * @param <I> intrinsic blip implementation
   */
  public interface Helper<I> {

    public void removePlaceholder(I impl);

    ThreadView getPlaceholderParent(I impl);
  }

  public PlaceholderViewImpl(PlaceholderViewImpl.Helper<? super I> helper, I impl) {
    super(helper, impl);
  }

  // Getters

  @Override
  public View.Type getType() {
    return View.Type.PLACEHOLDER;
  }

  // Structural delegation

  @Override
  public ThreadView getParent() {
    return helper.getPlaceholderParent(impl);
  }

  @Override
  public void remove() {
    helper.removePlaceholder(impl);
  }
}
