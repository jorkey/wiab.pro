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

package org.waveprotocol.box.webclient.client;

import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;
import org.waveprotocol.box.webclient.flags.Flags;
import org.waveprotocol.wave.client.StageOne;
import org.waveprotocol.wave.client.StageThree;
import org.waveprotocol.wave.client.StageTwo;
import org.waveprotocol.wave.client.account.ProfileManager;
import org.waveprotocol.wave.client.doodad.selection.SelectionExtractor;
import org.waveprotocol.wave.client.editor.EditorStaticDeps;
import org.waveprotocol.wave.client.render.undercurrent.ScreenController;
import org.waveprotocol.wave.client.render.undercurrent.ScreenControllerImpl;
import org.waveprotocol.wave.client.scheduler.SchedulerInstance;
import org.waveprotocol.wave.client.wavepanel.impl.edit.Actions;
import org.waveprotocol.wave.client.wavepanel.impl.edit.ActionsImpl;
import org.waveprotocol.wave.client.wavepanel.impl.edit.BlipIndicatorController;
import org.waveprotocol.wave.client.wavepanel.impl.edit.BlipMenuController;
import org.waveprotocol.wave.client.wavepanel.impl.edit.EditController;
import org.waveprotocol.wave.client.wavepanel.impl.edit.EditSession;
import org.waveprotocol.wave.client.wavepanel.impl.edit.KeepFocusInView;
import org.waveprotocol.wave.client.wavepanel.impl.edit.ParticipantController;
import org.waveprotocol.wave.client.wavepanel.impl.edit.TagController;
import org.waveprotocol.wave.client.wavepanel.impl.indicator.ReplyIndicatorController;
import org.waveprotocol.wave.client.wavepanel.impl.title.WaveTitleHandler;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.ToolbarSwitcher;
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.ViewToolbar;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.ContinuationIndicatorController;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.DraftModeController;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.WavePanelResourceLoader;
import org.waveprotocol.wave.client.widget.common.ImplPanel;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.Wavelet;

/**
 * Implementation of the stage three configuration. Each component is
 * defined by a factory method, any of which may be overridden in order to
 * stub out some dependencies. Circular dependencies are not detected.
 *
 */
public class StageThreeProvider extends StageProvider<StageThree> {

  // Initial values.
  private final StageOne stageOne;
  private final StageTwo stageTwo;
  private final ImplPanel waveHolder;
  private final String localDomain;
  private final String sessionId;
  private final ParticipantId signedInUser;
  private final IdGenerator idGenerator;
  private final ProfileManager profileManager;

  // Created values.
  private Actions actions;
  private EditSession editSession;
  private ScreenController screenController;
  private SelectionExtractor selectionExtractor;

  public StageThreeProvider(StageTwo stageTwo, String sessionId, ParticipantId signedInUser,
      ImplPanel waveHolder, String localDomain, IdGenerator idGenerator, ProfileManager profileManager) {
    this.stageOne = stageTwo.getStageOne();
    this.stageTwo = stageTwo;
    this.waveHolder = waveHolder;
    this.localDomain = localDomain;
    this.sessionId = sessionId;
    this.signedInUser = signedInUser;
    this.idGenerator = idGenerator;
    this.profileManager = profileManager;
  }

  @Override
  public void destroy() {
    if (screenController != null) {
      screenController.destroy();
      screenController = null;
    }
  }

  /**
   * Creates the third stage.
   */

  @Override
  public void create(Accessor<StageThree> whenReady) {
    if (Flags.get().enableUndercurrentEditing()) {
      install();
    }
    whenReady.use(new StageThree() {

      @Override
      public Actions getEditActions() {
        return StageThreeProvider.this.getEditActions();
      }

      @Override
      public EditSession getEditSession() {
        return StageThreeProvider.this.getEditSession();
      }

      @Override
      public ScreenController getScreenController() {
        return StageThreeProvider.this.getScreenController();
      }
    });
  }

  // Public getters

  private EditSession getEditSession() {
    if (editSession == null) {
      editSession = createEditSession();
    }
    return editSession;
  }

  private Actions getEditActions() {
    if (actions == null) {
      actions = createEditActions();
    }
    return actions;
  }

  private ScreenController getScreenController() {
    if (screenController == null) {
      screenController = createScreenController();
    }
    return screenController;
  }

  // Protected getters

  private StageOne getStageOne() {
    return stageTwo.getStageOne();
  }

  private SelectionExtractor getSelectionExtractor() {
    if (selectionExtractor == null) {
      selectionExtractor = createSelectionExtractor();
    }
    return selectionExtractor;
  }

  // Protected "create something" methods

  private EditSession createEditSession() {
    EditSession session = EditSession.create(getStageOne().getFocusFrame(),
        getStageOne().getWavePanel(), getSelectionExtractor());

    stageTwo.getStageOne().getFocusFrame().upgrade(session);
    session.addListener(stageOne.getDynamicRenderer().getEditSessionListener());

    return session;
  }

  private Actions createEditActions() {
    return ActionsImpl.create(stageOne.getModelAsViewProvider(), getStageOne().getFocusFrame(),
        stageOne.getScreenPositionScroller(), getEditSession(), stageOne.getDynamicRenderer(),
        stageOne.getNavigator());
  }

  private ScreenController createScreenController() {
    Wavelet wavelet = stageOne.getWave().getRoot();
    WaveletId waveletId = wavelet != null ? wavelet.getId() : null;
    ScreenControllerImpl sc = ScreenControllerImpl.create(waveHolder,
        stageOne.getScreenPositionScroller(), stageOne.getSupplement(), waveletId);

    sc.addListener(stageOne.getDynamicRenderer().getScreenListener());
    sc.addListener(stageOne.getBlipReader().getScreenListener());

    return sc;
  }

  private SelectionExtractor createSelectionExtractor() {
    return new SelectionExtractor(SchedulerInstance.getLowPriorityTimer(),
        signedInUser.getAddress(), sessionId);
  }

  /**
   * Installs parts of stage three that have dependencies.
   * <p>
   * This method is only called once all asynchronously loaded components of
   * stage three are ready.
   * <p>
   * Subclasses may override this to change the set of installed features.
   */
  private void install() {
    Timer timer = Timing.start("StageThreeImpl.install");
    try {
      EditorStaticDeps.setPopupProvider(PopupFactory.getProvider());
      EditorStaticDeps.setPopupChromeProvider(PopupChromeFactory.getProvider());

      // Eagerly create some features

      ViewToolbar viewToolbar = ViewToolbar.create(getStageOne().getFocusFrame(),
          stageOne.getScreenPositionScroller(), stageOne.getConversations(),
          stageOne.getNavigator(), stageOne.getBlipReader(), stageOne.getSupplement());
      ToolbarSwitcher.install(getStageOne().getWavePanel(), getEditSession(), viewToolbar,
          signedInUser, idGenerator, stageOne.getWave().getWaveId());

      WaveTitleHandler.install(stageOne.getNavigator(), getEditSession(),
          stageOne.getConversations());

      ReplyIndicatorController.install(getStageOne().getWavePanel(), stageOne.getModelAsViewProvider(),
          getEditActions());

      EditController.install(getStageOne().getFocusFrame(), getEditActions(),
          getStageOne().getWavePanel());

      ParticipantController.install(getStageOne().getWavePanel(), stageOne.getModelAsViewProvider(),
          stageOne.getSupplement(), profileManager,
          stageOne.getContactManager(), stageOne.getWave().getRootId(), localDomain,
          WavePanelResourceLoader.getParticipants().css(),
          WavePanelResourceLoader.getParticipantMessages());

      TagController.install(getStageOne().getWavePanel(), stageOne.getModelAsViewProvider(),
          stageOne.getSupplement(), stageOne.getWave().getRootId(),
          WavePanelResourceLoader.getTags().css(), WavePanelResourceLoader.getTagMessages());

      BlipMenuController.install(getStageOne().getWavePanel(), stageOne.getModelAsViewProvider(),
          getEditActions(), stageOne.getNavigator());

      ContinuationIndicatorController.install(getStageOne().getWavePanel(),
          stageOne.getModelAsViewProvider(), stageOne.getNavigator(), getEditActions());

      DraftModeController.install(getStageOne().getWavePanel(), actions,
          stageOne.getModelAsViewProvider(), getEditSession());

      KeepFocusInView.install(getEditSession(), getStageOne().getWavePanel());

      BlipIndicatorController.install(getStageOne().getWavePanel(), stageOne.getModelAsViewProvider(),
          stageOne.getBlipReader(), stageOne.getNavigator());

      stageOne.getDynamicRenderer().upgrade(getScreenController(), getEditSession());
    } finally {
      Timing.stop(timer);
    }
  }
}
