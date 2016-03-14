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

package org.waveprotocol.wave.client.wavepanel.view;

/**
 * Reveals the primitive state in the view of a blip.
 *
 * @see BlipView for structural state.
 */
public interface IntrinsicBlipView {
  /**
   * Returns the id of the blip.
   * @return id of the blip.
   */
  String getId();

  /**
   * Returns the z-index of the blip.
   */
  int getZIndex();

  /**
   * Sets the z-index of the blip.
   */
  void setZIndex(int zIndex);

  /**
   * Sets the indentation level of the blip
   * @param level hierarchy level
   */
  void setIndentationLevel(int level);

  /**
   * Sets margins to separate the blip from the neighbors
   *
   * @param top top margin, in pixels
   * @param bottom bottom margin, in pixels
   */
  void setMargins(int top, int bottom);

  /**
   * Sets this blip view as quasi-deleted.
   * 
   * @param title title to be placed upon quasi-deleted blip
   * @param isRowOwnerDeleted true, if the row owner of the blip is quasi-deleted, too
   */
  void setQuasiDeleted(String title, boolean isRowOwnerDeleted);
}
