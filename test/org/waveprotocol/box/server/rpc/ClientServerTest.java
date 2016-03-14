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

import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.google.protobuf.Descriptors;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static junit.framework.Assert.assertEquals;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.assertTrue;

import junit.framework.TestCase;
import org.mockito.Mockito;
import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.executor.ExecutorAnnotations;
import org.waveprotocol.box.server.executor.TestExecutorsModule;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.stat.RequestScopeFilter;
import org.waveprotocol.wave.clientserver.ClientServer;
import org.waveprotocol.wave.clientserver.ClientServer.DisconnectService;
import org.waveprotocol.wave.clientserver.ClientServer.OpenWaveletChannelRequest;
import org.waveprotocol.wave.clientserver.ClientServer.OpenWaveletChannelStream;
import org.waveprotocol.wave.clientserver.ClientServer.WaveletChannelService;
import org.waveprotocol.wave.clientserver.Rpc;

/**
 * Test case for ServerRpcProvider.
 *
 * @author unknown
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class ClientServerTest extends TestCase {

  private ServerRpcProvider server = null;
  private ClientRpcChannel client = null;
  private Injector injector = null;

  private DisconnectService.Interface disconnectImpl = new DisconnectService.Interface() {
    @Override
    public void disconnect(RpcController controller, ClientServer.DisconnectRequest request, RpcCallback<ClientServer.EmptyResponse> done) {
    }
  };

  private ClientRpcChannel newClient() throws IOException {
     return new WebSocketClientRpcChannel(server.getWebSocketAddress());
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    SessionManager sessionManager = Mockito.mock(SessionManager.class);
    injector = Guice.createInjector(new AbstractModule() {

      @Override
      protected void configure() {
        install(new TestExecutorsModule());
      }
    });
    /*
     * NOTE: Specifying port zero (0) causes the OS to select a random port.
     * This allows the test to run without clashing with any potentially in-use port.
     */
    server =
        new ServerRpcProvider(new InetSocketAddress[] {new InetSocketAddress("localhost", 0)}, 0,
            new String[] {"./war"}, sessionManager, null, null, false, null, null,
            injector.getInstance(Key.get(Executor.class, ExecutorAnnotations.ClientServerExecutor.class)));
    injector = injector.createChildInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ServerRpcProvider.class).toInstance(server);
        bind(Key.get(Integer.class, Names.named(CoreSettings.WEBSOCKET_MAX_IDLE_TIME))).toInstance(0);
        bind(Key.get(Integer.class, Names.named(CoreSettings.WEBSOCKET_MAX_MESSAGE_SIZE))).toInstance(2);
      }
    });
    server.addFilter("/*", RequestScopeFilter.class);
    server.startWebSocketServer(injector);
}

  @Override
  public void tearDown() throws Exception {
    server.stopServer();
    server = null;
    client = null;
    super.tearDown();
  }

  /**
   * Asserts that the streaming RPC option is being parsed correctly.
   */
  public void testIsStreamingRpc() throws Exception {
    Descriptors.ServiceDescriptor serviceDescriptor =
        WaveletChannelService.getDescriptor();
    assertTrue(serviceDescriptor.findMethodByName("Open").getOptions()
        .getExtension(Rpc.isStreamingRpc));
    assertFalse(serviceDescriptor.findMethodByName("Close").getOptions()
        .getExtension(Rpc.isStreamingRpc));
  }

  /**
   * Tests a complete, simple end-to-end RPC.
   */
  public void testSimpleRpc() throws Exception {
    if (isWindows()) {
      return;
    }

    final int TIMEOUT_SECONDS = 5;
    final String USER = "thorogood@google.com";
    final String WAVELET_NAME = "foowave/qwerty";
    final String CHANNEL = "channel1";
    final AtomicBoolean receivedOpenRequest = new AtomicBoolean(false);
    final CountDownLatch responseLatch = new CountDownLatch(2);
    final List<OpenWaveletChannelStream> responses = Lists.newArrayList();
    final OpenWaveletChannelStream cannedResponse =
        OpenWaveletChannelStream.newBuilder().setChannelId(CHANNEL).build();

    // Generate fairly dummy RPC implementation.
    WaveletChannelService.Interface rpcImpl =
        new WaveletChannelService.Interface() {

          @Override
          public void open(RpcController controller, ClientServer.OpenWaveletChannelRequest request,
              RpcCallback<ClientServer.OpenWaveletChannelStream> callback) {
            assertTrue(receivedOpenRequest.compareAndSet(false, true));
            assertEquals(WAVELET_NAME, request.getWaveletName());

            // Return a valid response.
            callback.run(cannedResponse);

            // Falling out of this method will automatically finish this RPC.
            callback.run(null);
            // TODO: terrible idea?
          }

          @Override
          public void close(RpcController controller, ClientServer.CloseWaveletChannelRequest request,
              RpcCallback<ClientServer.EmptyResponse> callback) {
          }
        };


    // Register implementations.
    server.registerService(WaveletChannelService.newReflectiveService(rpcImpl));
    server.registerService(DisconnectService.newReflectiveService(disconnectImpl));

    // Create a client connection to the server, *after* it has registered services.
    client = newClient();

    // Create a client-side stub for talking to the server.
    WaveletChannelService.Stub stub = WaveletChannelService.newStub(client);

    // Create a controller, set up request, wait for responses.
    RpcController controller = client.newRpcController();
    OpenWaveletChannelRequest request =
        OpenWaveletChannelRequest.newBuilder().setWaveletName(WAVELET_NAME).build();
    stub.open(controller, request, new RpcCallback<OpenWaveletChannelStream>() {
      @Override
      public void run(OpenWaveletChannelStream response) {
        responses.add(response);
        responseLatch.countDown();
      }
    });

    // Wait for both responses to be received and assert their equality.
    responseLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    assertEquals(Arrays.asList(cannedResponse, null), responses);
    assertEquals(0, responseLatch.getCount());

    client.close();
  }

  /**
   * Tests a RPC that will fail.
   */
  public void testFailedRpc() throws Exception {
    if (isWindows()) {
      return;
    }

    final int TIMEOUT_SECONDS = 5;
    final String ERROR_TEXT = "This error should flow down over the RPC connection!";
    final CountDownLatch responseLatch = new CountDownLatch(1);
    final List<OpenWaveletChannelStream> responses = Lists.newArrayList();

    // Generate fairly dummy RPC implementation.
    WaveletChannelService.Interface rpcImpl =
        new WaveletChannelService.Interface() {

          @Override
          public void open(RpcController controller, ClientServer.OpenWaveletChannelRequest request,
              RpcCallback<ClientServer.OpenWaveletChannelStream> callback) {
            controller.setFailed(ERROR_TEXT);
          }

          @Override
          public void close(RpcController controller, ClientServer.CloseWaveletChannelRequest request,
              RpcCallback<ClientServer.EmptyResponse> callback) {
          }
        };

    // Register implementations.
    server.registerService(WaveletChannelService.newReflectiveService(rpcImpl));
    server.registerService(DisconnectService.newReflectiveService(disconnectImpl));

    // Create a client connection to the server, *after* it has registered services.
    client = newClient();

    // Create a client-side stub for talking to the server.
    WaveletChannelService.Stub stub = WaveletChannelService.newStub(client);

    // Create a controller, set up request, wait for responses.
    RpcController controller = client.newRpcController();
    OpenWaveletChannelRequest request =
        OpenWaveletChannelRequest.newBuilder().setWaveletName("").build();
    stub.open(controller, request, new RpcCallback<OpenWaveletChannelStream>() {
      @Override
      public void run(OpenWaveletChannelStream response) {
        responses.add(response);
        responseLatch.countDown();
      }
    });

    // Wait for a response, and assert that is a complete failure. :-)
    responseLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    assertEquals(Arrays.asList((OpenWaveletChannelStream) null), responses);
    assertTrue(controller.failed());
    assertEquals(ERROR_TEXT, controller.errorText());

    client.close();
  }

  /**
   * Tests cancelling a streaming RPC. This is achieved by waiting for the first
   * streaming message, then cancelling the RPC.
   */
  public void testCancelStreamingRpc() throws Exception {
    if (isWindows()) {
      return;
    }

    final int TIMEOUT_SECONDS = 5;
    final int MESSAGES_BEFORE_CANCEL = 5;
    final OpenWaveletChannelStream cannedResponse =
      OpenWaveletChannelStream.newBuilder().setChannelId("").build();
    final CountDownLatch responseLatch = new CountDownLatch(MESSAGES_BEFORE_CANCEL);
    final CountDownLatch finishedLatch = new CountDownLatch(1);

    // Generate fairly dummy RPC implementation.
    WaveletChannelService.Interface rpcImpl =
        new WaveletChannelService.Interface() {
          @Override
          public void open(RpcController controller, OpenWaveletChannelRequest request,
              final RpcCallback<OpenWaveletChannelStream> callback) {
            // Initially return many responses.
            for (int m = 0; m < MESSAGES_BEFORE_CANCEL; ++m) {
              callback.run(cannedResponse);
            }

            // Register a callback to handle cancellation. There is no race
            // condition here with sending responses, since there are no
            // contracts on the timing/response to cancellation requests.
            controller.notifyOnCancel(new RpcCallback<Object>() {
              @Override
              public void run(Object object) {
                // Happily shut down this RPC.
                callback.run(null);
              }
            });

          }

          @Override
          public void close(RpcController controller, ClientServer.CloseWaveletChannelRequest request, RpcCallback<ClientServer.EmptyResponse> done) {
          }
        };

    // Register implementations.
    server.registerService(WaveletChannelService.newReflectiveService(rpcImpl));
    server.registerService(DisconnectService.newReflectiveService(disconnectImpl));

    // Create a client connection to the server, *after* it has registered
    // services.
    client = newClient();

    // Create a client-side stub for talking to the server.
    WaveletChannelService.Stub stub = WaveletChannelService.newStub(client);

    // Create a controller, set up request, wait for responses.
    RpcController controller = client.newRpcController();
    OpenWaveletChannelRequest request =
        OpenWaveletChannelRequest.newBuilder().setWaveletName("").build();
    stub.open(controller, request, new RpcCallback<OpenWaveletChannelStream>() {
      @Override
      public void run(OpenWaveletChannelStream response) {
        if (response != null) {
          responseLatch.countDown();
        } else {
          finishedLatch.countDown();
        }
      }
    });

    // Wait for all pending responses.
    responseLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    assertEquals(0, responseLatch.getCount());
    assertEquals(1, finishedLatch.getCount());

    // Cancel the RPC and wait for it to finish.
    controller.startCancel();
    finishedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    assertEquals(0, finishedLatch.getCount());
    assertFalse(controller.failed());

    client.close();
  }

  private boolean isWindows() {
    return System.getProperty("os.name").startsWith("Windows");
  }
}
