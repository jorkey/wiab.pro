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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.protobuf.InvalidProtocolBufferException;

import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.SnapshotStore.SnapshotAccess;
import org.waveprotocol.box.server.persistence.deltas.DeltaStore.DeltaAccess;
import org.waveprotocol.box.server.persistence.protos.ProtoSnapshotStoreData.ProtoWaveletSnapshot;
import org.waveprotocol.box.server.shutdown.LifeCycle;
import org.waveprotocol.box.server.shutdown.ShutdownPriority;
import org.waveprotocol.box.server.shutdown.Shutdownable;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.wave.model.id.InvalidIdException;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveletData;
import org.waveprotocol.wave.util.logging.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.waveprotocol.box.server.common.SnapshotSerializer;

/**
 * Access to file snapshots store.
 *
 * @author unknown
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class FileSnapshotAccess implements SnapshotAccess {
  private static final Log LOG = Log.get(FileSnapshotAccess.class);

  /** Suffix of file with initial snapshot. */
  public static final String SNAPSHOT_FILE_SUFFIX = ".snapshot";

  /** Suffix of file with history of snapshots. */
  public static final String SNAPSHOTS_FILE_SUFFIX = ".snapshots";

  /** Suffix of index file of snapshots history. */
  public static final String SNAPSHOTS_INDEX_FILE_SUFFIX = ".snapshots_index";

  /** Version of snapshot file format. */
  private static final int SNAPSHOT_FORMAT_VERSION = 1;

  private final WaveletName waveletName;
  private final FileSnapshot initialSnapshotFile;
  private final RandomAccessFile snapshotsFile;
  private final FileSnapshotIndex snapshotsIndex;

  private boolean isOpen;

  final private LifeCycle lifeCycle = new LifeCycle(FileSnapshotAccess.class.getSimpleName(), ShutdownPriority.Storage,
      new Shutdownable() {
    @Override
    public void shutdown() throws Exception {
      close();
    }
  });

  /**
   * A single record in the snapshot file.
   */
  private class SnapshotHeader {
    /** Length in bytes of the header */
    public static final int HEADER_LENGTH = 8;

    /** The protocol version of the remaining fields. For now, must be 1. */
    public final int protoVersion;

    /** The length of the applied snapshot segment, in bytes. */
    public final int snapshotLength;

    public SnapshotHeader(int protoVersion, int snapshotLength) {
      this.protoVersion = protoVersion;
      this.snapshotLength = snapshotLength;
    }

    public void checkVersion() throws IOException {
      if (protoVersion != SNAPSHOT_FORMAT_VERSION) {
        throw new IOException("Invalid snapshot header");
      }
    }
  }

  /**
   * Opens a file snapshot collection.
   *
   * @param waveletName name of the wavelet to open
   * @param basePath base path of files
   * @return an open collection
   * @throws IOException
   */
  public static FileSnapshotAccess open(WaveletName waveletName, String basePath)
      throws IOException {
    Preconditions.checkNotNull(waveletName, "null wavelet name");

    FileSnapshot snapshotFile = new FileSnapshot(snapshotFile(basePath, waveletName));
    RandomAccessFile snapshotsFile = FileUtils.getOrCreateFile(snapshotsFile(basePath, waveletName));
    FileSnapshotIndex snapshotsIndex = new FileSnapshotIndex(snapshotsIndexFile(basePath, waveletName));

    FileSnapshotAccess access = new FileSnapshotAccess(waveletName, snapshotFile, snapshotsFile, snapshotsIndex);

    snapshotFile.open();
    snapshotsIndex.open(access);
    return access;
  }

  /**
   * Delete the snapshot files from disk.
   *
   * @throws PersistenceException
   */
  public static void delete(WaveletName waveletName, String basePath) throws PersistenceException {
    String error = "";

    File snapshot = snapshotFile(basePath, waveletName);
    if (snapshot.exists()) {
      if (!snapshot.delete()) {
        error += "Could not delete snapshot file: " + snapshot.getAbsolutePath() + ". ";
      }
    }

    File snapshots = snapshotsFile(basePath, waveletName);
    if (snapshots.exists()) {
      if (!snapshots.delete()) {
        error += "Could not delete history snapshots file: " + snapshots.getAbsolutePath() + ". ";
      }
    }

    if (!error.isEmpty()) {
      throw new PersistenceException(error);
    }
  }

  /**
   * Create a new file snapshot collection for the given wavelet.
   */
  public FileSnapshotAccess(WaveletName waveletName, FileSnapshot snapshotFile, RandomAccessFile snapshotsFile,
      FileSnapshotIndex snapshotsIndex) {
    this.waveletName = waveletName;
    this.initialSnapshotFile = snapshotFile;
    this.snapshotsFile = snapshotsFile;
    this.snapshotsIndex = snapshotsIndex;
    this.isOpen = true;
    lifeCycle.start();
  }

  @Override
  public WaveletName getWaveletName() {
    return waveletName;
  }

  @Override
  public synchronized void close() throws IOException {
    lifeCycle.enter();
    try {
      initialSnapshotFile.close();
      snapshotsFile.close();
      snapshotsIndex.close();
      isOpen = false;
    } finally {
      lifeCycle.leave();
    }
  }

  @Override
  public synchronized void remakeSnapshotsHistory(DeltaAccess deltasAccess, int savingSnapshotPeriod)
      throws PersistenceException {
    lifeCycle.enter();
    try {
      checkIsOpen();
      snapshotsFile.setLength(0);
      snapshotsIndex.clear();
      WaveletData wavelet = null;
      long lastSnapshotVersion = 0;
      long version = 0;
      while (version != deltasAccess.getLastModifiedVersion().getVersion()) {
        TransformedWaveletDelta delta = deltasAccess.getDeltaByStartVersion(version).getTransformedDelta();
        if (wavelet == null) {
          wavelet = WaveletDataUtil.buildWaveletFromFirstDelta(waveletName, delta);
        } else {
          WaveletDataUtil.applyWaveletDelta(delta, wavelet);
        }
        if (delta.getResultingVersion().getVersion() >= lastSnapshotVersion+savingSnapshotPeriod) {
          writeSnapshotToHistory(wavelet);
          lastSnapshotVersion = delta.getResultingVersion().getVersion();
        }
        version = delta.getResultingVersion().getVersion();
      }
      initialSnapshotFile.writeSnapshot(wavelet);
    } catch (IOException ex) {
      throw new PersistenceException(ex);
    } catch (OperationException ex) {
      throw new PersistenceException(ex);
    } finally {
      lifeCycle.leave();
    }
  }

  @Override
  public synchronized WaveletData readInitialSnapshot() throws IOException {
    lifeCycle.enter();
    try {
      checkIsOpen();
      return initialSnapshotFile.readSnapshot(waveletName.waveId);
    } finally {
      lifeCycle.leave();
    }
  }

  @Override
  public synchronized WaveletData readNearestSnapshot(long version) throws IOException {
    lifeCycle.enter();
    try {
      checkIsOpen();
      long offsetForEndVersion = snapshotsIndex.getOffsetForVersion(version);
      if (offsetForEndVersion == FileSnapshotIndex.NO_RECORD_FOR_VERSION) {
        return null;
      }
      seekSnapshotsTo(offsetForEndVersion);
      return readSnapshotRecord(waveletName.waveId);
    } finally {
      lifeCycle.leave();
    }
  }

  @Override
  public synchronized long getLastHistorySnapshotVersion() {
    checkIsOpen();
    return snapshotsIndex.getLastVersion();
  }

  @Override
  public synchronized void writeInitialSnapshot(WaveletData snapshot) throws PersistenceException {
    lifeCycle.enter();
    try {
      checkIsOpen();
      initialSnapshotFile.writeSnapshot(snapshot);
    } catch (IOException ex) {
      throw new PersistenceException(ex);
    } finally {
      lifeCycle.leave();
    }
  }

  @Override
  public synchronized void writeSnapshotToHistory(WaveletData snapshot) throws PersistenceException {
    lifeCycle.enter();
    try {
      checkIsOpen();
      snapshotsFile.seek(snapshotsFile.length());
      snapshotsIndex.addSnapshot(snapshot.getVersion(), snapshotsFile.getFilePointer());
      writeSnapshotRecord(snapshot);
    } catch (IOException ex) {
      throw new PersistenceException(ex);
    } finally {
      lifeCycle.leave();
    }
  }

  @VisibleForTesting
  static File snapshotFile(String basePath, WaveletName waveletName) {
    String waveletPathPrefix = FileUtils.waveletNameToPathSegment(waveletName);
    return new File(basePath, waveletPathPrefix + SNAPSHOT_FILE_SUFFIX);
  }

  @VisibleForTesting
  static File snapshotsFile(String basePath, WaveletName waveletName) {
    String waveletPathPrefix = FileUtils.waveletNameToPathSegment(waveletName);
    return new File(basePath, waveletPathPrefix + SNAPSHOTS_FILE_SUFFIX);
  }

  @VisibleForTesting
  static File snapshotsIndexFile(String basePath, WaveletName waveletName) {
    String waveletPathPrefix = FileUtils.waveletNameToPathSegment(waveletName);
    return new File(basePath, waveletPathPrefix + SNAPSHOTS_INDEX_FILE_SUFFIX);
  }

  private void checkIsOpen() {
    Preconditions.checkState(isOpen, "Snapshot collection closed");
  }

  /**
   * Read and deserialize the snapshot at the current file position.
   */
  private WaveletData readSnapshotRecord(WaveId waveId) throws IOException {
    SnapshotHeader header = readSnapshotHeader();
    ProtoWaveletSnapshot snapshot = readSnapshot(header.snapshotLength);
    if (snapshot == null) {
      return null;
    }
    try {
      return SnapshotSerializer.deserializeWavelet(snapshot.getSnapshot(), waveId);
    } catch (OperationException ex) {
      throw new IOException(ex);
    } catch (InvalidParticipantAddress ex) {
      throw new IOException(ex);
    } catch (InvalidIdException ex) {
      throw new IOException(ex);
    }
  }

  /**
   * Read a header from the file. Does not move the file pointer before reading.
   */
  private SnapshotHeader readSnapshotHeader() throws IOException {
    int version = snapshotsFile.readInt();
    if (version != SNAPSHOT_FORMAT_VERSION) {
      throw new IOException("Snapshot header invalid");
    }
    int snapshotLength = snapshotsFile.readInt();
    SnapshotHeader snapshotHeader = new SnapshotHeader(version, snapshotLength);
    snapshotHeader.checkVersion();
    // Verify the file size.
    long remaining = snapshotsFile.length() - snapshotsFile.getFilePointer();
    long missing = snapshotLength - remaining;
    if (missing > 0) {
      throw new IOException("File is corrupted, missing " + missing + " bytes");
    }
    return snapshotHeader;
  }

  /**
   * Read the snapshot at the current file position. After method call,
   * file position is directly after applied snapshot field.
   */
  private ProtoWaveletSnapshot readSnapshot(int length)
      throws IOException {

    if (length == 0) {
      return null;
    }

    byte[] bytes = new byte[length];
    snapshotsFile.readFully(bytes);
    try {
      return ProtoWaveletSnapshot.parseFrom(bytes);
    } catch (InvalidProtocolBufferException e) {
      throw new IOException(e);
    }
  }

  /**
   * Write a snapshot to the file. Does not move the file pointer before writing. Returns number of
   * bytes written.
   */
  private long writeSnapshotRecord(ReadableWaveletData snapshot) throws IOException {
    // We'll write zeros in place of the header and come back & write it at the end.
    long headerPointer = snapshotsFile.getFilePointer();
    snapshotsFile.write(new byte[SnapshotHeader.HEADER_LENGTH]);

    int length = writeSnapshot(
        ProtoWaveletSnapshot.newBuilder().setSnapshot(SnapshotSerializer.serializeWavelet(snapshot)).build());

    long endPointer = snapshotsFile.getFilePointer();
    snapshotsFile.seek(headerPointer);
    writeSnapshotHeader(new SnapshotHeader(SNAPSHOT_FORMAT_VERSION, length));
    snapshotsFile.seek(endPointer);

    return endPointer - headerPointer;
  }

  /**
   * Write a header to the current location in the file
   */
  private void writeSnapshotHeader(SnapshotHeader header) throws IOException {
    snapshotsFile.writeInt(header.protoVersion);
    snapshotsFile.writeInt(header.snapshotLength);
  }

  /**
   * Write an snapshot to the current position in the file. Returns number of bytes written.
   */
  private int writeSnapshot(ProtoWaveletSnapshot snapshot)
      throws IOException {
    if (snapshot != null) {
      byte[] bytes = snapshot.toByteArray();
      snapshotsFile.write(bytes);
      return bytes.length;
    } else {
      return 0;
    }
  }

  private boolean seekSnapshotsTo(long offset) throws IOException {
    if (offset == FileSnapshotIndex.NO_RECORD_FOR_VERSION) {
      // There's no record for the specified version.
      return false;
    } else {
      snapshotsFile.seek(offset);
      return true;
    }
  }
}