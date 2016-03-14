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

import org.waveprotocol.box.webclient.client.FragmentRequesterImpl;
import org.waveprotocol.box.webclient.client.StageProvider;

import org.waveprotocol.wave.client.StageOne;
import org.waveprotocol.wave.client.StageZero;
import org.waveprotocol.wave.client.account.ContactManager;
import org.waveprotocol.wave.client.concurrencycontrol.WaveletOperationalizer;
import org.waveprotocol.wave.client.scroll.SmartScroller;
import org.waveprotocol.wave.client.state.BlipReadStateMonitor;
import org.waveprotocol.wave.client.state.InboxStateMonitor;
import org.waveprotocol.wave.client.wave.LocalSupplementedWave;
import org.waveprotocol.wave.client.wave.WaveDocuments;
import org.waveprotocol.wave.client.wavepanel.impl.focus.FocusFrameController;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.dom.UpgradeableDomAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.impl.WavePanelImpl;
import org.waveprotocol.wave.client.wavepanel.impl.blipreader.BlipReader;
import org.waveprotocol.wave.client.wavepanel.impl.focus.FocusFramePresenterImpl;
import org.waveprotocol.wave.client.wavepanel.render.DynamicDomRenderer;
import org.waveprotocol.wave.client.wavepanel.render.ScreenPositionDomScroller;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.concurrencycontrol.channel.ViewChannel;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationView;
import org.waveprotocol.wave.model.wave.opbased.ObservableWaveView;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.model.wave.opbased.WaveViewImpl;

/**
 * The first stage of Undercurrent code.
 * <p>
 * This exposes minimal features required for basic reading interactions.
 */
public class StageOneTestProvider extends StageProvider<StageOne> {

  public StageOneTestProvider(StageZero previous) {
    // Nothing in stage one depends on anything in stage zero currently, but
    // the dependency is wired up so that it is simple to add such
    // dependencies should they be necessary in the future.
  }

  @Override
  protected final void create(Accessor<StageOne> whenReady) {
    install();
    whenReady.use(new StageOne() {

      @Override
      public WavePanelImpl getWavePanel() {
        throw new UnsupportedOperationException();
      }

      @Override
      public FocusFramePresenterImpl getFocusFrame() {
        throw new UnsupportedOperationException();
      }

      @Override
      public WaveViewImpl<OpBasedWavelet> getWaveView() {
        throw new UnsupportedOperationException();
      }

      @Override
      public WaveletOperationalizer getWaveletOperationalizer() {
        throw new UnsupportedOperationException();
      }

      @Override
      public WaveDocuments getWaveDocuments() {
        throw new UnsupportedOperationException();
      }

      @Override
      public ViewChannel getViewChannel() {
        throw new UnsupportedOperationException();
      }

      @Override
      public FragmentRequesterImpl getFragmentRequester() {
        throw new UnsupportedOperationException();
      }

      @Override
      public ObservableQuasiConversationView getConversations() {
        throw new UnsupportedOperationException();
      }

      @Override
      public ObservableWaveView getWave() {
        throw new UnsupportedOperationException();
      }

      @Override
      public LocalSupplementedWave getSupplement() {
        throw new UnsupportedOperationException();
      }

      @Override
      public BlipReadStateMonitor getReadMonitor() {
        throw new UnsupportedOperationException();
      }

      @Override
      public InboxStateMonitor getInboxStateMonitor() {
        throw new UnsupportedOperationException();
      }

      @Override
      public ModelAsViewProvider getModelAsViewProvider() {
        throw new UnsupportedOperationException();
      }

      @Override
      public ContactManager getContactManager() {
        throw new UnsupportedOperationException();
      }

      @Override
      public BlipReader getBlipReader() {
        throw new UnsupportedOperationException();
      }

      @Override
      public ConversationNavigator getNavigator() {
        throw new UnsupportedOperationException();
      }

      @Override
      public DynamicDomRenderer getDynamicRenderer() {
        throw new UnsupportedOperationException();
      }

      @Override
      public ScreenPositionDomScroller getScreenPositionScroller() {
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