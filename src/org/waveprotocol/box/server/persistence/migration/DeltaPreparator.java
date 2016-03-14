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

package org.waveprotocol.box.server.persistence.migration;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.InvalidProtocolBufferException;

import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.deltas.DeltaStore;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;

import org.waveprotocol.box.server.waveserver.ByteStringMessage;
import org.waveprotocol.wave.federation.Proto;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.impl.DocOpUtil;
import org.waveprotocol.wave.model.document.util.DocOpScrub;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.BlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.version.HashedVersion;

import java.io.IOException;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import org.waveprotocol.wave.model.operation.wave.AddParticipant;
import org.waveprotocol.wave.model.operation.wave.RemoveParticipant;

/**
 * An utility class to copy all deltas between storages.
 * Based on DeltaMigrator.
 *
 * @author pablojan@gmail.com (Pablo Ojanguren)
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class DeltaPreparator {

  private final static String INDENT = "  ";  
  
  private static Format dateFormat = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
  
  private final DeltaStore store;
  private final String waveIdStr;

  public DeltaPreparator(DeltaStore store, String waveIdStr) {
    this.store = store;
    this.waveIdStr = waveIdStr;
  }

  public void run() {
    long startTime = System.currentTimeMillis();
    try {
      boolean isFound = false;
      ExceptionalIterator<WaveId, PersistenceException> it = store.getWaveIdIterator();      
      while (it.hasNext() && !isFound) {
        WaveId waveId = it.next();
        if (waveIdStr.equals(waveId.getId()) ) {
          log(0, "Starting preparation of wave with id=" + waveIdStr + ".");          
          prepareWave(waveId);
          long endTime = System.currentTimeMillis();
          log(0, "Preparation completed. Total time = " + (endTime - startTime) + "ms.");          
          isFound = true;
        }  
      }
      if (!isFound) {
        log(0, "The wave with id=" + waveIdStr + " isn't found!");
      }  
    } catch (PersistenceException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void prepareWave(WaveId waveId) throws PersistenceException, IOException {
    ImmutableSet<WaveletId> waveletIds = store.lookup(waveId);
    log(0, "Wave : " + waveId.toString() + " with " + waveletIds.size() + " wavelets");

    int waveletsTotal = waveletIds.size();
    int waveletsCount = 0;

    // Wavelets
    for (WaveletId waveletId : waveletIds) {
      waveletsCount++;
      log(1, "Wavelet " + waveletsCount + "/" + waveletsTotal + ": " + waveletId.toString());
      prepareWavelet(WaveletName.of(waveId, waveletId));
    }
  }

  private void prepareWavelet(WaveletName waveletName) throws PersistenceException, IOException {
    DeltaStore.DeltaAccess access = store.open(waveletName);

    // Get all deltas from last version to initial version (0): reverse order
    HashedVersion deltaResultingVersion = access.getLastModifiedVersion();

    // Deltas
    LinkedList<WaveletDeltaRecord> deltaRecords = new LinkedList<>();
    while (deltaResultingVersion != null && deltaResultingVersion.getVersion() != 0) {
      WaveletDeltaRecord deltaRecord = access.getDeltaByEndVersion(deltaResultingVersion.getVersion());
      deltaRecords.addLast(deltaRecord);
      // get the previous delta, this is the appliedAt
      deltaResultingVersion = deltaRecord.getAppliedAtVersion();        
    }
    while (!deltaRecords.isEmpty()) {
      prepareDeltaRecord(deltaRecords.pollLast());
    }
    log(1, "Prepared " + deltaRecords.size() + " delta(s)");
  }

  private void prepareDeltaRecord(WaveletDeltaRecord deltaRecord) throws InvalidProtocolBufferException {
    ByteStringMessage<Proto.ProtocolAppliedWaveletDelta> appliedDelta = deltaRecord.getAppliedDelta();    
    WaveletDelta delta = DataUtil.deserialize(appliedDelta);
    long timestamp = deltaRecord.getApplicationTimestamp();
    log(2, "Delta - Ver: " + delta.getResultingVersion() +
        " (" + deltaRecord.getAuthor().getName() +
        ", " + dateFormat.format(timestamp) + ", timestamp: " + timestamp + ")");
    for (int i = 0; i < delta.size(); i++) {
      prepareOperation(delta.get(i));
    }
  }
  
  private void prepareOperation(WaveletOperation op) {
    if (op instanceof AddParticipant) {
      log(3, "AddParticipant " + ((AddParticipant)op).getParticipantId().toString());
    } else if (op instanceof RemoveParticipant) {
      log(3, "RemoveParticipant " + ((RemoveParticipant)op).getParticipantId().toString());
    } else if (op instanceof WaveletBlipOperation) {
      WaveletBlipOperation waveletBlipOp = (WaveletBlipOperation) op;
      BlipOperation blipOp = waveletBlipOp.getBlipOp();
      if (blipOp instanceof BlipContentOperation) {
        DocOp docOp = ((BlipContentOperation) blipOp).getContentOp();
        log(3, "BlipId=" + waveletBlipOp.getBlipId() + ", Op=" + operationToString(docOp) +
            ", OpSize=" + docOp.size());
      }
    }
  }
  
  private static void log(int level, String message) {
    String indent = "";
    for (int i = 0; i < level; i++) {
      indent += INDENT;
    }
    System.out.println(indent + message);
  }
  
  private static String operationToString(DocOp docOp) {
    return "[" + DocOpUtil.toConciseString(DocOpScrub.maybeScrub(docOp)) + "]";
  }  
}  
