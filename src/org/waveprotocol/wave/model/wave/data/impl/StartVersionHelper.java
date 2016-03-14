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

package org.waveprotocol.wave.model.wave.data.impl;

import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.supplement.PrimitiveSupplement;
import org.waveprotocol.wave.model.supplement.Supplement;
import org.waveprotocol.wave.model.util.Preconditions;

/**
 * Helper for defining start version of wavelet and fragment.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class StartVersionHelper {
  /** Indicates the absence of version. */
  public static final long NO_VERSION = PrimitiveSupplement.NO_VERSION;

  /** User supplement. */
  private final Supplement supplement;

  /**
   * @param supplement the user supplement.
   */
  public StartVersionHelper(Supplement supplement) {
    this.supplement = supplement;
  }

  /**
   * Gets start version of segment, from which changes are displayed.
   *
   * @param waveletId the Id of wavelet.
   * @param segmentId the Id of segment.
   * @param segmentCreationVersion the creation segment version.
   * @return last look version of segment or {@link #NO_VERSION}.
   */
  public long getStartVersion(WaveletId waveletId, SegmentId segmentId, long segmentCreationVersion) {
    long version = PrimitiveSupplement.NO_VERSION;
    if (supplement != null) {
      if (segmentId.isIndex()) {
        version = getLookWaveletVersion(waveletId);
      } else if (segmentId.isParticipants()) {
        version = getLookParticipantsVersion(waveletId, segmentCreationVersion);
      } else if (segmentId.isBlip()) {
        if (IdConstants.TAGS_DOCUMENT_ID.equals(segmentId.getBlipId())) {
          version = getLookTagsVersion(waveletId, segmentCreationVersion);
        } else {
          version = getLookBlipVersion(waveletId, segmentId.getBlipId(), segmentCreationVersion);
        }
      } else {
        Preconditions.illegalArgument("Invalid segment " + segmentId.toString());
      }
    }
    return version != PrimitiveSupplement.NO_VERSION && version != 0 && version >= segmentCreationVersion ? version : NO_VERSION;
  }

  /**
   * Gets start version of wavelet, from which changes are displayed.
   *
   * @param waveletId the Id of wavelet.
   * @return last look wavelet version or {@link #NO_VERSION}.
   */
  public long getStartVersion(WaveletId waveletId) {
    long version = getLookWaveletVersion(waveletId);
    return version != PrimitiveSupplement.NO_VERSION && version != 0  ? version : NO_VERSION;
  }

  private long getLookWaveletVersion(WaveletId waveletId) {
    long version = PrimitiveSupplement.NO_VERSION;
    if (supplement != null) {
      version = supplement.getLastReadWaveletVersion(waveletId);
      if (version == PrimitiveSupplement.NO_VERSION) {
        version = supplement.getFirstLookWaveletVersion(waveletId);
      }
    }
    return version;
  }

  private long getLookParticipantsVersion(WaveletId waveletId, long participantsCreationVersion) {
    long version = PrimitiveSupplement.NO_VERSION;
    if (supplement != null) {
      version = maxVersion(supplement.getLastReadParticipantsVersion(waveletId),
        getLastReadVersionByWavelet(waveletId, participantsCreationVersion));
      if (version == PrimitiveSupplement.NO_VERSION) {
        version = getFirstLookVersionByWavelet(waveletId, participantsCreationVersion);
      }
    }
    return version;
  }

  private long getLookTagsVersion(WaveletId waveletId, long tagsCreationVersion) {
    long version = PrimitiveSupplement.NO_VERSION;
    if (supplement != null) {
      version = maxVersion(supplement.getLastReadTagsVersion(waveletId),
        getLastReadVersionByWavelet(waveletId, tagsCreationVersion));
      if (version == PrimitiveSupplement.NO_VERSION) {
        version = getFirstLookVersionByWavelet(waveletId, tagsCreationVersion);
      }
    }
    return version;
  }

  private long getLookBlipVersion(WaveletId waveletId, String blipId, long blipCreationVersion) {
    long version = PrimitiveSupplement.NO_VERSION;
    if (supplement != null) {
      version = maxVersion(supplement.getLastReadBlipVersion(waveletId, blipId),
        getLastReadVersionByWavelet(waveletId, blipCreationVersion));
      if (version == PrimitiveSupplement.NO_VERSION) {
        version = maxVersion(supplement.getFirstLookBlipVersion(waveletId, blipId),
          getFirstLookVersionByWavelet(waveletId, blipCreationVersion));
      }
    }
    return version;
  }
  
  private long getLastReadVersionByWavelet(WaveletId waveletId, long segmentCreationVersion) {
    long version = supplement.getLastReadWaveletVersion(waveletId);
    if (version != PrimitiveSupplement.NO_VERSION && version >= segmentCreationVersion) {
      return version;
    }
    return PrimitiveSupplement.NO_VERSION;
  }

  private long getFirstLookVersionByWavelet(WaveletId waveletId, long segmentCreationVersion) {
    long version = supplement.getFirstLookWaveletVersion(waveletId);
    if (version != PrimitiveSupplement.NO_VERSION && version >= segmentCreationVersion) {
      return version;
    }
    return PrimitiveSupplement.NO_VERSION;
  }

  private static long maxVersion(long version1, long version2) {
    if (version1 == PrimitiveSupplement.NO_VERSION) {
      return version2;
    }
    if (version2 == PrimitiveSupplement.NO_VERSION) {
      return version1;
    }
    return Math.max(version1, version2);
  }
}
