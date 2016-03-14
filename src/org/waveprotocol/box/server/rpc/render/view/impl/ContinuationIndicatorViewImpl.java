/**
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
 *
 */
package org.waveprotocol.box.server.rpc.render.view.impl;

import org.waveprotocol.box.server.rpc.render.view.BlipView;
import org.waveprotocol.box.server.rpc.render.view.ContinuationIndicatorView;
import org.waveprotocol.box.server.rpc.render.view.IntrinsicContinuationIndicatorView;

/**
 * Implements a continuation indicator on a blip.
 *
 * @param <I> intrinsic participants implementation
 */
public final class ContinuationIndicatorViewImpl
    <I extends IntrinsicContinuationIndicatorView> // \u2620
    extends AbstractStructuredView<ContinuationIndicatorViewImpl.
    Helper<? super I>, I> // \u2620
    implements ContinuationIndicatorView {

  /**
   * Handles structural queries on participants views.
   *
   * @param <I> intrinsic participants implementation
   */
  public interface Helper<I> {

    BlipView getParent(I impl);
  }

  public ContinuationIndicatorViewImpl(Helper<? super I> helper, I impl) {
    super(helper, impl);
  }

  @Override
  public String getId() {
    return impl.getId();
  }

  @Override
  public BlipView getParent() {
    return helper.getParent(impl);
  }
}
