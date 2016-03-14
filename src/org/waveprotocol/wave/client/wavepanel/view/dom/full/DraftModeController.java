/*
 * Copyright 2014 fwnd80@gmail.com (Nikolay Liber).
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
package org.waveprotocol.wave.client.wavepanel.view.dom.full;

import com.google.common.base.Preconditions;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;
import org.waveprotocol.wave.client.common.util.LogicalPanel;
import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.wavepanel.WavePanel;
import org.waveprotocol.wave.client.wavepanel.impl.edit.Actions;
import org.waveprotocol.wave.client.wavepanel.impl.edit.EditSession;
import org.waveprotocol.wave.client.wavepanel.view.BlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomUtil;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.i18n.BlipMessages;
import org.waveprotocol.wave.model.conversation.ConversationBlip;

/**
 *
 * @author fwnd80@gmail.com (Nikolay Liber)
 */
public class DraftModeController implements EditSession.Listener,
        BlipMetaView.DraftModeControls.Listener {
  private final Actions actions;
  private final ModelAsViewProvider modelAsViewProvider;
  private final DomAsViewProvider domAsViewProvider;
  private final LogicalPanel container;
  private BlipMetaView blipMeta;
  private BlipMetaView.DraftModeControls controlsWidget;
  private final EditSession editSession;  
  private final BlipMessages messages;
  
  public static void install(WavePanel panel, Actions actions, ModelAsViewProvider modelAsViewProvider, EditSession editSession) {
    DraftModeController controller = new DraftModeController(actions,
        panel.getGwtPanel(), panel.getViewProvider(), modelAsViewProvider, editSession);
    editSession.addListener(controller);
  }

  protected DraftModeController(Actions actions, LogicalPanel container, DomAsViewProvider domAsViewProvider,
      ModelAsViewProvider modelAsViewProvider, EditSession editSession) {
    this.actions = actions;
    this.container = container;
    this.domAsViewProvider = domAsViewProvider;
    this.modelAsViewProvider = modelAsViewProvider;
    this.editSession = editSession;
    
    messages = WavePanelResourceLoader.getBlipMessages();
  }
  
  @Override
  public void onSessionStart(Editor e, ConversationBlip blip) {
    attachWidgets(getElement(modelAsViewProvider.getBlipView(blip).getId()));
  }

  @Override
  public void onSessionEnd(Editor e, ConversationBlip blip) {
    detachWidgets();
  }
  
  private Element getElement(String id) {
    return Document.get().getElementById(id);
  }
  
  private void attachWidgets(Element parent) {
    Element meta = DomUtil.findFirstChildElement(parent, Type.META);
    blipMeta = domAsViewProvider.asBlipMeta(meta);
    
//    Preconditions.checkArgument(indicatorWidget == null, "Draft mode indicator widget is already attached");
//    indicatorWidget = blipMeta.attachDraftModeIndicator();
//    container.doAdopt((Widget) indicatorWidget);
//    
//    indicatorWidget.setListener(this);   
    
    Preconditions.checkArgument(controlsWidget == null, "Draft mode controls widget is already attached");
    controlsWidget = blipMeta.attachDraftModeCotrols();
    container.doAdopt((Widget) controlsWidget);
    
    controlsWidget.setListener(this);
    blipMeta.showDraftModeControls();
  }
  
  private void detachWidgets() {
//    Preconditions.checkNotNull(indicatorWidget, "No draft mode indicator widget is attached");
//    container.doOrphan((Widget) indicatorWidget);
//    blipMeta.detachDraftModeIndicator();
//    indicatorWidget = null;
    
    Preconditions.checkNotNull(controlsWidget, "Attempt to detach unattached draft mode controls");
    container.doOrphan((Widget) controlsWidget);
    blipMeta.hideDraftModeControls();
    blipMeta.detachDraftModeControls();
    controlsWidget = null;
  }

  @Override
  public void onModeChange(boolean draft) {
    if (draft)
      actions.enterDraftMode();
    else
      actions.leaveDraftMode(true);
  }

  @Override
  public void onDone() {
    actions.stopEditing(true);
  }

  @Override
  public void onCancel() {
    actions.stopEditing(false);
  }
}
