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

package org.waveprotocol.wave.model.id;

import org.waveprotocol.wave.model.util.Preconditions;

/**
 * A globally-unique segment identifier.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class SegmentName implements Comparable<SegmentName> {

  public final WaveletName waveletName;
  public final SegmentId segmentId;

  /** Constructs a wavelet name for a wave id and wavelet id. */
  public static SegmentName of(WaveletName waveletName, SegmentId segmentId) {
    return new SegmentName(waveletName, segmentId);
  }

  /** Private constructor to allow future instance optimization. */
  private SegmentName(WaveletName waveletName, SegmentId segmentId) {
    if (waveletName == null || segmentId == null) {
      Preconditions.nullPointer("Cannot create SegmentName with null value in [waveletName:"
          + waveletName + "] [segmentId:" + segmentId + "]");
    }
    this.waveletName = waveletName;
    this.segmentId = segmentId;
  }

  @Override
  public String toString() {
    return "[SegmentName " + waveletName.toString() + "/" + segmentId.toString() + "]";
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof SegmentName) {
      SegmentName o = (SegmentName) other;
      return waveletName.equals(o.waveletName) && segmentId.equals(o.segmentId);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return waveletName.hashCode() * 43 + segmentId.hashCode();
  }

  @Override
  public int compareTo(SegmentName o) {
      return waveletName.equals(o.waveletName) ? segmentId.compareTo(o.segmentId)
      : waveletName.compareTo(o.waveletName);
  }
}
