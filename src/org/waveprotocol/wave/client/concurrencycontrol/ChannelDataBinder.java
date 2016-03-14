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

package org.waveprotocol.wave.client.concurrencycontrol;

import org.waveprotocol.wave.client.wave.WaveDocuments;
import org.waveprotocol.wave.concurrencycontrol.channel.Accessibility;
import org.waveprotocol.wave.concurrencycontrol.channel.OperationChannel;
import org.waveprotocol.wave.concurrencycontrol.channel.OperationChannelMultiplexer;
import org.waveprotocol.wave.concurrencycontrol.channel.OperationChannelMultiplexer.KnownWavelet;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.StringMap;
import org.waveprotocol.wave.model.wave.ObservableWavelet;
import org.waveprotocol.wave.model.wave.WaveViewListener;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.model.wave.opbased.WaveViewImpl;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletFragmentData;

import java.util.Collection;
import org.waveprotocol.wave.client.wave.DiffContentDocument;

/**
 * Binds operation channels from a {@link OperationChannelMultiplexer mux} with
 * the output sinks of wavelets, and keeps binding matching channel/wavelet
 * pairs while live.
 *
 * @author hearnden@google.com (David Hearnden)
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public final class ChannelDataBinder {

  private final OperationChannelMultiplexer mux;
  private WaveViewImpl<OpBasedWavelet> waveView;
  private WaveletOperationalizer operationalizer;
  private StaticChannelBinder binder;

  /**
   * Operation channels waiting to be bound. This map is populated from {@link
   * #onOperationChannelCreated(OperationChannel, ObservableWaveletData,
   * Accessibility)}, and deleted by {@link #connect(String)}.
   */
  private final StringMap<OperationChannel> channelsToBound = CollectionUtils.createStringMap();

  //
  // The binding flow is not completely trivial, because it has to work with
  // two directions of control flow:
  //
  // Client-created wavelets:
  // 1. wavelet shows up in the model
  // 2. this binder tells mux to create op channel,
  // 3. its operation channel shows up, then
  // 4. wavelet and op channel are bound together.
  //
  // Server-created wavelets:
  // 1. op channel shows up in the mux,
  // 2. this binder builds wavelet and puts it in the model,
  // 3. wavelet shows up in the model, then
  // 4. wavelet and op channel are bound together.
  //
  // Also, the initial set of operation channels when opening a wave with known
  // wavelet states is just like the server-created wavelet flow, except without
  // step 2.
  //

  public ChannelDataBinder(OperationChannelMultiplexer mux) {
    this.mux = mux;
  }

  /*
   * Starts listening to wave events and channel events.
   */
  public void bindWaveView(final WaveViewImpl<OpBasedWavelet> waveView,
      final WaveletOperationalizer operationalizer, WaveDocuments<DiffContentDocument> waveDocuments) {
    Preconditions.checkArgument(this.waveView == null, "Wave view already binded");
    this.waveView = waveView;
    this.operationalizer = operationalizer;
    this.binder = StaticChannelBinder.create(operationalizer, waveDocuments);
    waveView.addListener(new WaveViewListener() {

      @Override
      public void onWaveletAdded(ObservableWavelet wavelet) {
        String id = ModernIdSerialiser.INSTANCE.serialiseWaveletId(wavelet.getId());
        if (channelsToBound.containsKey(id)) {
          connect(binder, id);
        } else {
          // This will trigger the onOperationChannelCreated callback below.
          mux.createOperationChannel(wavelet.getId(), wavelet.getCreatorId());
        }
      }

      @Override
      public void onWaveletRemoved(ObservableWavelet wavelet) {
        // TODO
      }
    });
    mux.setChannelsPresenceListener(new OperationChannelMultiplexer.ChannelsPresenceListener() {

      @Override
      public void onOperationChannelCreated(OperationChannel channel, ObservableWaveletFragmentData snapshot,
          Accessibility accessibility) {
        WaveletId wid = snapshot.getWaveletId();
        String id = ModernIdSerialiser.INSTANCE.serialiseWaveletId(wid);

        Preconditions.checkState(!channelsToBound.containsKey(id), "No channel for wavelet " + id);
        channelsToBound.put(id, channel);

        if (waveView.getWavelet(wid) != null) {
          connect(binder, id);
        } else {
          // This will trigger the onWaveletAdded callback above.
          waveView.addWavelet(operationalizer.operationalize(snapshot));
        }
      }

      @Override
      public void onOperationChannelRemoved(OperationChannel channel, WaveletId waveletId) {
        // TODO
      }
    });
    mux.setWaveView(waveView);
  }

  public Collection<KnownWavelet> getKnownWavelets() {
    Preconditions.checkNotNull(waveView, "Wave view is not binded");
    Collection<KnownWavelet> knownWavelets = CollectionUtils.createQueue();
    for (ObservableWaveletFragmentData wavelet : operationalizer.getWavelets()) {
      knownWavelets.add(new KnownWavelet(wavelet, wavelet.getHashedVersion(), Accessibility.READ_WRITE));
    }
    return knownWavelets;
  }

  public WaveViewImpl<OpBasedWavelet> getWaveView() {
    return waveView;
  }

  private void connect(StaticChannelBinder binder, String id) {
    binder.bind(id, removeAndReturn(channelsToBound, id));
  }

  // Something that should have been on StringMap from the beginning.
  private static <V> V removeAndReturn(StringMap<V> map, String key) {
    V value = map.get(key);
    map.remove(key);
    return value;
  }
}
