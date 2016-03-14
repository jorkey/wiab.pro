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

import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.concurrencycontrol.client.ConcurrencyControl;
import org.waveprotocol.wave.concurrencycontrol.common.ChannelException;
import org.waveprotocol.wave.clientserver.ReturnStatus;
import org.waveprotocol.wave.clientserver.ReturnCode;
import org.waveprotocol.wave.concurrencycontrol.common.UnsavedDataListenerFactory;
import org.waveprotocol.wave.concurrencycontrol.common.Recoverable;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.raw.RawOperation;
import org.waveprotocol.wave.model.raw.RawFragment;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.FuzzingBackOffScheduler;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.Scheduler;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.NoOp;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletFragmentData;
import org.waveprotocol.wave.model.wave.data.impl.EmptyWaveletFragmentSnapshot;
import org.waveprotocol.wave.model.wave.data.impl.WaveletFragmentDataImpl;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.RuntimeOperationsException;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.OperationRuntimeException;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.model.wave.opbased.WaveViewImpl;

/**
 * Multiplexes several {@link OperationChannel operation channels} over one
 * {@link ViewChannel view channel}.
 *
 *
 *       |- OperationChannelMultiplexer -----------------------------------------|
 *       |                                                                       |
 *       |  |-Stacklet---------------------------------|                         |
 *       |  | OperationChannel <-> WaveletDeltaChannel |-|                       |
 *   <-> |  |------------------------------------------| |-|   <=> View Channel  | <-> WaveService
 *       |    |------------------------------------------| |                     |
 *       |      |------------------------------------------|                     |
 *       |                                                                       |
 *       |          All exceptions are directed here                             |
 *       |-----------------------------------------------------------------------|
 *
 * Note:
 *
 * All exceptions that are emitted from using the OperationChannel or
 * OperationChannelMultiplexer interfaces are caught in this class.
 * i.e. when the client calls methods from the left part of the diagram.
 *
 * All exceptions generated as a result of handling server messages in ViewChannel
 * are routed here through onException(). i.e. when the WaveService calls methods on
 * the right part of the diagram through call backs.
 *
 * This class is responsible for reporting all the exceptions to the user.
 *
 * @author unknown
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class OperationChannelMultiplexerImpl implements OperationChannelMultiplexer {

  /**
   * Factory for creating delta channels.
   */
  interface DeltaChannelFactory {
    /**
     * Creates a delta channel.
     *
     * @param waveletChannel channel through which the delta channel
     *        communicates
     */
    WaveletDeltaChannel create(WaveletChannel waveletChannel);
  }

  /**
   * Factory for operation channels.
   */
  interface OperationChannelFactory {
    /**
     * Creates an operation channel.
     *
     * @param deltaChannel channel through which the op channel communicates
     * @param waveletId wavelet id for the new operation channel
     * @param startVersion the version to start from
     * @param accessibility accessibility of the new channel
     * @return a new operation channel.
     */
    InternalOperationChannel create(WaveletDeltaChannel deltaChannel, WaveletId waveletId,
        HashedVersion startVersion, Accessibility accessibility);
  }

  /**
   * A per-wavelet stack above this multiplexer. A stacklet forwards message
   * from the server to a listener at the bottom of the stacklet (a delta
   * channel). When communications fail a stacklet fetches reconnection version
   * from the contained operation channel.
   */
  private static class Stacklet implements WaveletChannel.Listener {
    private final WaveletDeltaChannel deltaChannel;
    private final InternalOperationChannel opChannel;

    /**
     * Creates a stacklet.
     *
     * @param deltaChannel delta channel at the bottom of the stacklet
     * @param opChannel operation channel at the top of the stacklet
     */
    private Stacklet(WaveletDeltaChannel deltaChannel, InternalOperationChannel opChannel) {
      this.deltaChannel = deltaChannel;
      this.opChannel = opChannel;
    }

    @Override
    public void onConnection(HashedVersion connectVersion, HashedVersion lastModifiedVersion,
        HashedVersion lastCommittedVersion, HashedVersion unacknowledgedDeltaVersion,
        List<WaveletOperation> operations) throws ChannelException {
      deltaChannel.onConnection(connectVersion, lastModifiedVersion, lastCommittedVersion,
          unacknowledgedDeltaVersion, operations);
    }

    @Override
    public void onWaveletUpdate(List<TransformedWaveletDelta> deltas,
        HashedVersion lastCommittedVersion) throws ChannelException {
      deltaChannel.onWaveletUpdate(deltas, lastCommittedVersion);
    }

    /**
     * Resets this stacklet ready for reconnection.
     */
    public void reset() {
      deltaChannel.reset(opChannel);
      opChannel.reset();
    }

    /**
     * Closes this stacklet permanently.
     */
    public void close() {
      deltaChannel.reset(null);
      opChannel.close();
    }

    public OperationChannel getOperationChannel() {
      return opChannel;
    }
  }

  /**
   * Holder class for the copious number of loggers.
   */
  public static class LoggerContext {
    public final LoggerBundle ops;
    public final LoggerBundle delta;
    public final LoggerBundle cc;
    public final LoggerBundle mux;

    public LoggerContext(LoggerBundle ops, LoggerBundle delta, LoggerBundle cc, LoggerBundle mux) {
      this.ops = ops;
      this.delta = delta;
      this.cc = cc;
      this.mux = mux;
    }
  }

  /** Multiplexer state. */
  private static enum State { INITIAL, CONNECTING, CONNECTED, DISCONNECTED, RECONNECTING }

  /** Wave id for channels in this mux. */
  private final WaveId waveId;

  /**
   * Underlying multiplexed view channel; set null on close.
   */
  private ViewChannel viewChannel;

  /** Multiplexed channels, indexed by wavelet id. */
  private final Map<WaveletId, Stacklet> channels = CollectionUtils.newHashMap();

  /** Factory for creating delta channels. */
  private final DeltaChannelFactory deltaChannelFactory;

  /** Factory for creating operation-channel stacks on top of wave services. */
  private final OperationChannelFactory opChannelFactory;

  /** Logger. */
  private final LoggerBundle logger;

  /** Synthesizer of initial wavelet snapshots for locally-created wavelets. */
  private final WaveletFragmentDataImpl.Factory dataFactory;

  /** Produces hashed versions. */
  private final HashedVersionFactory hashFactory;

  /** Connection state of the mux. */
  private State state;

  /** Listener for handling multiplexer events. */
  private ChannelsPresenceListener channelsPresenceListener;

  /** Listener for handling channels state events. */
  private ChannelsOpeningListener channelsOpeningListener;

  /** Wave data. */
  private WaveViewImpl<OpBasedWavelet> waveView;

  /** Mux connection listener. */
  private StreamListener streamListener;

  /** Used to backoff when reconnecting. */
  private final Scheduler scheduler;

  /**
   * Creates factory for building delta channels.
   *
   * @param logger logger to use for created channels
   */
  private static DeltaChannelFactory createDeltaChannelFactory(final LoggerBundle logger) {
    return new DeltaChannelFactory() {
        @Override
        public WaveletDeltaChannel create(WaveletChannel waveletChannel) {
          return new WaveletDeltaChannelImpl(waveletChannel, logger);
        }
      };
  }

  /**
   * Creates a factory for building operation channels on a wave.
   *
   * @param unsavedDataListenerFactory factory for unsaved data listeners
   * @param loggers logger bundle
   * @return a new operation channel factory
   */
  private static OperationChannelFactory createOperationChannelFactory(
        final UnsavedDataListenerFactory unsavedDataListenerFactory, final LoggerContext loggers) {
    return new OperationChannelFactory() {
          @Override
          public InternalOperationChannel create(WaveletDeltaChannel deltaChannel, WaveletId waveletId,
              HashedVersion startVersion, Accessibility accessibility) {
            ConcurrencyControl cc = new ConcurrencyControl(loggers.cc, startVersion);
            if (unsavedDataListenerFactory != null) {
              cc.setUnsavedDataListener(unsavedDataListenerFactory.create(waveletId));
            }
            return new OperationChannelImpl(loggers.ops, deltaChannel, cc, accessibility);
          }
        };
  }

  /**
   * Creates a multiplexer.
   *
   * WARNING: the scheduler should provide back-off. Providing a scheduler which
   * executes immediately or does not back off may cause denial-of-service-like
   * reconnection attempts against the servers. Use something like
   * {@link FuzzingBackOffScheduler}.
   *
   * @param waveId wave id to open
   * @param viewChannel the view channel
   * @param dataFactory factory for making snapshots of empty wavelets
   * @param loggers log targets
   * @param unsavedDataListenerFactory a factory for adding listeners
   * @param scheduler scheduler for reconnection
   * @param hashFactory factory for hashed versions
   */
  public OperationChannelMultiplexerImpl(WaveId waveId, ViewChannel viewChannel,
      WaveletFragmentDataImpl.Factory dataFactory, LoggerContext loggers,
      UnsavedDataListenerFactory unsavedDataListenerFactory, Scheduler scheduler,
      HashedVersionFactory hashFactory) {
    // Construct default dependency implementations, based on given arguments.
    this(waveId, viewChannel, createDeltaChannelFactory(loggers.delta),
        createOperationChannelFactory(unsavedDataListenerFactory, loggers),
        dataFactory, scheduler, loggers.mux, hashFactory);
    Preconditions.checkNotNull(dataFactory, "null dataFactory");
  }

  /**
   * Creates a multiplexer (direct dependency arguments only). Exposed as
   * package-private for testing.
   *
   * @param waveId wave id to open
   * @param viewChannel the view channel
   * @param deltaChannelFactory factory for creating delta-channel stacks
   * @param opChannelFactory factory for creating operation-channel stacks
   * @param dataFactory factory for creating wavelet snapshots
   * @param scheduler used to back off when reconnecting. assumed not null.
   * @param logger log target
   * @param hashFactory factory for hashed versions
   */
  OperationChannelMultiplexerImpl(WaveId waveId, ViewChannel viewChannel,
      DeltaChannelFactory deltaChannelFactory, OperationChannelFactory opChannelFactory,
      WaveletFragmentDataImpl.Factory dataFactory,
      Scheduler scheduler, LoggerBundle logger, HashedVersionFactory hashFactory) {
    this.waveId = waveId;
    this.viewChannel = viewChannel;
    this.deltaChannelFactory = deltaChannelFactory;
    this.opChannelFactory = opChannelFactory;
    this.dataFactory = dataFactory;
    this.logger = logger;
    this.state = State.INITIAL;
    this.scheduler = scheduler;
    this.hashFactory = hashFactory;
  }

  @Override
  public void setChannelsPresenceListener(ChannelsPresenceListener listener) {
    this.channelsPresenceListener = listener;
  }

  @Override
  public void setChannelsOpeningListener(ChannelsOpeningListener listener) {
    this.channelsOpeningListener = listener;
  }

  @Override
  public void setWaveView(WaveViewImpl<OpBasedWavelet> waveView) {
    this.waveView = waveView;
  }

  @Override
  public void open(Collection<KnownWavelet> knownWavelets,
      Map<WaveletId, Set<SegmentId>> knownSegmentIds, StreamListener streamListener) {
    this.streamListener = streamListener;

    for (KnownWavelet knownWavelet : knownWavelets) {
      Preconditions.checkNotNull(knownWavelet.snapshot, "Wavelet has no snapshot");
      Preconditions.checkNotNull(knownWavelet.committedVersion,
          "Known wavelet has null committed version");
      if (!channels.containsKey(knownWavelet.snapshot.getWaveletId())) {
        createStacklet(knownWavelet.snapshot.getWaveletId(), knownWavelet.snapshot, knownWavelet.accessibility);
      }
    }

    Map<WaveletId, List<HashedVersion>> knownSignatures = getSignaturesFromWavelets(knownWavelets);
    connect(knownSignatures, knownSegmentIds, null);
  }

  @Override
  public void disconnect() {
    if (state != State.INITIAL && state != State.DISCONNECTED) {
      state = State.DISCONNECTED;
      viewChannel.disconnect();
      // Terminates all stacklets.
      for (WaveletId waveletId : channels.keySet()) {
        channels.get(waveletId).reset();
      }
    }
  }

  @Override
  public void reopen(Map<WaveletId, Set<SegmentId>> knownSegmentIds) {
    reconnect(knownSegmentIds);
  }

  @Override
  public void close() {
    shutdown(ReturnCode.OK, "Multiplexer is closed", null);
  }

  @Override
  public void createOperationChannel(WaveletId waveletId, ParticipantId creator) {
    if (channels.containsKey(waveletId)) {
      Preconditions.illegalArgument("Operation channel already exists for: " + waveletId);
    }

    // Create the new channel, and fake an initial snapshot.
    // TODO(anorth): inject a clock for providing timestamps.
    HashedVersion v0 = hashFactory.createVersionZero(WaveletName.of(waveId, waveletId));
    ObservableWaveletFragmentData emptySnapshot =
        dataFactory.create(
            new EmptyWaveletFragmentSnapshot(waveId, waveletId, creator, v0, System.currentTimeMillis()));

    createStacklet(waveletId, emptySnapshot, Accessibility.READ_WRITE);
  }

  /**
   * Creates a view channel listener.
   */
  private ViewChannel.Listener createViewListener() {
    return
        new ViewChannel.Listener() {

          @Override
          public void onWaveletOpened(WaveletId waveletId, HashedVersion connectVersion,
              HashedVersion lastModifiedVersion, long lastModifiedTime, HashedVersion lastCommittedVersion,
              HashedVersion unacknowledgedDeltaVersion, Map<SegmentId, RawFragment> fragments) throws ChannelException {
            Preconditions.checkArgument(!connectVersion.equals(lastModifiedVersion) || fragments.isEmpty(),
              "Unexpected diff operations - version not changed");
            Stacklet stacklet = channels.get(waveletId);
            Preconditions.checkNotNull(stacklet, "No stacklet " + waveletId.toString());
            List<WaveletOperation> operations = new LinkedList<>();
            if (waveView != null) {
              OpBasedWavelet wavelet = waveView.getWavelet(waveletId);
              Preconditions.checkNotNull(wavelet, "No wavelet " + waveletId.toString());
              ObservableWaveletFragmentData waveletData = (ObservableWaveletFragmentData)wavelet.getWaveletData();
              waveletData.setLastModifiedTime(lastModifiedTime);
              for (Map.Entry<SegmentId, RawFragment> entry : fragments.entrySet()) {
                if (entry.getKey().isIndex() || waveletData.isRaw(entry.getKey())) {
                  try {
                    waveletData.applyRawFragment(entry.getKey(), entry.getValue());
                  } catch (OperationException ex) {
                    throw new OperationRuntimeException("Error of applying raw fragment", ex);
                  }
                } else {
                  Preconditions.checkArgument(!entry.getValue().hasSnapshot(), "Duplicate fragment");
                  for (RawOperation rawOperation : entry.getValue().getAdjustOperations()) {
                    operations.addAll(rawOperation.getOperations());
                  }
                  for (RawOperation rawOperation : entry.getValue().getDiffOperations()) {
                    operations.addAll(rawOperation.getOperations());
                  }
                }
              }
            } else {
              for (Map.Entry<SegmentId, RawFragment> entry : fragments.entrySet()) {
                if (!entry.getKey().isIndex()) {
                  Preconditions.checkArgument(!entry.getValue().hasSnapshot(), "Duplicate fragment");
                  for (RawOperation rawOperation : entry.getValue().getAdjustOperations()) {
                    operations.addAll(rawOperation.getOperations());
                  }
                  for (RawOperation rawOperation : entry.getValue().getDiffOperations()) {
                    operations.addAll(rawOperation.getOperations());
                  }
                }
              }
            }
            stacklet.onConnection(connectVersion, lastModifiedVersion, lastCommittedVersion,
                unacknowledgedDeltaVersion, operations);
            if (channelsOpeningListener != null) {
              channelsOpeningListener.onOperationChannelOpened(stacklet.getOperationChannel(), waveletId);
            }
          }

          @Override
          public void onConnected() throws ChannelException {
            state = State.CONNECTED;
            if (streamListener != null) {
              streamListener.onConnected();
            }
          }

          @Override
          public void onUpdate(WaveletId waveletId, List<TransformedWaveletDelta> deltas,
              HashedVersion lastCommittedVersion) throws ChannelException {
            Stacklet stacklet = channels.get(waveletId);
            if (stacklet != null) {
              stacklet.onWaveletUpdate(deltas, lastCommittedVersion);
            } else {
              logger.error().log("No stacklet for wavelet " + waveletId.toString());
            }
          }

          @Override
          public void onFailure(ReturnStatus status) {
            if (streamListener != null) {
              streamListener.onFailed(status);
            }
          }

          @Override
          public void onException(ChannelException e) {
            if (e.getRecoverable() != Recoverable.RECOVERABLE) {
              shutdown(e.getResponseCode(), "Channel Exception", e);
            }
            if (streamListener != null) {
              streamListener.onException(e);
            }
          }

          @Override
          public void onDisconnected() {
          }

          @Override
          public void onClosed() {
          }
        };
  }

  /**
   * Adds a new operation-channel stacklet to this multiplexer and notifies the
   * listener of the new channel's creation.
   *
   * @param waveletId id of the concurrency domain for the new channel
   * @param snapshot wavelet initial state snapshot
   * @param accessibility accessibility of the stacklet; if not
   *        {@link Accessibility#READ_WRITE} then
   *        the stacklet will fail on send
   */
  private Stacklet createStacklet(final WaveletId waveletId, ObservableWaveletFragmentData snapshot,
      Accessibility accessibility) {
    if (channels.containsKey(waveletId)) {
      Preconditions.illegalArgument("Cannot create duplicate channel for wavelet: " + waveId + "/"
          + waveletId);
    }
    WaveletChannel waveletChannel = createWaveletChannel(waveletId);
    WaveletDeltaChannel deltaChannel = deltaChannelFactory.create(waveletChannel);
    InternalOperationChannel opChannel = opChannelFactory.create(deltaChannel, waveletId,
        snapshot.getHashedVersion(), accessibility);
    Stacklet stacklet = new Stacklet(deltaChannel, opChannel);
    stacklet.reset();
    channels.put(waveletId, stacklet);

    if (channelsPresenceListener != null) {
      channelsPresenceListener.onOperationChannelCreated(stacklet.getOperationChannel(), snapshot,
          accessibility);
    }

    return stacklet;
  }

  private void connect(Map<WaveletId, List<HashedVersion>> knownWavelets,
      Map<WaveletId, Set<SegmentId>> knownSegmentIds, Map<WaveletId, WaveletDelta> unacknowledgedDeltas) {
    Preconditions.checkState(state != State.CONNECTED, "Cannot connect already-connected channel");
    logger.trace().log("Multiplexer reconnecting wave " + waveId);
    state = State.CONNECTING;
    viewChannel.open(knownWavelets, knownSegmentIds, unacknowledgedDeltas, createViewListener());
  }

  /**
   * Reconnects with the known versions provided by stacklets.
   *
   * @param exception The exception that caused the reconnection
   */
  private void reconnect(final Map<WaveletId, Set<SegmentId>> knownSegmentIds) {
    Preconditions.checkState(state != State.CONNECTED, "Cannot connect already-connected channel");
    logger.trace().log("Reconnecting multiplexer");
    state = State.RECONNECTING;

    // NOTE(zdwang): don't clear this as we'll lose wavelets if we've never
    // been connected. This is a reminder.
    // onConnected.clear();

    // Reset each stacklet, collecting the reconnect versions.
    final Map<WaveletId, List<HashedVersion>> knownWavelets = CollectionUtils.newHashMap();
    final Map<WaveletId, WaveletDelta> unacknowledgedDeltas = CollectionUtils.newHashMap();
    for (final WaveletId waveletId : channels.keySet()) {
      Stacklet stacklet = channels.get(waveletId);
      knownWavelets.put(waveletId, stacklet.getOperationChannel().getReconnectVersions());
      if (stacklet.getOperationChannel().getUnacknowledgedDelta() != null) {
        unacknowledgedDeltas.put(waveletId, stacklet.getOperationChannel().getUnacknowledgedDelta());
      }
    }

    // Run the connect part in the scheduler
    scheduler.schedule(new Scheduler.Command() {

          @Override
          public void execute() {
            connect(knownWavelets, knownSegmentIds, unacknowledgedDeltas.isEmpty() ? null : unacknowledgedDeltas);
          }
        });
  }

  /**
   * Shuts down this multiplexer permanently.
   *
   * @param reasonCode code representing failure reason. If the value is not
   *    {@code ResponseCode.OK} then the listener will be notified of connection failure.
   * @param description reason for failure
   * @param exception any exception that caused the shutdown.
   */
  private void shutdown(ReturnCode reasonCode, String description, Throwable exception) {
    logger.trace().log("Shutdown of multiplexer.");
    
    scheduler.reset();

    if (description == null) {
      description = "(No error description provided)";
    }

    boolean notifyFailure = (reasonCode != ReturnCode.OK);

    // We are telling the user through UI that the wave is corrupt, so we must also report it
    // to the server.
    if (notifyFailure) {
      if (exception == null) {
        logger.error().log(description);
      } else {
        logger.error().log(description, exception);
      }
    }

    if (viewChannel != null) {
      // Ignore future messages.
      state = State.INITIAL;

      for (Stacklet stacklet : channels.values()) {
        stacklet.close();
      }
      channels.clear();
      viewChannel.close();
      if (streamListener != null && notifyFailure) {
        streamListener.onFailed(new ReturnStatus(reasonCode, description, exception));
        streamListener = null;
      }
    }
  }

  /**
   * Creates a wavelet channel for submissions against a wavelet.
   *
   * @param waveletId wavelet id for the channel
   */
  private WaveletChannel createWaveletChannel(final WaveletId waveletId) {
    return new WaveletChannel() {
      @Override
      public void submit(WaveletDelta delta, final SubmitCallback callback) {
        viewChannel.submitDelta(waveletId, delta, callback);
      }
    };
  }

  /**
   * Constructs a maps of list of wavelet signatures from a collection of
   * wavelet snapshots.
   *
   * Package-private for testing.
   */
  static Map<WaveletId, List<HashedVersion>> getSignaturesFromWavelets(
      Collection<KnownWavelet> knownWavelets) {
    Map<WaveletId, List<HashedVersion>> signatures = new HashMap<>();
    for (KnownWavelet knownWavelet : knownWavelets) {
      if (knownWavelet.accessibility.isReadable()) {
        ObservableWaveletData snapshot = knownWavelet.snapshot;
        WaveletId waveletId = snapshot.getWaveletId();
        List<HashedVersion> sigs = Collections.singletonList(snapshot.getHashedVersion());
        signatures.put(waveletId, sigs);
      }
    }
    return signatures;
  }
}
