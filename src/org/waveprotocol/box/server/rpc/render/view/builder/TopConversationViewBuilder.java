/**
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.waveprotocol.box.server.rpc.render.view.builder;

import org.waveprotocol.box.server.rpc.render.uibuilder.BuilderHelper.Component;
import org.waveprotocol.box.server.rpc.render.uibuilder.UiBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.WavePanelResources.WaveImageResource;

/**
 * A top level conversation builder. Each inherited class must implements the
 * outputHtml method and the structured produced must contains all the
 * components.
 *
 */
public abstract class TopConversationViewBuilder implements UiBuilder {

  /** Resources used by this widget. */
  public interface Resources {
    WaveImageResource emptyToolbar();
    Css css();
  }

  /** CSS for this widget. */
  public interface Css {
    String fixedSelf();
    String fixedThread();
    String toolbar();
  }

  /** An enum for all the components of a blip view. */
  public enum Components implements Component {
    /** Container of the main thread. */
    THREAD_CONTAINER("T"),
    /** Container of the toolbar. */
    TOOLBAR_CONTAINER("B"), ;

    private final String suffix;

    Components(String postfix) {
      this.suffix = postfix;
    }

    @Override
    public String getDomId(String baseId) {
      return baseId + suffix;
    }
  }

  TopConversationViewBuilder() {
  }
}
