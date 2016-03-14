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

package org.waveprotocol.wave.concurrencycontrol.client;

import com.google.protobuf.ByteString;
import org.waveprotocol.wave.concurrencycontrol.common.ChannelException;
import org.waveprotocol.wave.concurrencycontrol.server.ConcurrencyControlCore;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.TransformException;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import org.waveprotocol.box.common.ListReceiver;
import org.waveprotocol.box.common.ThrowableReceiver;
import org.waveprotocol.box.server.serialize.OperationSerializer;
import org.waveprotocol.box.server.waveserver.AppliedDeltaUtil;
import org.waveprotocol.box.server.waveserver.ByteStringMessage;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.wave.federation.Proto;
import org.waveprotocol.wave.federation.Proto.ProtocolAppliedWaveletDelta;
import org.waveprotocol.wave.federation.Proto.ProtocolSignedDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;

/**
 * A server for us to pretend it did things.
 *
 * @author zdwang@google.com (David Wang)
 */
public class ServerMock {
  /**
   * History that we need to update
   */
  private final SimpleDeltaHistory history;

  /**
   * A connection to clients.
   */
  private final Vector<ServerConnectionMock> connectionMocks = new Vector<ServerConnectionMock>();

  /**
   * A cache of all the deltas that's received from the client.
   */
  private final Vector<ReceiveCache> clientDeltas = new Vector<ReceiveCache>();

  /**
   * An entry of a delta received from the client.
   */
  private class ReceiveCache {
    public ServerConnectionMock connection;
    public WaveletDelta delta;

    public ReceiveCache(ServerConnectionMock connection, WaveletDelta delta) {
      this.connection = connection;
      this.delta = delta;
    }
  }

  /** Core and history must not be null. */
  public ServerMock(SimpleDeltaHistory history) {
    this.history = history;
  }

  /**
   * A simple sever that simply calls transform and publishes the changes to all
   * the other clients.
   *
   * @param ServerConnectionMock
   * @param delta
   */
  public void receive(ServerConnectionMock ServerConnectionMock, WaveletDelta delta) {
    clientDeltas.add(new ReceiveCache(ServerConnectionMock, delta));
  }

  /**
   * This starts the server mock and processes non stop until all client deltas
   * have been processed.
   *
   * @throws OperationException
   * @throws TransformException
   */
  public void start() throws TransformException, OperationException {
    // NOTE(zdwang): Use an i instead of foreach because the client can send more data to the
    // server in the triggerXXX which can cause the clientDeltas to be changed.
    for (int i = 0; i < clientDeltas.size(); i++) {
      ReceiveCache cache = clientDeltas.get(i);
      ServerConnectionMock connection = cache.connection;

      int initialClientOpsSize = cache.delta.size();

      // Assume that they are working only on 1 wave.
      WaveletDelta transformedClientDelta = ConcurrencyControlCore.onClientDelta(cache.delta, history);
      HashedVersion endVersion = generateSignature(transformedClientDelta);
      TransformedWaveletDelta serverDelta =
          TransformedWaveletDelta.cloneOperations(endVersion, 0L, transformedClientDelta);

      // Update the version of the wave
      Proto.ProtocolWaveletDelta serializedClientDelta =
          OperationSerializer.serialize(transformedClientDelta);
      ProtocolSignedDelta signedDelta = ProtocolSignedDelta.newBuilder().setDelta(
          serializedClientDelta.toByteString()).build();
      ByteStringMessage<ProtocolAppliedWaveletDelta> appliedDelta =
          AppliedDeltaUtil.buildAppliedDelta(signedDelta, transformedClientDelta.getTargetVersion(),
              transformedClientDelta.size(), 0);
      history.addDelta(new WaveletDeltaRecord(transformedClientDelta.getTargetVersion(), appliedDelta, serverDelta));
      history.setCurrentSignature(endVersion);

      // Tell all other clients of the delta
      for (ServerConnectionMock conn : connectionMocks) {
        if (!conn.equals(connection)) {
          conn.triggerServerDelta(serverDelta);
        } else {
          conn.triggerServerSuccess(initialClientOpsSize,
              serverDelta.getResultingVersion());
        }
      }
    }
    clientDeltas.clear();
  }

  /** Generate some predictable signature */
  private HashedVersion generateSignature(WaveletDelta transformedClientDelta) {
    long version = transformedClientDelta.getResultingVersion();
    return HashedVersion.of(version, new byte[] {(byte) version});
  }

  /**
   * Adds a client connection.
   * @param connection
   */
  public void addClientConnection(ServerConnectionMock connection) {
    connectionMocks.add(connection);
  }

  /**
   * Remove a client connection
   * @param connection
   */
  public void removeClientConnection(ServerConnectionMock connection) {
    connectionMocks.remove(connection);
  }

  /**
   * Reopen a connection to the client.
   * @param clientConnection
   * @param clientKnownVersions
   * @param unacknowledgedDelta
   * @throws OperationException
   * @throws TransformException
   */
  public void reOpen(ServerConnectionMock clientConnection,
      List<HashedVersion> clientKnownVersions, WaveletDelta unacknowledgedDelta)
      throws ChannelException, TransformException, OperationException, WaveletStateException {
    HashedVersion startSignature = ConcurrencyControlCore.getConnectVersion(clientKnownVersions, history);
    // We have none of the client's signature. Then return the most recent.
    if (startSignature == null) {
      startSignature = history.getCurrentSignature();
      clientConnection.triggerOnOpen(startSignature, startSignature, null, null);
    } else {
      HashedVersion endSignature = history.getCurrentSignature();
      final List<WaveletDeltaRecord> deltas = new LinkedList<>();
      history.getDeltaHistory(startSignature, endSignature, new ThrowableReceiver<WaveletDeltaRecord, WaveletStateException>() {

        @Override
        public boolean put(WaveletDeltaRecord delta) throws WaveletStateException {
          return deltas.add(delta);
        }
      });
      List<WaveletOperation> operations = new LinkedList<>();
      ByteString unacknowledgedDeltaBytes = null;
      if (unacknowledgedDelta != null) {
        unacknowledgedDeltaBytes =
            OperationSerializer.serialize(unacknowledgedDelta).toByteString();
      }
      HashedVersion unacknowledgedDeltaVersion = null;
      for (WaveletDeltaRecord delta : deltas) {
        if (unacknowledgedDelta != null
            && unacknowledgedDeltaBytes.equals(
            delta.getAppliedDelta().getMessage().getSignedOriginalDelta().getDelta())) {
          unacknowledgedDeltaVersion = delta.getResultingVersion();
        } else {
          operations.addAll(delta.getTransformedDelta());
        }
      }
      clientConnection.triggerOnOpen(startSignature, endSignature, operations, unacknowledgedDeltaVersion);
    }
  }

  /**
   * Reboot the server and make the server wake up with only deltas that end before or at the
   * given version.
   * @param version The version the server wakes up to.
   */
  public void reboot(long version) {
    history.truncateAt(version);
  }

  // //////////////////////
  // Below are auto generated methods
  // //////////////////////

  /**
   * @return the history
   */
  public SimpleDeltaHistory getHistory() {
    return history;
  }
}
