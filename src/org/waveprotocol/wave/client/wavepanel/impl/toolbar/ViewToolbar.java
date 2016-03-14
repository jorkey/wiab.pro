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

package org.waveprotocol.wave.client.wavepanel.impl.toolbar;

import com.google.gwt.core.client.GWT;

import org.waveprotocol.wave.client.common.util.KeyComboContext;
import org.waveprotocol.wave.client.common.util.KeyComboManager;
import org.waveprotocol.wave.client.common.util.KeyComboTask;
import org.waveprotocol.wave.client.wave.LocalSupplementedWave;
import org.waveprotocol.wave.client.wavepanel.impl.focus.FocusBlipSelector;
import org.waveprotocol.wave.client.wavepanel.impl.blipreader.BlipReader;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.ViewToolbarResources.Css;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.i18n.ToolbarMessages;
import org.waveprotocol.wave.client.wavepanel.render.ScreenPositionScroller;
import org.waveprotocol.wave.client.widget.toolbar.ToolbarButtonViewBuilder;
import org.waveprotocol.wave.client.widget.toolbar.ToolbarView;
import org.waveprotocol.wave.client.widget.toolbar.ToplevelToolbarWidget;
import org.waveprotocol.wave.client.widget.toolbar.buttons.ToolbarClickButton;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.conversation.focus.ObservableFocusFramePresenter;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;
import org.waveprotocol.wave.model.supplement.SupplementedWave;

/**
 * Attaches actions that can be performed in a Wave's "view mode" to a toolbar.
 *
 * @author kalman@google.com (Benjamin Kalman)
 */
public final class ViewToolbar {

  private final static ToolbarMessages messages = GWT.create(ToolbarMessages.class);

  private final ToplevelToolbarWidget toolbarUi;
  private final ObservableFocusFramePresenter focusFrame;
  private final ScreenPositionScroller scroller;
  private final BlipReader reader;
  private final SupplementedWave supplement;
  private final Css css;
  private final FocusBlipSelector blipSelector;

  private ViewToolbar(ToplevelToolbarWidget toolbarUi, ObservableFocusFramePresenter focusFrame,
      ScreenPositionScroller scroller, ConversationView wave, ConversationNavigator navigator,
      BlipReader reader, SupplementedWave supplement, ViewToolbarResources.Css css) {
    this.toolbarUi = toolbarUi;
    this.focusFrame = focusFrame;
    this.scroller = scroller;
    this.reader = reader;
    this.supplement = supplement;
    this.css = css;

    blipSelector = FocusBlipSelector.create(wave, navigator);
  }

  public static ViewToolbar create(ObservableFocusFramePresenter focusFrame,
      ScreenPositionScroller scroller, ConversationView wave, ConversationNavigator navigator,
      BlipReader reader, LocalSupplementedWave supplement) {
    return new ViewToolbar(new ToplevelToolbarWidget(), focusFrame, scroller, wave, navigator,
        reader, supplement, ViewToolbarResources.Loader.res.css());
  }

  public void init() {
    toolbarUi.clearItems();
    ToolbarView group = toolbarUi.addGroup();

    // "Recent" button
    new ToolbarButtonViewBuilder()
        .setText(messages.recent()).setIcon(css.recent())
        .setTooltip(messages.recentHint())
        .applyTo(group.addClickButton(),
        new ToolbarClickButton.Listener() {

      @Override
      public void onClicked() {
        gotoMostRecent();
      }
    });

    // "Next Unread" button
    new ToolbarButtonViewBuilder()
        .setText(messages.nextUnread()).setIcon(css.nextUnread())
        .setTooltip(messages.nextUnreadHint())
        .setShortcut(KeyComboManager.getKeyComboHintByTask(KeyComboContext.WAVE,
            KeyComboTask.FOCUS_NEXT_UNREAD_BLIP))
        .applyTo(group.addClickButton(),
        new ToolbarClickButton.Listener() {

      @Override
      public void onClicked() {
        gotoNextUnread();
      }
    });

    // "Previous" button
    new ToolbarButtonViewBuilder()
        .setText(messages.previous()).setIcon(css.previous())
        .setTooltip(messages.previousHint())
        .setShortcut(KeyComboManager.getKeyComboHintByTask(KeyComboContext.WAVE,
            KeyComboTask.FOCUS_PREVIOUS_BLIP))
        .applyTo(group.addClickButton(),
        new ToolbarClickButton.Listener() {

      @Override
      public void onClicked() {
        gotoNeighbor(false);
      }
    });

    // "Next" button
    new ToolbarButtonViewBuilder()
        .setText(messages.next()).setIcon(css.next())
        .setTooltip(messages.nextHint())
        .setShortcut(KeyComboManager.getKeyComboHintByTask(KeyComboContext.WAVE,
            KeyComboTask.FOCUS_NEXT_BLIP))
        .applyTo(group.addClickButton(),
        new ToolbarClickButton.Listener() {

      @Override
      public void onClicked() {
        gotoNeighbor(true);
      }
    });

    // "To Archive" button
    if (supplement.isInbox()) {
      new ToolbarButtonViewBuilder()
          .setText(messages.toArchive()).setIcon(css.archive())
          .setTooltip(messages.toArchiveHint())
          .applyTo(group.addClickButton(),
          new ToolbarClickButton.Listener() {

        @Override
        public void onClicked() {
          toArchive();
        }
      });
    }

    // "To Inbox" button
    if (supplement.isArchived()) {
      new ToolbarButtonViewBuilder()
          .setText(messages.toInbox()).setIcon(css.inbox())
          .setTooltip(messages.toInboxHint())
          .applyTo(group.addClickButton(),
          new ToolbarClickButton.Listener() {

        @Override
        public void onClicked() {
          toInbox();
        }
      });
    }

    // "Read All" button
    new ToolbarButtonViewBuilder()
        .setText(messages.readAll()).setIcon(css.readAll())
        .setTooltip(messages.readAllHint())
        .applyTo(group.addClickButton(),
        new ToolbarClickButton.Listener() {

      @Override
      public void onClicked() {
        readAll();
      }
    });

    // Fake group
    group = toolbarUi.addGroup();
    new ToolbarButtonViewBuilder().setText("").applyTo(group.addClickButton(), null);
  }

  /**
   * @return the {@link ToplevelToolbarWidget} backing this toolbar.
   */
  public ToplevelToolbarWidget getWidget() {
    return toolbarUi;
  }

  private void gotoMostRecent() {
    gotoBlip(blipSelector.selectMostRecentlyModified());
  }

  private void gotoNeighbor(boolean next) {
    gotoBlip(focusFrame.getNeighborBlip(next));
  }

  private void gotoNextUnread() {
    gotoBlip(focusFrame.getNextUnreadBlip());
  }

  private void gotoBlip(ConversationBlip blip) {
    if (blip != null) {
      focusFrame.focus(blip);
      scroller.scrollToBlip(blip);
    }
  }

  private void toArchive() {
    supplement.archive();
    init();
  }

  private void toInbox() {
    supplement.inbox();
    init();
  }

  private void readAll() {
    reader.read();
  }
}
