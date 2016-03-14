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

package org.waveprotocol.box.server.waveletstate.segment;

import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.AddParticipant;
import org.waveprotocol.wave.model.operation.wave.RemoveParticipant;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.operation.wave.AddSegment;
import org.waveprotocol.wave.model.operation.wave.RemoveSegment;
import org.waveprotocol.wave.model.operation.wave.EndModifyingSegment;
import org.waveprotocol.wave.model.operation.wave.StartModifyingSegment;
import org.waveprotocol.wave.model.operation.OperationRuntimeException;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.operation.wave.NoOp;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionFactoryImpl;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;
import org.waveprotocol.wave.util.logging.Log;
import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.common.ThrowableReceiver;
import org.waveprotocol.box.server.persistence.blocks.Segment;
import org.waveprotocol.box.server.persistence.blocks.impl.SegmentCache;
import org.waveprotocol.box.server.persistence.blocks.SegmentOperation;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.persistence.blocks.Fragment;
import org.waveprotocol.box.server.persistence.blocks.ReadableParticipantsSnapshot;
import org.waveprotocol.box.server.persistence.blocks.ReadableBlockIndex;
import org.waveprotocol.box.server.persistence.blocks.Interval;
import org.waveprotocol.box.server.persistence.blocks.ReadableIndexSnapshot;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.box.server.persistence.blocks.Block;
import org.waveprotocol.box.server.persistence.blocks.impl.SegmentOperationImpl;
import org.waveprotocol.box.server.persistence.blocks.impl.IntervalImpl;
import org.waveprotocol.box.server.waveletstate.delta.DeltaWaveletState;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.box.server.waveletstate.block.BlockWaveletState;
import org.waveprotocol.box.server.waveletstate.IndexingState;
import org.waveprotocol.box.server.waveletstate.SnapshotProvider;
import org.waveprotocol.box.server.shutdown.LifeCycle;
import org.waveprotocol.box.server.shutdown.ShutdownPriority;
import org.waveprotocol.box.stat.Timed;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.waveprotocol.wave.model.conversation.AnnotationConstants;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMapBuilder;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.AttributesUpdate;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.DocOpCursor;
import org.waveprotocol.wave.model.document.operation.impl.DocOpBuilder;

/**
 * Resident state of wavelet segment access.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class SegmentWaveletStateImpl implements SegmentWaveletState {
  private static final Log LOG = Log.get(SegmentWaveletState.class);

  /** Cache of segments. */
  private final SegmentCache segmentCache;

  /** Block wavelet state. */
  private final BlockWaveletState blockState;

  /** The final name of wavelet. */
  private final WaveletName waveletName;

  /** Index of placing segments to blocks. */
  private ReadableBlockIndex blockIndex;

  private static final IdURIEncoderDecoder URI_CODEC = new IdURIEncoderDecoder(new JavaUrlCodec());
  private static final HashedVersionFactory HASH_FACTORY = new HashedVersionFactoryImpl(URI_CODEC);

  /** The last modified version of wavelet. */
  private HashedVersion lastModifiedVersion = HashedVersion.unsigned(0);

  /** The last modified time of wavelet. */
  private long lastModifiedTime = 0;

  /** The indexing state of wavelet. */
  private IndexingState indexingState;

  /** Locks. */
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
  private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
  private final Object appendDeltaLock = new Object();

  final private LifeCycle lifeCycle = new LifeCycle(SegmentWaveletStateImpl.class.getSimpleName(), ShutdownPriority.Storage);

  @Inject
  SegmentWaveletStateImpl(SegmentCache segmentCache, @Assisted WaveletName waveletName,
      @Assisted BlockWaveletState blockState) {
    this.segmentCache = segmentCache;
    this.waveletName = waveletName;
    this.blockState = blockState;
  }

  @Timed
  @Override
  public void open() throws WaveletStateException {
    blockIndex = blockState.getSegmentsIndex();
    if (!blockIndex.isFormatCompatible()) {
      blockState.clear();
    }
    Pair<HashedVersion, Long> versionAndTime = blockState.getLastModifiedVersionAndTime();
    lastModifiedVersion = versionAndTime.getFirst();
    lastModifiedTime = versionAndTime.getSecond();
  }

  @Override
  public ListenableFuture close() {
    writeLock.lock();
    try {
      if (indexingState != null) {
        try {
          indexingState.getFuture().get();
        } catch (InterruptedException | ExecutionException ex) {
        }
      }
      for (Segment segment : segmentCache.getAvailableSegments()) {
        segment.flush();
      }
      return blockState.close();
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public ListenableFuture flush() {
    for (Segment segment : segmentCache.getAvailableSegments()) {
      segment.flush();
    }
    return blockState.flush();
  }

  @Override
  public boolean isConsistent() {
    return blockIndex.isConsistent();
  }

  @Timed
  @Override
  public IndexingState startSynchronization(DeltaWaveletState deltaState, Executor executor) throws WaveletStateException {
    writeLock.lock();
    try {
      if (lastModifiedVersion.getVersion() > deltaState.getLastModifiedVersion().getVersion()) {
        LOG.warning("Segments version " + lastModifiedVersion
            + " is larger then deltas version " + deltaState.getLastModifiedVersion().getVersion()
            + " - rebuild segment store");
        return startRemaking(deltaState, executor);
      } else if (lastModifiedVersion.getVersion() < deltaState.getLastModifiedVersion().getVersion()) {
        LOG.warning("Segments version " + lastModifiedVersion
            + " is less then deltas version " + deltaState.getLastModifiedVersion().getVersion()
            + " - update segment store");
        HashedVersion startVersion = deltaState.getHashedVersion(lastModifiedVersion.getVersion());
        segmentCache.removeWavelet(waveletName);
        startIndexing(deltaState, startVersion, deltaState.getLastModifiedVersion(), executor);
        return indexingState;
      } else {
        lastModifiedVersion = deltaState.getLastModifiedVersion();
        return IndexingState.NO_INDEXING;
      }
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public WaveletName getWaveletName() {
    return waveletName;
  }

  @Timed
  @Override
  public Set<SegmentId> getSegmentIds(final long version) throws WaveletStateException {
    Map<SegmentId, Interval> indexIntervals = getIntervals(Sets.newHashSet(SegmentId.INDEX_ID), version, true);
    Interval indexInterval = indexIntervals.get(SegmentId.INDEX_ID);
    if (indexInterval != null) {
      ReadableIndexSnapshot indexSnapshot = (ReadableIndexSnapshot)indexInterval.getSnapshot(version);
      Preconditions.checkNotNull(indexSnapshot, "Can't get snapshot of version " + version +
        " of index segment");
      return ImmutableSet.<SegmentId>builder()
          .addAll(indexSnapshot.getExistingSegmentIds()).add(SegmentId.INDEX_ID).build();
    } else {
      return Collections.EMPTY_SET;
    }
  }

  @Override
  public Map<SegmentId, Interval> getIntervals(long version) throws WaveletStateException {
    Collection<SegmentId> segmentIds = getSegmentIds(version);
    return getIntervals(segmentIds, version, true);
  }

  @Override
  public Map<SegmentId, Interval> getIntervals(Map<SegmentId, VersionRange> ranges, boolean onlyFromCache) throws WaveletStateException {
    final Map<SegmentId, Interval> intervals = CollectionUtils.newHashMap();
    getIntervals(ranges, onlyFromCache, true, new Receiver<Pair<SegmentId, Interval>>() {

      @Override
      public boolean put(Pair<SegmentId, Interval> pair) {
        intervals.put(pair.first, pair.second);
        return true;
      }
    });
    return intervals;
  }

  @Override
  public void getIntervals(Map<SegmentId, VersionRange> ranges, boolean onlyFromCache, Receiver<Pair<SegmentId, Interval>> receiver)
      throws WaveletStateException {
    getIntervals(ranges, onlyFromCache, true, receiver);
  }

  @Override
  public ReadableWaveletData getSnapshot() throws WaveletStateException {
    return getSnapshot(lastModifiedVersion);
  }

  @Override
  public ReadableWaveletData getSnapshot(HashedVersion version) throws WaveletStateException {
    if (version.getVersion() == 0) {
      return null;
    }
    Map<SegmentId, Interval> intervals = getIntervals(version.getVersion());
    try {
      return SnapshotProvider.makeSnapshot(waveletName, version, intervals);
    } catch (OperationException ex) {
      throw new WaveletStateException(ex);
    }
  }

  @Override
  public ParticipantId getCreator() throws WaveletStateException {
    long version = getLastModifiedVersion().getVersion();
    if (version == 0) {
      return null;
    }
    Map<SegmentId, Interval> intervals =
      getIntervals(Collections.singleton(SegmentId.PARTICIPANTS_ID), version, true);
    Preconditions.checkArgument(intervals.size() == 1, "Can't get participant segment");
    Interval interval = intervals.values().iterator().next();
    ReadableParticipantsSnapshot snapshot = (ReadableParticipantsSnapshot)interval.getSnapshot(version);
    return snapshot.getCreator();
  }

  @Override
  public long getCreationTime() throws WaveletStateException {
    long version = getLastModifiedVersion().getVersion();
    if (version == 0) {
      return 0;
    }
    Map<SegmentId, Interval> intervals =
      getIntervals(Collections.singleton(SegmentId.INDEX_ID), version, true);
    Preconditions.checkArgument(intervals.size() == 1, "Can't get index segment");
    Interval interval = intervals.values().iterator().next();
    ReadableIndexSnapshot snapshot = (ReadableIndexSnapshot)interval.getSnapshot(version);
    return snapshot.getCreationTime();
  }

  @Override
  public Set<ParticipantId> getParticipants() throws WaveletStateException {
    return getParticipants(getLastModifiedVersion().getVersion());
  }

  @Override
  public Set<ParticipantId> getParticipants(long version) throws WaveletStateException {
    if (version == 0) {
      return Collections.EMPTY_SET;
    }
    Map<SegmentId, Interval> intervals =
      getIntervals(Collections.singleton(SegmentId.PARTICIPANTS_ID), version, true);
    Preconditions.checkArgument(intervals.size() == 1, "Can't get participant segment");
    Interval interval = intervals.values().iterator().next();
    ReadableParticipantsSnapshot snapshot = (ReadableParticipantsSnapshot)interval.getSnapshot(version);
    return snapshot.getParticipants();
  }

  @Override
  public HashedVersion getLastModifiedVersion() {
    readLock.lock();
    try {
      return lastModifiedVersion;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public long getLastModifiedTime() {
    readLock.lock();
    try {
      return lastModifiedTime;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Pair<HashedVersion, Long> getLastModifiedVersionAndTime() {
    readLock.lock();
    try {
      return Pair.of(lastModifiedVersion, lastModifiedTime);
    } finally {
      readLock.unlock();
    }
  }

  @Timed
  @Override
  public void appendDelta(WaveletDeltaRecord delta) throws WaveletStateException {
    synchronized (appendDeltaLock) {
      markAsInconsistent();
      long lastModifiedVersion = getLastModifiedVersion().getVersion();
      Preconditions.checkArgument(lastModifiedVersion == delta.getAppliedAtVersion().getVersion(),
          "Applied version %s doesn't match current version %s", delta.getAppliedAtVersion(),
          lastModifiedVersion);
      Map<SegmentId, SegmentOperation> segmentOperations = makeSegmentOperations(delta);
      appendSegmentOperations(segmentOperations, delta.getAppliedAtVersion(), delta.getResultingVersion(),
        delta.getApplicationTimestamp(), delta.getAuthor());
      setLastModifiedVersionandTime(delta.getResultingVersion(), delta.getApplicationTimestamp());
    }
  }

  @Override
  public IndexingState startRemaking(final DeltaWaveletState deltaState, Executor executor) throws WaveletStateException {
    writeLock.lock();
    try {
      HashedVersion fromVersion = HASH_FACTORY.createVersionZero(waveletName);
      HashedVersion toVersion = deltaState.getLastModifiedVersion();
      clear();
      if (toVersion.getVersion() != 0) {
        startIndexing(deltaState, fromVersion, toVersion, executor);
      }
      return indexingState;
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public void clear() throws WaveletStateException {
    writeLock.lock();
    try {
      markAsInconsistent();
      segmentCache.removeWavelet(waveletName);
      lastModifiedVersion = HashedVersion.unsigned(0);
      indexingState = null;
      blockState.clear();
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public void markAsConsistent() {
    blockState.markAsConsistent();
  }

  @Override
  public void markAsInconsistent() throws WaveletStateException {
    blockState.markAsInconsistent();
  }

  static WaveletOperationContext makeSegmentContext(WaveletOperationContext context, long segmentVersion) {
    return new WaveletOperationContext(context.getCreator(), context.getTimestamp(), segmentVersion);
  }

  void setLastModifiedVersionandTime(HashedVersion lastModifiedVersion, long lastModifiedTime) {
    writeLock.lock();
    try {
      this.lastModifiedVersion = lastModifiedVersion;
      this.lastModifiedTime = lastModifiedTime;
      blockState.setLastModifiedVersionAndTime(lastModifiedVersion, lastModifiedTime);
    } finally {
      writeLock.unlock();
    }
  }

  Map<SegmentId, Interval> getIntervals(Collection<SegmentId> segmentIds, long version, boolean strictly)
      throws WaveletStateException {
    final Map<SegmentId, Interval> intervals = CollectionUtils.newHashMap();
    getIntervals(segmentIds, version, strictly, new Receiver<Pair<SegmentId, Interval>>() {

      @Override
      public boolean put(Pair<SegmentId, Interval> pair) {
        intervals.put(pair.first, pair.second);
        return true;
      }
    });
    return intervals;
  }

  void getIntervals(Collection<SegmentId> segmentIds, long version, boolean strictly,
      Receiver<Pair<SegmentId, Interval>> receiver) throws WaveletStateException {
    ImmutableMap.Builder<SegmentId, VersionRange> builder = ImmutableMap.builder();
    for (SegmentId segmentId : segmentIds) {
      builder.put(segmentId, VersionRange.of(version, version));
    }
    getIntervals(builder.build(), false, strictly, receiver);
  }

  void getIntervals(Map<SegmentId, VersionRange> ranges, boolean onlyFromCache, boolean strictly,
      Receiver<Pair<SegmentId, Interval>> receiver) throws WaveletStateException {
    Map<SegmentId, Interval> incompleteIntervals = CollectionUtils.newHashMap();
    Map<SegmentId, Interval> completeIntervals = getIntervalsFromCache(ranges, incompleteIntervals);
    boolean canceled = false;
    for (Entry<SegmentId, Interval> entry : completeIntervals.entrySet()) {
      if (!receiver.put(Pair.of(entry.getKey(), entry.getValue()))) {
        canceled = true;
        break;
      }
    }
    if (!onlyFromCache && !canceled && !incompleteIntervals.isEmpty()) {
      Map<SegmentId, VersionRange> missingRanges = getMissingRanges(ranges, completeIntervals.keySet(),
        incompleteIntervals);
      getIntervals(missingRanges, incompleteIntervals, strictly, receiver);
    }
  }

  @Timed
  void getIntervals(final Map<SegmentId, VersionRange> ranges, final Map<SegmentId, Interval> incompleteIntervals,
      boolean strictly, final Receiver<Pair<SegmentId, Interval>> receiver) throws WaveletStateException {
    for (Entry<SegmentId, VersionRange> segmentEntry : ranges.entrySet()) {
      SegmentId segmentId = segmentEntry.getKey();
      VersionRange range = segmentEntry.getValue();
      Set<String> blockIds = blockIndex.getBlockIds(segmentId, range, strictly);
      // Variable blocks prevents blocks to releasing by GC.
      Map<String, Block> blocks;
      try {
        blocks = blockState.readBlocks(blockIds).get();
      } catch (InterruptedException | ExecutionException ex) {
        throw new WaveletStateException(ex);
      }
      Preconditions.checkArgument(blocks.keySet().containsAll(blockIds),
          "Some of requested blocks are not received");
      // That container prevents fragments from releasing by GC while intervals are fetched.
      List<Fragment> fragments = CollectionUtils.newLinkedList();
      for (Entry<String, Block> blockEntry : blocks.entrySet()) {
        fragments.add(registerFragment(blockEntry.getValue(), segmentId));
      }
      Interval receivedInterval = getIntervalFromCache(segmentId, range);
      Preconditions.checkArgument(!strictly || (!receivedInterval.isEmpty()
          && receivedInterval.getRange().from() <= range.from()),
          "Interval " + receivedInterval.toString() + " of segment " + segmentId.toString()
          + " must start on " + range.from());
      Interval interval = incompleteIntervals.get(segmentId);
      if (interval != null && !interval.isEmpty()) {
        interval.append(receivedInterval);
      } else {
        interval = receivedInterval;
      }
      if (!receiver.put(Pair.of(segmentId, interval))) {
        break;
      }
    }
  }

  @Timed
  Fragment registerFragment(Block block, SegmentId segmentId) {
    block.getReadLock().lock();
    try {
      Fragment fragment = block.getFragment(segmentId);
      Segment segment = segmentCache.getOrCreateSegment(waveletName, segmentId, blockState);
      segment.registryFragment(fragment);
      return fragment;
    } catch (OperationException ex) {
      throw new OperationRuntimeException("Fragment registry exception", ex);
    } finally {
      block.getReadLock().unlock();
    }
  }

  @Timed
  Map<SegmentId, VersionRange> getMissingRanges(final Map<SegmentId, VersionRange> ranges,
      Set<SegmentId> completeIntervals, Map<SegmentId, Interval> incompleteIntervals) {
    ImmutableMap.Builder<SegmentId, VersionRange> builder = ImmutableMap.builder();
    for (Entry<SegmentId, VersionRange> entry : ranges.entrySet()) {
      if (!completeIntervals.contains(entry.getKey())) {
        Interval interval = incompleteIntervals.get(entry.getKey());
        if (interval == null || interval.isEmpty()) {
          builder.put(entry.getKey(), entry.getValue());
        } else if (!interval.getRange().contains(entry.getValue().from())) {
          builder.put(entry.getKey(),
              VersionRange.of(entry.getValue().from(), interval.getRange().from()-1));
        }
      }
    }
    return builder.build();
  }

  Map<SegmentId, Interval> getIntervalsFromCache(Map<SegmentId, VersionRange> ranges,
      Map<SegmentId, Interval> incompleteIntervals) {
    ImmutableMap.Builder<SegmentId, Interval> intervals = ImmutableMap.builder();
    for (Entry<SegmentId, VersionRange> entry : ranges.entrySet()) {
      SegmentId segmentId = entry.getKey();
      VersionRange range = entry.getValue();
      Interval interval = getIntervalFromCache(segmentId, range);
      if (!interval.isEmpty() && interval.getRange().from() <= range.from()) {
        intervals.put(segmentId, interval);
      } else {
        incompleteIntervals.put(segmentId, interval);
      }
    }
    return intervals.build();
  }

  Interval getIntervalFromCache(SegmentId segmentId, VersionRange range) {
    Segment segment = segmentCache.getSegment(waveletName, segmentId);
    if (segment != null) {
      return segment.getInterval(range.from(), range.to());
    }
    return new IntervalImpl();
  }

  @Timed
  static Map<SegmentId, SegmentOperation> makeSegmentOperations(WaveletDeltaRecord delta) {
    LoadingCache<SegmentId, ImmutableList.Builder<WaveletOperation>> builders = CacheBuilder.newBuilder().build(
      new CacheLoader<SegmentId, ImmutableList.Builder<WaveletOperation>>(){

      @Override
      public ImmutableList.Builder<WaveletOperation> load(SegmentId segmentId) {
        return ImmutableList.builder();
      }
    });
    Map<SegmentId, SegmentOperation> segmentOperations = CollectionUtils.newHashMap();
    for (WaveletOperation op : delta.getTransformedDelta()) {
      WaveletOperationContext newContext = makeSegmentContext(op.getContext(), delta.getResultingVersion().getVersion());
      if (op instanceof AddParticipant) {
        builders.getUnchecked(SegmentId.PARTICIPANTS_ID).add(
          new AddParticipant(newContext, ((AddParticipant)op).getParticipantId()));
      } else if (op instanceof RemoveParticipant) {
        builders.getUnchecked(SegmentId.PARTICIPANTS_ID).add(
          new RemoveParticipant(newContext, ((RemoveParticipant)op).getParticipantId()));
      } else if (op instanceof NoOp) {
        builders.getUnchecked(SegmentId.PARTICIPANTS_ID).add(new NoOp(newContext));
      } else {
        WaveletBlipOperation blipOp = (WaveletBlipOperation)op;
        BlipContentOperation blipContentOp = (BlipContentOperation)blipOp.getBlipOp();
        BlipContentOperation newBlipContentOp = new BlipContentOperation(newContext,
          blipContentOp.getContentOp(), blipContentOp.getContributorMethod());
        WaveletBlipOperation newBlipOp = new WaveletBlipOperation(blipOp.getBlipId(), newBlipContentOp);
        builders.getUnchecked(SegmentId.ofBlipId(blipOp.getBlipId())).add(newBlipOp);
      }
    }
    for (Entry<SegmentId, ImmutableList.Builder<WaveletOperation>> entry : builders.asMap().entrySet()) {
      segmentOperations.put(entry.getKey(), new SegmentOperationImpl(entry.getValue().build()));
    }
    return segmentOperations;
  }

  private static DocOp filterNotWorthyAnnotations(DocOp docOp) {
    final DocOpBuilder builder = new DocOpBuilder();
    docOp.apply(new DocOpCursor() {
      @Override
      public void retain(int itemCount) {
        builder.retain(itemCount);
      }

      @Override
      public void characters(String characters) {
        builder.characters(characters);
      }

      @Override
      public void elementStart(String type, Attributes attributes) {
        builder.elementStart(type, attributes);
      }

      @Override
      public void elementEnd() {
        builder.elementEnd();
      }

      @Override
      public void deleteCharacters(String chars) {
        builder.deleteCharacters(chars);
      }

      @Override
      public void deleteElementStart(String type, Attributes attrs) {
        builder.deleteElementStart(type, attrs);
      }

      @Override
      public void deleteElementEnd() {
        builder.deleteElementEnd();
      }

      @Override
      public void replaceAttributes(Attributes oldAttrs, Attributes newAttrs) {
        builder.replaceAttributes(oldAttrs, newAttrs);
      }

      @Override
      public void updateAttributes(AttributesUpdate attrUpdate) {
        builder.updateAttributes(attrUpdate);
      }

      @Override
      public void annotationBoundary(AnnotationBoundaryMap map) {
        AnnotationBoundaryMapBuilder b = new AnnotationBoundaryMapBuilder();
        for (int i = 0; i < map.endSize(); i++) {
          if (isWorthyAnnotation(map.getEndKey(i))) {
            b.end(map.getEndKey(i));
          }
        }
        for (int i = 0; i < map.changeSize(); i++) {
          if (isWorthyAnnotation(map.getChangeKey(i))) {
            b.change(map.getChangeKey(i), map.getOldValue(i), map.getNewValue(i));
          }
        }
        builder.annotationBoundary(b.build());
      }
    });
    return builder.build();
  }

  private static boolean isWorthyAnnotation(String annotationKey) {
    return !annotationKey.startsWith(AnnotationConstants.USER_PREFIX) &&
           !annotationKey.startsWith(AnnotationConstants.SPELLY_PREFIX) &&
           !annotationKey.startsWith(AnnotationConstants.ROSY_PREFIX) &&
           !annotationKey.startsWith(AnnotationConstants.LANGUAGE_PREFIX);
  }

  @Timed
  void appendSegmentOperations(Map<SegmentId, SegmentOperation> operations,
      HashedVersion appliedAtVersion, HashedVersion targetVersion,
      long timestamp, ParticipantId author) throws WaveletStateException {
    HashSet segmentsForRequest = new HashSet(operations.keySet());
    segmentsForRequest.add(SegmentId.INDEX_ID);
    Map<SegmentId, Interval> intervals = getIntervals(segmentsForRequest, appliedAtVersion.getVersion(), false);
    try {
      Interval indexInterval = intervals.get(SegmentId.INDEX_ID);
      Set<SegmentId> beingModifiedSegmentIds = CollectionUtils.newHashSet();
      SegmentId lastModifiedSegmentId = null;
      if (!indexInterval.isEmpty()) {
        ReadableIndexSnapshot indexSnapshot = (ReadableIndexSnapshot)indexInterval.getSnapshot(appliedAtVersion.getVersion());
        beingModifiedSegmentIds.addAll(indexSnapshot.getBeingModifiedSegmentIds());
        lastModifiedSegmentId = indexSnapshot.getLastModifiedSegmentId();
      }
      List<WaveletOperation> indexOperations = CollectionUtils.newLinkedList();
      for (Iterator<SegmentId> it = beingModifiedSegmentIds.iterator(); it.hasNext(); ) {
        SegmentId segmentId = it.next();
        SegmentOperation segmentOperation = operations.get(segmentId);
        if (segmentOperation == null || !segmentOperation.isWorthy()) {
          WaveletOperationContext context = new WaveletOperationContext(author, timestamp, targetVersion.getVersion());
          SegmentId operationSegmentId = segmentId.equals(lastModifiedSegmentId) ? null : segmentId;
          indexOperations.add(new EndModifyingSegment(context, operationSegmentId));
          lastModifiedSegmentId = segmentId;
          it.remove();
        }
      }
      for (SegmentId segmentId : operations.keySet()) {
        SegmentOperation op = operations.get(segmentId);
        boolean created = false;
        Interval interval = intervals.get(segmentId);
        Segment segment;
        if (interval == null || interval.isEmpty()) {
          segment = segmentCache.getOrCreateSegment(waveletName, segmentId, blockState);
          created = true;
        } else {
          segment = interval.getSegment();
          if (!indexInterval.isEmpty()) {
            created = !((ReadableIndexSnapshot)indexInterval.getSnapshot(appliedAtVersion.getVersion())).hasSegment(segmentId);
          }
        }
        SegmentOperation reverseOp = segment.addOperation(author, timestamp, op);
        boolean removed = !segment.hasContent();
        WaveletOperationContext context = new WaveletOperationContext(author, timestamp, op.getTargetVersion());
        SegmentId operationSegmentId = segmentId.equals(lastModifiedSegmentId) ? null : segmentId;
        if (created && !removed) {
          indexOperations.add(new AddSegment(context, operationSegmentId));
          beingModifiedSegmentIds.add(segmentId);
          lastModifiedSegmentId = segmentId;
        } else if (removed && !created) {
          indexOperations.add(new RemoveSegment(context, operationSegmentId));
          beingModifiedSegmentIds.add(segmentId);
          lastModifiedSegmentId = segmentId;
        } else if (op.isWorthy() && !beingModifiedSegmentIds.contains(segmentId)) {
          indexOperations.add(new StartModifyingSegment(context, operationSegmentId));
          beingModifiedSegmentIds.add(segmentId);
          lastModifiedSegmentId = segmentId;
        }
      }
      if (!indexOperations.isEmpty()) {
        Segment indexSegment = !indexInterval.isEmpty() ? indexInterval.getSegment() :
          segmentCache.getOrCreateSegment(waveletName, SegmentId.INDEX_ID, blockState);
        indexSegment.addOperation(author, timestamp, new SegmentOperationImpl(ImmutableList.copyOf(indexOperations)));
      }
    } catch (OperationException ex) {
      throw new WaveletStateException(ex);
    }
  }

  void startIndexing(final DeltaWaveletState deltaState, final HashedVersion fromVersion, final HashedVersion toVersion, Executor executor) {
    Preconditions.checkArgument(indexingState == null, "Indexing is already started");
    indexingState = new IndexingState(deltaState.getLastModifiedVersion().getVersion());
    indexingState.setCurrentVersion(fromVersion.getVersion());
    executor.execute(new Runnable() {

      @Override
      public void run() {
        Timer timer = Timing.start("Indexing");
        try {
          applyDeltas(deltaState, fromVersion, toVersion);
          flush().get();
          segmentCache.removeWavelet(waveletName);
        } catch (Exception ex) {
          LOG.severe("Indexing exception", ex);
          indexingState.setException(new WaveletStateException(ex));
        } finally {
          Timing.stop(timer);
        }
      }
    });
  }

  @Timed
  void applyDeltas(final DeltaWaveletState deltaState,
      final HashedVersion fromVersion, final HashedVersion toVersion) {
    try {
      deltaState.getDeltaHistory(fromVersion, toVersion,
        new ThrowableReceiver<WaveletDeltaRecord, WaveletStateException>(){

          @Override
          public boolean put(WaveletDeltaRecord delta) throws WaveletStateException {
            appendDelta(delta);
            indexingState.setCurrentVersion(delta.getResultingVersion().getVersion());
            return true;
          }
        });
    } catch (WaveletStateException ex) {
      LOG.severe("Indexing exception of wavelet " + waveletName.toString(), ex);
      indexingState.setException(ex);
    }
  }
}
