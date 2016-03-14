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

import org.waveprotocol.box.webclient.search.SearchPresenter;
import org.waveprotocol.box.webclient.search.WaveContext;
import org.waveprotocol.box.webclient.search.WaveStore;
import org.waveprotocol.box.webclient.widget.frame.FramedPanel;
import org.waveprotocol.box.webclient.widget.loading.LoadingIndicator;
import org.waveprotocol.wave.client.StageOne;
import org.waveprotocol.wave.client.StageThree;
import org.waveprotocol.wave.client.StageTwo;
import org.waveprotocol.wave.client.StageZero;
import org.waveprotocol.wave.client.Stages;
import org.waveprotocol.wave.client.account.ProfileManager;
import org.waveprotocol.wave.client.common.util.AsyncHolder;
import org.waveprotocol.wave.client.common.util.AsyncHolder.Accessor;
import org.waveprotocol.wave.client.widget.common.ImplPanel;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;
import org.waveprotocol.wave.client.i18n.ErrorMessages;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.waveref.WaveRef;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;

import java.util.Set;

/**
 * Stages for loading the undercurrent Wave Panel
 *
 * @author zdwang@google.com (David Wang)
 */
public class StagesProvider extends Stages {
  private static final ErrorMessages errorMessages = GWT.create(ErrorMessages.class);

  private final static AsyncHolder<Object> HALT = new AsyncHolder<Object>() {
    @Override
    public void call(Accessor<Object> accessor) {
      // Never ready, so never notify the accessor.
    }
  };

  private final Element waveElement;
  private final FramedPanel waveFrame;
  private final ImplPanel waveHolder;
  private final WaveRef waveRef;
  private final WaveWebSocketClient webSocket;
  private final IdGenerator idGenerator;
  private final ProfileManager profileManager;
  private final WaveStore waveStore;
  private final boolean newWave;
  private final String localDomain;
  private final Set<ParticipantId> participants;
  private final boolean fromLastRead;
  private final SearchPresenter searchPresenter;
  private final LoadingIndicator loadingIndicator;
  private final UniversalPopup errorPopup;

  private StageOneProvider oneProvider;
  private StageTwoProvider twoProvider;
  private StageThreeProvider threeProvider;
  private StageOne one;
  private StageTwo two;
  private StageThree three;
  private WaveContext wave;
  private WindowTitleHandler windowTitleHandler;
  private boolean closed;

  /**
   * @param waveElement the DOM element to become the conversationView panel.
   * @param waveFrame the conversationView frame.
   * @param waveHolder a panel that this an ancestor of waveElement. This is
   *        used for adopting to the GWT widget tree.
   * @param waveRef the id of the conversationView to open. If null, it means, install a new
   *        conversationView.
   * @param webSocket the WebSocket of connection.
   * @param isNewWave true if the conversationView is a new client-created conversationView
   * @param profileManager the manager of user profiles.
   * @param waveStore access to a group of open waves.
   * @param idGenerator the generator of Ids.
   * @param localDomain local domain.
   * @param participants the participants to add to the newly created conversationView. null
   *                     if only the creator should be added
   * @param fromLastRead if true, the wave should be opened with diffs
   * @param searchPresenter the search presenter.
   * @param loadingIndicator the indicator of loading.
   * @param errorPopup the error popup.
   */
  public StagesProvider(Element waveElement, FramedPanel waveFrame, ImplPanel waveHolder,
      WaveRef waveRef, WaveWebSocketClient webSocket, IdGenerator idGenerator, ProfileManager profileManager,
      WaveStore waveStore, boolean isNewWave, String localDomain, Set<ParticipantId> participants,
      boolean fromLastRead, SearchPresenter searchPresenter, LoadingIndicator loadingIndicator,
      UniversalPopup errorPopup) {
    this.waveElement = waveElement;
    this.waveFrame = waveFrame;
    this.waveHolder = waveHolder;
    this.waveRef = waveRef;
    this.webSocket = webSocket;
    this.idGenerator = idGenerator;
    this.profileManager = profileManager;
    this.waveStore = waveStore;
    this.newWave = isNewWave;
    this.localDomain = localDomain;
    this.participants = participants;
    this.fromLastRead = fromLastRead;
    this.searchPresenter = searchPresenter;
    this.loadingIndicator = loadingIndicator;
    this.errorPopup = errorPopup;
  }

  /**
   * Closes the editor, marks deferred blips as read and
   * saves wave's current state to the supplement.
   */
  public void complete() {
    if (three != null) {
      three.getEditActions().stopEditing(false);
      three.getScreenController().complete();
    }

    if (one != null) {
      one.getSupplement().complete();
      one.getDynamicRenderer().complete();
      one.getFocusFrame().complete();
    }
  }

  /**
   * Closes the wave.
   */
  public void destroy() {
    if (wave != null) {
      waveStore.remove(wave);
      wave = null;
    }

    if (threeProvider != null) {
      threeProvider.destroy();
      threeProvider = null;
    }

    if (twoProvider != null) {
      twoProvider.destroy();
      twoProvider = null;
    }

    if (oneProvider != null) {
      oneProvider.destroy();
      oneProvider = null;
    }

    if (windowTitleHandler != null) {
      windowTitleHandler.deinstall();
      windowTitleHandler = null;
    }

    closed = true;
  }

  @Override
  protected AsyncHolder<StageZero> createStageZeroLoader() {
    return haltIfClosed(new StageZeroProvider());
  }

  @Override
  protected AsyncHolder<StageOne> createStageOneLoader(StageZero zero) {
    return haltIfClosed(oneProvider = new StageOneProvider(zero, waveHolder, waveElement, Session.get().getIdSeed(),
      ParticipantId.ofUnsafe(Session.get().getAddress()), fromLastRead, newWave, participants,
      webSocket, waveRef, idGenerator, profileManager, loadingIndicator, errorPopup, errorMessages));
  }

  @Override
  protected AsyncHolder<StageTwo> createStageTwoLoader(StageOne one) {
    return haltIfClosed(twoProvider = new StageTwoProvider(this.one = one, webSocket, waveRef, errorPopup, errorMessages));
  }

  @Override
  protected AsyncHolder<StageThree> createStageThreeLoader(final StageTwo two) {
    return haltIfClosed(threeProvider = new StageThreeProvider(this.two = two, Session.get().getIdSeed(),
      ParticipantId.ofUnsafe(Session.get().getAddress()), waveHolder, localDomain, idGenerator, profileManager) {

      @Override
      public void create(final Accessor<StageThree> whenReady) {
        // Prepend an init wave flow onto the stage continuation.
        super.create(new Accessor<StageThree>() {
          @Override
          public void use(StageThree x) {
            onStageThreeLoaded(x, whenReady);
          }
        });
      }
    });
  }

  private void onStageThreeLoaded(StageThree x, Accessor<StageThree> whenReady) {
    if (closed) {
      // Stop the loading process.
      return;
    }
    three = x;

    if (newWave) {
      // Init new wave.
      // Install the new-wave flow.
      ConversationBlip blip = one.getConversations().getRoot().getRootThread().getFirstBlip();
      three.getEditActions().startEditing(blip);
    }
    wave = new WaveContext(one.getWave(), one.getConversations(),
        one.getSupplement(), one.getReadMonitor(), one.getInboxStateMonitor(),
        one.getDynamicRenderer());

    //install title handler
    windowTitleHandler = WindowTitleHandler.install(waveStore, waveFrame, searchPresenter);

    waveStore.add(wave);
    whenReady.use(x);
  }

  /**
   * @return a halting provider if this stage is closed. Otherwise, returns the
   * given provider.
   */
  @SuppressWarnings("unchecked") // HALT is safe as a holder for any type
  private <T> AsyncHolder<T> haltIfClosed(AsyncHolder<T> provider) {
    return closed ? (AsyncHolder<T>) HALT : provider;
  }

  public StageThree getStageThree() {
    return three;
  }
}