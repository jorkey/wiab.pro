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

import org.waveprotocol.box.server.persistence.blocks.Marker;
import org.waveprotocol.box.server.persistence.protos.ProtoBlockStore.SnapshotsIndexRecord;
import org.waveprotocol.box.server.persistence.blocks.FarBackwardMarker;
import org.waveprotocol.box.server.persistence.blocks.FarForwardMarker;
import org.waveprotocol.box.server.persistence.blocks.TopMarker;
import org.waveprotocol.box.server.persistence.blocks.SnapshotMarker;

import org.waveprotocol.wave.model.util.Preconditions;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;

/**
 * Index of fragment.
 * Markers and snapshots index.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class FragmentIndex {
  /** Type of marker. */
  private static final int MARKER_TYPE_TOP = 1;
  private static final int MARKER_TYPE_FAR_BACKWARD = 2;
  private static final int MARKER_TYPE_FAR_FORWARD = 4;
  private static final int MARKER_TYPE_SNAPSHOT = 8;
  private static final int MARKER_TYPE_ADDITIONAL = 14;
  private static final int MARKER_TYPE_ANY = 15;

  /** Serialized markers. */
  private byte[] markersBuffer;
  private int markersBufferLength;

  /** Serialized snapshots index from read block. */
  private final byte[] initialSnapshotsIndex;

  /** Serialized last snapshot from read block. */
  private byte[] lastSnapshot;

  /** Deserialized and added snapshots versions and markers offsets. */
  private ArrayList<Long> snapshotVersions;
  private ArrayList<Integer> snapshotMarkerOffsets;

  /** Last wrote marker offset. */
  private int lastMarkerOffset = -1;

  /** Fragment is first in the segment. */
  private final boolean first;

  /** Fragment is last in the segment. */
  private volatile boolean last;

  /**
   * Creates new fragment index.
   */
  public FragmentIndex(boolean first) {
    this(new byte[0], new byte[0], new byte[0], first, true);
    snapshotVersions = new ArrayList<>();
    snapshotMarkerOffsets = new ArrayList<>();
  }

  /**
   * Creates fragment index from read block data.
   */
  public FragmentIndex(byte[] initialMarkers, byte[] initialSnapshotsIndex,
      byte[] lastSnapshot, boolean first, boolean last) {
    this.markersBuffer = initialMarkers;
    this.markersBufferLength = initialMarkers.length;
    this.initialSnapshotsIndex = initialSnapshotsIndex;
    this.lastSnapshot = lastSnapshot.length != 0 ? lastSnapshot : null;
    this.first = first;
    this.last = last;
    if (initialMarkers != null && initialMarkers.length != 0) {
      int markerLength = initialMarkers[initialMarkers.length-1] & 0xFF;
      lastMarkerOffset = initialMarkers.length-markerLength-3;
    }
  }
  
  /**
   * Gets last snapshot or null.
   */
  public synchronized byte[] getLastSnapshot() {
    return lastSnapshot;
  }

  /**
   * Gets nearest snapshot index to specified version.
   * Returns -1 if no snapshots.
   */
  public synchronized int getNearestSnapshotIndex(long version) {
    ArrayList<Long> versions = getSnapshotVersions();
    if (versions.isEmpty()) {
      return -1;
    }
    long lastVersion = versions.get(versions.size()-1);
    if (version >= lastVersion) {
      return versions.size()-1;
    }
    return getNearest(versions, 0, versions.size()-1, version);
  }

  /**
   * Gets snapshot version by index.
   */
  public synchronized long getSnapshotVersion(int index) {
    ArrayList<Long> versions = getSnapshotVersions();
    Preconditions.checkArgument(index < versions.size(), "Invalid snapshot index");
    return versions.get(index);
  }

  /**
   * Gets snapshot marker offset by index.
   */
  public synchronized int getSnapshotMarkerOffset(int index) {
    ArrayList<Integer> offsets = getSnapshotMarkerOffsets();
    Preconditions.checkArgument(index < offsets.size(), "Invalid snapshot index");
    return offsets.get(index);
  }

  /**
   * Gets last marker offset.
   */
  public synchronized int getLastMarkerOffset() {
    return lastMarkerOffset;
  }

  /**
   * Gets last snapshot version.
   * Returns 0 if snapshots are not exist.
   */
  public synchronized long getLastStreamSnapshotVersion() {
    ArrayList<Long> versions = getSnapshotVersions();
    if (!versions.isEmpty()) {
      return versions.get(versions.size()-1);
    }
    return 0;
  }

  /**
   * Checks that fragment is first.
   */
  public boolean isFirst() {
    return first;
  }

  /**
   * Checks that fragment is last.
   */
  public boolean isLast() {
    return last;
  }

  /**
   * Deserializes top marker at offset.
   */
  public synchronized TopMarker deserializeTopMarker(int offset) {
    return (TopMarker)deserializeMarker(offset, MARKER_TYPE_TOP);
  }
  
  /**
   * Tries to deserialize additional marker at offset.
   */
  public synchronized Marker deserializeAdditionalMarker(int offset) {
    return deserializeMarker(offset, MARKER_TYPE_ADDITIONAL);
  }
  
  /**
   * Deserializes any marker at offset.
   */
  public synchronized Marker deserializeMarker(int offset) {
    return deserializeMarker(offset, MARKER_TYPE_ANY);
  }

  /**
   * Finds top marker offset by offset of any version's marker.
   */
  public synchronized int findTopMarkerOffset(int offset) {
    Preconditions.checkArgument(offset < markersBufferLength, "Invalid offset");
    do {
      int type = markersBuffer[offset] & 0xFF;
      if (type == MARKER_TYPE_TOP) {
        return offset;
      }
      int prevMarkerLen = markersBuffer[offset-1] & 0xFF;
      offset -= (prevMarkerLen + 3);
    } while (offset >= 0);
    return -1;
  }
  
  /**
   * Adds a marker.
   *
   * @param marker the marker.
   * @return marker offset.
   */
  public synchronized int addMarker(Marker marker) {
    Preconditions.checkArgument(last, "Fragment is finished");
    int markerOffset = markersBufferLength;
    writeMarker(markerOffset, marker);
    return markerOffset;
  }

  /**
   * Writes a marker at specified offset.
   *
   * @param offset offset of marker.
   * @param marker the marker.
   */
  public synchronized void writeMarker(int offset, Marker marker) {
    Preconditions.checkArgument(last, "Fragment is finished");
    Preconditions.checkArgument(offset <= markersBufferLength, "Invalid offset");
    int markerType = -1;
    if (marker instanceof TopMarker) {
      markerType = MARKER_TYPE_TOP;
    } else if (marker instanceof FarBackwardMarker) {
      markerType = MARKER_TYPE_FAR_BACKWARD;
    } else if (marker instanceof FarForwardMarker) {
      markerType = MARKER_TYPE_FAR_FORWARD;
    } else if (marker instanceof SnapshotMarker) {
      markerType = MARKER_TYPE_SNAPSHOT;
    } else {
      Preconditions.illegalArgument("Invalid marker type " + marker);
    }
    byte[] serialized = marker.serialize();
    for (;;) {
      int emptySpace = markersBuffer.length - offset;
      if (emptySpace >= serialized.length + 3) {
        break;
      }
      byte[] newBuffer = new byte[markersBuffer.length==0 ? 1024 : markersBuffer.length*2];
      System.arraycopy(markersBuffer, 0, newBuffer, 0, markersBufferLength);
      markersBuffer = newBuffer;
    }
    int index = offset;
    if (index == markersBufferLength) {
      markersBuffer[index++] = (byte)markerType;
      markersBuffer[index++] = (byte)serialized.length;
    } else {
      Preconditions.checkArgument(markersBuffer[index++] == (byte)markerType, "Rewritable marker type is different");
      Preconditions.checkArgument(markersBuffer[index++] == (byte)serialized.length, "Rewritable marker length is different");
    }
    System.arraycopy(serialized, 0, markersBuffer, index, serialized.length);
    index += serialized.length;
    markersBuffer[index++] = (byte)serialized.length;
    if (index > markersBufferLength) {
      marker.setPreviousMarkerOffset(lastMarkerOffset);
      marker.setNextMarkerOffset(index);
      markersBufferLength = index;
      lastMarkerOffset = offset;
    }
  }
  
  /**
   * Sets last snapshot.
   */
  public synchronized void setLastSnapshot(byte[] lastSnapshot) {
    this.lastSnapshot = lastSnapshot;
  }

  /**
   * Adds snapshot index.
   *
   * @param version the version of snapshot.
   * @param markerOffset offset of marker of specified version.
   * @return snapshot index.
   */
  public synchronized int addSnapshot(long version, int markerOffset) {
    Preconditions.checkArgument(last, "Fragment is finished");
    getSnapshotVersions().add(version);
    getSnapshotMarkerOffsets().add(markerOffset);
    return getSnapshotVersions().size()-1;
  }

  /**
   * Finishes fragment.
   */
  public synchronized void finish() {
    Preconditions.checkArgument(last, "Fragment is already finished");
    last = false;
    lastSnapshot = null;
  }

  /**
   * Serializes markers.
   */
  public synchronized ByteString serializeMarkers() {
    return ByteString.copyFrom(markersBuffer, 0, markersBufferLength);
  }

  /**
   * Serializes snapshots index.
   */
  public synchronized ByteString serializeSnapshotsIndex() {
    if (snapshotVersions != null) {
      SnapshotsIndexRecord.Builder builder = SnapshotsIndexRecord.newBuilder();
      for (long version : snapshotVersions) {
        builder.addSnapshotVersions(version);
      }
      for (int offset : snapshotMarkerOffsets) {
        builder.addMarkerOffsets(offset);
      }
      return builder.build().toByteString();
    } else {
      return ByteString.copyFrom(initialSnapshotsIndex);
    }
  }

  /**
   * Serializes last snapshot.
   */
  public synchronized ByteString serializeLastSnapshot() {
    if (lastSnapshot != null) {
      return ByteString.copyFrom(lastSnapshot);
    }
    return ByteString.EMPTY;
  }

  /**
   * Deserializes marker at offset of required type mask.
   */
  private Marker deserializeMarker(int offset, int markerTypeMask) {
    Preconditions.checkArgument(offset <= markersBufferLength, "Invalid offset");
    if (offset == markersBufferLength) {
      return null;
    }
    int type = markersBuffer[offset] & 0xFF;
    if ((type & markerTypeMask) == 0) {
      return null;
    }
    int len = markersBuffer[offset+1] & 0xFF;
    ByteArrayInputStream in = new ByteArrayInputStream(markersBuffer, offset+2, len);
    Marker marker = null;
    if (type == MARKER_TYPE_TOP) {
      marker = TopMarkerImpl.deserialize(in);
    } else if (type == MARKER_TYPE_FAR_BACKWARD) {
      marker = FarBackwardMarkerImpl.deserialize(in);
    } else if (type == MARKER_TYPE_FAR_FORWARD) {
      marker = FarForwardMarkerImpl.deserialize(in);
    } else if (type == MARKER_TYPE_SNAPSHOT) {
      marker = SnapshotMarkerImpl.deserialize(in);
    } else {
      Preconditions.illegalArgument("Invalid marker type " + type);
    }
    int nextMarkerOffset = offset+len+3;
    marker.setNextMarkerOffset(nextMarkerOffset);
    if (offset > 0) {
      int prevMarkerLen = markersBuffer[offset-1] & 0xFF;
      marker.setPreviousMarkerOffset(offset-prevMarkerLen-3);
    }
    return marker;
  }
  
  private ArrayList<Long> getSnapshotVersions() {
    if (snapshotVersions == null) {
      deserializeSnapshotsIndex();
    }
    return snapshotVersions;
  }

  private ArrayList<Integer> getSnapshotMarkerOffsets() {
    if (snapshotMarkerOffsets == null) {
      deserializeSnapshotsIndex();
    }
    return snapshotMarkerOffsets;
  }

  private void deserializeSnapshotsIndex() {
    if (snapshotVersions == null) {
      snapshotVersions = new ArrayList<>();
      snapshotMarkerOffsets = new ArrayList<>();
      if (initialSnapshotsIndex.length != 0) {
        try {
          SnapshotsIndexRecord record = SnapshotsIndexRecord.parseFrom(initialSnapshotsIndex);
          for (long version : record.getSnapshotVersionsList()) {
            snapshotVersions.add(version);
          }
          for (int offset : record.getMarkerOffsetsList()) {
            snapshotMarkerOffsets.add(offset);
          }
        } catch (InvalidProtocolBufferException ex) {
          throw new RuntimeException(ex);
        }
      }
    }
  }

  private static int getNearest(ArrayList<Long> array, int low, int high, long value) {
    long lowValue = array.get(low);
    long hightValue = array.get(high);
    if (lowValue >= value) {
      return low;
    }
    if (hightValue <= value) {
      return high;
    }
    if (high - low == 1) {
      long leftDist = value - lowValue;
      long rightDist = hightValue - value;
      return (leftDist <= rightDist) ? low : high;
    }
    int middle = low + (high-low)/2;
    long middleValue = array.get(middle);
    if (value < middleValue) {
      return getNearest(array, low, middle, value);
    } else if (value > middleValue) {
      return getNearest(array, middle, high, value);
    }
    return middle;
  }
}
