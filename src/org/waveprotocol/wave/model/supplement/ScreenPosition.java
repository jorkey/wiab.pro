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
package org.waveprotocol.wave.model.supplement;

import org.waveprotocol.wave.model.id.IdConstants;

/**
 * Visible screen position in the wave. 
 */
public class ScreenPosition implements Comparable<ScreenPosition> {
  
  /**
   * Wave begin position.
   */
  public static final ScreenPosition WAVE_BEGIN = new ScreenPosition(IdConstants.FIRST_BLIP_ID);
  
  /**
   * Wave end position.
   */
  public static final ScreenPosition WAVE_END = new ScreenPosition(IdConstants.LAST_BLIP_ID);
  
  /**
   * Id of the blip at the screen position.
   */
  private final String blipId;
  
  /**
   * Offset of blip document's paragraph at the screen position.
   * If there is no such paragraph, paragrapOffset=-1.
   */
  private final int paragraphOffset;
  
  /**
   * Relative offset of the base element at the screen position.
   * The base element = paragraphOffset != -1 ? paragraph : blip.
   * Equals to absolute offset divided to the base element height.
   */
  private final double relativeOffset;
  
  public ScreenPosition(String blipId) {
    this.blipId = blipId;
    paragraphOffset = -1;
    relativeOffset = 0;
  }
  
  public ScreenPosition(String blipId, int paragraphOffset, double relativeOffset) {
    this.blipId = blipId;
    this.paragraphOffset = paragraphOffset;
    this.relativeOffset = relativeOffset;
  }
  
  public String getBlipId() {
    return blipId;
  }
  
  public int getParagraphOffset() {
    return paragraphOffset;
  }

  public double getRelativeOffset() {
    return relativeOffset;
  }
  
  public boolean isScrolledToEnd() {
    return IdConstants.LAST_BLIP_ID.equals(blipId);
  }
  
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ScreenPosition) {
      ScreenPosition sp = (ScreenPosition) obj;
      return blipId.equals(sp.blipId) && paragraphOffset == sp.paragraphOffset
          && relativeOffset == sp.relativeOffset;
    }
    return false;
  } 
  
  @Override
  public int compareTo(ScreenPosition o) {
    if (o == null) {
      return 1;
    }
    if (!blipId.equals(o.blipId)) {
      return blipId.compareTo(o.blipId);
    }
    if (paragraphOffset != o.paragraphOffset) {
      return Integer.signum(paragraphOffset - o.paragraphOffset);
    }
    return (int) Math.signum(relativeOffset - o.relativeOffset);
  }

  @Override
  public String toString() {
    return "(blipId: " + blipId + ", paragraphOffset: " + paragraphOffset +
        ", relativeOffset: " + relativeOffset + ")";
  }  
}
