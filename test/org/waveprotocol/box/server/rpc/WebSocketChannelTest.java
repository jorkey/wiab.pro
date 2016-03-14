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
package org.waveprotocol.box.server.rpc;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import junit.framework.TestCase;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;

import org.waveprotocol.wave.federation.Proto;
import org.waveprotocol.wave.clientserver.ClientServer.OpenWaveletChannelRequest;

/**
 * @author arb@google.com
 */
public class WebSocketChannelTest extends TestCase {
  private TestWebSocketChannel channel;
  private TestCallback callback;

  private static final int SEQUENCE_NUMBER = 5;

  class TestWebSocketChannel extends WebSocketChannel {
    String message;

    public TestWebSocketChannel(ProtoCallback callback) {
      super(callback);
      this.message = null;
    }

    @Override
    protected void sendMessageString(final String data) {
      this.message = data;
    }
  }

  class TestCallback implements ProtoCallback {
      Message savedMessage = null;
      long sequenceNumber;

      @Override
      public void message(int sequenceNo, final Message message, String connectionId) {
        this.sequenceNumber = sequenceNo;
        this.savedMessage = message;
      }

      @Override
      public void disconnect(String connectionId) {
      }
    }

  @Override
  public void setUp() {
    callback = new TestCallback();
    channel = new TestWebSocketChannel(callback);
  }

  public void testRoundTrippingJson() throws Exception {
    OpenWaveletChannelRequest.Builder sourceBuilder = buildProtocolOpenRequest();
    checkRoundtripping(sourceBuilder);
  }

  public void testRoundTrippingJsonRepeatedField() throws Exception {
    OpenWaveletChannelRequest.Builder sourceBuilder = buildProtocolOpenRequest();
    sourceBuilder.addKnownVersion(Proto.ProtocolHashedVersion.newBuilder().
        setHistoryHash(ByteString.EMPTY).setVersion(123));
    sourceBuilder.addKnownVersion(Proto.ProtocolHashedVersion.newBuilder().
        setHistoryHash(ByteString.EMPTY).setVersion(456));
    sourceBuilder.addKnownVersion(Proto.ProtocolHashedVersion.newBuilder().
        setHistoryHash(ByteString.EMPTY).setVersion(789));
    checkRoundtripping(sourceBuilder);
  }

  private void checkRoundtripping(final OpenWaveletChannelRequest.Builder sourceBuilder) {
    OpenWaveletChannelRequest sourceRequest = sourceBuilder.build();
    channel.sendMessage(SEQUENCE_NUMBER, sourceRequest);
    String sentRequest = channel.message;
    assertNotNull(sentRequest);
    System.out.println(sentRequest);
    channel.handleMessageString(sentRequest);
    assertNotNull(callback.savedMessage);
    assertEquals(SEQUENCE_NUMBER, callback.sequenceNumber);
    assertEquals(sourceRequest, callback.savedMessage);
  }

  private OpenWaveletChannelRequest.Builder buildProtocolOpenRequest() {
    OpenWaveletChannelRequest.Builder sourceBuilder = OpenWaveletChannelRequest.newBuilder();
    sourceBuilder.setWaveletName("example.com!w+test/example.com!qwe");
    return sourceBuilder;
  }
}
