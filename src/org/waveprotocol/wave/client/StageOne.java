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

package org.waveprotocol.wave.client;

import org.waveprotocol.box.webclient.client.FragmentRequesterImpl;
import org.waveprotocol.wave.client.account.ContactManager;
import org.waveprotocol.wave.client.concurrencycontrol.WaveletOperationalizer;
import org.waveprotocol.wave.client.state.BlipReadStateMonitor;
import org.waveprotocol.wave.client.state.InboxStateMonitor;
import org.waveprotocol.wave.client.wave.LocalSupplementedWave;
import org.waveprotocol.wave.client.wave.WaveDocuments;
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
 *
 * @see StageZero
 */
public interface StageOne {
  /** @return the wave panel. */
  WavePanelImpl getWavePanel();
  
  /** @return the wave view. */
  WaveViewImpl<OpBasedWavelet> getWaveView();
  
  /** @return the wavelet operationalizer. */
  WaveletOperationalizer getWaveletOperationalizer();
  
  /** @return the wave documents. */
  WaveDocuments getWaveDocuments();
  
  /** @return the view channel. */
  ViewChannel getViewChannel();
  
  FragmentRequesterImpl getFragmentRequester();

  /** @return the focus feature. */
  FocusFramePresenterImpl getFocusFrame();

  /** @return the (live) conversations in the wave. */
  ObservableQuasiConversationView getConversations();

  /** @return the core wave. */
  ObservableWaveView getWave();

  /** @return the signed-in user's (live) supplementary data in the wave. */
  LocalSupplementedWave getSupplement();

  /** @return live blip read/unread information. */
  BlipReadStateMonitor getReadMonitor();

  /** @return live inbox/archive information. */
  InboxStateMonitor getInboxStateMonitor();

  /** @return the provider of view objects given model objects. */
  ModelAsViewProvider getModelAsViewProvider();

  /** @return the contact manager. */
  ContactManager getContactManager();

  /** @return blip reader. */
  BlipReader getBlipReader();

  /** @return navigator. */
  ConversationNavigator getNavigator();

  /** @return dynamic renderer. */
  DynamicDomRenderer getDynamicRenderer();

  /** @return screen position scroller. */
  ScreenPositionDomScroller getScreenPositionScroller();
}
