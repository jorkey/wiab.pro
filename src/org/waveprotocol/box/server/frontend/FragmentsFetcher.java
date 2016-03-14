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

package org.waveprotocol.box.server.frontend;


import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.persistence.blocks.ReadableBlipSnapshot;
import org.waveprotocol.box.server.persistence.blocks.ReadableIndexSnapshot;

import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;
import org.waveprotocol.box.stat.Timed;

import org.waveprotocol.wave.model.conversation.DocumentBasedManifest;
import org.waveprotocol.wave.model.conversation.ManifestBlip;
import org.waveprotocol.wave.model.conversation.ManifestThread;
import org.waveprotocol.wave.model.conversation.navigator.ManifestAdapter;
import org.waveprotocol.wave.model.conversation.navigator.NavigatorImpl;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.automaton.DocumentSchema;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.supplement.Supplement;
import org.waveprotocol.wave.model.supplement.ScreenPosition;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.impl.ObservablePluggableMutableDocument;
import org.waveprotocol.wave.model.wave.data.impl.StartVersionHelper;
import org.waveprotocol.wave.model.conversation.navigator.Navigator;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.Preconditions;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
class FragmentsFetcher {
  private final WaveletProvider waveletProvider;

  @Inject
  FragmentsFetcher(WaveletProvider waveletProvider) {
    this.waveletProvider = waveletProvider;
  }

  @Timed
  boolean fetchWavelet(FragmentsBuffer buffer, ParticipantId loggedInUser,
       ListenableFuture<Supplement> supplementFuture, 
       int minBlipReplySize, int maxBlipReplySize, int maxBlipCount) throws WaveServerException {
    WaveletName waveletName = buffer.getWaveletName();
    if (waveletProvider.checkExistence(waveletName) && waveletProvider.checkAccessPermission(waveletName, loggedInUser)) {
      Pair<HashedVersion, Long> versionAndTime = waveletProvider.getLastModifiedVersionAndTime(waveletName);
      HashedVersion lastModifiedVersion = versionAndTime.first;
      long lastModifiedTime = versionAndTime.second;
      buffer.setLastModifiedVersion(lastModifiedVersion);
      buffer.setLastModifiedTime(lastModifiedTime);
      if (lastModifiedVersion.getVersion() != 0) {
        if (IdUtil.isConversationalId(waveletName.waveletId)) {
          // First request obtains index and manifest documents of last version.
          FragmentsRequest firstRequest = new FragmentsRequest.Builder()
            .addRange(SegmentId.INDEX_ID, lastModifiedVersion.getVersion())
            .addRange(SegmentId.MANIFEST_ID, lastModifiedVersion.getVersion())
            .build();
          fetchFragmentsRequest(buffer, firstRequest);
          Interval indexInterval = buffer.getIntervals().get(SegmentId.INDEX_ID);
          Interval manifestInterval = buffer.getIntervals().get(SegmentId.MANIFEST_ID);
          buffer.removeSegment(SegmentId.MANIFEST_ID);
          if (manifestInterval != null) {
            Supplement supplement = null;
            if (supplementFuture != null) {
              Timer timer = Timing.start("Waiting for supplement");
              try {
                supplement = supplementFuture.get();
              } catch (InterruptedException | ExecutionException ex) {
                throw new WaveServerException("Getting of supplement error", ex);
              } finally {
                Timing.stop(timer);
              }
            }
            String firstBlipId = null;
            if (supplement != null) {
              ScreenPosition screenPosition = supplement.getScreenPosition(waveletName.waveletId);
              if (screenPosition != null) {
                firstBlipId = screenPosition.getBlipId();
              }
            }
            StartVersionHelper lookVersionHelper = new StartVersionHelper(supplement);
            // Second request obtains obligatory segments in specified version range.
            ReadableBlipSnapshot manifestSnapshot = (ReadableBlipSnapshot)manifestInterval.getSnapshot(lastModifiedVersion.getVersion());
            DocumentBasedManifest manifest = getManifest(manifestSnapshot.getContent());
            ManifestBlip firstBlip = null;
            if (firstBlipId != null) {
              firstBlip = findBlip(manifest.getRootThread(), firstBlipId);
            }
            NavigatorImpl<ManifestThread, ManifestBlip> navigator = new NavigatorImpl();
            navigator.init(new ManifestAdapter(manifest));
            if (firstBlip == null) {
              firstBlip = navigator.getFirstBlip(manifest.getRootThread());
            }
            ImmutableSet.Builder<SegmentId> segmentIds = ImmutableSet.<SegmentId>builder().add(
              SegmentId.PARTICIPANTS_ID,
              SegmentId.MANIFEST_ID,
              SegmentId.TAGS_ID);
            if (maxBlipCount == -1 || maxBlipCount > 0 && firstBlip != null) {
              segmentIds.add(SegmentId.ofBlipId(firstBlip.getId()));
            }
            ReadableIndexSnapshot indexSnapshot = (ReadableIndexSnapshot)indexInterval.getSnapshot(
              lastModifiedVersion.getVersion());
            Map<SegmentId, VersionRange> ranges = getRanges(waveletName.waveletId,
              segmentIds.build(), indexSnapshot, lastModifiedVersion.getVersion(), lookVersionHelper);
            FragmentsRequest secondRequest = new FragmentsRequest.Builder().addRanges(ranges).build();
            fetchFragmentsRequest(buffer, secondRequest);
            if (maxBlipCount > 1 && firstBlip != null) {
              // Next requests obtains optional segments.
              Set<SegmentId> attendantBlips = getAttendantBlips(navigator, firstBlip, maxBlipCount - 1);
              ranges = getRanges(waveletName.waveletId, attendantBlips, indexSnapshot,
                lastModifiedVersion.getVersion(), lookVersionHelper);
              if (!ranges.isEmpty()) {
                fetchOptionalFragments(buffer, ranges, 
                  minBlipReplySize != -1 ? buffer.getSerializedSize() + minBlipReplySize : -1, 
                  maxBlipReplySize != -1 ? buffer.getSerializedSize() + maxBlipReplySize : -1);
              }
            }
          }
        }
        if (buffer.isEmpty()) {
          FragmentsRequest secondRequest = new FragmentsRequest.Builder()
            .setStartVersion(lastModifiedVersion.getVersion())
            .setEndVersion(lastModifiedVersion.getVersion()).build();
          fetchFragmentsRequest(buffer, secondRequest);
        }
        return true;
      }
    }
    return false;
  }

  @Timed
  void fetchOptionalFragments(final FragmentsBuffer buffer, Map<SegmentId, VersionRange> ranges,
      int minReplySize, int maxReplySize) throws WaveServerException {
    Preconditions.checkArgument(!ranges.isEmpty(), "Ranges are empty");
    // First request try to make minimal reply.
    FragmentsRequest firstRequest = new FragmentsRequest.Builder().addRanges(ranges)
      .setMaxReplySize(minReplySize).build();
    fetchFragmentsRequest(buffer, firstRequest);
    Set<SegmentId> receivedSegmentIds = buffer.getIntervals().keySet();
    ImmutableMap.Builder<SegmentId, VersionRange> optionalRangesBuilder = ImmutableMap.builder();
    for (SegmentId segmentId : ranges.keySet()) {
      if (!receivedSegmentIds.contains(segmentId)) {
        optionalRangesBuilder.put(segmentId, ranges.get(segmentId));
      }
    }
    ImmutableMap<SegmentId, VersionRange> optionalRanges = optionalRangesBuilder.build();
    if (!optionalRanges.isEmpty()) {
      // Second request try to build maximal reply from cache.
      FragmentsRequest secondRequest = new FragmentsRequest.Builder()
        .addRanges(optionalRanges).setOnlyFromCache(true)
        .setMaxReplySize(maxReplySize).build();
      fetchFragmentsRequest(buffer, secondRequest);
    }
  }

  @Timed
  void fetchFragmentsRequest(final FragmentsBuffer buffer, final FragmentsRequest request) throws WaveServerException {
    WaveletName waveletName = buffer.getWaveletName();
    Map<SegmentId, VersionRange> ranges = request.getVersionRanges();
    if (ranges.isEmpty()) {
      Set<SegmentId> segmentIds = waveletProvider.getSegmentIds(waveletName, request.getStartVersion());
      ranges = new LinkedHashMap<>();
      for (SegmentId segmentId : segmentIds) {
        ranges.put(segmentId, VersionRange.of(request.getStartVersion(), request.getEndVersion()));
      }
    }
    waveletProvider.getIntervals(waveletName, ranges, request.isOnlyFromCache(), new Receiver<Pair<SegmentId, Interval>>() {

      @Override
      public boolean put(Pair<SegmentId, Interval> pair) {
        buffer.addInterval(pair.first, pair.second);
        if (request.getMaxReplySize() != -1) {
          if (buffer.getSerializedSize() > request.getMaxReplySize()) {
            return false;
          }
        }
        return true;
      }
    });
  }

  @Timed
  private Map<SegmentId, VersionRange> getRanges(WaveletId waveletId, Set<SegmentId> segmentIds,
      ReadableIndexSnapshot indexSnapshot, long endVersion, StartVersionHelper startVersionHelper) {
    ImmutableMap.Builder<SegmentId, VersionRange> ranges = ImmutableMap.builder();
    for (SegmentId segmentId : segmentIds) {
      if (indexSnapshot.hasSegment(segmentId)) {
        Long creationVersion = segmentId.isIndex() ? Long.valueOf(0L) : indexSnapshot.getCreationVersion(segmentId);
        if (creationVersion != null) {
          long startVersion = startVersionHelper.getStartVersion(waveletId, segmentId, creationVersion);
          if (startVersion == StartVersionHelper.NO_VERSION || startVersion > endVersion) {
            startVersion = endVersion;
          }
          ranges.put(segmentId, VersionRange.of(startVersion, endVersion));
        }
      }
    }
    return ranges.build();
  }

  @Timed
  private Set<SegmentId> getAttendantBlips(Navigator<ManifestThread, ManifestBlip> navigator,
      ManifestBlip startBlip, final int maxCount) {
    LinkedHashSet<SegmentId> blips = new LinkedHashSet();
    final List<ManifestBlip> nearestBlips = new LinkedList<>();
    navigator.findNeighborBlips(CollectionUtils.newLinkedList(startBlip), new Receiver<ManifestBlip>() {

      @Override
      public boolean put(ManifestBlip blip) {
        nearestBlips.add(blip);
        return maxCount == -1 || nearestBlips.size() < maxCount;
      }
    }, false);
    for (ManifestBlip blip : nearestBlips) {
      SegmentId segmentId = SegmentId.ofBlipId(blip.getId());
      if (!blips.contains(segmentId)) {
        List<ManifestBlip> parents = getNecessaryParentBlips(navigator, blip);
        for (ManifestBlip parent : parents) {
          if (parent != startBlip) {
            blips.add(SegmentId.ofBlipId(parent.getId()));
          }
        }
        blips.add(segmentId);
        if (maxCount != -1 && blips.size() >= maxCount) {
          break;
        }
      }
    }
    return blips;
  }

  @Timed
  private List<ManifestBlip> getNecessaryParentBlips(Navigator<ManifestThread, ManifestBlip> navigator,
      ManifestBlip blip) {
    List<ManifestBlip> parents = new LinkedList();
    for (;;) {
      ManifestBlip parent = navigator.getBlipParentBlip(blip);
      if (parent == null) {
        break;
      }
      ManifestThread thread = navigator.getBlipParentThread(blip);
      if (thread.isInline()) {
        parents.add(0, parent);
      }
      blip = parent;
    }
    return parents;
  }


  private DocumentBasedManifest getManifest(DocInitialization content) {
    ObservablePluggableMutableDocument manifestDoc = new ObservablePluggableMutableDocument(
      DocumentSchema.NO_SCHEMA_CONSTRAINTS, content);
    return DocumentBasedManifest.createOnExisting(manifestDoc);
  }

  private ManifestBlip findBlip(ManifestThread thread, String blipId) {
    for (ManifestBlip blip : thread.getBlips()) {
      if (blip.getId().equals(blipId)) {
        return blip;
      }
      for (ManifestThread th : blip.getReplies()) {
        ManifestBlip b = findBlip(th, blipId);
        if (b != null) {
          return b;
        }
      }
    }
    return null;
  }
}
