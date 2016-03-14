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
import com.google.protobuf.InvalidProtocolBufferException;

import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.blocks.Block;
import org.waveprotocol.box.server.persistence.blocks.BlockStore.BlockAccess;
import org.waveprotocol.box.server.persistence.protos.ProtoBlockIndex;
import org.waveprotocol.box.server.shutdown.LifeCycle;
import org.waveprotocol.box.server.shutdown.ShutdownPriority;
import org.waveprotocol.box.server.shutdown.Shutdownable;
import org.waveprotocol.box.server.persistence.blocks.BlockIndex;
import org.waveprotocol.box.server.persistence.blocks.impl.BlockImpl;
import org.waveprotocol.box.server.persistence.blocks.impl.BlockIndexImpl;
import org.waveprotocol.box.server.persistence.blocks.BlockIndex;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.util.logging.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Access to file block store.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class FileBlockAccess implements BlockAccess {
  private static final Log LOG = Log.get(FileBlockAccess.class);

  /** Suffix of block file name. */
  public static final String BLOCK_FILE_SUFFIX = ".block";

  /** Index for segments. */
  public static final String BLOCK_INDEX_FILE = "block.index";

  /** Name of wavelet. */
  private final WaveletName waveletName;

  /** Store directory. */
  private final String basePath;

  final private LifeCycle lifeCycle = new LifeCycle(FileDeltaAccess.class.getSimpleName(), ShutdownPriority.Storage,
      new Shutdownable() {
    @Override
    public void shutdown() throws Exception {
      close();
    }
  });

  FileBlockAccess(WaveletName waveletName, String basePath) throws PersistenceException {
    this.waveletName = waveletName;
    this.basePath = basePath;
    lifeCycle.start();
    String waveletDir = waveletDir(basePath, waveletName).toString();
    FileUtils.createDirIfNotExists(waveletDir, "blocks");
  }

  @Override
  public WaveletName getWaveletName() {
    return waveletName;
  }

  @Override
  public void close() throws IOException {
  }

  @Override
  public BlockIndex readBlockIndex() throws PersistenceException {
    lifeCycle.enter();
    Timer timer = Timing.start("FileBlockAccess.readBlockIndex");
    try {
      File indexFile = blockIndexFile(basePath, waveletName);
      if (indexFile.exists()) {
        ProtoBlockIndex.BlockIndex lastModifiedIndex = null;
        InputStream in = null;
        try {
          in = new FileInputStream(indexFile);
          lastModifiedIndex = ProtoBlockIndex.BlockIndex.parseFrom(in);
        } catch (InvalidProtocolBufferException ex) {
          LOG.warning("Format of index file is incompatible", ex);
        } catch (IOException ex) {
          throw new PersistenceException(ex);
        } finally {
          closeInput(in);
        }
        return new BlockIndexImpl(lastModifiedIndex);
      }
      return new BlockIndexImpl();
    } finally {
      Timing.stop(timer);
      lifeCycle.leave();
    }
  }

  @Override
  public synchronized Collection<Block> readBlocks(Set<String> blockIds) throws PersistenceException {
    lifeCycle.enter();
    try {
      List<Block> result = CollectionUtils.newLinkedList();
      for (String blockId : blockIds) {
        Block block = readBlockFile(blockId);
        result.add(block);
      }
      return result;
    } finally {
      lifeCycle.leave();
    }
  }

  @Override
  public synchronized void writeBlock(Block block) throws PersistenceException {
    lifeCycle.enter();
    Timer timer = Timing.start("FileBlockAccess.writeBlock");
    try {
      LOG.info("Writing block " + block.getBlockId() + " : wavelet " + waveletName.toString());
      writeBlockFile(block);
    } finally {
      Timing.stop(timer);
      lifeCycle.leave();
    }
  }

  @Override
  public synchronized void writeBlockIndex(BlockIndex segmentsIndex) throws PersistenceException {
    lifeCycle.enter();
    Timer timer = Timing.start("FileBlockAccess.writeBlockIndex");
    try {
      OutputStream out = null;
      try {
        out = new FileOutputStream(blockIndexFile(basePath, waveletName));
        segmentsIndex.serialize().writeTo(out);
      } catch (IOException ex) {
        throw new PersistenceException(ex);
      } finally {
        closeOutput(out);
      }
    } finally {
      Timing.stop(timer);
      lifeCycle.leave();
    }
  }

  public synchronized static void delete(WaveletName waveletName, String basePath) throws PersistenceException {
    String[] files = waveletDir(basePath, waveletName).list();
    for (String file : files) {
      new File(waveletDir(basePath, waveletName), file).delete();
    }
    waveletDir(basePath, waveletName).delete();
  }

  private Block readBlockFile(String blockId) throws PersistenceException {
    LOG.fine("Reading block " + blockId + " ...");
    Timer timer = Timing.start("FileBlockAccess.readBlockFile");
    try {
      InputStream in = null;
      try {
        in = new FileInputStream(blockFile(basePath, waveletName, blockId));
        return BlockImpl.deserialize(in);
      } catch (IOException ex) {
        throw new PersistenceException(ex);
      } finally {
        closeInput(in);
      }
    } finally {
      Timing.stop(timer);
      LOG.fine("Reading block " + blockId + " finished");
    }
  }

  private void writeBlockFile(Block block) throws PersistenceException {
    Timer timer = Timing.start("FileBlockAccess.writeBlockFile");
    try {
      OutputStream out = null;
      try {
        out = new FileOutputStream(blockFile(basePath, waveletName, block.getBlockId()));
        block.serialize(out);
      } catch (FileNotFoundException ex) {
        throw new PersistenceException(ex);
      } finally {
        closeOutput(out);
      }
    } finally {
      Timing.stop(timer);
    }
  }

  private static void closeInput(InputStream in) throws PersistenceException {
    if (in != null) {
      try {
        in.close();
      } catch (IOException ex) {
        throw new PersistenceException(ex);
      }
    }
  }

  private static void closeOutput(OutputStream out) throws PersistenceException {
    if (out != null) {
      try {
        out.close();
      } catch (IOException ex) {
        throw new PersistenceException(ex);
      }
    }
  }

  @VisibleForTesting
  static File waveletDir(String basePath, WaveletName waveletName) {
    String waveletPathPrefix = FileUtils.waveletNameToPathSegment(waveletName);
    return new File(basePath, waveletPathPrefix);
  }

  @VisibleForTesting
  static File blockIndexFile(String basePath, WaveletName waveletName) {
    return new File(waveletDir(basePath, waveletName), BLOCK_INDEX_FILE);
  }

  @VisibleForTesting
  static File blockFile(String basePath, WaveletName waveletName, String blockId) {
    return new File(waveletDir(basePath, waveletName), FileUtils.toFilenameFriendlyString(blockId)
      + BLOCK_FILE_SUFFIX);
  }
}
