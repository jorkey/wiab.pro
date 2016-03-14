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

package org.waveprotocol.box.server.persistence.blocks.impl;

import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.box.server.persistence.protos.ProtoBlockIndex;
import org.waveprotocol.box.server.persistence.blocks.Fragment;
import org.waveprotocol.box.server.persistence.blocks.BlockIndex;
import org.waveprotocol.box.server.persistence.protos.ProtoBlockIndex.BlockIndex.SegmentRanges;
import org.waveprotocol.box.server.serialize.OperationSerializer;

import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;

import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.util.logging.Log;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutionException;

/**
 * Map of placing segments to blocks.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class BlockIndexImpl implements BlockIndex {
  private static final Log LOG = Log.get(BlockIndexImpl.class);

  private static final int FORMAT_VERSION = 1;

  private ProtoBlockIndex.BlockIndex serialized;
  private final LoadingCache<SegmentId, RangeMap<RangeValue, String>> rangeMap = CacheBuilder.newBuilder().build(
        new CacheLoader<SegmentId, RangeMap<RangeValue, String>>() {

          @Override
          public RangeMap<RangeValue, String> load(SegmentId segmentId) throws Exception {
            return TreeRangeMap.create();
          }
      });
  private HashedVersion lastModifiedVersion = HashedVersion.unsigned(0);
  private long lastModifiedTime;
  private final boolean formatCompatible;
  private boolean consistent;

  public BlockIndexImpl() {
    this.serialized = null;
    consistent = false;
    formatCompatible = true;
  }

  public BlockIndexImpl(ProtoBlockIndex.BlockIndex serialized) {
    this.serialized = serialized;
    if (serialized != null) {
      lastModifiedVersion = OperationSerializer.deserialize(serialized.getLastModifiedVersion());
      lastModifiedTime = serialized.getLastModifiedTime();
      formatCompatible = serialized.getFormatVersion() == FORMAT_VERSION;
      consistent = serialized.getConsistent();
    } else {
      formatCompatible = false;
    }
  }

  @Override
  public synchronized boolean isFormatCompatible() {
    return formatCompatible;
  }

  @Override
  public synchronized boolean isConsistent() {
    return formatCompatible && consistent;
  }

  @Override
  public synchronized Set<String> getBlockIds(SegmentId segmentId, VersionRange range, boolean strictly) {
    Timer timer = Timing.start("BlockIndexImpl.getBlockIds");
    Set<String> blockIds = new LinkedHashSet<>();
    try {
      long startVersion = range.from();
      long endVersion = range.to();
      for (;;) {
        Map.Entry<Range<RangeValue>, String> blockEntry = getRanges(segmentId).getEntry(RangeValue.of(startVersion));
        if (blockEntry != null) {
          blockIds.add(blockEntry.getValue());
          long endFragmentVersion = blockEntry.getKey().upperEndpoint().get();
          if (endFragmentVersion >= endVersion) {
            break;
          }
          startVersion = endFragmentVersion + 1;
        } else if (strictly) {
          Preconditions.illegalState("No version " + startVersion + " of segment " + segmentId.toString());
        } else {
          break;
        }
      }
    } finally {
      Timing.stop(timer);
    }
    return blockIds;
  }

  @Override
  public synchronized Pair<HashedVersion, Long> getLastModifiedVersionAndTime() {
    return Pair.of(lastModifiedVersion, lastModifiedTime);
  }

  @Override
  public synchronized boolean isEmpty() {
    return rangeMap.size() == 0;
  }

  @Override
  public synchronized void setLastModifiedVersionAndTime(HashedVersion lastModifiedVersion, long lastModifiedTime) {
    Preconditions.checkArgument(lastModifiedVersion.getVersion() != 0, "Last modified version is 0");
    this.lastModifiedVersion = lastModifiedVersion;
    this.lastModifiedTime = lastModifiedTime;
  }

  @Override
  public synchronized void update(Fragment fragment) {
    RangeMap<RangeValue, String> ranges = getRanges(fragment.getSegmentId());
    Map.Entry<Range<RangeValue>, String> entry = ranges.getEntry(RangeValue.of(fragment.getStartVersion()));
    if (entry != null) {
        Preconditions.checkArgument(entry.getValue().equals(fragment.getBlock().getBlockId()),
        "New range overlaps an existing by block Id");
      Preconditions.checkArgument(fragment.getStartVersion() == entry.getKey().lowerEndpoint().get(),
        "New range overlaps an existing by start version");
      entry.getKey().upperEndpoint().set(fragment.isLast() ? Long.MAX_VALUE : fragment.getLastModifiedVersion());
    } else {
      ranges.put(Range.closed(RangeValue.of(fragment.getStartVersion()),
        RangeValue.of(fragment.isLast() ? Long.MAX_VALUE : fragment.getLastModifiedVersion())),
        fragment.getBlock().getBlockId());
    }
  }

  @Override
  public synchronized void clear() {
    serialized = null;
    rangeMap.invalidateAll();
    lastModifiedVersion = HashedVersion.unsigned(0);
    lastModifiedTime = 0;
  }

  @Override
  public synchronized void setConsistent(boolean consistent) {
    this.consistent = consistent;
  }

  @Override
  public synchronized ProtoBlockIndex.BlockIndex serialize() {
    ProtoBlockIndex.BlockIndex.Builder builder = ProtoBlockIndex.BlockIndex.newBuilder();
    builder.setFormatVersion(FORMAT_VERSION);
    if (serialized != null) {
      for (int i=0; i < serialized.getSegmentIdCount(); i++) {
        SegmentId segmentId = SegmentId.of(serialized.getSegmentId(i));
        if (rangeMap.getIfPresent(segmentId) == null) {
          builder.addSegmentId(serialized.getSegmentId(i));
          builder.addSegmentRanges(serialized.getSegmentRanges(i));
        }
      }
    }
    for (Map.Entry<SegmentId, RangeMap<RangeValue, String>> segmentEntry : rangeMap.asMap().entrySet()) {
      builder.addSegmentId(segmentEntry.getKey().toString());
      SegmentRanges.Builder rangesBuilder = builder.addSegmentRangesBuilder();
      Map<Range<RangeValue>, String> mapOfRanges = segmentEntry.getValue().asMapOfRanges();
      for (Map.Entry<Range<RangeValue>, String> range : mapOfRanges.entrySet()) {
        ProtoBlockIndex.BlockIndex.FragmentRange.Builder fragmentBuilder = ProtoBlockIndex.BlockIndex.FragmentRange.newBuilder();
        fragmentBuilder.setSourceVersion(range.getKey().lowerEndpoint().get());
        fragmentBuilder.setTargetVersion(range.getKey().upperEndpoint().get());
        fragmentBuilder.setBlockId(range.getValue());
        rangesBuilder.addRawFragmentRanges(fragmentBuilder.build().toByteString());
      }
    }
    Preconditions.checkArgument(lastModifiedVersion.getVersion() != 0 || rangeMap.asMap().isEmpty(),
        "Last modified version is 0 but range map is not empty");
    builder.setLastModifiedVersion(OperationSerializer.serialize(lastModifiedVersion));
    builder.setLastModifiedTime(lastModifiedTime);
    builder.setConsistent(consistent);
    return builder.build();
  }

  private RangeMap<RangeValue, String> getRanges(SegmentId segmentId) {
    RangeMap<RangeValue, String> ranges = rangeMap.getIfPresent(segmentId);
    if (ranges == null) {
      try {
        ranges = rangeMap.get(segmentId);
        if (serialized != null) {
          int index = serialized.getSegmentIdList().indexOf(segmentId.serialize());
          if (index != -1) {
            SegmentRanges serializedRanges = serialized.getSegmentRanges(index);
            for (ByteString serializedFragment : serializedRanges.getRawFragmentRangesList()) {
              ProtoBlockIndex.BlockIndex.FragmentRange fragment =
                  ProtoBlockIndex.BlockIndex.FragmentRange.parseFrom(serializedFragment);
              ranges.put(Range.closed(RangeValue.of(fragment.getSourceVersion()),
                  RangeValue.of(fragment.getTargetVersion())), fragment.getBlockId());
            }
          }
        }
      } catch (ExecutionException | InvalidProtocolBufferException ex) {
        throw new RuntimeException(ex);
      }
    }
    return ranges;
  }
}
