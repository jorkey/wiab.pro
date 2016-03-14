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

package org.waveprotocol.box.server.persistence.blocks;

import org.waveprotocol.wave.model.util.Preconditions;

/**
 * Closed range of versions.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class VersionRange {
  private final long from;
  private final long to;

  public static VersionRange of() {
    return new VersionRange(-1, -1);
  }

  public static VersionRange of(long from, long to) {
    Preconditions.checkArgument(from <= to, "Invalid range");
    return new VersionRange(from, to);
  }

  private VersionRange(long from, long to) {
    this.from = from;
    this.to = to;
  }

  public long from() {
    return from;
  }

  public long to() {
    return to;
  }

  public boolean contains(long version) {
    if (isEmpty()) {
      return false;
    }
    return from <= version && version <= to;
  }

  public boolean isEmpty() {
    return from == -1 || to == -1;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final VersionRange other = (VersionRange) obj;
    if (this.from != other.from) {
      return false;
    }
    if (this.to != other.to) {
      return false;
    }
    return true;
  }


  @Override
  public String toString() {
    return isEmpty() ? "[]" : "[" + from + "-" + to + "]";
  }
}
