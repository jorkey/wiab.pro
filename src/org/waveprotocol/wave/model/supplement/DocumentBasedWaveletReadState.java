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

import org.waveprotocol.wave.model.adt.ObservableMonotonicMap;
import org.waveprotocol.wave.model.adt.ObservableMonotonicValue;
import org.waveprotocol.wave.model.adt.docbased.DocumentBasedMonotonicMap;
import org.waveprotocol.wave.model.adt.docbased.DocumentBasedMonotonicValue;
import org.waveprotocol.wave.model.document.util.DocumentEventRouter;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.supplement.ObservablePrimitiveSupplement.Listener;
import org.waveprotocol.wave.model.util.Serializer;

/**
 * Implements the per-wavelet read state, using the
 * {@link DocumentBasedMonotonicValue} embedding for the participants and
 * wavelet-override last-read versions, and the
 * {@link DocumentBasedMonotonicMap} embedding for per-blip last-read versions.
 *
 * @param <E> element type of the document implementation
 */
class DocumentBasedWaveletReadState<E>
    implements WaveletReadState {

  private final ObservableMonotonicMap<String, Long> blips;
  private final ObservableMonotonicValue<Long> participants;
  private final ObservableMonotonicValue<Long> tags;
  private final ObservableMonotonicValue<Long> wavelet;
  private final DocumentEventRouter<? super E, E, ?> router;
  private final E container;

  DocumentBasedWaveletReadState(DocumentEventRouter<? super E, E, ?> router, E container) {
    this.router = router;
    this.container = container;
    blips = DocumentBasedMonotonicMap.create(router, container,
        Serializer.STRING, Serializer.LONG, WaveletBasedSupplement.BLIP_READ_TAG,
        WaveletBasedSupplement.ID_ATTR, WaveletBasedSupplement.VERSION_ATTR);
    participants = DocumentBasedMonotonicValue.create(router, container, Serializer.LONG,
        WaveletBasedSupplement.PARTICIPANTS_READ_TAG, WaveletBasedSupplement.VERSION_ATTR);
    tags = DocumentBasedMonotonicValue.create(router, container, Serializer.LONG,
        WaveletBasedSupplement.TAGS_READ_TAG, WaveletBasedSupplement.VERSION_ATTR);
    wavelet = DocumentBasedMonotonicValue.create(router, container, Serializer.LONG,
        WaveletBasedSupplement.WAVELET_READ_TAG, WaveletBasedSupplement.VERSION_ATTR);
  }

  /**
   * Creates
   *
   * @param router     router
   * @param container  element in which the read state is contained
   * @param id         wavelet id being tracked
   * @param listener   listener for read-state changes
   * @return a new read-state tracker.
   */
  public static <E> DocumentBasedWaveletReadState<E> create(
      DocumentEventRouter<? super E, E, ?> router, E container, WaveletId id,
      Listener listener) {
    DocumentBasedWaveletReadState<E> x = new DocumentBasedWaveletReadState<>(router, container);
    x.installListeners(id, listener);
    return x;
  }

  /**
   * Injects listeners into the underlying ADTs that translate their events
   * into primitive-supplement events.
   *
   * @param wid
   * @param listener
   */
  private void installListeners(final WaveletId wid, final Listener listener) {
    
    blips.addListener(new ObservableMonotonicMap.Listener<String, Long>() {

      @Override
      public void onEntrySet(String key, Long oldValue, Long newValue) {
        listener.onLastReadBlipVersionChanged(wid, key, valueOf(oldValue), valueOf(newValue));
      }
    });

    participants.addListener(new ObservableMonotonicValue.Listener<Long>() {

      @Override
      public void onSet(Long oldValue, Long newValue) {
        listener.onLastReadParticipantsVersionChanged(wid, valueOf(oldValue), valueOf(newValue));
      }
    });

    tags.addListener(new ObservableMonotonicValue.Listener<Long>() {

      @Override
      public void onSet(Long oldValue, Long newValue) {
        listener.onLastReadTagsVersionChanged(wid, valueOf(oldValue), valueOf(newValue));
      }
    });

    wavelet.addListener(new ObservableMonotonicValue.Listener<Long>() {

      @Override
      public void onSet(Long oldValue, Long newValue) {
        listener.onLastReadWaveletVersionChanged(wid, valueOf(oldValue), valueOf(newValue));
      }
    });
  }

  private static long valueOf(Long version) {
    return version != null ? version : PrimitiveSupplement.NO_VERSION;
  }

  @Override
  public void setBlipLastReadVersion(String blipId, long version) {
    blips.put(blipId, version);
  }

  @Override
  public long getBlipLastReadVersion(String id) {
    return valueOf(blips.get(id));
  }

  @Override
  public void setParticipantsLastReadVersion(long version) {
    participants.set(version);
  }

  @Override
  public long getParticipantsLastReadVersion() {
    return valueOf(participants.get());
  }

  @Override
  public long getTagsLastReadVersion() {
    return valueOf(tags.get());
  }

  @Override
  public void setTagsLastReadVersion(long version) {
    tags.set(version);
  }

  @Override
  public void setWaveletLastReadVersion(long version) {
    wavelet.set(version);
  }
  
  @Override
  public long getWaveletLastReadVersion() {
    return valueOf(wavelet.get());
  }

  @Override
  public Iterable<String> getReadBlips() {
    return blips.keySet();
  }

  @Override
  public void remove() {
    router.getDocument().deleteNode(container);
  }

  @Override
  public void clearBlipReadState(String blipId) {
    blips.remove(blipId);
  }
}
