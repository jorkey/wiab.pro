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

import org.waveprotocol.box.webclient.client.StageProvider;

import org.waveprotocol.wave.client.StageThree;
import org.waveprotocol.wave.client.StageTwo;
import org.waveprotocol.wave.client.render.undercurrent.ScreenController;
import org.waveprotocol.wave.client.wavepanel.impl.edit.Actions;
import org.waveprotocol.wave.client.wavepanel.impl.edit.EditSession;

/**
 * The first stage of Undercurrent code.
 * <p>
 * This exposes minimal features required for basic reading interactions.
 */
public class StageThreeTestProvider extends StageProvider<StageThree> {

  public StageThreeTestProvider(StageTwo previous) {
    // Nothing in stage one depends on anything in stage zero currently, but
    // the dependency is wired up so that it is simple to add such
    // dependencies should they be necessary in the future.
  }

  @Override
  protected final void create(Accessor<StageThree> whenReady) {
    install();
    whenReady.use(new StageThree() {

      @Override
      public Actions getEditActions() {
        throw new UnsupportedOperationException();
      }

      @Override
      public EditSession getEditSession() {
        throw new UnsupportedOperationException();
      }

      @Override
      public ScreenController getScreenController() {
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