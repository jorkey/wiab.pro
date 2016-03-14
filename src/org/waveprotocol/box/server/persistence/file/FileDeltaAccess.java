/**
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.waveprotocol.box.server.persistence.file;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.deltas.DeltaStore.DeltaAccess;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.persistence.protos.ProtoDeltaStoreData.ProtoTransformedWaveletDelta;
import org.waveprotocol.box.server.persistence.protos.ProtoDeltaStoreDataSerializer;
import org.waveprotocol.box.server.shutdown.LifeCycle;
import org.waveprotocol.box.server.shutdown.ShutdownPriority;
import org.waveprotocol.box.server.shutdown.Shutdownable;
import org.waveprotocol.box.server.waveserver.AppliedDeltaUtil;
import org.waveprotocol.box.server.waveserver.ByteStringMessage;
import org.waveprotocol.wave.federation.Proto.ProtocolAppliedWaveletDelta;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.util.logging.Log;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import org.waveprotocol.box.common.ThrowableReceiver;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;

/**
 * A flat file based implementation of DeltasAccess. This class provides a storage backend for the
 * deltas in a single wavelet.
 *
 * The file starts with a header. The header contains the version of the file protocol. After the
 * version, the file contains a sequence of delta records. Each record contains a header followed
 * by a WaveletDeltaRecord.
 *
 * A particular FileDeltaCollection instance assumes that it's <em>the only one</em> reading and
 * writing a particular wavelet. The methods are <em>not</em> multithread-safe.
 *
 * See this document for design specifics:
 * https://sites.google.com/a/waveprotocol.org/wave-protocol/protocol/design-proposals/wave-store-design-for-wave-in-a-box
 *
 * @author josephg@gmail.com (Joseph Gentle)
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class FileDeltaAccess implements DeltaAccess {
  public static final String DELTAS_FILE_SUFFIX = ".deltas";
  public static final String INDEX_FILE_SUFFIX = ".index";

  private static final byte[] FILE_MAGIC_BYTES = new byte[]{'W', 'A', 'V', 'E'};
  private static final int FILE_PROTOCOL_VERSION = 1;
  private static final int FILE_HEADER_LENGTH = 8;

  private static final int DELTA_FORMAT_VERSION = 1;

  private static final Log LOG = Log.get(FileDeltaAccess.class);

  private final WaveletName waveletName;
  private final RandomAccessFile deltasFile;
  private final DeltaIndex deltasIndex;

  private HashedVersion lastModifiedVersion;
  private long lastModifiedTime;
  private boolean isOpen;

  final private LifeCycle lifeCycle = new LifeCycle(FileDeltaAccess.class.getSimpleName(), ShutdownPriority.Storage,
      new Shutdownable() {
    @Override
    public void shutdown() throws Exception {
      close();
    }
  });

  /**
   * A single record in the delta file.
   */
  private class DeltaHeader {
    /** Length in bytes of the header */
    public static final int HEADER_LENGTH = 12;

    /** The protocol version of the remaining fields. For now, must be 1. */
    public final int protoVersion;

    /** The length of the applied delta segment, in bytes. */
    public final int appliedDeltaLength;
    public final int transformedDeltaLength;

    public DeltaHeader(int protoVersion, int appliedDeltaLength, int transformedDeltaLength) {
      this.protoVersion = protoVersion;
      this.appliedDeltaLength = appliedDeltaLength;
      this.transformedDeltaLength = transformedDeltaLength;
    }

    public void checkVersion() throws IOException {
      if (protoVersion != DELTA_FORMAT_VERSION) {
        throw new IOException("Invalid delta header");
      }
    }
  }

  /**
   * Opens a file delta collection.
   *
   * @param waveletName name of the wavelet to open
   * @param basePath base path of files
   * @return an open collection
   * @throws IOException
   */
  public static FileDeltaAccess open(WaveletName waveletName, String basePath)
      throws IOException {
    Timer timer = Timing.start("FileDeltaAccess.open");
    try {
      Preconditions.checkNotNull(waveletName, "null wavelet name");

      RandomAccessFile deltaFile = FileUtils.getOrCreateFile(deltasFile(basePath, waveletName));
      setOrCheckFileHeader(deltaFile);
      DeltaIndex index = new DeltaIndex(indexFile(basePath, waveletName));

      FileDeltaAccess collection = new FileDeltaAccess(waveletName, deltaFile, index);

      index.openForCollection(collection);
      collection.initializeEndVersionAndTruncateTrailingJunk();
      return collection;
    } finally {
      Timing.stop(timer);
    }
  }

  /**
   * Delete the delta files from disk.
   *
   * @throws PersistenceException
   */
  public static void delete(WaveletName waveletName, String basePath) throws PersistenceException {
    String error = "";
    File deltas = deltasFile(basePath, waveletName);

    if (deltas.exists()) {
      if (!deltas.delete()) {
        error += "Could not delete deltas file: " + deltas.getAbsolutePath() + ". ";
      }
    }

    File index = indexFile(basePath, waveletName);
    if (index.exists()) {
      if (!index.delete()) {
        error += "Could not delete index file: " + index.getAbsolutePath();
      }
    }
    if (!error.isEmpty()) {
      throw new PersistenceException(error);
    }
  }

  /**
   * Create a new file delta collection for the given wavelet.
   */
  public FileDeltaAccess(WaveletName waveletName, RandomAccessFile deltasFile,
      DeltaIndex deltasIndex) {
    this.waveletName = waveletName;
    this.deltasFile = deltasFile;
    this.deltasIndex = deltasIndex;
    this.isOpen = true;
    lifeCycle.start();
  }

  @Override
  public WaveletName getWaveletName() {
    return waveletName;
  }

  @Override
  public HashedVersion getLastModifiedVersion() {
    return lastModifiedVersion;
  }

  @Override
  public long getLastModifiedTime() {
    return lastModifiedTime;
  }

  @Override
  public synchronized WaveletDeltaRecord getDeltaByStartVersion(long version) throws IOException {
    lifeCycle.enter();
    try {
      checkIsOpen();
      return seekDeltasToRecordByStartVersion(version) ? readDeltaRecord() : null;
    } finally {
      lifeCycle.leave();
    }
  }

  @Override
  public synchronized WaveletDeltaRecord getDeltaByEndVersion(long version) throws IOException {
    lifeCycle.enter();
    try {
      checkIsOpen();
      return seekDeltasToRecordByEndVersion(version) ? readDeltaRecord() : null;
    } finally {
      lifeCycle.leave();
    }
  }

  @Override
  public synchronized WaveletDeltaRecord getDeltaByArbitraryVersion(long version) throws IOException {
    lifeCycle.enter();
    try {
      checkIsOpen();
      return seekDeltasToRecordByArbitraryVersion(version) ? readDeltaRecord() : null;
    } finally {
      lifeCycle.leave();
    }
  }

  @Override
  public synchronized void getDeltasFromVersion(long version,
      ThrowableReceiver<WaveletDeltaRecord, IOException> receiver) throws IOException {
    lifeCycle.enter();
    try {
      checkIsOpen();
      if (seekDeltasToRecordByArbitraryVersion(version)) {
        for (;;) {
          WaveletDeltaRecord delta = readDeltaRecord();
          if (delta == null) {
            break;
          }
          if (!receiver.put(delta)) {
            break;
          }
        }
      }
    } finally {
      lifeCycle.leave();
    }
  }

  @Override
  public synchronized void close() throws IOException {
    lifeCycle.enter();
    try {
      deltasFile.close();
      deltasIndex.close();
      lastModifiedVersion = null;
      lastModifiedTime = 0;
      isOpen = false;
    } finally {
      lifeCycle.leave();
    }
  }

  @Override
  public synchronized void append(Collection<WaveletDeltaRecord> deltas) throws PersistenceException {
    lifeCycle.enter();
    Timer timer = Timing.start("FileDeltaAccess.append");
    try {
      checkIsOpen();
      try {
        deltasFile.seek(deltasFile.length());

        WaveletDeltaRecord lastDelta = null;
        for (WaveletDeltaRecord delta : deltas) {
          deltasIndex.addDelta(delta.getTransformedDelta().getAppliedAtVersion(), delta.getTransformedDelta().size(),
              deltasFile.getFilePointer());
          writeDelta(delta);
          lastDelta = delta;
        }

        // fsync() before returning.
        deltasFile.getChannel().force(true);
        lastModifiedVersion = lastDelta.getTransformedDelta().getResultingVersion();
        lastModifiedTime = lastDelta.getTransformedDelta().getApplicationTimestamp();
      } catch (IOException e) {
        throw new PersistenceException(e);
      }
    } finally {
      Timing.stop(timer);
      lifeCycle.leave();
    }
  }

  @Override
  public synchronized boolean isEmpty() {
    checkIsOpen();
    return deltasIndex.length() == 0;
  }

  /**
   * Creates a new iterator to move over the positions of the deltas in the file.
   *
   * Each pair returned is ((version, numOperations), offset).
   * @throws IOException
   */
  Iterable<Pair<Pair<Long,Integer>, Long>> getOffsetsIterator() throws IOException {
    checkIsOpen();

    return new Iterable<Pair<Pair<Long, Integer>, Long>>() {
      @Override
      public Iterator<Pair<Pair<Long, Integer>, Long>> iterator() {
        return new Iterator<Pair<Pair<Long, Integer>, Long>>() {
          Pair<Pair<Long, Integer>, Long> nextRecord;
          long nextPosition = FILE_HEADER_LENGTH;

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }

          @Override
          public Pair<Pair<Long, Integer>, Long> next() {
            Pair<Pair<Long, Integer>, Long> record = nextRecord;
            nextRecord = null;
            return record;
          }

          @Override
          public boolean hasNext() {
            // We're using hasNext to prime the next call to next(). This works because in practice
            // any call to next() is preceeded by at least one call to hasNext().
            // We need to actually read the record here because hasNext() should return false
            // if there's any incomplete data at the end of the file.
            try {
              if (deltasFile.length() <= nextPosition) {
                // End of file.
                return false;
              }
            } catch (IOException e) {
              throw new RuntimeException("Could not get file position", e);
            }

            if (nextRecord == null) {
              // Read the next record
              try {
                deltasFile.seek(nextPosition);
                TransformedWaveletDelta transformed = readTransformedDeltaFromRecord();
                nextRecord = Pair.of(Pair.of(transformed.getAppliedAtVersion(),
                        transformed.size()), nextPosition);
                nextPosition = deltasFile.getFilePointer();
              } catch (IOException e) {
                // The next entry is invalid. There was probably a write error / crash.
                LOG.severe("Error reading delta file for " + waveletName + " starting at " +
                    nextPosition, e);
                return false;
              }
            }

            return true;
          }
        };
      }
    };
  }

  @VisibleForTesting
  static File deltasFile(String basePath, WaveletName waveletName) {
    String waveletPathPrefix = FileUtils.waveletNameToPathSegment(waveletName);
    return new File(basePath, waveletPathPrefix + DELTAS_FILE_SUFFIX);
  }

  @VisibleForTesting
  static File indexFile(String basePath, WaveletName waveletName) {
    String waveletPathPrefix = FileUtils.waveletNameToPathSegment(waveletName);
    return new File(basePath, waveletPathPrefix + INDEX_FILE_SUFFIX);
  }

  /**
   * Checks that a file has a valid deltas header, adding the header if the
   * file is shorter than the header.
   */
  private static void setOrCheckFileHeader(RandomAccessFile file) throws IOException {
    Timer timer = Timing.start("FileDeltaAccess.setOrCheckFileHeader");
    try {
      Preconditions.checkNotNull(file);
      file.seek(0);

      if (file.length() < FILE_HEADER_LENGTH) {
        // The file is new. Insert a header.
        file.write(FILE_MAGIC_BYTES);
        file.writeInt(FILE_PROTOCOL_VERSION);
      } else {
        byte[] magic = new byte[4];
        file.readFully(magic);
        if (!Arrays.equals(FILE_MAGIC_BYTES, magic)) {
          throw new IOException("Delta file magic bytes are incorrect");
        }

        int version = file.readInt();
        if (version != FILE_PROTOCOL_VERSION) {
          throw new IOException(String.format("File protocol version mismatch - expected %d got %d",
              FILE_PROTOCOL_VERSION, version));
        }
      }
    } finally {
      Timing.stop(timer);
    }
  }

  private void checkIsOpen() {
    Preconditions.checkState(isOpen, "Delta collection closed");
  }

  /**
   * Seek to the start of a delta record. Returns false if the record doesn't exist.
   */
  private boolean seekDeltasToRecordByStartVersion(long version) throws IOException {
    Timer timer = Timing.start("FileDeltaAccess.seekDeltasToRecordByStartVersion");
    try {
      Preconditions.checkArgument(version >= 0, "Version can't be negative");
      long offset = deltasIndex.getOffsetForVersion(version);
      return seekDeltasTo(offset);
    } finally {
      Timing.stop(timer);
    }
  }

  /**
   * Seek to the start of a delta record given its end version.
   * Returns false if the record doesn't exist.
   */
  private boolean seekDeltasToRecordByEndVersion(long version) throws IOException {
    Timer timer = Timing.start("FileDeltaAccess.seekDeltasToRecordByEndVersion");
    try {
      Preconditions.checkArgument(version >= 0, "Version can't be negative");
      long offset = deltasIndex.getOffsetForEndVersion(version);
      return seekDeltasTo(offset);
    } finally {
      Timing.stop(timer);
    }
  }

  /**
   * Seek to the start of a delta record given its start or middle version.
   * Returns false if the record doesn't exist.
   */
  private boolean seekDeltasToRecordByArbitraryVersion(long version) throws IOException {
    Timer timer = Timing.start("FileDeltaAccess.seekDeltasToRecordByArbitraryVersion");
    try {
      Preconditions.checkArgument(version >= 0, "Version can't be negative");
      long offset = deltasIndex.getOffsetForArbitraryVersion(version);
      return seekDeltasTo(offset);
    } finally {
      Timing.stop(timer);
    }
  }

  private boolean seekDeltasTo(long offset) throws IOException {
    Timer timer = Timing.start("FileDeltaAccess.seekDeltasTo");
    try {
      if (offset == DeltaIndex.NO_RECORD_FOR_VERSION) {
        // There's no record for the specified version.
        return false;
      } else {
        deltasFile.seek(offset);
        return true;
      }
    } finally {
      Timing.stop(timer);
    }
  }

  /**
   * Read a record and return it.
   */
  private WaveletDeltaRecord readDeltaRecord() throws IOException {
    Timer timer = Timing.start("FileDeltaAccess.readDeltaRecord");
    try {
      if (deltasFile.length() == deltasFile.getFilePointer()) {
        return null;
      }

      DeltaHeader header = readDeltaHeader();

      ByteStringMessage<ProtocolAppliedWaveletDelta> appliedDelta =
          readAppliedDelta(header.appliedDeltaLength);
      TransformedWaveletDelta transformedDelta = readTransformedWaveletDelta(
          header.transformedDeltaLength);

      return new WaveletDeltaRecord(AppliedDeltaUtil.getHashedVersionAppliedAt(appliedDelta),
          appliedDelta, transformedDelta);
    } finally {
      Timing.stop(timer);
    }
  }

  /**
   * Reads a record, and only parses & returns the transformed data field.
   */
  private TransformedWaveletDelta readTransformedDeltaFromRecord() throws IOException {
    Timer timer = Timing.start("FileDeltaAccess.readTransformedDeltaFromRecord");
    try {
      DeltaHeader header = readDeltaHeader();

      deltasFile.skipBytes(header.appliedDeltaLength);
      TransformedWaveletDelta transformedDelta = readTransformedWaveletDelta(
          header.transformedDeltaLength);

      return transformedDelta;
    } finally {
      Timing.stop(timer);
    }
  }

  // *************** Low level data reading methods for deltas

  /** Read a header from the file. Does not move the file pointer before reading. */
  private DeltaHeader readDeltaHeader() throws IOException {
    int version = deltasFile.readInt();
    if (version != DELTA_FORMAT_VERSION) {
      throw new IOException("Delta header invalid");
    }
    int appliedDeltaLength = deltasFile.readInt();
    int transformedDeltaLength = deltasFile.readInt();
    DeltaHeader deltaHeader = new DeltaHeader(version, appliedDeltaLength, transformedDeltaLength);
    deltaHeader.checkVersion();
    // Verify the file size.
    long remaining = deltasFile.length() - deltasFile.getFilePointer();
    long missing = (appliedDeltaLength + transformedDeltaLength) - remaining;
    if (missing > 0) {
      throw new IOException("File is corrupted, missing " + missing + " bytes");
    }
    return deltaHeader;
  }

  /**
   * Write a header to the current location in the file
   */
  private void writeDeltaHeader(DeltaHeader header) throws IOException {
    Timer timer = Timing.start("FileDeltaAccess.writeDeltaHeader");
    try {
      deltasFile.writeInt(header.protoVersion);
      deltasFile.writeInt(header.appliedDeltaLength);
      deltasFile.writeInt(header.transformedDeltaLength);
    } finally {
      Timing.stop(timer);
    }
  }

  /**
   * Read the applied delta at the current file position. After method call,
   * file position is directly after applied delta field.
   */
  private ByteStringMessage<ProtocolAppliedWaveletDelta> readAppliedDelta(int length)
      throws IOException {
    if (length == 0) {
      return null;
    }

    byte[] bytes = new byte[length];
    deltasFile.readFully(bytes);
    try {
      return ByteStringMessage.parseProtocolAppliedWaveletDelta(ByteString.copyFrom(bytes));
    } catch (InvalidProtocolBufferException e) {
      throw new IOException(e);
    }
  }

  /**
   * Write an applied delta to the current position in the file. Returns number of bytes written.
   */
  private int writeAppliedDelta(ByteStringMessage<ProtocolAppliedWaveletDelta> delta)
      throws IOException {
    Timer timer = Timing.start("FileDeltaAccess.writeAppliedDelta");
    try {
      if (delta != null) {
        byte[] bytes = delta.getByteArray();
        deltasFile.write(bytes);
        return bytes.length;
      } else {
        return 0;
      }
    } finally {
      Timing.stop(timer);
    }
  }

  /**
   * Read a {@link TransformedWaveletDelta} from the current location in the file.
   */
  private TransformedWaveletDelta readTransformedWaveletDelta(int transformedDeltaLength)
      throws IOException {
    if(transformedDeltaLength < 0) {
      throw new IOException("Invalid delta length");
    }

    byte[] bytes = new byte[transformedDeltaLength];
    deltasFile.readFully(bytes);
    Timer parsingTimer = Timing.start("Parse delta");
    try {
      ProtoTransformedWaveletDelta delta;
      try {
        delta = ProtoTransformedWaveletDelta.parseFrom(bytes);
      } catch (InvalidProtocolBufferException e) {
        throw new IOException(e);
      }
      return ProtoDeltaStoreDataSerializer.deserialize(delta);
    } finally {
      Timing.stop(parsingTimer);
    }
  }

  /**
   * Write a {@link TransformedWaveletDelta} to the file at the current location.
   * @return length of written data
   */
  private int writeTransformedWaveletDelta(TransformedWaveletDelta delta) throws IOException {
    Timer timer = Timing.start("FileDeltaAccess.writeTransformedWaveletDelta");
    try {
      long startingPosition = deltasFile.getFilePointer();
      ProtoTransformedWaveletDelta protoDelta = ProtoDeltaStoreDataSerializer.serialize(delta);
      OutputStream stream = Channels.newOutputStream(deltasFile.getChannel());
      protoDelta.writeTo(stream);
      return (int) (deltasFile.getFilePointer() - startingPosition);
    } finally {
      Timing.stop(timer);
    }
  }

  /**
   * Read a delta to the file. Does not move the file pointer before writing. Returns number of
   * bytes written.
   */
  private long writeDelta(WaveletDeltaRecord delta) throws IOException {
    Timer timer = Timing.start("FileDeltaAccess.writeTransformedWaveletDelta");
    try {
      // We'll write zeros in place of the header and come back & write it at the end.
      long headerPointer = deltasFile.getFilePointer();
      deltasFile.write(new byte[DeltaHeader.HEADER_LENGTH]);

      int appliedLength = writeAppliedDelta(delta.getAppliedDelta());
      int transformedLength = writeTransformedWaveletDelta(delta.getTransformedDelta());

      long endPointer = deltasFile.getFilePointer();
      deltasFile.seek(headerPointer);
      writeDeltaHeader(new DeltaHeader(DELTA_FORMAT_VERSION, appliedLength, transformedLength));
      deltasFile.seek(endPointer);

      return endPointer - headerPointer;
    } finally {
      Timing.stop(timer);
    }
  }

  /**
   * Reads the last complete record in the deltas file and truncates any trailing junk.
   */
  private void initializeEndVersionAndTruncateTrailingJunk() throws IOException {
    Timer timer = Timing.start("FileDeltaAccess.initializeEndVersionAndTruncateTrailingJunk");
    try {
      long numRecords = deltasIndex.length();
      lastModifiedVersion = null;
      lastModifiedTime = 0;
      while (numRecords > 0) {
        try {
          WaveletDeltaRecord delta = getDeltaByEndVersion(numRecords);
          if (delta != null) {
            lastModifiedVersion = delta.getResultingVersion();
            lastModifiedTime = delta.getApplicationTimestamp();
            break;
          }
        } catch (IOException ex) {
          LOG.severe("Open wavelet", ex);
        }
        if (lastModifiedVersion == null) {
          deltasIndex.rebuildIndexFromDeltas(this);
          numRecords = deltasIndex.length();
        }
      }
      // The file's position should be at the end. Truncate any
      // trailing junk such as from a partially completed write.
      deltasFile.setLength(deltasFile.getFilePointer());
    } finally {
      Timing.stop(timer);
    }
  }
}