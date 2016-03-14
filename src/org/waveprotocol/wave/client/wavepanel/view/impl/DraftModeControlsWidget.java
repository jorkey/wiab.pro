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

package org.waveprotocol.wave.client.wavepanel.view.impl;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import org.waveprotocol.wave.client.wavepanel.view.BlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.i18n.DraftModeControlsMessages;
import org.waveprotocol.wave.client.widget.button.ButtonFactory;
import org.waveprotocol.wave.client.widget.button.ClickButton.ClickButtonListener;
import org.waveprotocol.wave.client.widget.button.ClickButtonWidget;
import org.waveprotocol.wave.client.widget.button.text.TextButton;

/**
 *
 * @author fwnd80@gmail.com (Nikolay Liber)
 */
public class DraftModeControlsWidget extends SimplePanel
  implements BlipMetaView.DraftModeControls {
  public interface Resources extends ClientBundle {
    @Source("DraftModeControlsWidget.css")
    Css css();
  }
  
  public interface Css extends CssResource {
    String checkbox();
    String doneButton();
  }
  
  private static final Resources res = GWT.create(Resources.class);
  
  interface WidgetUiBinder extends UiBinder<FlowPanel, DraftModeControlsWidget> {};
  private static final WidgetUiBinder uiBinder = GWT.create(WidgetUiBinder.class);
  private static final DraftModeControlsMessages messages = GWT.create(DraftModeControlsMessages.class);
  
  @UiField CheckBox draftMode;
  @UiField SimplePanel donePanel;
  @UiField SimplePanel cancelPanel;
  
  private Listener listener;
  
  @UiHandler("draftMode")
  void onModeChange(ValueChangeEvent<Boolean> e) {
    listener.onModeChange(e.getValue());
  }

  public DraftModeControlsWidget(Element element) {
    super(element);
    
    res.css().ensureInjected();
    FlowPanel p = uiBinder.createAndBindUi(this);
    
    ClickButtonWidget doneButton = ButtonFactory.createHtmlButtonWithHotkeyHint(messages.doneTitle(),
            true, "Shift+Enter", TextButton.TextButtonStyle.REGULAR_BUTTON, messages.doneHint(), new ClickButtonListener() {

      @Override
      public void onClick() {
        listener.onDone();
      }
    });
    donePanel.add(doneButton);
    
    ClickButtonWidget cancelButton = ButtonFactory.createHtmlButtonWithHotkeyHint(messages.cancelTitle(),
            false, "Esc", TextButton.TextButtonStyle.REGULAR_BUTTON, messages.cancelHint(), new ClickButtonListener() {

      @Override
      public void onClick() {
        listener.onCancel();
      }
    });    
    cancelPanel.add(cancelButton);    
    setWidget(p);
  }
  
  @Override
  public void setListener(Listener listener) {
    this.listener = listener;
  }
}
