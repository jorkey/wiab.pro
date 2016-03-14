/**
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
 *
 */
package org.waveprotocol.wave.client.wavepanel.impl.edit;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.MouseDownEvent;

import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.event.EventHandlerRegistry;
import org.waveprotocol.wave.client.wavepanel.event.WaveMouseDownHandler;
import org.waveprotocol.wave.client.wavepanel.impl.blipreader.BlipReader;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TypeCodes;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;

/**
 * Controller of the blip's indicator.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class BlipIndicatorController implements WaveMouseDownHandler {

  /**
   * Creates and installs the blip indicator controller feature.
   * 
   * @param wavePanel wave panel
   * @param modelProvider model provider
   * @param reader reader to mark blip as read
   * @param navigator navigator
   */
  public static void install(WavePanel wavePanel, ModelAsViewProvider modelProvider,
      BlipReader reader, ConversationNavigator navigator) {
    BlipIndicatorController controller = new BlipIndicatorController(wavePanel.getViewProvider(),
        modelProvider, reader, navigator);
    EventHandlerRegistry handlers = wavePanel.getHandlers();
    handlers.registerMouseDownHandler(TypeCodes.kind(Type.BLIP_INDICATOR), controller);
  }

  private final DomAsViewProvider viewProvider;
  private final ModelAsViewProvider modelProvider;
  private final BlipReader reader;
  private final ConversationNavigator navigator;
  
  BlipIndicatorController(DomAsViewProvider viewProvider, ModelAsViewProvider modelProvider,
      BlipReader reader, ConversationNavigator navigator) {
    this.viewProvider = viewProvider;
    this.modelProvider = modelProvider;
    this.reader = reader;
    this.navigator = navigator;
  }

  // WaveMouseDownHandler
  
  @Override
  public boolean onMouseDown(MouseDownEvent event, Element context) {
    Element metaElement = context.getParentElement();
    Element blipElement = metaElement.getParentElement();
    BlipView blipView = viewProvider.asBlip(blipElement);
    ConversationBlip blip = modelProvider.getBlip(blipView);
    reader.read(blip);
    
    event.stopPropagation();
    return true;
  }
}
