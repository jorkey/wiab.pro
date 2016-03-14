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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Button;

/**
 * Button of the dialog box.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class DialogButton {

  /** Button title. */
  private String title;

  /** Command to be executed when GUI button is clicked. */
  private Command onClick;

  /** GUI button. */
  private Button button;

  public DialogButton(String title) {
    this.title = title;
  }

  public DialogButton(String title, Command onClick) {
    this.title = title;
    this.onClick = onClick;
  }

  public Button getButton() {
    return button;
  }

  public void setTitle(String title) {
    this.title = title;
    if (button != null) {
      button.setText(title);
    }
  }

  public void setOnClick(Command onClick) {
    this.onClick = onClick;
  }

  public String getTitle() {
    return title;
  }

  public void execute() {
    if (onClick != null) {
      onClick.execute();
    }
  }

  void link(Button button) {
    this.button = button;
    button.setText(title);
    button.addClickHandler(new ClickHandler() {

      @Override
      public void onClick(ClickEvent event) {
        execute();
      }
    });
  }
}
