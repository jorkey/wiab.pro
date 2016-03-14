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

package org.waveprotocol.box.server.waveletstate;

import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.ReadableBlipSnapshot;
import org.waveprotocol.box.server.persistence.blocks.ReadableIndexSnapshot;
import org.waveprotocol.box.server.persistence.blocks.ReadableParticipantsSnapshot;
import org.waveprotocol.box.stat.Timed;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.schema.SchemaCollection;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.impl.EmptyWaveletSnapshot;
import org.waveprotocol.wave.model.wave.data.impl.ObservablePluggableMutableDocument;
import org.waveprotocol.wave.model.wave.data.impl.WaveletDataImpl;

import java.util.Map;

/**
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class SnapshotProvider {
  
  @Timed
  public static ObservableWaveletData makeSnapshot(WaveletName waveletName, HashedVersion version, Map<SegmentId, Interval> intervals) throws OperationException {
    ParticipantId creator = null;
    long creationTime = 0;
    Interval indexInterval = intervals.get(SegmentId.INDEX_ID);
    ReadableIndexSnapshot indexSnapshot = null;
    if (indexInterval != null) {
      indexSnapshot = (ReadableIndexSnapshot)indexInterval.getSnapshot(version.getVersion());
      if (indexSnapshot != null) {
        creationTime = indexSnapshot.getCreationTime();
      }
    }
    long lastModifiedTime = 0;
    Interval participantsInterval = intervals.get(SegmentId.PARTICIPANTS_ID);
    ReadableParticipantsSnapshot participantsSnapshot = null;
    if (participantsInterval != null) {
      participantsSnapshot = (ReadableParticipantsSnapshot)participantsInterval.getSnapshot(version.getVersion());
      creator = participantsSnapshot.getCreator();
      lastModifiedTime = participantsInterval.getLastModifiedTime(version.getVersion());
    }
    // Create empty wavelet.
    ObservableWaveletData.Factory<? extends ObservableWaveletData> factory =
        WaveletDataImpl.Factory.create(
            ObservablePluggableMutableDocument.createFactory(SchemaCollection.empty()));
    ObservableWaveletData wavelet = factory.create(new EmptyWaveletSnapshot(waveletName.waveId,
        waveletName.waveletId, creator, version, creationTime));
    // Add participants.
    if (participantsSnapshot != null) {
      for (ParticipantId participant : participantsSnapshot.getParticipants()) {
        wavelet.addParticipant(participant, null);
      }
    }
    // Add blips.
    for (Map.Entry<SegmentId, Interval> entry : intervals.entrySet()) {
      if (entry.getKey().isBlip()) {
        ReadableBlipSnapshot snapshot = (ReadableBlipSnapshot)entry.getValue().getSnapshot(version.getVersion());
        wavelet.createBlip(entry.getKey().getBlipId(), snapshot.getAuthor(),
          snapshot.getContributors(), snapshot.getContent(),
          snapshot.getCreationTime(), snapshot.getCreationVersion(),
          snapshot.getLastModifiedTime(), snapshot.getLastModifiedVersion());
        if (snapshot.getLastModifiedTime() > lastModifiedTime) {
          lastModifiedTime = snapshot.getLastModifiedTime();
        }
      }
    }
    // Set last version and modify time.
    wavelet.setLastModifiedTime(lastModifiedTime);
    // Done.
    return wavelet;
  }
}
