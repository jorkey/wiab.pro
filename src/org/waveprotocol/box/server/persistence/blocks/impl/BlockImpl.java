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

import org.waveprotocol.box.server.persistence.blocks.SegmentOperation;
import org.waveprotocol.box.server.persistence.blocks.DeserializationBlockException;
import org.waveprotocol.box.server.persistence.blocks.VersionInfo;
import org.waveprotocol.box.server.persistence.blocks.Block;
import org.waveprotocol.box.server.persistence.blocks.Fragment;
import org.waveprotocol.box.server.persistence.blocks.FragmentObserver;
import org.waveprotocol.box.server.persistence.blocks.ReadableSegmentSnapshot;

import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.CollectionUtils;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.List;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

/**
 * Block container implementation.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class BlockImpl implements Block {
  /** Version of block file format. */
  private final static int BLOCK_FORMAT_VERSION = 1;

  /** Block Id and fragment indexes. */
  private final BlockHeader header;

  /** Serialized data from read block. */
  private final byte[] initialData;

  /** New data to append to initial data. */
  private final ByteArrayOutputStream addedData = new ByteArrayOutputStream();

  /** Observers access and modification of block. */
  private final List<FragmentObserver> observers = CollectionUtils.newLinkedList();

  /** Data size. */
  private volatile int size;

  /** Locks. */
  private final ReentrantReadWriteLock lock;
  private final ReentrantReadWriteLock.ReadLock readLock;
  private final ReentrantReadWriteLock.WriteLock writeLock;

  public static Block create(String blockId) {
    ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    BlockHeader header = new BlockHeader(blockId);
    return new BlockImpl(header, null, lock);
  }

  public static Block deserialize(InputStream in) throws IOException {
    ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    int version = Recorder.readInt(in);
    if (version != BLOCK_FORMAT_VERSION) {
      throw new IOException("Block format version is invalid");
    }
    BlockHeader header = BlockHeader.deserialize(Recorder.readRecord(in));
    byte[] data = Recorder.readRecord(in);
    return new BlockImpl(header, data, lock);
  }

  private BlockImpl(BlockHeader header, byte[] initialData, ReentrantReadWriteLock lock) {
    this.header = header;
    this.initialData = initialData;
    this.lock = lock;
    size = (initialData != null) ? initialData.length : 0;
    readLock = lock.readLock();
    writeLock = lock.writeLock();
  }

  @Override
  public String getBlockId() {
    return header.getBlockId();
  }

  @Override
  public int getSize() {
    return size;
  }

  @Override
  public long getLastModifiedVersion() {
    return header.getLastModifiedVersion();
  }

  @Override
  public Fragment getFragment(SegmentId segmentId) {
    readLock.lock();
    try {
      return new FragmentImpl(this, segmentId, header.getFragmentIndex(segmentId));
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public VersionInfo deserializeVersionInfo(int offset) {
    readLock.lock();
    try {
      return VersionInfoImpl.deserialize(readRecord(offset), offset, header.getAuthors());
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public ReadableSegmentSnapshot deserializeSegmentSnapshot(int offset, SegmentId segmentId) {
    readLock.lock();
    try {
      return SegmentSnapshotImpl.deserialize(readRecord(offset), segmentId);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public SegmentOperation deserializeSegmentOperation(int offset, SegmentId segmentId, WaveletOperationContext context) {
    readLock.lock();
    try {
      return SegmentOperationImpl.deserialize(new String(readRecord(offset), "utf-8"), segmentId, context);
    } catch (UnsupportedEncodingException ex) {
      throw new DeserializationBlockException(ex);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public void serialize(OutputStream out) {
    readLock.lock();
    try {
      Recorder.writeInt(out, BLOCK_FORMAT_VERSION);
      Recorder.writeRecord(out, header.serialize().toByteString().toByteArray());
      if (initialData != null) {
        Recorder.writeRecord(out, initialData, addedData.toByteArray());
      } else {
        Recorder.writeRecord(out, addedData.toByteArray());
      }
    } catch (IOException ex) {
      throw new SerializationBlockException(ex);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Fragment createFragment(SegmentId segmentId, boolean first) {
    writeLock.lock();
    try {
      return new FragmentImpl(this, segmentId, header.createFragmentIndex(segmentId, first));
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public int writeVersionInfo(VersionInfo versionInfo) {
    writeLock.lock();
    try {
      if (versionInfo.getVersion() > header.getLastModifiedVersion()) {
        header.setLastModifiedVersion(versionInfo.getVersion());
      }
      if (versionInfo.getAuthor() != null) {
        header.registryAuthor(versionInfo.getAuthor());
      }
      return writeRecord(versionInfo.serialize(header.getAuthors()).toByteArray());
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public int writeSegmentSnapshot(ReadableSegmentSnapshot segmentSnapshot) {
    return writeRecord(segmentSnapshot.serialize().toByteArray());
  }

  @Override
  public int writeSegmentOperation(SegmentOperation segmentOperation) {
    try {
      return writeRecord(segmentOperation.serialize().getData().getBytes("utf-8"));
    } catch (UnsupportedEncodingException ex) {
      throw new SerializationBlockException(ex);
    }
  }

  @Override
  public void addObserver(FragmentObserver observer) {
    observers.add(observer);
  }

  @Override
  public ReentrantReadWriteLock.ReadLock getReadLock() {
    return readLock;
  }

  @Override
  public ReentrantReadWriteLock.WriteLock getWriteLock() {
    return writeLock;
  }

  @Override
  public void writeAccessNotify(Fragment fragment) {
    synchronized (observers) {
      for (FragmentObserver observer : observers) {
        observer.onFragmentModified(fragment);
      }
    }
  }

  private int writeRecord(byte[] record) {
    writeLock.lock();
    try {
      int index = size;
      int addedSize = addedData.size();
      try {
        Recorder.writeRecord(addedData, record);
      } catch (IOException ex) {
        throw new SerializationBlockException(ex);
      }
      size += addedData.size() - addedSize;
      return index;
    } finally {
      writeLock.unlock();
    }
  }

  private byte[] readRecord(int offset) {
    try {
      byte[] buf;
      if (initialData != null && offset < initialData.length) {
        buf = initialData;
      } else {
        if (initialData != null) {
          offset -= initialData.length;
        }
        Preconditions.checkArgument(offset < addedData.size(), "Invalid offset");
        buf = addedData.toByteArray();
      }
      return Recorder.readRecord(new ByteArrayInputStream(buf, offset, buf.length-offset));
    } catch (IOException ex) {
      throw new DeserializationBlockException(ex);
    }
  }
}
