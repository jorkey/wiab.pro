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


package org.waveprotocol.wave.client.testing;

import org.waveprotocol.box.webclient.client.FragmentRequester;
import org.waveprotocol.box.webclient.client.StageProvider;

import org.waveprotocol.wave.client.StageOne;
import org.waveprotocol.wave.client.StageTwo;
import org.waveprotocol.wave.client.account.ContactManager;
import org.waveprotocol.wave.client.concurrencycontrol.MuxConnector;
import org.waveprotocol.wave.client.state.BlipReadStateMonitor;
import org.waveprotocol.wave.client.state.InboxStateMonitor;
import org.waveprotocol.wave.client.wave.LocalSupplementedWave;
import org.waveprotocol.wave.client.wavepanel.impl.blipreader.BlipReader;
import org.waveprotocol.wave.client.wavepanel.render.DynamicDomRenderer;
import org.waveprotocol.wave.client.wavepanel.render.ScreenPositionDomScroller;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationView;
import org.waveprotocol.wave.model.wave.opbased.ObservableWaveView;

/**
 * The first stage of Undercurrent code.
 * <p>
 * This exposes minimal features required for basic reading interactions.
 */
public class StageTwoTestProvider extends StageProvider<StageTwo> {

  public StageTwoTestProvider(StageOne previous) {
    // Nothing in stage one depends on anything in stage zero currently, but
    // the dependency is wired up so that it is simple to add such
    // dependencies should they be necessary in the future.
  }

  @Override
  protected final void create(Accessor<StageTwo> whenReady) {
    install();
    whenReady.use(new StageTwo() {

      @Override
      public StageOne getStageOne() {
        throw new UnsupportedOperationException();
      }

      @Override
      public MuxConnector getConnector() {
        throw new UnsupportedOperationException();
      }
    });
  }

  @Override
  public void destroy() {
    throw new UnsupportedOperationException();
  }

  private void install() {

  }
}