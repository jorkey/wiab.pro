/**
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.waveprotocol.box.server.rpc;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.wave.api.JsonRpcConstant.ParamsProperty;
import com.google.wave.api.JsonRpcResponse;
import com.google.wave.api.OperationQueue;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.SearchResult;
import com.google.wave.api.SearchResult.Digest;
import com.google.wave.api.data.converter.EventDataConverterManager;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.authentication.AccountStoreHolder;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.robots.OperationContextImpl;
import org.waveprotocol.box.server.robots.OperationServiceRegistry;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.robots.util.OperationUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * @author vega113@gmail.com (Yuri Zelikov)
 */
@SuppressWarnings("serial")
@Singleton
public class SitemapServlet extends HttpServlet {

  private static Logger LOG = Logger
      .getLogger(SitemapServlet.class.getName());

  private final ConversationUtil conversationUtil;
  private final EventDataConverterManager converterManager;
  private final WaveletProvider waveletProvider;
  private final SessionManager sessionManager;
  private final OperationServiceRegistry operationRegistry;

  @Inject
  public SitemapServlet(
      EventDataConverterManager converterManager,
      @Named("DataApiRegistry") OperationServiceRegistry operationRegistry,
      WaveletProvider waveletProvider, ConversationUtil conversationUtil,
      SessionManager sessionManager) {
    this.converterManager = converterManager;
    this.waveletProvider = waveletProvider;
    this.conversationUtil = conversationUtil;
    this.sessionManager = sessionManager;
    this.operationRegistry = operationRegistry;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    ParticipantId user = sessionManager.getLoggedInUser(req.getSession(false));
    PrintWriter w = resp.getWriter();
    String query = "with:@";
    user = user != null ? user : ParticipantId.ofUnsafe("@" + AccountStoreHolder.getDefaultDomain());
    List<String> sharedUris = fetchSearchResult(query, user, new URL(req.getRequestURL().toString()));
    LOG.info("Fetched sitemap.txt, " + sharedUris.size() + " lines");
    String Uris = Joiner.on("\n").join(sharedUris);
    w.print(Uris);
    w.flush();
  }

  private List<String> fetchSearchResult(String query, ParticipantId viewer, URL requestURL) throws MalformedURLException {
    OperationContextImpl context =
        new OperationContextImpl(waveletProvider,
            converterManager.getEventDataConverter(ProtocolVersion.DEFAULT), conversationUtil);
    if (viewer == null) {
      viewer = ParticipantId.ofUnsafe("@" + AccountStoreHolder.getDefaultDomain());
    }
    OperationQueue opQueue = new OperationQueue();
    opQueue.search(query, 0, 1000000);
    OperationRequest operationRequest = opQueue.getPendingOperations().get(0);
    String opId = operationRequest.getId();
    OperationUtil.executeOperation(operationRequest, operationRegistry, context, viewer);
    JsonRpcResponse jsonRpcResponse = context.getResponses().get(opId);
    SearchResult searchResult =
        (SearchResult) jsonRpcResponse.getData().get(ParamsProperty.SEARCH_RESULTS);
    List<Digest> digests = searchResult.getDigests();
    List<String> sharedWaveUris = Lists.newArrayListWithCapacity(digests.size() + 1);
    for (Digest digest : digests) {
      String digestId = digest.getWaveId().replace("!", "/");
      sharedWaveUris.add(new URL(requestURL, "/render/wave/" + digestId).toString());
    }
    return sharedWaveUris;
  }
}
