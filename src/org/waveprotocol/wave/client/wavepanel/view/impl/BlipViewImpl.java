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

import org.waveprotocol.wave.client.wavepanel.view.AnchorView;
import org.waveprotocol.wave.client.wavepanel.view.BlipLinkPopupView;
import org.waveprotocol.wave.client.wavepanel.view.BlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.ConversationView;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicBlipView;
import org.waveprotocol.wave.client.wavepanel.view.OutlineThreadView;
import org.waveprotocol.wave.client.wavepanel.view.ThreadView;
import org.waveprotocol.wave.model.conversation.ConversationThread;

/**
 * Implements a blip view by delegating primitive state matters to a view
 * object, and structural state matters to a helper. The intent is that the
 * helper is a flyweight handler.
 *
 * @param <I> intrinsic blip implementation
 */
public final class BlipViewImpl<I extends IntrinsicBlipView> // \u2620
    extends AbstractStructuredView<BlipViewImpl.Helper<? super I>, I> // \u2620
    implements BlipView {

  /**
   * Handles structural queries on blip views.
   *
   * @param <I> intrinsic blip implementation
   */
  public interface Helper<I> {

    ThreadView getBlipParent(I impl);

    ConversationView getBlipConversation(I impl);

    BlipMetaView getMeta(I impl);

    void removeBlip(I impl);

    //
    // Anchors
    //

    AnchorView getDefaultAnchorBefore(I impl, AnchorView ref);

    AnchorView getDefaultAnchorAfter(I impl, AnchorView ref);

    AnchorView insertDefaultAnchor(I impl, ConversationThread thread, AnchorView neighbor,
        boolean beforeNeighbor);

    void insertOutlineThread(I impl, ConversationThread thread);

    BlipLinkPopupView createLinkPopup(I impl);
  }

  public BlipViewImpl(Helper<? super I> helper, I impl) {
    super(helper, impl);
  }

  @Override
  public Type getType() {
    return Type.BLIP;
  }

  @Override
  public String getId() {
    return impl.getId();
  }

  @Override
  public int getZIndex() {
    return impl.getZIndex();
  }

  @Override
  public void setZIndex(int zIndex) {
    impl.setZIndex(zIndex);
  }

  @Override
  public void setIndentationLevel(int level) {
    impl.setIndentationLevel(level);
  }

  @Override
  public void setMargins(int top, int bottom) {
    impl.setMargins(top, bottom);
  }

  @Override
  public void setQuasiDeleted(String title, boolean isRowOwnerDeleted) {
    impl.setQuasiDeleted(title, isRowOwnerDeleted);
  }

  // Structural delegation

  @Override
  public BlipMetaView getMeta() {
    return helper.getMeta(impl);
  }

  @Override
  public AnchorView insertDefaultAnchor(ConversationThread thread, AnchorView neighbor,
      boolean beforeNeighbor) {
    return helper.insertDefaultAnchor(impl, thread, neighbor, beforeNeighbor);
  }

  @Override
  public void insertOutlineThread(ConversationThread thread) {
    helper.insertOutlineThread(impl, thread);
  }

  @Override
  public ThreadView getParent() {
    return helper.getBlipParent(impl);
  }

  @Override
  public ConversationView getConversation() {
    return helper.getBlipConversation(impl);
  }

  @Override
  public void remove() {
    helper.removeBlip(impl);
  }

  @Override
  public BlipLinkPopupView createLinkPopup() {
    return helper.createLinkPopup(impl);
  }

  @Override
  public boolean isFocused() {
    BlipMetaView meta = getMeta();
    return meta != null && meta.hasFocusChrome();
  }

  @Override
  public boolean isBeingEdited() {
    BlipMetaView meta = getMeta();
    return meta != null && meta.hasEditedFocusChrome();
  }

  @Override
  public void detach(OutlineThreadView view) {
    // do nothing here
  }
}
