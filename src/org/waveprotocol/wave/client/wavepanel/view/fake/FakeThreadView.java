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
import org.waveprotocol.wave.client.wavepanel.view.PlaceholderView;
import org.waveprotocol.wave.client.wavepanel.view.ThreadView;
import org.waveprotocol.wave.client.wavepanel.view.View;
import org.waveprotocol.wave.model.conversation.ConversationBlip;

/**
 * Fake, pojo implementation of a thread view.
 *
 */
public abstract class FakeThreadView implements ThreadView {

  private final FakeRenderer renderer;
  private final LinkedSequence<FakeBlipView> blips;

  FakeThreadView(FakeRenderer renderer, LinkedSequence<FakeBlipView> blips) {
    this.renderer = renderer;
    this.blips = blips;

    for (FakeBlipView blip : blips) {
      blip.setContainer(this);
    }
  }

  @Override
  public String getId() {
    return "fakeId";
  }

  private FakeBlipView asBlip(View ref) {
    if (ref == null) {
      return null;
    } else {
      switch (ref.getType()) {
        case BLIP:
          return (FakeBlipView) ref;
        default:
          throw new RuntimeException("unknown child: " + ref);
      }
    }
  }

  @Override
  public FakeBlipView insertBlip(ConversationBlip blip, View neighbor, boolean beforeNeighbor) {
    FakeBlipView view = (FakeBlipView) renderer.render(blip);
    view.setContainer(this);
    FakeBlipView fakeNeighborView = asBlip(neighbor);
    if (beforeNeighbor) {
      blips.insertBefore(fakeNeighborView, view);
    } else {
      blips.insertAfter(fakeNeighborView, view);
    }
    return view;
  }

  @Override
  public PlaceholderView insertPlaceholder(View neighbor, boolean beforeNeighbor) {
    return null;
  }

  void removeChild(FakeBlipView blip) {
    blips.remove(blip);
  }

  protected String blipsToString() {
    return (blips.isEmpty() ? "" : " " + blips.toString());
  }
}
