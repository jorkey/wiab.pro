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

package org.waveprotocol.wave.client.account.impl;

import org.waveprotocol.wave.client.account.ContactListener;
import org.waveprotocol.wave.client.account.ContactManager;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;


/**
 * Serves as a base for concrete {@link ContactManager} implementations.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public abstract class AbstractContactManager implements ContactManager {

  protected final CopyOnWriteSet<ContactListener> listeners = CopyOnWriteSet.create();

  @Override
  public void addListener(ContactListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(ContactListener listener) {
    listeners.remove(listener);
  }

  protected void fireOnUpdated() {
    for (ContactListener listener : listeners) {
      listener.onContactsUpdated();
    }
  }
}
