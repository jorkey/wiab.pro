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
package org.waveprotocol.wave.model.conversation.navigator;

import java.util.Stack;

/**
 * Context for blips and threads index creation.
 * 
 * @param <B> blip type
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class IndexContext<B> {

  public static <B> IndexContext<B> createEmpty() {
    return new IndexContext();
  }

  public static <B> IndexContext<B> create(B previousBlip, B previousBlipInRow) {
    return new IndexContext(previousBlip, previousBlipInRow);
  }

  /** Previous blip. */
  private B previousBlip;
  /** Stack of previous blips in the row. */
  private final Stack<B> previousBlipsInRow = new Stack<>();    

  /** Empty context. */
  private IndexContext() {
  }

  /** Non-empty context. */
  private IndexContext(B previousBlip, B previousBlipInRow) {
    this.previousBlip = previousBlip;
    previousBlipsInRow.push(previousBlipInRow);
  }

  public void setPreviousBlip(B previousBlip) {
    this.previousBlip = previousBlip;
  }

  public void pushPreviousBlipInRow(B previousBlipInRow) {
    previousBlipsInRow.push(previousBlipInRow);
  }

  public B getPreviousBlip() {
    return previousBlip;
  }

  public B popPreviousBlipInRow() {
    return previousBlipsInRow.isEmpty() ? null : previousBlipsInRow.pop();
  }

  @Override
  public String toString() {
    return "prevBlip=" + previousBlip +
        ", prevBlipsInRow=" + previousBlipsInRow;
  }
}
