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

package org.waveprotocol.wave.client.wavepanel.view.fake;

import org.waveprotocol.wave.client.common.util.LinkedSequence;
import org.waveprotocol.wave.client.wavepanel.view.AnchorView;
import org.waveprotocol.wave.client.wavepanel.view.BlipLinkPopupView;
import org.waveprotocol.wave.client.wavepanel.view.BlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.ConversationView;
import org.waveprotocol.wave.client.wavepanel.view.OutlineThreadView;
import org.waveprotocol.wave.client.wavepanel.view.ThreadView;
import org.waveprotocol.wave.client.wavepanel.view.View;
import org.waveprotocol.wave.model.conversation.ConversationThread;

/**
 * Fake, pojo implementation of a blip view.
 *
 */
public final class FakeBlipView implements BlipView {

  private final FakeRenderer renderer;
  private final LinkedSequence<FakeAnchor> anchors;
  private final FakeBlipMetaView meta;

  private FakeThreadView container;

  FakeBlipView(FakeRenderer renderer, LinkedSequence<FakeAnchor> anchors) {
    this.meta = new FakeBlipMetaView(renderer, this);
    this.renderer = renderer;
    this.anchors = anchors;

    for (FakeAnchor anchor : anchors) {
      anchor.setContainer(this);
    }
  }

  void setContainer(FakeThreadView container) {
    this.container = container;
  }

  @Override
  public Type getType() {
    return Type.BLIP;
  }

  @Override
  public String getId() {
    return "fakeId";
  }

  @Override
  public int getZIndex() {
    return 0;
  }

  @Override
  public void setZIndex(int zIndex) {
    // do nothing here
  }

  @Override
  public void setIndentationLevel(int level) {
    // do nothing here
  }

  @Override
  public void setMargins(int top, int bottom) {
    // do nothing here
  }

  @Override
  public ThreadView getParent() {
    return container;
  }

  @Override
  public ConversationView getConversation() {
    return null;
  }

  @Override
  public FakeBlipMetaView getMeta() {
    return meta;
  }

  @Override
  public void remove() {
    container.removeChild(this);
  }

  @Override
  public void setQuasiDeleted(String delete, boolean isRowOwnerDeleted) {
    // do nothing here
  }

  @Override
  public FakeAnchor insertDefaultAnchor(ConversationThread thread, AnchorView neighbor,
      boolean beforeNeighbor) {
    FakeAnchor anchor = (FakeAnchor) renderer.render(thread);
    anchor.setContainer(this);
    FakeAnchor fakeNeighborAnchor = asAnchorUi(neighbor);
    if (beforeNeighbor) {
      anchors.insertBefore(fakeNeighborAnchor, anchor);
    } else {
      anchors.insertAfter(fakeNeighborAnchor, anchor);
    }
    return anchor;
  }

  @Override
  public void insertOutlineThread(ConversationThread thread) {
    // do nothing here
  }

  @Override
  public BlipLinkPopupView createLinkPopup() {
    return new FakeBlipLinkPopupView(this);
  }

  void removeChild(FakeAnchor x) {
    anchors.remove(x);
  }

  void removeChild(BlipMetaView x) {
    throw new UnsupportedOperationException("Fakes do not support dynamic metas");
  }

  private FakeAnchor asAnchorUi(View ref) {
    return (FakeAnchor) ref;
  }

  private FakeInlineConversationView asConvUi(View ref) {
    return (FakeInlineConversationView) ref;
  }

  @Override
  public String toString() {
    return "Blip [" + //
      "meta: " + meta + //
      (anchors.isEmpty() ? "" : ", default-anchors: " + anchors.toString()) + //
      "]";
  }

  @Override
  public boolean isFocused() {
    return false;
  }

  @Override
  public boolean isBeingEdited() {
    return false;
  }

  @Override
  public void detach(OutlineThreadView view) {
    // do nothing here
  }
}
