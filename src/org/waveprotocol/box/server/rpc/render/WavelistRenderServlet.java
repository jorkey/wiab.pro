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
package org.waveprotocol.box.server.rpc.render;

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
import java.util.Date;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.authentication.AccountStoreHolder;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.robots.OperationContextImpl;
import org.waveprotocol.box.server.robots.OperationServiceRegistry;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.robots.util.OperationUtil;
import org.waveprotocol.box.server.rpc.render.web.template.Templates;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * @author vega113@gmail.com (Yuri Zelikov)
 */
@SuppressWarnings("serial")
@Singleton
public class WavelistRenderServlet extends HttpServlet {


  private static Logger LOG = Logger
      .getLogger(WavelistRenderServlet.class.getName());

  private final Templates templates;
  private final ConversationUtil conversationUtil;
  private final EventDataConverterManager converterManager;
  private final WaveletProvider waveletProvider;
  private final SessionManager sessionManager;
  private final OperationServiceRegistry operationRegistry;
  private final String httpAddress;

  @Inject
  public WavelistRenderServlet(EventDataConverterManager converterManager,
      @Named("DataApiRegistry") OperationServiceRegistry operationRegistry,
      WaveletProvider waveletProvider, ConversationUtil conversationUtil, Templates templates,
      SessionManager sessionManager, @Named(CoreSettings.HTTP_FRONTEND_PUBLIC_ADDRESS) String httpAddress) {
    this.converterManager = converterManager;
    this.waveletProvider = waveletProvider;
    this.conversationUtil = conversationUtil;
    this.templates = templates;
    this.sessionManager = sessionManager;
    this.operationRegistry = operationRegistry;
    this.httpAddress = httpAddress;
  }

  @Override
  protected synchronized void doGet(HttpServletRequest req, HttpServletResponse resp)
 throws ServletException,
      IOException {
    ParticipantId user = sessionManager.getLoggedInUser(req.getSession(false));
    resp.setContentType("text/html; charset=UTF-8");
    PrintWriter w = resp.getWriter();
    StringBuilder out = new StringBuilder();
    String query = "with:@";
    user = user != null ? user : ParticipantId.ofUnsafe("@" + AccountStoreHolder.getDefaultDomain());
        String innerHtml = fetchSearchResult(query, user);
    String outerHtml =
        templates.process(Templates.WAVELIST_TEMPLATE, new String[] {innerHtml});
    out.append(outerHtml);
    w.print(out.toString());
    w.flush();
  }

  private String fetchSearchResult(String query, ParticipantId viewer) {
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
    StringBuilder html = new StringBuilder();
    for (Digest digest : searchResult.getDigests()) {
      int participantsLength = digest.getParticipants().size();
      StringBuilder avatarsSb = new StringBuilder();
      for (int i = 0; i < 3; i++) {
        if (i >= participantsLength) {
          break;
        }
        String id =  digest.getParticipants().get(i);
        avatarsSb.append(templates.process(Templates.AVATAR_TEMPLATE, new String[] {id}));
      }
      String digestId = "'" + digest.getWaveId().replace("!", "/") + "'";
      String href = "'http://" + httpAddress +  "/render/wave/" + digest.getWaveId().replace("!", "/") + "'";
      String lmt = HtmlShallowBlipRenderer.formatPastDate(new Date(digest.getLastModified()), new Date());
      String unread = "0"; ///String.valueOf(digest.getUnreadCount());
      String total = String.valueOf(digest.getBlipCount()) + " msgs";
      String title = digest.getTitle();
      String snippet = digest.getSnippet();
      String[] args = new String[] {digestId, avatarsSb.toString(), lmt, total, unread, title, href, snippet};
      String digestStr = templates.process(Templates.DIGEST_TEMPLATE, args);
      html.append(digestStr);
    }
    return html.toString();
  }
}