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

import com.google.gxp.base.GxpContext;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import org.waveprotocol.box.common.SessionConstants;
import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.gxp.TopBar;
import org.waveprotocol.box.server.gxp.WaveClientPage;
import org.waveprotocol.box.server.util.ClientFlagsUtil;
import org.waveprotocol.box.server.util.RandomBase64Generator;
import org.waveprotocol.box.server.util.UrlParameters;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import org.apache.commons.lang.StringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.inject.Singleton;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * The HTTP servlet for serving a wave client along with content generated on
 * the server.
 *
 * @author kalman@google.com (Benjamin Kalman)
 */
@SuppressWarnings("serial")
@Singleton
public class WaveClientServlet extends HttpServlet {

  private static final Log LOG = Log.get(WaveClientServlet.class);

  private static final String CLIENT_CONFIG_FILE_NAME = "client.config";
  
  private final List<String> resourceBases;  
  private final String domain;
  private final String websocketAddress;
  private final String websocketPresentedAddress;  
  private final String analyticsAccount;
  private final SessionManager sessionManager;
  
  /** Last modified time of the read client configuration file. */
  private long readClientConfigFileLmt;
  private JSONObject readClientFlags = new JSONObject();

  /**
   * Creates a servlet for the wave client.
   */
  @Inject
  public WaveClientServlet(
      @Named(CoreSettings.RESOURCE_BASES) List<String> resourceBases,
      @Named(CoreSettings.WAVE_SERVER_DOMAIN) String domain,
      @Named(CoreSettings.HTTP_FRONTEND_ADDRESSES) List<String> httpAddresses,
      @Named(CoreSettings.HTTP_WEBSOCKET_PUBLIC_ADDRESS) String websocketAddress,
      @Named(CoreSettings.HTTP_WEBSOCKET_PRESENTED_ADDRESS) String websocketPresentedAddress,
      @Named(CoreSettings.ANALYTICS_ACCOUNT) String analyticsAccount,
      SessionManager sessionManager) {
    this.resourceBases = resourceBases;
    this.domain = domain;
    this.websocketAddress = StringUtils.isEmpty(websocketAddress) ?
        httpAddresses.get(0) : websocketAddress;
    this.websocketPresentedAddress = StringUtils.isEmpty(websocketPresentedAddress) ?
        this.websocketAddress : websocketPresentedAddress;
    this.analyticsAccount = analyticsAccount;
    this.sessionManager = sessionManager;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    ParticipantId id = sessionManager.getLoggedInUser(request.getSession(false));

    // Eventually, it would be nice to show users who aren't logged in the public waves.
    // However, public waves aren't implemented yet. For now, we'll just redirect users
    // who haven't signed in to the sign in page.
    if (id == null) {
      response.sendRedirect(sessionManager.getLoginUrl("/"));
      return;
    }

    AccountData account = sessionManager.getLoggedInAccount(request.getSession(false));
    if (account != null) {
      String locale = account.asHuman().getLocale();
      if (locale != null) {
        String requestLocale = UrlParameters.getParameters(request.getQueryString()).get("locale");
        if (requestLocale == null) {
          response.sendRedirect(UrlParameters.addParameter(request.getRequestURL().toString(),
              "locale", locale));
          return;
        }
      }
    }

    String[] parts = id.getAddress().split("@");
    String username = parts[0];
    String userDomain = id.getDomain();

    long clientConfigFileLmt = getFileLastModifiedTime(CLIENT_CONFIG_FILE_NAME);
    if (clientConfigFileLmt != readClientConfigFileLmt) {
      readClientFlags = ClientFlagsUtil.convertParamsFromConfigFileToJson(CLIENT_CONFIG_FILE_NAME,
          LOG);
      readClientConfigFileLmt = clientConfigFileLmt;
    }
    
    // 15/04/2013 by KK: calculate lastModified for the main GWT script - 'webclient.nocache.js'
    // 20/03/2015 by DK: added lastModified for the client configuration file - 'client.config'
    String lastModified = Long.toString(getMainScriptLastModifiedTime()) + "_"
        + Long.toString(clientConfigFileLmt);
    
    try {
      WaveClientPage.write(response.getWriter(), new GxpContext(request.getLocale()),
          getSessionJson(request.getSession(false)), readClientFlags, websocketPresentedAddress,
          TopBar.getGxpClosure(username, userDomain), analyticsAccount, lastModified);
    } catch (IOException e) {
      LOG.warning("Failed to write GXP for request " + request, e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    response.setContentType("text/html");
    response.setStatus(HttpServletResponse.SC_OK);
  }

  private JSONObject getSessionJson(HttpSession session) {
    try {
      ParticipantId user = sessionManager.getLoggedInUser(session);
      String address = (user != null) ? user.getAddress() : null;

      // TODO(zdwang): Figure out a proper session id rather than generating a
      // random number
      String sessionId = (new RandomBase64Generator()).next(10);

      return new JSONObject()
          .put(SessionConstants.DOMAIN, domain)
          .putOpt(SessionConstants.ADDRESS, address)
          .putOpt(SessionConstants.ID_SEED, sessionId);
    } catch (JSONException e) {
      LOG.severe("Failed to create session JSON");
      return new JSONObject();
    }
  }
  
  // by KK: calculate lastModified for the main GWT script: 'webclient.nocache.js'
  private long getMainScriptLastModifiedTime() {
    for (String path : resourceBases) {
      if (!path.endsWith("/") && !path.endsWith("\\")) {
        path += "/";
      }
      long lastModifiedTime = getFileLastModifiedTime(path + "webclient/webclient.nocache.js");
      if (lastModifiedTime > 0L) {
        return lastModifiedTime;
      }
    }
    return 0L;
  }
  
  private long getFileLastModifiedTime(String fileName) {
    File file = new File(fileName);
    return file.exists() ? file.lastModified() : 0L;
  }
}
