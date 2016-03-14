/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.box.server.frontend;

import org.waveprotocol.box.stat.Timed;
import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.waveletstate.SnapshotProvider;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.supplement.PrimitiveSupplement;
import org.waveprotocol.wave.model.supplement.Supplement;
import org.waveprotocol.wave.model.supplement.SupplementImpl;
import org.waveprotocol.wave.model.supplement.WaveletBasedSupplement;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.Wavelet;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.util.logging.Log;

import com.google.inject.Inject;
import java.util.Map;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class SupplementProviderImpl implements SupplementProvider {
  private static final Log LOG = Log.get(SupplementProviderImpl.class);

  @Inject
  SupplementProviderImpl() {
  }

  @Timed
  @Override
  public Supplement getSupplement(WaveletName waveletName, ParticipantId participant, 
      HashedVersion version, Map<SegmentId, Interval> intervals) throws WaveServerException {
    try {
      ObservableWaveletData snapshot = SnapshotProvider.makeSnapshot(waveletName, version, intervals);
      Wavelet wavelet = OpBasedWavelet.createReadOnly(snapshot);
      PrimitiveSupplement udwState = WaveletBasedSupplement.create(wavelet);
      return new SupplementImpl(udwState);
    } catch (OperationException ex) {
      throw new WaveServerException(ex);
    }
  }
}
