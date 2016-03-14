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

import com.google.inject.Inject;
import com.google.inject.Singleton;


import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
@SuppressWarnings("serial")
@Singleton
public class RobotsServlet extends HttpServlet {

  private static Logger LOG = Logger
      .getLogger(RobotsServlet.class.getName());

  @Inject
  public RobotsServlet() {
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
      IOException {
    PrintWriter w = resp.getWriter();
    w.println("User-agent: *");
    w.println("Disallow: /auth/");
    URL url = new URL(req.getRequestURL().toString());
    w.println("Host: " + url.getHost());
    w.println("Sitemap: " + new URL(url, "sitemap.txt").toString());
    w.flush();
  }
}
