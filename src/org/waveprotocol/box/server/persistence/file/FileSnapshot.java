/**
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

import org.waveprotocol.box.server.persistence.protos.ProtoSnapshotStoreData.ProtoWaveletSnapshot;
import org.waveprotocol.box.server.common.SnapshotSerializer;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.wave.data.WaveletData;
import org.waveprotocol.wave.model.operation.OperationException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * File with snapshot.
 * 
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class FileSnapshot {
  private static final Logger LOG = Logger.getLogger(FileSnapshot.class.getName());

  private final File fileRef;
  private RandomAccessFile file;

  public FileSnapshot(File fileRef) {
    this.fileRef = fileRef;
  }

  /** Opens file. */
  public void open() throws IOException {
    if (file == null) {
      file = FileUtils.getOrCreateFile(fileRef);
    }
  }

  /** Closes file if not closed. */
  public void close() throws IOException {
    if (file != null) {
      file.close();
      file = null;
    }
  }

  /** Reads snapshot from file. */
  public WaveletData readSnapshot(WaveId waveId) {
    try {
      if (file.length() != 0) {
        file.seek(0);
        InputStream in = Channels.newInputStream(file.getChannel());
        ProtoWaveletSnapshot snapshot = ProtoWaveletSnapshot.parseFrom(in);
        return SnapshotSerializer.deserializeWavelet(snapshot.getSnapshot(), waveId);
      }
    } catch (Exception ex) {
      LOG.log(Level.SEVERE, "Read snapshot", ex);
    }
    return null;
  }

  /** Writes snapshot to file. */
  public void writeSnapshot(WaveletData snapshot) throws IOException {
    ProtoWaveletSnapshot protoSnapsot = 
      ProtoWaveletSnapshot.newBuilder().setSnapshot(SnapshotSerializer.serializeWavelet(snapshot)).build();
    file.setLength(0);
    OutputStream out = Channels.newOutputStream(file.getChannel());
    protoSnapsot.writeTo(out);
    out.flush();
  }

}
