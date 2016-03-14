package org.waveprotocol.wave.model.supplement;

import java.util.Map;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.WaveDigest;

/**
 *
 * @author Andrew Kaplanov
 */
public class WaveDigestWithSupplements {

  private final WaveDigest digest;
  private final Map<ParticipantId, WaveDigestSupplement> supplements;

  public WaveDigestWithSupplements(WaveDigest digest, Map<ParticipantId, WaveDigestSupplement> supplements) {
    this.digest = digest;
    this.supplements = supplements;
  }

  public WaveDigest getDigest() {
    return digest;
  }

  public Map<ParticipantId, WaveDigestSupplement> getSupplements() {
    return supplements;
  }

}
