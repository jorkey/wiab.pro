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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Record formatter.
 * 
 * Writes/reads records of format <length><body>.
 * 
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
class Recorder {

  /** Reads byte array record. */
  static byte[] readRecord(InputStream in) throws IOException {
    int len = readInt(in);
    byte[] buf = new byte[len];
    if (in.read(buf) != len) {
      throw new EOFException();
    }
    return buf;
  }

  /** Reads integer. */
  static int readInt(InputStream in) throws IOException {
    int x = 0;
    for (int seek=0; seek <= 28; seek+=7) {
      int b = readByte(in);
      x |= ((b & 0x7F) << seek);
      if (b >= 0) {
        return x;
      }
    }
    throw new IOException("Malformed integer");
  }

  /** Writes byte buffer as record. */
  static void writeRecord(OutputStream out, byte[]... buffers) throws IOException {
    int len = 0;
    for (byte[] buf : buffers) {
      len += buf.length;
    }
    writeInt(out, len);
    for (byte[] buf : buffers) {
      out.write(buf);
    }
  }

  /** Writes integer as compact value of various length. */
  static void writeInt(OutputStream out, int value) throws IOException {
    for (;;) {
      if ((value & ~0x7F) == 0) {
        out.write(value);
        return;
      } else {
        out.write((value & 0x7F) | 0x80);
        value >>>= 7;
      }
    }
  }

  private static byte readByte(InputStream in) throws IOException, EOFException {
    int b = in.read();
    if (b == -1) {
      throw new EOFException();
    }
    return (byte)b;
  }
}
