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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a collection of per-wavelet look-states, implemented by embedding
 * them in elements of a document.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
class WaveletLookStateCollection<E> implements ElementListener<E> {
  
  private final DocumentEventRouter<? super E, E, ?> router;
  private final E container;

  /** Read state, expressed as a per-wavelet structure. */
  private final Map<WaveletId, WaveletLookState> waveletSupplements = new HashMap<>();

  /** Listener to inject into each read-state. */
  private final Listener listener;

  private WaveletLookStateCollection(DocumentEventRouter<? super E, E, ?> router, E container,
      Listener listener) {
    this.router = router;
    this.container = container;
    this.listener = listener;
  }

  public static <E> WaveletLookStateCollection<E> create(
      DocumentEventRouter<? super E, E, ?> router, E e, Listener listener) {
    WaveletLookStateCollection<E> col = new WaveletLookStateCollection<>(router, e, listener);
    router.addChildListener(e, col);
    col.load();
    return col;
  }

  private ObservableMutableDocument<? super E, E, ?> getDocument() {
    return router.getDocument();
  }

  private void load() {
    E child = DocHelper.getFirstChildElement(getDocument(), getDocument().getDocumentElement());
    while (child != null) {
      onElementAdded(child, null);
      child = DocHelper.getNextSiblingElement(getDocument(), child);
    }
  }

  private WaveletId valueOf(E element) {
    String waveletIdStr = getDocument().getAttribute(element, WaveletBasedSupplement.ID_ATTR);
    return WaveletBasedConversation.widFor(waveletIdStr);
  }

  @Override
  public void onElementAdded(E element, WaveletOperationContext opContext) {
    assert container == getDocument().getParentElement(element);
    if (!WaveletBasedSupplement.WAVELET_TAG.equals(getDocument().getTagName(element))) {
      return;
    }

    WaveletId waveletId = valueOf(element);
    if (waveletId != null) {
      WaveletLookState existing = waveletSupplements.get(waveletId);
      if (existing == null) {
        WaveletLookState read =
            DocumentBasedWaveletLookState.create(router, element, waveletId, listener);
        waveletSupplements.put(waveletId, read);

        // Fire events reflecting the initial read state.
        //
        // NOTE(user): it is important that these events get fired after the new read-state
        //   object is added to the map above, in order that the interface presented by this
        //   collection object is consistent with the events being broadcast to the listener.
        //
        long waveletVersion = read.getWaveletFirstLookVersion();
        if (waveletVersion != WaveletBasedSupplement.NO_VERSION) {
          listener.onFirstLookWaveletVersionSet(waveletId, waveletVersion);
        }
        for (String blipId : read.getLookedBlips()) {
          long blipVersion = read.getBlipFirstLookVersion(blipId);
          if (blipVersion != PrimitiveSupplement.NO_VERSION) {
            listener.onFirstLookBlipVersionSet(waveletId, blipId, blipVersion);
          }
        }
      } else {
        //
        // We can't mutate during callbacks yet.
        // Let's just ignore the latter :(. Clean up on timer?
        //
        // Merge the two together, and delete latter.
        //
        // for (String blip : read.getReadBlips()) {
        // existing.setBlipLastReadVersion(blip,
        // read.getBlipLastReadVersion(blip));
        // }
        // int participantsReadVersion =
        // read.getParticipantsLastReadVersion();
        // int waveletReadVersion = read.getWaveletLastReadVersion();
        // if (participantsReadVersion != NO_VERSION) {
        // existing.setParticipantsLastReadVersion(participantsReadVersion);
        // }
        // if (waveletReadVersion != NO_VERSION) {
        // existing.setWaveletLastReadVersion(waveletReadVersion);
        // }
      }
    } else {
      // XML error: someone added a WAVELET element without an id. Ignore.
      // TODO(user): log this at error level, once loggers are injected into
      // these classes.
    }
  }

  @Override
  public void onElementRemoved(E element, WaveletOperationContext opContext) {
    if (WaveletBasedSupplement.WAVELET_TAG.equals(getDocument().getTagName(element))) {
      WaveletId waveletId = valueOf(element);
      if (waveletId != null) {
        WaveletLookState state = waveletSupplements.remove(waveletId);
        if (state == null) {
          // Not good - there was a read-state element and we weren't tracking
          // it...
          // TODO(user): report an error here.
          return;
        }
        // Fire events reflecting everything becoming unread.
        for (String blipId : state.getLookedBlips()) {
          long blipVersion = state.getBlipFirstLookVersion(blipId);
          if (blipVersion != PrimitiveSupplement.NO_VERSION) {
            listener.onFirstLookBlipVersionSet(waveletId, blipId, PrimitiveSupplement.NO_VERSION);
          }
        }
        long waveletVersion = state.getWaveletFirstLookVersion();
        if (waveletVersion != WaveletBasedSupplement.NO_VERSION) {
          listener.onFirstLookWaveletVersionSet(waveletId, waveletVersion);
        }
      }
    }
  }

  private void createEntry(WaveletId waveletId) {
    String waveletIdStr = WaveletBasedConversation.idFor(waveletId);
    getDocument().createChildElement(getDocument().getDocumentElement(),
        WaveletBasedSupplement.WAVELET_TAG,
        new AttributesImpl(WaveletBasedSupplement.ID_ATTR, waveletIdStr));
  }

  WaveletLookState getSupplement(WaveletId waveletId) {
    Preconditions.checkNotNull(waveletId, "wavelet id must not be null");
    WaveletLookState wavelet = waveletSupplements.get(waveletId);
    if (wavelet == null) {
      // Create a new container element for tracking state for the wavelet.
      // Callbacks should
      // build it.
      createEntry(waveletId);
      wavelet = waveletSupplements.get(waveletId);
      assert wavelet != null;
    }
    return wavelet;
  }

  void setFirstLookBlipVersion(WaveletId waveletId, String blipId, long version) {
    getSupplement(waveletId).setBlipFirstLookVersion(blipId, version);
  }
  
  void setFirstLookWaveletVersion(WaveletId waveletId, long version) {
    getSupplement(waveletId).setWaveletFirstLookVersion(version);
  }
  
  void clearWaveletLookState(WaveletId waveletId) {
    getSupplement(waveletId).clearWaveletLookState();
  }
  
  void clear() {
    Collection<WaveletId> toRemove = new ArrayList<>(waveletSupplements.keySet());
    for (WaveletId waveletId : toRemove) {
      waveletSupplements.get(waveletId).remove();
    }
  }

  void clearBlipLookState(WaveletId waveletId, String blipId) {
    getSupplement(waveletId).clearBlipLookState(blipId);
  }
  
  long getFirstLookBlipVersion(WaveletId waveletId, String blipId) {
    WaveletLookState wavelet = waveletSupplements.get(waveletId);
    return wavelet != null ? wavelet.getBlipFirstLookVersion(blipId)
        : WaveletBasedSupplement.NO_VERSION;
  }
  
  boolean isBlipLooked(WaveletId waveletId, String blipId) {
    return getFirstLookBlipVersion(waveletId, blipId) != WaveletBasedSupplement.NO_VERSION;
  }
  
  long getLastReadWaveletVersion(WaveletId waveletId) {
    WaveletLookState wavelet = waveletSupplements.get(waveletId);
    return wavelet != null ? wavelet.getWaveletFirstLookVersion()
        : WaveletBasedSupplement.NO_VERSION;
  }

  long getFirstLookWaveletVersion(WaveletId waveletId) {
    WaveletLookState wavelet = waveletSupplements.get(waveletId);
    return wavelet != null ? wavelet.getWaveletFirstLookVersion()
        : WaveletBasedSupplement.NO_VERSION;
  }

  boolean isWaveletLooked(WaveletId waveletId) {
    return getFirstLookWaveletVersion(waveletId) != WaveletBasedSupplement.NO_VERSION;
  }
  
  Iterable<WaveletId> getLookedWavelets() {
    return waveletSupplements.keySet();
  }

  Iterable<String> getLookedBlips(WaveletId waveletId) {
    WaveletLookState wavelet = waveletSupplements.get(waveletId);
    return wavelet != null ? wavelet.getLookedBlips() : Collections.<String> emptySet();
  }
}
