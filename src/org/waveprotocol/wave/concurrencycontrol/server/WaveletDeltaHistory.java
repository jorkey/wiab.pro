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
package org.waveprotocol.wave.concurrencycontrol.server;

import org.waveprotocol.box.common.ThrowableReceiver;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.waveletstate.delta.DeltaWaveletState;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.wave.model.version.HashedVersion;

/**
 * Implementation of delta history using WaveletState.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class WaveletDeltaHistory implements DeltaHistory {

  private final DeltaWaveletState waveletState;

  public WaveletDeltaHistory(DeltaWaveletState waveletState) {
    this.waveletState = waveletState;
  }

  @Override
  public void getDeltaHistory(HashedVersion startVersion, HashedVersion endVersion,
      ThrowableReceiver<WaveletDeltaRecord, WaveletStateException> receiver) throws WaveletStateException {
    waveletState.getDeltaHistory(startVersion, endVersion, receiver);
  }

  @Override
  public WaveletDeltaRecord getDeltaStartingAt(HashedVersion version) {
    return waveletState.getDeltaRecord(version);
  }

  @Override
  public boolean hasSignature(HashedVersion signature) {
    if (signature.getVersion() == 0) {
      return true;
    }
    return waveletState.getTransformedDeltaByEndVersion(signature) != null;
  }

  @Override
  public HashedVersion getCurrentVersion() {
    return waveletState.getLastModifiedVersion();
  }
}
