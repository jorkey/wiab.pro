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

package org.waveprotocol.wave.model.wave.data.impl;

import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.DocumentFactory;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.BlipData;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletFragmentData;
import org.waveprotocol.wave.model.wave.data.LazyContentBlipData;
import org.waveprotocol.wave.model.raw.RawFragment;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.AddSegment;
import org.waveprotocol.wave.model.operation.wave.EndModifyingSegment;
import org.waveprotocol.wave.model.operation.wave.RemoveSegment;
import org.waveprotocol.wave.model.operation.wave.StartModifyingSegment;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.raw.RawOperation;
import org.waveprotocol.wave.model.raw.RawIndexSnapshot;
import org.waveprotocol.wave.model.conversation.TagsDocument;

import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of {@link WaveletFragmentData} which uses {@link LazyContentBlipDataImpl}
 * to represent blips.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public final class WaveletFragmentDataImpl extends AbstractWaveletData<BlipData>
    implements ObservableWaveletFragmentData {

  /**
   * Factory for constructing wavelet data copies.
   */
  public static final class Factory implements ObservableWaveletData.Factory<WaveletFragmentDataImpl> {
    /**
     * @param contentFactory
     *
     * @return A new WaveletDataImpl.Factory using the given content factory.
     * @throws NullPointerException if contentFactory is null.
     */
    public static Factory create(DocumentFactory<?> contentFactory) {
      return new Factory(contentFactory);
    }

    private final DocumentFactory<?> contentFactory;

    private Factory(DocumentFactory<?> contentFactory) {
      Preconditions.checkNotNull(contentFactory, "null DocumentFactory");
      this.contentFactory = contentFactory;
    }

    @Override
    public WaveletFragmentDataImpl create(ReadableWaveletData data) {
      WaveletFragmentDataImpl waveletData = new WaveletFragmentDataImpl(data, contentFactory);
      waveletData.copyParticipants(data);
      waveletData.copyDocuments(data);
      return waveletData;
    }
  }

  /** The list of participants in this wavelet. */
  private final LinkedHashSet<ParticipantId> participants = new LinkedHashSet<>();

  /** The participants diff-operations. */
  private final LinkedList<WaveletOperation> participantsDiffs = new LinkedList<>();

  /** The set of documents in this wave, indexed by their identifier. */
  private final Map<String, LazyContentBlipDataImpl> blips = CollectionUtils.newHashMap();

  public WaveletFragmentDataImpl(WaveletId id, ParticipantId creator, long creationTime,
      HashedVersion hashedVersion, long lastModifiedTime, WaveId waveId, DocumentFactory<?> contentFactory) {
    super(id, creator, creationTime, hashedVersion.getVersion(), hashedVersion, lastModifiedTime, waveId, contentFactory);
  }

  public WaveletFragmentDataImpl(ReadableWaveletData data, DocumentFactory<?> contentFactory) {
    super(data, contentFactory);
  }

  @Override
  public Set<SegmentId> getSegmentIds() {
    HashSet<SegmentId> segmentIds = CollectionUtils.newHashSet();
    if (!participants.isEmpty()) {
      segmentIds.add(SegmentId.PARTICIPANTS_ID);
    }
    for (String blipId : blips.keySet()) {
      LazyContentBlipDataImpl blip = blips.get(blipId);
      if (blip.isConsistent()) {
        segmentIds.add(SegmentId.ofBlipId(blipId));
      }
    }
    return segmentIds;
  }

  @Override
  public void applyRawFragment(SegmentId segmentId, RawFragment fragment) throws OperationException {
    if (segmentId.isIndex()) {
      applyIndexRawFragment(fragment);
    } else if (segmentId.isParticipants()) {
      applyParticipantsRawFragment(fragment);
    } else {
      Preconditions.checkArgument(segmentId.isBlip(), "Invalid fragment type");
      applyBlipRawFragment(fragment, segmentId.getBlipId());
    }
  }

  @Override
  public void processParticipantsDiffs() throws OperationException {
    for (WaveletOperation op : participantsDiffs) {
      op.apply(this);
    }
    participantsDiffs.clear();
  }

  @Override
  public boolean isRaw(SegmentId segmentId) {
    if (segmentId.isParticipants()) {
      return false;
    }
    LazyContentBlipDataImpl blip = getBlip(segmentId.getBlipId());
    return blip != null && !blip.isContentInitialized();
  }

  @Override
  protected Set<ParticipantId> getMutableParticipants() {
    return participants;
  }

  @Override
  public LazyContentBlipDataImpl internalCreateBlip(String docId, ParticipantId author,
      Collection<ParticipantId> contributors, DocInitialization content,
      long creationTime, long creationVersion,
      long lastModifiedTime, long lastModifiedVersion) {
    LazyContentBlipDataImpl blip = createBlip(docId);
    blip.setDocInitialization(content);
    blip.setAuthor(author);
    blip.setContributors(contributors);
    blip.setCreationTime(creationTime);
    blip.setCreationVersion(creationVersion);
    blip.setLastModifiedTime(lastModifiedTime);
    blip.setLastModifiedVersion(lastModifiedVersion);
    return blip;
  }

  @Override
  public LazyContentBlipDataImpl getBlip(String documentId) {
    return blips.get(documentId);
  }

  @Override
  public ImmutableSet<String> getDocumentIds() {
    ImmutableSet.Builder<String> blipIds = ImmutableSet.builder();
    for (Map.Entry<String, LazyContentBlipDataImpl> entry : blips.entrySet()) {
      if (entry.getValue().isConsistent()) {
        blipIds.add(entry.getKey());
      }
    }
    return blipIds.build();
  }

  @Override
  public ImmutableSet<String> getTags() {
    LazyContentBlipDataImpl blipDoc = blips.get(IdConstants.TAGS_DOCUMENT_ID);
    if (blipDoc != null && blipDoc.isConsistent()) {
      return TagsDocument.getTags(blipDoc.getContent().getMutableDocument());
    }
    return ImmutableSet.of();
  }

  private void applyIndexRawFragment(RawFragment fragment) throws OperationException {
    Set<LazyContentBlipData> beingModifiedBlips = CollectionUtils.newHashSet();
    List<SegmentId> segmentIds = CollectionUtils.newLinkedList();
    if (fragment.hasSnapshot()) {
      RawIndexSnapshot indexSnapshot = fragment.getIndexSnapshot();
      segmentIds.addAll(indexSnapshot.getExistingSegments());
      for (Map.Entry<SegmentId, Long> entry : indexSnapshot.getCreationVersions().entrySet()) {
        SegmentId segmentId = entry.getKey();
        if (segmentId.isBlip()) {
          LazyContentBlipDataImpl blip = getOrCreateBlip(segmentId.getBlipId());
          if (!blip.hasContent()) {
            blip.setCreationVersion(entry.getValue());
            blip.setLastModifiedVersion(indexSnapshot.getLastModifiedVersions().get(entry.getKey()));
          }
          if (indexSnapshot.getBeingModifiedSegments().contains(segmentId)) {
            beingModifiedBlips.add(blip);
          }
        }
      }
    }
    if (IdUtil.isConversationalId(getWaveletId())) {
      for (LazyContentBlipData blip : beingModifiedBlips) {
        if (!blip.hasContent()) {
          blip.setLastModifiedVersion(getVersion());
        }
      }
    }
  }

  private void applyParticipantsRawFragment(RawFragment fragment) throws OperationException {
    if (fragment.hasSnapshot()) {
      Preconditions.checkArgument(participants.isEmpty(),
          "Can't apply participants snapshot - participants list is not empty");
      for (ParticipantId participant : fragment.getParticipantsSnapshot().getParticipants()) {
        participants.add(participant);
      }
    }
    for (RawOperation rawOp : fragment.getAdjustOperations()) {
      for (WaveletOperation waveletOp : rawOp.getOperations()) {
        waveletOp.apply(this);
      }
    }
    for (RawOperation rawOp : fragment.getDiffOperations()) {
      for (WaveletOperation waveletOp : rawOp.getOperations()) {
        participantsDiffs.add(waveletOp);
      }
    }
  }

  private void applyBlipRawFragment(RawFragment fragment, String blipId) throws OperationException {
    LazyContentBlipDataImpl blip = getOrCreateBlip(blipId);
    blip.applyRawFragment(fragment);
  }

  private LazyContentBlipDataImpl getOrCreateBlip(String blipId) {
    LazyContentBlipDataImpl blip = getBlip(blipId);
    if (blip == null) {
      blip = new LazyContentBlipDataImpl(id, blipId, getListenerManager(), contentFactory);
      blips.put(blipId, blip);
    }
    return blip;
  }

  private LazyContentBlipDataImpl createBlip(String blipId) {
    Preconditions.checkArgument(!blips.containsKey(blipId), "Blip " + blipId + " is already exists");
    LazyContentBlipDataImpl blip = new LazyContentBlipDataImpl(id, blipId, getListenerManager(), contentFactory);
    blips.put(blipId, blip);
    return blip;
  }
}
