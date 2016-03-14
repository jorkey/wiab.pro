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
package org.waveprotocol.box.server.persistence.file;

import com.google.common.base.Preconditions;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * An index for quickly accessing snapshots. The index is an array of longs, one for each version.
 * The index must return the offset of a snapshot of specified or nearest lower version.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class FileSnapshotIndex {

  /** Returned from methods when there is no record for a specified version. */
  public static final int NO_RECORD_FOR_VERSION = -1;
  private static final int RECORD_LENGTH = 8;
  private final File fileRef;
  private RandomAccessFile file;
  private long lastVersion = 0;
  private long offset = NO_RECORD_FOR_VERSION;

  public FileSnapshotIndex(File fileRef) {
    this.fileRef = fileRef;
  }

  /**
   * Opens the index.
   *
   * @param baseCollection the collection which the index indexes.
   * @throws IOException
   */
  public synchronized void open(FileSnapshotAccess baseCollection) throws IOException {
    file = FileUtils.getOrCreateFile(fileRef);
    Preconditions.checkArgument(file.length() % RECORD_LENGTH == 0, "Index file is corrupt");
    if (file.length() >= RECORD_LENGTH) {
      file.seek(file.length() - RECORD_LENGTH);
      offset = file.readLong();
      lastVersion = file.length() / RECORD_LENGTH;
    }
  }

  /**
   * Gets last version.
   */
  public synchronized long getLastVersion() {
    return lastVersion;
  }

  /**
   * Clears the index.
   */
  public synchronized void clear() throws IOException {
    file.setLength(0);
    lastVersion = 0;
    offset = NO_RECORD_FOR_VERSION;
  }

  /**
   * Gets the snapshot file offset for the specified version.
   *
   * @param version
   * @return the offset on success, NO_RECORD_FOR_VERSION if there's no record.
   * @throws IOException
   */
  public synchronized long getOffsetForVersion(long version) throws IOException {
    if (version >= this.lastVersion) {
      return offset;
    }
    if (!seekToVersion(version)) {
      return NO_RECORD_FOR_VERSION;
    }
    if (file.getFilePointer() + RECORD_LENGTH >= file.length()) {
      return NO_RECORD_FOR_VERSION;
    }
    return file.readLong();
  }

  /**
   * Indexes a new snapshot.
   *
   * @param version the version at which the delta is applied
   * @param offset offset at which the snapshot is stored
   */
  public synchronized void addSnapshot(long version, long offset)
      throws IOException {
    checkOpen();

    Preconditions.checkArgument(version > lastVersion, "Version is old");

    file.seek(file.length());
    for (long i = lastVersion; i < version - 1; i++) {
      file.writeLong(this.offset);
    }
    file.writeLong(offset);

    lastVersion = version;
    this.offset = offset;
  }

  /**
   * Closes the index.
   */
  public synchronized void close() throws IOException {
    if (file != null) {
      file.close();
      file = null;
    }
  }
  
  private void checkOpen() {
    Preconditions.checkState(file != null, "Index file not open");
  }

  /**
   * Seeks to the corresponding version, if it is valid.
   *
   * @param version version to seek to.
   * @return true if the position is valid
   * @throws IOException
   */
  private boolean seekToVersion(long version) throws IOException {
    if (version <= 0) {
      return false;
    }
    checkOpen();

    long position = (version-1) * RECORD_LENGTH;
    if (position > (file.length()- RECORD_LENGTH)) {
      return false;
    }

    file.seek(position);
    return true;
  }
}
