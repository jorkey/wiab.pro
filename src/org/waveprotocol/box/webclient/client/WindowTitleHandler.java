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

import com.google.gwt.user.client.Window;

import org.waveprotocol.box.webclient.search.SearchPresenter;
import org.waveprotocol.box.webclient.search.WaveContext;
import org.waveprotocol.box.webclient.search.WaveStore;
import org.waveprotocol.box.webclient.widget.frame.FramedPanel;
import org.waveprotocol.wave.model.conversation.TitleHelper;

/**
 * Sets the title to the browser window and wave frame.
 *
 * @author yurize@apache.org (Yuri Zelikov)
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public final class WindowTitleHandler {
  
  private static final String APP_NAME = "WIAB";

  private static final String DEFAULT_TITLE = "Communicate and collaborate in real-time";

  private final WaveStore waveStore;
  private final FramedPanel waveFrame;
  private final SearchPresenter searchPresenter;
  
  private boolean waveOpened = false;
  private String oldWaveTitle;

  private final WaveStore.Listener waveStoreListener = new WaveStore.Listener() {

    @Override
    public void onOpened(WaveContext wave) {
      waveOpened = true;
      updateTitle();      
    }

    @Override
    public void onClosed(WaveContext wave) {
      waveOpened = false;
      updateTitle();      
    }
  };
  
  private final SearchPresenter.WaveTitleListener searchPresenterListener =
      new SearchPresenter.WaveTitleListener() {

    @Override
    public void onMaybeWaveTitleChanged() {
      updateTitle();      
    }
  };
  
  public static WindowTitleHandler install(WaveStore waveStore, FramedPanel waveFrame,
      SearchPresenter searchPresenter) {
    WindowTitleHandler windowTitleHandler = new WindowTitleHandler(waveStore, waveFrame,
        searchPresenter);
    waveStore.addListener(windowTitleHandler.getWaveStoreListener());
    searchPresenter.addListener(windowTitleHandler.getSearchPresenterListener());
    return windowTitleHandler;
  }

  private WindowTitleHandler(WaveStore waveStore, FramedPanel waveFrame, 
      SearchPresenter searchPresenter) {
    this.waveStore = waveStore;
    this.waveFrame = waveFrame;
    this.searchPresenter = searchPresenter;
  }

  public void deinstall() {
    waveStore.removeListener(waveStoreListener);
    searchPresenter.removeListener(searchPresenterListener);
  }  
  
  public WaveStore.Listener getWaveStoreListener() {
    return waveStoreListener;
  }
  
  public SearchPresenter.WaveTitleListener getSearchPresenterListener() {
    return searchPresenterListener;
  }
  
  private void updateTitle() {
    String waveTitle = waveOpened ? searchPresenter.getSelectedTitle() : TitleHelper.EMPTY_STRING;
    if (!TitleHelper.isUnknown(waveTitle) && !waveTitle.equals(oldWaveTitle)) {
      oldWaveTitle = waveTitle;      
      waveFrame.setTitleText(waveTitle);
      Window.setTitle(waveTitle2WindowTitle(waveTitle));
    }  
  }
  
  private String waveTitle2WindowTitle(String waveTitle) {
    String windowTitle = waveTitle;
    if (TitleHelper.isEmpty(windowTitle)) {
      windowTitle = DEFAULT_TITLE;
    }
    windowTitle += " - " + Session.get().getAddress() + " - " + APP_NAME;
    return windowTitle;
  }
}