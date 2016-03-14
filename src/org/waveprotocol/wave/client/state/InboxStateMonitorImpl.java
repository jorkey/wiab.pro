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
 */
package org.waveprotocol.wave.client.state;

import com.google.common.base.Preconditions;

import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.supplement.ObservableSupplementedWave;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;


/**
 * Eagerly monitors the inbox state of wave and broadcasting events.
 */
public final class InboxStateMonitorImpl extends ObservableSupplementedWave.ListenerImpl
    implements InboxStateMonitor {

  private static final DomLogger LOG = new DomLogger("inbox-state");

  private final CopyOnWriteSet<InboxStateMonitor.Listener> listeners = CopyOnWriteSet.create();
  private final WaveId waveId;
  private final ObservableSupplementedWave supplementedWave;

  /**
   * @return a new InboxStateMonitor
   */
  public static InboxStateMonitorImpl create(WaveId waveId, ObservableSupplementedWave supplementedWave) {
    InboxStateMonitorImpl monitor = new InboxStateMonitorImpl(waveId, supplementedWave);
    monitor.init();
    return monitor;
  }

  private InboxStateMonitorImpl(WaveId waveId, ObservableSupplementedWave supplementedWave) {
    Preconditions.checkNotNull(waveId, "waveId cannot be null");
    Preconditions.checkNotNull(supplementedWave, "supplementedWave cannot be null");
    this.waveId = waveId;
    this.supplementedWave = supplementedWave;
  }

  private void init() {
    // Listen supplement events.
    supplementedWave.addListener(this);
  }

  //
  // InboxStateMonitor
  //

  @Override
  public boolean isInbox() {
    return supplementedWave.isInbox();
  }

  @Override
  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  // ObservableSupplementedWave.Listener
  //

  @Override
  public void onMaybeInboxStateChanged() {
    notifyListeners();
  }

  /**
   * Notifies listeners of a change.
   */
  private void notifyListeners() {
    LOG.trace().log(waveId, ": notifying inbox state change");
    for (Listener listener : listeners) {
      listener.onInboxStateChanged();
    }
  }

}
