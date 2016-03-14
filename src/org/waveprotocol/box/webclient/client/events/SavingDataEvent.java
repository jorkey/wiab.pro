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

package org.waveprotocol.box.webclient.client.events;
import com.google.gwt.event.shared.GwtEvent;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.util.Preconditions;

public class SavingDataEvent extends GwtEvent<SavingDataEventHandler> {
  public static final GwtEvent.Type<SavingDataEventHandler> TYPE = 
      new GwtEvent.Type<SavingDataEventHandler>();
  private final WaveletId waveletId;
  private final boolean inProcess;

  public SavingDataEvent(WaveletId waveletId, boolean inProcess) {
    this.waveletId = Preconditions.checkNotNull(waveletId,"null waveletId");
    this.inProcess = inProcess;
  }

  @Override
  public Type<SavingDataEventHandler> getAssociatedType() {
    return TYPE;
  }

  public WaveletId getWaveletId() {
    return waveletId;
  }

  public boolean isInProcess() {
    return inProcess;
  }

  @Override
  protected void dispatch(SavingDataEventHandler handler) {
    handler.onSavingData(this);
  }
  
  @Override
  public String toString() {
    return "SavingDataEvent (" + 
        "waveletId: " + (waveletId != null ? waveletId : "null") + ", " +
        "inProcess: " + inProcess + ")";
  }

}
