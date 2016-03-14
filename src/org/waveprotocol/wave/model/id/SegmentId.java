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

import java.util.Objects;

/**
 * Identifier of segment.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public final class SegmentId implements Comparable<SegmentId> {
  /** Id of index segment. */
  public static final SegmentId INDEX_ID = new SegmentId("index");

  /** Id of participants segment. */
  public static final SegmentId PARTICIPANTS_ID = new SegmentId("participants");

  /** Id of manifest segment. */
  public static final SegmentId MANIFEST_ID = SegmentId.ofBlipId(IdConstants.MANIFEST_DOCUMENT_ID);
  
  /** Id of tags segment. */
  public static final SegmentId TAGS_ID = SegmentId.ofBlipId(IdConstants.TAGS_DOCUMENT_ID);
  
  /** Prefix of blip segment. */
  private static final String BLIP_PREFIX = "blip-";
  
  /** Id of segment. */
  private final String id;

  /** Makes identifier. */
  public static SegmentId of(String id) {
    return new SegmentId(id);
  }

  /** Makes blip segment identifier. */
  public static SegmentId ofBlipId(String blipId) {
    return new SegmentId(BLIP_PREFIX + blipId);
  }

  private SegmentId(String id) {
    this.id = id;
  }

  /** Checks that is index segment. */
  public boolean isIndex() {
    return id.equals(INDEX_ID.id);
  }

  /** Checks that is participants segment. */
  public boolean isParticipants() {
    return id.equals(PARTICIPANTS_ID.id);
  }

  /** Checks that is blip segment. */
  public boolean isBlip() {
    return id.startsWith(BLIP_PREFIX);
  }

  /** Gets blip Id. */
  public String getBlipId() {
    Preconditions.checkArgument(isBlip(), "Not blip");
    return id.substring(BLIP_PREFIX.length());
  }
  
  /** Serializes Id. */
  public String serialize() {
    return id;
  }

  @Override
  public String toString() {
    return id;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 67 * hash + Objects.hashCode(this.id);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final SegmentId other = (SegmentId) obj;
    if (!Objects.equals(this.id, other.id)) {
      return false;
    }
    return true;
  }

  @Override
  public int compareTo(SegmentId o) {
    return id.compareTo(o.id);
  }
}
