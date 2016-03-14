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

package org.waveprotocol.box.server.waveletstate.delta;

import org.waveprotocol.box.server.executor.ExecutorAnnotations;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.deltas.DeltaStore;
import org.waveprotocol.box.server.persistence.deltas.DeltaStore.DeltaAccess;
import org.waveprotocol.box.server.persistence.deltas.WaveletDeltaRecord;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.box.server.waveserver.ByteStringMessage;
import org.waveprotocol.box.server.waveserver.RuntimeIOException;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.box.common.ThrowableReceiver;
import org.waveprotocol.box.stat.Timed;
import org.waveprotocol.box.stat.ThrowableTimingReceiver;
import org.waveprotocol.wave.federation.Proto.ProtocolAppliedWaveletDelta;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionFactoryImpl;
import org.waveprotocol.wave.model.wave.data.WaveletData;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;
import org.waveprotocol.wave.util.logging.Log;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import java.io.IOException;

import static java.lang.String.format;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Simplistic {@link DeltaStore}-backed wavelet state implementation
 * which goes to persistent storage for every history request.
 *
 * @author soren@google.com (Soren Lassen)
 * @author akaplanov@gmail.com (Andew Kaplanov)
 */
public class DeltaWaveletStateImpl implements DeltaWaveletState {
  private static final Log LOG = Log.get(DeltaWaveletState.class);

  private static final IdURIEncoderDecoder URI_CODEC = new IdURIEncoderDecoder(new JavaUrlCodec());
  private static final HashedVersionFactory HASH_FACTORY = new HashedVersionFactoryImpl(URI_CODEC);

  private HashedVersion versionZero;

  /** Executor for writing deltas and closing store. */
  private final Executor persistExecutor;

  /** The name of wavelet. */
  private final WaveletName waveletName;

  /** Delta store. */
  private final DeltaStore deltaStore;

  /** Delta access. */
  private DeltaStore.DeltaAccess deltaAccess;

  /**
   * Indicates the version of the latest appended delta that was already requested to be
   * persisted.
   */
  private HashedVersion latestVersionToPersist = null;

  /** The persist task that will be executed next. */
  private ListenableFutureTask<Void> nextPersistTask = null;

  /** The persist task that will be executed when closing. */
  private ListenableFutureTask<Void> closingFutureTask = null;

  /** Closing is deferred because a persist task in flight. */
  private boolean deferredClosing = false;

  /**
   * Processes the persist task and checks if there is another task to do when
   * one task is done. In such a case, it writes all waiting to be persisted
   * deltas to persistent storage in one operation.
   */
  private final Callable<Void> persisterTask = new Callable<Void>() {
    @Override
    public Void call() throws PersistenceException {
      HashedVersion last;
      HashedVersion version;
      checkOpened();
      readLock.lock();
      try {
        last = lastPersistedVersion;
        version = latestVersionToPersist;
      } finally {
        readLock.unlock();
      }
      if (last != null && version.getVersion() <= last.getVersion()) {
        LOG.info("Attempt to persist version " + version
            + " smaller than last persisted version " + last);
        // Done, version is already persisted.
        version = last;
      } else {
        ImmutableList.Builder<WaveletDeltaRecord> deltasBuilder = ImmutableList.builder();
        HashedVersion v = (last == null) ? versionZero : last;
        do {
          WaveletDeltaRecord d = cachedDeltas.get(v);
          deltasBuilder.add(d);
          v = d.getResultingVersion();
        } while (v.getVersion() < version.getVersion());
        Preconditions.checkState(v.equals(version));
        ImmutableList<WaveletDeltaRecord> deltas = deltasBuilder.build();
        deltaAccess.append(deltas);
      }
      writeLock.lock();
      try {
        Preconditions.checkState(last == lastPersistedVersion,
            "lastPersistedVersion changed while we were writing to storage");
        lastPersistedVersion = version;
        if (nextPersistTask != null) {
          persistExecutor.execute(nextPersistTask);
          nextPersistTask = null;
        } else {
          latestVersionToPersist = null;
          if (deferredClosing) {
            persistExecutor.execute(closingFutureTask);
            deferredClosing = false;
          }
        }
      } finally {
        writeLock.unlock();
      }
      return null;
    }
  };

  /** Closes store. */
  private final Callable<Void> closingTask = new Callable<Void>() {
    @Override
    public Void call() throws IOException {
      writeLock.lock();
      try {
        Preconditions.checkNotNull(deltaAccess, "Store is not opened.");
        deltaAccess.close();
        deltaAccess = null;
        return null;
      } finally {
        writeLock.unlock();
      }
    }
  };

  /** Keyed by appliedAtVersion. */
  private final ConcurrentNavigableMap<HashedVersion, WaveletDeltaRecord> cachedDeltas =
      new ConcurrentSkipListMap<>();

  /** Current version of wavelet. */
  private volatile HashedVersion lastModifiedVersion;

  /** Current timestamp of wavelet. */
  private volatile long lastModifiedTime;

  /**
   * Last version persisted with a call to persist(), or null if never called.
   */
  private HashedVersion lastPersistedVersion;

  /** Locks. */
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
  private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

  /**
   * Constructs a wavelet state with the given deltas and snapshot.
   * The deltas must be the contents of deltasAccess, and they
   * must be contiguous from version zero.
   * The snapshot must be the composition of the deltas, or null if there
   * are no deltas. The constructed object takes ownership of the
   * snapshot and will mutate it if appendDelta() is called.
   */
  @Inject
  DeltaWaveletStateImpl(DeltaStore deltaStore, @ExecutorAnnotations.DeltaPersistExecutor Executor persistExecutor,
      @Assisted WaveletName waveletName) {
    this.deltaStore = deltaStore;
    this.persistExecutor = persistExecutor;
    this.waveletName = waveletName;
  }

  /**
   * Opens a delta store based state.
   *
   * The executor must ensure that only one thread executes at any time for each
   * state instance.
   */
  @Timed
  public void open() throws WaveletStateException {
    writeLock.lock();
    try {
      Preconditions.checkArgument(deltaAccess == null, "Delta state already opened");
      versionZero = HASH_FACTORY.createVersionZero(waveletName);
      try {
        deltaAccess = deltaStore.open(waveletName);
      } catch (PersistenceException ex) {
        throw new WaveletStateException(ex);
      }
      HashedVersion endVersion = deltaAccess.getLastModifiedVersion();
      if (endVersion != null) {
        lastModifiedVersion = endVersion;
        lastModifiedTime = deltaAccess.getLastModifiedTime();
      } else {
        lastModifiedVersion = versionZero;
      }
      lastPersistedVersion = deltaAccess.getLastModifiedVersion();
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public ListenableFuture close() {
    checkOpened();
    writeLock.lock();
    try {
      closingFutureTask = ListenableFutureTask.<Void>create(closingTask);
      if (latestVersionToPersist != null) {
        deferredClosing = true;
      } else {
        persistExecutor.execute(closingFutureTask);
      }
      return closingFutureTask;
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public ListenableFuture flush() {
    throw new UnsupportedOperationException();
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
  public Pair<HashedVersion, Long> getLastModifiedVersionAndTime() {
    readLock.lock();
    try {
      return Pair.of(getLastModifiedVersion(), getLastModifiedTime());
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public HashedVersion getLastPersistedVersion() {
    readLock.lock();
    try {
      checkOpened();
      HashedVersion version = lastPersistedVersion;
      return (version == null) ? versionZero : version;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public HashedVersion getHashedVersion(long version) {
    checkOpened();
    Entry<HashedVersion, WaveletDeltaRecord> cachedEntry = lookupCached(cachedDeltas, version);
    if (version == 0) {
      return versionZero;
    }
    HashedVersion v = getLastModifiedVersion();
    if (version == v.getVersion()) {
      return v;
    }
    WaveletDeltaRecord delta;
    try {
      delta = lookup(version);
    } catch (IOException e) {
      throw new RuntimeIOException(new IOException(format("Version : %d", version), e));
    }
    if (delta == null && cachedEntry != null) {
      return cachedEntry.getKey();
    } else {
     return delta != null ? delta.getAppliedAtVersion() : null;
    }
  }

  @Override
  public HashedVersion getNearestHashedVersion(long version) {
    checkOpened();
    if (version == 0) {
      return versionZero;
    }
    Entry<HashedVersion, WaveletDeltaRecord> cachedEntry = lookupCached(cachedDeltas, version);
    WaveletDeltaRecord delta;
    try {
      delta = lookupNearest(version);
    } catch (IOException e) {
      throw new RuntimeIOException(new IOException(format("Version : %d", version), e));
    }
    if (delta == null && cachedEntry != null) {
      return cachedEntry.getKey();
    } else {
     return delta != null ? delta.getAppliedAtVersion() : null;
    }
  }

  @Override
  public TransformedWaveletDelta getTransformedDelta(final HashedVersion beginVersion) {
    checkOpened();
    WaveletDeltaRecord delta = cachedDeltas.get(beginVersion);
    if (delta != null) {
      return delta.getTransformedDelta();
    }
    WaveletDeltaRecord nowDelta;
    try {
      nowDelta = lookup(beginVersion.getVersion());
    } catch (IOException e) {
      throw new RuntimeIOException(new IOException(format("Begin version : %s",
          beginVersion.toString()), e));
    }
    return nowDelta != null ? nowDelta.getTransformedDelta() : null;
  }

  @Override
  public TransformedWaveletDelta getTransformedDeltaByEndVersion(final HashedVersion endVersion) {
    checkOpened();
    Preconditions.checkArgument(endVersion.getVersion() > 0, "end version %s is not positive",
        endVersion);
    Entry<HashedVersion, WaveletDeltaRecord> transformedEntry = cachedDeltas.lowerEntry(endVersion);
    WaveletDeltaRecord cachedDelta = transformedEntry != null ? transformedEntry.getValue() : null;
    WaveletDeltaRecord deltaRecord = getDeltaRecordByEndVersion(endVersion);
    TransformedWaveletDelta delta;
    if (deltaRecord == null && cachedDelta != null
        && cachedDelta.getResultingVersion().equals(endVersion)) {
      delta = cachedDelta.getTransformedDelta();
    } else {
      delta = deltaRecord != null ? deltaRecord.getTransformedDelta() : null;
    }
    return delta;
  }

  @Override
  public ByteStringMessage<ProtocolAppliedWaveletDelta> getAppliedDelta(
      HashedVersion beginVersion) {
    checkOpened();
    WaveletDeltaRecord delta = cachedDeltas.get(beginVersion);
    if (delta != null) {
      return delta.getAppliedDelta();
    }
    WaveletDeltaRecord record = null;
    try {
      record = lookup(beginVersion.getVersion());
    } catch (IOException e) {
      throw new RuntimeIOException(new IOException(format("Begin version : %s",
          beginVersion.toString()), e));
    }
    return record != null ? record.getAppliedDelta() : null;
  }

  @Override
  public ByteStringMessage<ProtocolAppliedWaveletDelta> getAppliedDeltaByEndVersion(
      final HashedVersion endVersion) {
    checkOpened();
    Preconditions.checkArgument(endVersion.getVersion() > 0,
        "end version %s is not positive", endVersion);
    Entry<HashedVersion, WaveletDeltaRecord> appliedEntry = cachedDeltas.lowerEntry(endVersion);
    final ByteStringMessage<ProtocolAppliedWaveletDelta> cachedDelta =
        appliedEntry != null ? appliedEntry.getValue().getAppliedDelta() : null;
    WaveletDeltaRecord deltaRecord = getDeltaRecordByEndVersion(endVersion);
    ByteStringMessage<ProtocolAppliedWaveletDelta> appliedDelta;
    if (deltaRecord == null && isDeltaBoundary(endVersion)) {
      appliedDelta = cachedDelta;
    } else {
      appliedDelta = deltaRecord != null ? deltaRecord.getAppliedDelta() : null;
    }
    return appliedDelta;
  }

  @Override
  public void getDeltaHistory(HashedVersion startVersion, HashedVersion endVersion,
      final ThrowableReceiver<WaveletDeltaRecord, WaveletStateException> receiver) throws WaveletStateException {
    checkOpened();
    readDeltasInRange(startVersion, endVersion, new ThrowableReceiver<WaveletDeltaRecord, WaveletStateException>() {

      @Override
      public boolean put(WaveletDeltaRecord record) throws WaveletStateException {
        return receiver.put(record);
      }
    });
  }

  @Override
  public WaveletDeltaRecord getDeltaRecord(HashedVersion beginVersion) {
    checkOpened();
    WaveletDeltaRecord delta = cachedDeltas.get(beginVersion);
    if (delta != null) {
      return delta;
    } else {
      WaveletDeltaRecord record = null;
      try {
        record = lookup(beginVersion.getVersion());
      } catch (IOException e) {
        throw new RuntimeIOException(new IOException(format("Begin version : %s",
            beginVersion.toString()), e));
      }
      return record;
    }
  }

  @Override
  public WaveletDeltaRecord getDeltaRecordByEndVersion(HashedVersion endVersion) {
    checkOpened();
    long version = endVersion.getVersion();
    try {
      return deltaAccess.getDeltaByEndVersion(version);
    } catch (IOException e) {
      throw new RuntimeIOException(new IOException(format("Version : %d", version), e));
    }
  }

  @Override
  public DeltaAccess getDeltaAccess() {
    return deltaAccess;
  }

  @Override
  public void appendDelta(WaveletDeltaRecord deltaRecord) {
    checkOpened();
    writeLock.lock();
    try {
      Preconditions.checkArgument(lastModifiedVersion.equals(deltaRecord.getAppliedAtVersion()),
          "Applied version %s doesn't match current version %s", deltaRecord.getAppliedAtVersion(),
          lastModifiedVersion);

      cachedDeltas.put(deltaRecord.getAppliedAtVersion(), deltaRecord);
      lastModifiedVersion = deltaRecord.getResultingVersion();
      lastModifiedTime = deltaRecord.getApplicationTimestamp();
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public ListenableFuture<Void> persist(final HashedVersion version) {
    checkOpened();
    Preconditions.checkArgument(version.getVersion() > 0,
        "Cannot persist non-positive version %s", version);
    Preconditions.checkArgument(isDeltaBoundary(version),
        "Version to persist %s matches no delta", version);
    writeLock.lock();
    try {
      if (latestVersionToPersist != null) {
        // There's a persist task in flight.
        if (version.getVersion() <= latestVersionToPersist.getVersion()) {
          LOG.info("Attempt to persist version " + version
              + " smaller than last version requested " + latestVersionToPersist);
        } else {
          latestVersionToPersist = version;
        }
        if (nextPersistTask == null) {
          nextPersistTask = ListenableFutureTask.<Void>create(persisterTask);
        }
        return nextPersistTask;
      } else {
        latestVersionToPersist = version;
        ListenableFutureTask<Void> resultTask = ListenableFutureTask.<Void>create(persisterTask);
        persistExecutor.execute(resultTask);
        return resultTask;
      }
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public void flush(HashedVersion version) {
    checkOpened();
    cachedDeltas.remove(cachedDeltas.lowerKey(version));
    if (LOG.isFineLoggable()) {
      LOG.fine("Flushed deltas up to version " + version);
    }
  }

  @Override
  public void clear() throws WaveletStateException {
    throw new UnsupportedOperationException();
  }

  /**
   * @return An entry keyed by a hashed version with the given version number,
   *         if any, otherwise null.
   */
  static <T> Map.Entry<HashedVersion, T> lookupCached(NavigableMap<HashedVersion, T> map,
      long version) {
    // Smallest key with version number >= version.
    HashedVersion key = HashedVersion.unsigned(version);
    Map.Entry<HashedVersion, T> entry = map.ceilingEntry(key);
    return (entry != null && entry.getKey().getVersion() == version) ? entry : null;
  }

  /**
   * Constructs wavelet of specified version from deltas.
   */
  @Timed
  WaveletData createWaveletFromDeltas(HashedVersion endVersion) throws WaveletStateException {
    checkOpened();
    HashedVersion startVersion = HASH_FACTORY.createVersionZero(deltaAccess.getWaveletName());
    final AtomicReference<WaveletData> wavelet = new AtomicReference<WaveletData>();
    readDeltasInRange(startVersion, endVersion,
        new ThrowableTimingReceiver<WaveletDeltaRecord, WaveletStateException>().init(
          new ThrowableReceiver<WaveletDeltaRecord, WaveletStateException>() {
      @Override
      public boolean put(WaveletDeltaRecord delta) throws WaveletStateException {
        try {
          if (wavelet.get() == null) {
            wavelet.set(WaveletDataUtil.buildWaveletFromFirstDelta(deltaAccess.getWaveletName(),
                delta.getTransformedDelta()));
          } else {
            WaveletDataUtil.applyWaveletDelta(delta.getTransformedDelta(), wavelet.get());
          }
          return true;
        } catch (OperationException ex) {
          throw new WaveletStateException(ex);
        }
      }
    }, "applyDeltas"));
    return wavelet.get();
  }

  void readDeltasInRange(HashedVersion startVersion, final HashedVersion endVersion,
      final ThrowableReceiver<WaveletDeltaRecord, WaveletStateException> receiver) throws WaveletStateException {
    Preconditions.checkArgument(startVersion.getVersion() < endVersion.getVersion());
    final HashedVersion nextVersion[] = { startVersion };
    final boolean continued[] = { true };
    try {
      deltaAccess.getDeltasFromVersion(startVersion.getVersion(),
        new ThrowableReceiver<WaveletDeltaRecord, IOException>() {

          @Override
          public boolean put(WaveletDeltaRecord delta) throws IOException {
            Preconditions.checkArgument(delta != null && delta.getAppliedAtVersion().equals(nextVersion[0]),
              "invalid start version");
            try {
              if (!receiver.put(delta)) {
                return continued[0] = false;
              }
            } catch (WaveletStateException ex) {
              throw new IOException(ex);
            }
            nextVersion[0] = delta.getResultingVersion();
            if (delta.getResultingVersion().getVersion() >= endVersion.getVersion()) {
              return false;
            }
            return true;
          }
        });
      if (continued[0]) {
        while (nextVersion[0].getVersion() < endVersion.getVersion()) {
          WaveletDeltaRecord delta = cachedDeltas.get(nextVersion[0]);
          if (delta == null) {
            break;
          }
          if (!receiver.put(delta)) {
            return;
          }
          nextVersion[0] = delta.getResultingVersion();
        }
        Preconditions.checkArgument(nextVersion[0].equals(endVersion), "invalid end version");
      }
    } catch (IOException ex) {
      throw new WaveletStateException(ex);
    }
  }

  /**
   * @return An entry keyed by a hashed version with the given version number,
   *         if any, otherwise null.
   */
  @Timed
  WaveletDeltaRecord lookup(long version) throws IOException {
    return deltaAccess.getDeltaByStartVersion(version);
  }

  @Timed
  WaveletDeltaRecord lookupNearest(long version) throws IOException {
    return deltaAccess.getDeltaByArbitraryVersion(version);
  }

  private boolean isDeltaBoundary(HashedVersion version) {
    Preconditions.checkNotNull(version, "version is null");
    return version.equals(getLastModifiedVersion()) || cachedDeltas.containsKey(version);
  }

  private void checkOpened() {
    readLock.lock();
    try {
      Preconditions.checkArgument(deltaAccess != null && closingFutureTask == null,
        "Not opened or closing");
    } finally {
      readLock.unlock();
    }
  }
}