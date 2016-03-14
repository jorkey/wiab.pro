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

import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author vega113@gmail.com (Yuri Zelikov)
 */
@SuppressWarnings("serial")
@Singleton
public class WaveRefServlet extends HttpServlet {


  private static Logger LOG = Logger.getLogger(WaveRefServlet.class.getName());
  private final SessionManager sessionManager;

  @Inject
  public WaveRefServlet(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
  }

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
      IOException {
    ParticipantId user = sessionManager.getLoggedInUser(req.getSession(false));
    String path = req.getRequestURI().replace("/waveref/", "");
    if (user != null) {
      resp.sendRedirect("/#" + path);
    } else {
      resp.sendRedirect("/render/wave/" + path);
    }
  }
}
