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

import org.waveprotocol.box.server.persistence.blocks.impl.Recorder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import junit.framework.TestCase;
import org.bouncycastle.util.Arrays;

/**
 * Tests of FragmentIndex.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class RecorderTest extends TestCase {

  @Override
  protected void setUp() throws Exception {
  }

  public void testRecordOutputAndInput() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    Recorder.writeInt(out, 1);
    Recorder.writeInt(out, 0xFF);
    Recorder.writeInt(out, 0x12345678);

    byte[] bytes = "qwerty".getBytes();
    Recorder.writeRecord(out, bytes);

    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

    assertEquals(1, Recorder.readInt(in));
    assertEquals(0xFF, Recorder.readInt(in));
    assertEquals(0x12345678, Recorder.readInt(in));

    assertTrue(Arrays.areEqual(bytes, Recorder.readRecord(in)));
  }

  public void testInvalidRecordInput() throws Exception {
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    byteStream.write(0xFF);
    byteStream.write(0xFF);

    ByteArrayInputStream in = new ByteArrayInputStream(byteStream.toByteArray());
    boolean eofExceptionThrown = false;
    try {
      Recorder.readInt(in);
    } catch (EOFException ex) {
      eofExceptionThrown = true;
    }
    assertTrue(eofExceptionThrown);

    byteStream.write(0xFF);
    byteStream.write(0xFF);
    byteStream.write(0xFF);

    in = new ByteArrayInputStream(byteStream.toByteArray());
    boolean ioExceptionThrown = false;
    try {
      Recorder.readInt(in);
    } catch (EOFException ex) {
    } catch (IOException ex) {
      ioExceptionThrown = true;
    }
    assertTrue(ioExceptionThrown);
  }

}
