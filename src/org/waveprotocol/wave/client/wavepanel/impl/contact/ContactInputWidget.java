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
package org.waveprotocol.wave.client.wavepanel.impl.contact;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.ui.TextBox;

/**
 * Widget for input contact name.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class ContactInputWidget extends TextBox implements KeyPressHandler,
    KeyDownHandler, KeyUpHandler {
  public interface Listener {
    void onKeyUp();
    void onKeyDown();
    void onInput(String value);
    void onSelect(String value);
  }

  private Listener listener;

  public ContactInputWidget() {
    addKeyPressHandler(this);
    addKeyDownHandler(this);
    addKeyUpHandler(this);
  }

  public void setListener(final Listener listener) {
    this.listener = listener;
  }

  @Override
  public void onKeyPress(KeyPressEvent event) {
    if (listener != null) {
      if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
        listener.onSelect(getInput());
      } else {
        listener.onInput(getInput());
      }
    }
  }

  @Override
  public void onKeyDown(KeyDownEvent event) {
    if (listener != null) {
      if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_UP) {
        listener.onKeyUp();
      } else if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_DOWN) {
        listener.onKeyDown();
      }
    }
  }

  @Override
  public void onKeyUp(KeyUpEvent event) {
    if (listener != null) {
      listener.onInput(getInput());
    }
  }

  private String getInput() {
    return getValue().trim();
  }
}
