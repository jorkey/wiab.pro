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

import org.waveprotocol.wave.model.adt.docbased.DocumentBasedBasicValue;
import org.waveprotocol.wave.model.document.util.DocumentEventRouter;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.supplement.ObservablePrimitiveSupplement.Listener;
import org.waveprotocol.wave.model.util.Serializer;

/**
 * Implements the per-wavelet blip state.
 *
 * @param <E> element type of the document implementation
 */
public class DocumentBasedWaveletBlipState<E>
    implements WaveletBlipState {

  /**
   * Creates a document based store for blip state.
   *
   * @param router document event router
   * @param container element in which the collapsed state is contained
   * @param id wavelet id being tracked
   * @param listener listener for collapsed-state changes
   * @return a new blip state tracker.
   */
  public static <E> DocumentBasedWaveletBlipState<E> create(
      DocumentEventRouter<? super E, E, ?> router, E container, WaveletId id, Listener listener) {
    return new DocumentBasedWaveletBlipState<>(router, container);
  }  
  
  private final DocumentEventRouter<? super E, E, ?> router;
  private final E container;
  private final DocumentBasedBasicValue<E, String> focusedBlipId;
  private final DocumentBasedBasicValue<E, ScreenPosition> screenPosition;
  
  DocumentBasedWaveletBlipState(DocumentEventRouter<? super E, E, ?> router, E container) {
    this.router = router;
    this.container = container;
    focusedBlipId = DocumentBasedBasicValue.create(
        router, container, Serializer.STRING, WaveletBasedSupplement.FOCUSED_ATTR);
    screenPosition = DocumentBasedBasicValue.create(
        router, container, ScreenPositionSerializer.INSTANCE, WaveletBasedSupplement.SCREEN_ATTR);    
  }

  @Override
  public String getFocusedBlipId() {
    return focusedBlipId.get();
  }

  @Override
  public ScreenPosition getScreenPosition() {
    return screenPosition.get();
  }  
  
  @Override
  public void setFocusedBlipId(String focusedBlipId) {
    this.focusedBlipId.set(focusedBlipId);
  }

  @Override
  public void setScreenPosition(ScreenPosition screenPosition) {
    this.screenPosition.set(screenPosition);
  }

  @Override
  public void remove() {
    router.getDocument().deleteNode(container);
  }
}
