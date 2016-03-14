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

package org.waveprotocol.wave.concurrencycontrol.channel;

import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletFragmentData;
import org.waveprotocol.wave.concurrencycontrol.common.ChannelException;

import org.waveprotocol.wave.clientserver.ReturnStatus;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.model.wave.opbased.WaveViewImpl;

/**
 * Multiplexes several {@link OperationChannel operation channels} together.
 */
public interface OperationChannelMultiplexer {
  /**
   * Listener for handling channels presence events.
   */
  interface ChannelsPresenceListener {
    /**
     * Notifies this listener that a new channel has been established. The new
     * channel, based on the snapshot given here, is ready to be used
     * immediately.
     *
     * @param channel new channel
     * @param snapshot initial state of the wavelet on the new channel
     * @param accessibility initial accessibility of the new wavelet
     */
    void onOperationChannelCreated(OperationChannel channel, ObservableWaveletFragmentData snapshot,
        Accessibility accessibility);
    
    /**
     * Notifies this listener that an existing channel has been destroyed. The dropped
     * channel is typically likely to be replaced by a new one.
     *
     * @param channel destroyed channel
     * @param waveletId the id of the wavelet serviced over this channel
     */
    void onOperationChannelRemoved(OperationChannel channel, WaveletId waveletId);
  }

  /**
   * Listener for handling channels opening events.
   */
  interface ChannelsOpeningListener {
    /**
     * Notifies this listener that an channel has been opened.
     *
     * @param channel opened channel
     * @param waveletId the id of the wavelet serviced over this channel
     */
    void onOperationChannelOpened(OperationChannel channel, WaveletId waveletId);
  }
  
  /**
   * Listener for handling stream events.
   */
  interface StreamListener {
    /**
     * Notifies this listener that the initial open has finished.
     *
     * The "initial open" is defined as the point after which the channel
     * listener has been notified of all operation channels for some "initial
     * set" of wavelets as decided by the server. Note that the channel listener
     * may also be notified of other channels, interleaved amongst the channels
     * for the initial set, before {@link #onOpenFinished()} is called.
     */
    void onConnected();

    /**
     * Notifies this listener that the multiplexer has failed. This may occur
     * before or after any channels are created, before or after
     * {@link #onOpenFinished()}.
     *
     * No further messages will be received by any callback or operation channel
     * after this message.
     */
    void onFailed(ReturnStatus detail);

    /**
     * Notifies this listener that an exception has occurred. The multiplexer is not
     * closed yet when this method is called, but it will be closed after this call.
     *
     * @param ex The exception that occured whilst handling responses from the server.
     */
    void onException(ChannelException ex);
  }

  /**
   * Information required to open a wavelet at a known state.
   */
  public static final class KnownWavelet {
    /** Wavelet data. May not be null. */
    public final ObservableWaveletFragmentData snapshot;

    /** Last committed version of wavelet. May not be null. */
    public final HashedVersion committedVersion;

    /** The wavelet's accessibility to the user. May not be null. */
    public final Accessibility accessibility;

    public KnownWavelet(ObservableWaveletFragmentData snapshot, HashedVersion committedVersion,
        Accessibility accessibility) {
      this.snapshot = snapshot;
      this.committedVersion = committedVersion;
      this.accessibility = accessibility;
    }
  }

  /**
   * Sets channels presence listener.
   */
  void setChannelsPresenceListener(ChannelsPresenceListener listener);

  /**
   * Sets channels state listener.
   */
  void setChannelsOpeningListener(ChannelsOpeningListener listener);

  /**
   * Sets wave view data.
   */
  void setWaveView(WaveViewImpl<OpBasedWavelet> waveView);
  
  /**
   * Opens this multiplexer. After it becomes open, operation channels will be
   * passed to the {@code muxListener} as they come into existence. The listener
   * may be notified as part of this call, or afterwards.
   *
   * @param knownWavelets the known wavelets.
   * @param knownSegmentIds the knownSegmentIds
   * @param streamListener the stream listener.
.  */
  public void open(Collection<KnownWavelet> knownWavelets,
      Map<WaveletId, Set<SegmentId>> knownSegmentIds, StreamListener streamListener);

  /**
   * Disconnects this multiplexer.
   */
  public void disconnect();
  
  /**
   * Reopens this multiplexer.
   *
   * @param knownSegmentIds the knownSegmentIds
.  */
  public void reopen(Map<WaveletId, Set<SegmentId>> knownSegmentIds);

  /**
   * Closes this multiplexer. The channel listener will no longer be notified of
   * new channels, and no more operations will be received on existing channels.
   * The behavior of requests to create new channels, or the submission of
   * operations to existing channels, is undefined.
   */
  public void close();

  /**
   * Creates a new operation channel.  The listener will be notified of the new
   * channel during this method.
   *
   * @param waveletId wavelet id for the new operation channel
   * @param creator address of the wavelet creator
   */
  public void createOperationChannel(WaveletId waveletId, ParticipantId creator);
}
