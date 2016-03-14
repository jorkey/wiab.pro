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

import java.util.HashMap;
import java.util.Map;
import org.waveprotocol.wave.model.conversation.WaveletBasedConversation;
import org.waveprotocol.wave.model.document.ObservableMutableDocument;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.document.operation.impl.AttributesImpl;
import org.waveprotocol.wave.model.document.util.DocHelper;
import org.waveprotocol.wave.model.document.util.DocumentEventRouter;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.supplement.ObservablePrimitiveSupplement.Listener;
import org.waveprotocol.wave.model.util.ElementListener;
import org.waveprotocol.wave.model.util.Preconditions;

/**
 * Represents a collection of per-blipState blip states, implemented by embedding
 * them in elements of a document.
 */
public class WaveletBlipStateCollection<E>
    implements ElementListener<E> {
  
  public static <E> WaveletBlipStateCollection<E> create(
      DocumentEventRouter<? super E, E, ?> router, E e, Listener listener) {
    WaveletBlipStateCollection<E> col = new WaveletBlipStateCollection<E>(router, e, listener);
    router.addChildListener(e, col);
    col.load();
    return col;
  }
  
  private final DocumentEventRouter<? super E, E, ?> router;
  private final E container;

  /** Blip state, expressed as a per-blipState structure. */
  private final Map<WaveletId, WaveletBlipState> waveletSupplements =
      new HashMap<WaveletId, WaveletBlipState>();
  
  /** Listener to inject into each read-state. */
  private final Listener listener;
  
  private WaveletBlipStateCollection(
      DocumentEventRouter<? super E, E, ?> router, E container, Listener listener) {
    this.router = router;
    this.container = container;
    this.listener = listener;
  }
  
  public String getFocusedBlipId(WaveletId waveletId) {
    final WaveletBlipState blipState = waveletSupplements.get(waveletId);
    return blipState != null ? blipState.getFocusedBlipId() : null;
  }

  public ScreenPosition getScreenPosition(WaveletId waveletId) {
    final WaveletBlipState blipState = waveletSupplements.get(waveletId);
    return blipState != null ? blipState.getScreenPosition() : null;
  }  

  public Iterable<WaveletId> getStatefulWavelets() {
    return waveletSupplements.keySet();
  }  
  
  public void setFocusedBlipId(WaveletId waveletId, String blipId) {
    getBlipStateSupplement(waveletId).setFocusedBlipId(blipId);
  }

  public void setScreenPosition(WaveletId waveletId, ScreenPosition screenPosition) {
    getBlipStateSupplement(waveletId).setScreenPosition(screenPosition);
  }  
  
  @Override
  public void onElementAdded(E element, WaveletOperationContext opContext) {
    ObservableMutableDocument<? super E, E, ?> doc = getDocument();
    assert container.equals(doc.getParentElement(element));
    if (!WaveletBasedSupplement.CONVERSATION_TAG.equals(doc.getTagName(element))) {
      return;
    }

    WaveletId waveletId = valueOf(element);
    if (waveletId != null) {
      WaveletBlipState existing = waveletSupplements.get(waveletId);
      if (existing == null) {
        WaveletBlipState read =
            DocumentBasedWaveletBlipState.create(router, element, waveletId, listener);
        waveletSupplements.put(waveletId, read);
      } else {
        //
        // We can't mutate during callbacks yet.
        // Let's just ignore the latter :(. Clean up on timer?
        //
      }
    } else {
      // XML error: someone added a WAVELET element without an id. Ignore.
      // TODO(user): we should log this
    }
  }

  @Override
  public void onElementRemoved(E element, WaveletOperationContext opContext) {
    if (WaveletBasedSupplement.CONVERSATION_TAG.equals(getDocument().getTagName(element))) {
      WaveletId waveletId = valueOf(element);
      if (waveletId != null) {
        WaveletBlipState state = waveletSupplements.remove(waveletId);
        if (state == null) {
          // Not good - there was a collapsed-state element and we weren't
          // tracking it...
          // TODO(user): this is the same problem as the read state
          // tracker
        }
      }
    }
  }
  
  private ObservableMutableDocument<? super E, E, ?> getDocument() {
    return router.getDocument();
  }
  
  private void load() {
    ObservableMutableDocument<? super E, E, ?> doc = getDocument();
    E child = DocHelper.getFirstChildElement(doc, doc.getDocumentElement());
    while (child != null) {
      onElementAdded(child, null);
      child = DocHelper.getNextSiblingElement(doc, child);
    }
  }
  
  private WaveletId valueOf(E element) {
    String waveletIdStr = getDocument().getAttribute(element, WaveletBasedSupplement.ID_ATTR);
    return WaveletBasedConversation.widFor(waveletIdStr);
  }
  
  private WaveletBlipState getBlipStateSupplement(WaveletId waveletId) {
    Preconditions.checkNotNull(waveletId, "wavelet id must not be null");
    WaveletBlipState blipState = waveletSupplements.get(waveletId);
    if (blipState == null) {
      // Create a new container element for tracking state for the blipState.
      // Callbacks should build it.
      createEntry(waveletId);
      blipState = waveletSupplements.get(waveletId);
      assert blipState != null;
    }
    return blipState;
  }
  
  private void createEntry(WaveletId waveletId) {
    String waveletIdStr = WaveletBasedConversation.idFor(waveletId);
    ObservableMutableDocument<? super E, E, ?> doc = getDocument();
    doc.createChildElement(
        doc.getDocumentElement(), WaveletBasedSupplement.CONVERSATION_TAG,
        new AttributesImpl(WaveletBasedSupplement.ID_ATTR, waveletIdStr));
  }  
}
