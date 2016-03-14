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
 *
 */
package org.waveprotocol.box.server.rpc.render.view.impl;

import org.waveprotocol.box.server.rpc.render.view.AnchorView;
import org.waveprotocol.box.server.rpc.render.view.BlipMetaView;
import org.waveprotocol.box.server.rpc.render.view.BlipView;
import org.waveprotocol.box.server.rpc.render.view.FocusFrameView;
import org.waveprotocol.box.server.rpc.render.view.IntrinsicBlipMetaView;

/**
 * Implements a blip view by delegating primitive state matters to a view
 * object, and structural state matters to a helper. The intent is that the
 * helper is a flyweight handler.
 *
 * @param <I> intrinsic blip implementation
 */
public final class BlipMetaViewImpl<I extends IntrinsicBlipMetaView> // \u2620
    extends AbstractStructuredView<BlipMetaViewImpl.Helper<? super I>, I> // \u2620
    implements BlipMetaView {

  /**
   * Handles structural queries on blip views.
   *
   * @param <I> intrinsic blip implementation
   */
  public interface Helper<I> {

    void insertChrome(I impl, FocusFrameView frame);

    void removeChrome(I impl, FocusFrameView frame);

    //
    // Anchors
    //

    AnchorView getInlineAnchorBefore(I impl, AnchorView ref);

    AnchorView getInlineAnchorAfter(I impl, AnchorView ref);

    void insertInlineAnchorBefore(I impl, AnchorView ref, AnchorView x);

    //
    // Structure
    //

    BlipView getBlip(I impl);
  }

  public BlipMetaViewImpl(Helper<? super I> helper, I impl) {
    super(helper, impl);
  }

  //
  // Structural delegation.
  //

  @Override
  public BlipView getParent() {
    return helper.getBlip(impl);
  }

  @Override
  public AnchorView getInlineAnchorAfter(AnchorView ref) {
    return helper.getInlineAnchorAfter(impl, ref);
  }

  @Override
  public AnchorView getInlineAnchorBefore(AnchorView ref) {
    return helper.getInlineAnchorBefore(impl, ref);
  }

  @Override
  public void insertInlineAnchorBefore(AnchorView ref, AnchorView x) {
    helper.insertInlineAnchorBefore(impl, ref, x);
  }

  @Override
  public void placeFocusFrame(FocusFrameView frame) {
    helper.insertChrome(impl, frame);
  }

  @Override
  public void removeFocusChrome(FocusFrameView frame) {
    helper.removeChrome(impl, frame);
  }

  //
  // Intrinsic delegation.
  //

  @Override
  public void setTime(String time) {
    impl.setTime(time);
  }

  @Override
  public void setAvatar(String imageUrl, String authorName) {
     impl.setAvatar(imageUrl, authorName);
  }

  @Override
  public void setMetaline(String metaline) {
    impl.setMetaline(metaline);
  }

  @Override
  public void setRead(boolean read) {
    impl.setRead(read);
  }

  @Override
  public void setBlipUri(String blipUri) {
    impl.setBlipUri(blipUri);

  }
}
