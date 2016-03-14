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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.ServerMain;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;

import org.eclipse.jetty.proxy.ProxyServlet;

@SuppressWarnings("serial")
@Singleton
public class GadgetProxyServlet extends HttpServlet {

  private static final Log LOG = Log.get(ServerMain.class);  
  
  private final ProxyServlet.Transparent proxyServlet;
  private final String gadgetServerHostname;
  private final int gadgetServerPort;
  private final String gadgetServerPath;  

  @Inject
  public GadgetProxyServlet(
      @Named(CoreSettings.GADGET_SERVER_HOSTNAME) final String gadgetServerHostname,
      @Named(CoreSettings.GADGET_SERVER_PORT) final int gadgetServerPort,
      @Named(CoreSettings.GADGET_SERVER_PATH) final String gadgetServerPath) {
    this.gadgetServerHostname = gadgetServerHostname;
    this.gadgetServerPort = gadgetServerPort;
    this.gadgetServerPath = gadgetServerPath;

    LOG.info("Starting GadgetProxyServlet for " + gadgetServerHostname + ":" + gadgetServerPort);
    proxyServlet = new ProxyServlet.Transparent();
  }

  @Override
  public void destroy() {
    proxyServlet.destroy();
  }

  @Override
  public void init(final ServletConfig config) throws ServletException {
    // Workaround: transparent servlet does not redefine host header.
    // TODO(akaplanov): Pass original config after Jetty fix that error.
    proxyServlet.init(new ServletConfig() {

      private final String HOST_HEADER = "hostHeader";
      private final String PROXY_TO = "proxyTo";
      private final String PREFIX = "prefix";

      @Override
      public String getServletName() {
        return config.getServletName();
      }

      @Override
      public ServletContext getServletContext() {
        return config.getServletContext();
      }

      @Override
      public String getInitParameter(String name) {
        if (HOST_HEADER.equals(name)) {
          return gadgetServerHostname;
        }
        if (PROXY_TO.equals(name)) {
          return "http://" + gadgetServerHostname + ":" + gadgetServerPort + gadgetServerPath;
        }
        if (PREFIX.equals(name)) {
          return gadgetServerPath;
        }
        return config.getInitParameter(name);
      }

      @Override
      public Enumeration<String> getInitParameterNames() {
        List<String> names = Collections.list(config.getInitParameterNames());
        if (!names.contains(HOST_HEADER)) {
          names.add(HOST_HEADER);
        }
        names.add(PROXY_TO);
        names.add(PREFIX);
        return Collections.enumeration(names);
      }
    });
  }

  @Override
  public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
    proxyServlet.service(req, res);
  }
}  
