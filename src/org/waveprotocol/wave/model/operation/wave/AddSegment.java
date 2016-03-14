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

package org.waveprotocol.wave.model.operation.wave;

import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.wave.data.WaveletData;

import java.util.Collections;
import java.util.List;

/**
 * Operation class for the add-segment operation.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public final class AddSegment extends WaveletOperation {

  /** Segment to add. */
  private final SegmentId segment;

  /**
   * Creates an add-segment operation.
   *
   * @param segment  segment to add
   */
  public AddSegment(WaveletOperationContext context, SegmentId segment) {
    super(context);
    this.segment = segment;
  }

  public SegmentId getSegmentId() {
    return segment;
  }

  @Override
  public void doApply(WaveletData target) {
  }

  @Override
  public void acceptVisitor(WaveletOperationVisitor visitor) {
  }

  @Override
  public String toString() {
    return "add segment " + segment + " " + suffixForToString();
  }

  @Override
  public List<? extends WaveletOperation> applyAndReturnReverse(WaveletData target) throws OperationException {
    WaveletOperationContext reverseContext = createReverseContext(target);
    return reverse(reverseContext);
  }

  @Override
  public List<? extends WaveletOperation> reverse(WaveletOperationContext reverseContext) throws OperationException {
    return Collections.singletonList(new RemoveSegment(reverseContext, segment));
  }

  @Override
  public int hashCode() {
    return segment.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof AddSegment)) {
      return false;
    }
    AddSegment other = (AddSegment) obj;
    return segment.equals(other.segment);
  }
}
