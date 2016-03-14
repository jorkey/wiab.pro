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

package org.waveprotocol.wave.client.wavepanel.impl.edit;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.i18n.EditSessionFinisherMessages;
import org.waveprotocol.wave.client.widget.dialog.DialogBox;
import org.waveprotocol.wave.client.widget.dialog.DialogButton;
import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;

/**
 *
 * @author fwnd80@gmail.com (Nikolay Liber)
 */
public class InteractiveEditSessionFinisher implements EditSession.Finisher {

  private final static EditSessionFinisherMessages messages = 
          GWT.create(EditSessionFinisherMessages.class);
  private boolean exitAllowed = false;
  private boolean saveDraft = false;
  
  private void showDialog(final Command onDone) {
    exitAllowed = false;
    saveDraft = false;

    final UniversalPopup popup = PopupFactory.createPopup(RootPanel.getBodyElement(),
            new CenterPopupPositioner(), PopupChromeFactory.createPopupChrome(), false);
    popup.setMaskEnabled(true);
    DialogButton[] buttons = {
      new DialogButton(messages.save(), new Command() {
        @Override
        public void execute() {
          popup.hide();
          exitAllowed = true;
          saveDraft = true;
          onDone.execute();
        }
      }),

      new DialogButton(messages.discard(), new Command() {
        @Override
        public void execute() {
          popup.hide();
          exitAllowed = true;
          saveDraft = false;
          onDone.execute();
        }
      }),

      new DialogButton(messages.continueEditing(), new Command() {

        @Override
        public void execute() {
          popup.hide();
          exitAllowed = false;
          saveDraft = true;
          onDone.execute();
        }
      })
    };
    DialogBox.create(popup, messages.saveDraft(), new Label(messages.saveDraft()), buttons);
    popup.show();
  }

  @Override
  public void onEndEditing(Command onDone) {
    showDialog(onDone);
  }

  @Override
  public void onFocusMove(Command onDone) {
    showDialog(onDone);
  }

  @Override
  public void onWaveCompletion(Command onDone) {
    showDialog(onDone);
  }

  @Override
  public boolean isExitAllowed() {
    return exitAllowed;
  }

  @Override
  public boolean shouldDraftBeSaved() {
    return saveDraft;
  }
}
