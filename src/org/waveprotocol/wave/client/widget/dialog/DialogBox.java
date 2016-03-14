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

package org.waveprotocol.wave.client.widget.dialog;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Focusable;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import org.waveprotocol.wave.client.widget.dialog.i18n.DialogMessages;
import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;

/**
 * Standard dialog box with title, message and set of buttons.
 *
 * @author dyukon@gmail.com (D. Konovalchik)
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class DialogBox {

  /**
   * Processor of the dialog input.
   */
  public interface InputProcessor {

    /**
     * Processes the dialog input.
     *
     * @param input the string input of the dialog
     */
    void process(String input);
  }

  private final static DialogMessages messages = GWT.create(DialogMessages.class);

  /**
   * Creates dialog box.
   *
   * @param popup - UniversalPopup on which the dialog is based
   * @param title - title placed in the title bar
   * @param innerWidget - the inner widget of the dialog
   * @param dialogButtons - buttons
   */
  public static void create(UniversalPopup popup, String title, Widget innerWidget,
      DialogButton[] dialogButtons) {
    // Title
    popup.getTitleBar().setTitleText(title);

    VerticalPanel contents = new VerticalPanel();
    popup.add(contents);

    // Message
    contents.add(innerWidget);

    // Buttons
    HorizontalPanel buttonPanel = new HorizontalPanel();
    for (DialogButton dialogButton : dialogButtons) {
      Button button = new Button(dialogButton.getTitle());
      button.setStyleName(Dialog.getCss().dialogButton());
      buttonPanel.add(button);
      dialogButton.link(button);
    }
    contents.add(buttonPanel);
    buttonPanel.setStyleName(Dialog.getCss().dialogButtonPanel());
    contents.setCellHorizontalAlignment(buttonPanel, HasHorizontalAlignment.ALIGN_RIGHT);
  }

  /**
   * Standard information dialog with prompt and "OK" button.
   *
   * @param message message to display
   */
  public static void information(String message) {
    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    final UniversalPopup popup = PopupFactory.createPopup(RootPanel.getBodyElement(),
        new CenterPopupPositioner(), chrome, true);

    VerticalPanel verticalPanel = new VerticalPanel();
    verticalPanel.setStyleName(Dialog.getCss().verticalPanel());
    Label label = new Label(message);
    verticalPanel.add(label);

    DialogButton okButton = new DialogButton(messages.ok(), new Command() {

      @Override
      public void execute() {
        popup.hide();
      }
    });

    DialogBox.create(popup, messages.confirmation(), verticalPanel,
        new DialogButton[] { okButton });

    popup.show();
    requestFocus(okButton.getButton());
  }  
  
  /**
   * Standard confirmation dialog with prompt, "OK" and "Cancel" buttons.
   *
   * @param message message to display
   * @param okCommand command to be executed if OK button is pressed
   */
  public static void confirm(String message, final Command okCommand) {
    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    final UniversalPopup popup = PopupFactory.createPopup(RootPanel.getBodyElement(),
        new CenterPopupPositioner(), chrome, true);

    VerticalPanel verticalPanel = new VerticalPanel();
    verticalPanel.setStyleName(Dialog.getCss().verticalPanel());
    Label label = new Label(message);
    verticalPanel.add(label);

    DialogButton okButton = new DialogButton(messages.ok(), new Command() {

      @Override
      public void execute() {
        popup.hide();
        if (okCommand != null) {
          okCommand.execute();
        }
      }
    });

    DialogBox.create(popup, messages.confirmation(), verticalPanel,
        new DialogButton[] { okButton, createCancelButton(popup) });

    popup.show();
    requestFocus(okButton.getButton());
  }

  /**
   * Standard input dialog with prompt, input line, "OK" and "Cancel" buttons.
   *
   * @param prompt prompt for input
   * @param initialValue initial value for input string
   * @param inputProcessor processor for input string
   */
  public static void prompt(String prompt, String initialValue, final InputProcessor inputProcessor) {
    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    final UniversalPopup popup = PopupFactory.createPopup(RootPanel.getBodyElement(),
        new CenterPopupPositioner(), chrome, true);

    VerticalPanel verticalPanel = new VerticalPanel();
    verticalPanel.setStyleName(Dialog.getCss().verticalPanel());
    Label promptLabel = new Label(prompt);
    verticalPanel.add(promptLabel);
    promptLabel.setStyleName(Dialog.getCss().promptLabel());

    final TextBox inputTextBox = new TextBox();
    inputTextBox.setText(initialValue);
    verticalPanel.add(inputTextBox);
    inputTextBox.setStyleName(Dialog.getCss().inputTextBox());

    final Command okCommand = new Command() {

      @Override
      public void execute() {
        popup.hide();
        if (inputProcessor != null) {
          inputProcessor.process(inputTextBox.getText());
        }
      }
    };

    inputTextBox.addKeyUpHandler(new KeyUpHandler() {

      @Override
      public void onKeyUp(KeyUpEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
          okCommand.execute();
        }
      }
    });

    DialogBox.create(popup, messages.confirmation(), verticalPanel, new DialogButton[] {
      new DialogButton(messages.ok(), okCommand), createCancelButton(popup)
    });

    popup.show();
    requestFocus(inputTextBox);
  }

  private static DialogButton createCancelButton(final UniversalPopup popup) {
    return new DialogButton(messages.cancel(), new Command() {

      @Override
      public void execute() {
        popup.hide();
      }
    });
  }

  private static void requestFocus(final Focusable widget) {
    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {

      @Override
      public void execute() {
        widget.setFocus(true);
      }
    });
  }
}
