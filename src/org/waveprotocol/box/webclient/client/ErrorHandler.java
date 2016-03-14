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
package org.waveprotocol.box.webclient.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.RootPanel;
import java.util.Date;
import java.util.logging.Logger;
import org.waveprotocol.box.webclient.widget.error.ErrorIndicatorPresenter;
import org.waveprotocol.wave.client.common.safehtml.SafeHtml;
import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.common.util.AsyncHolder;
import org.waveprotocol.wave.client.debug.logger.LogLevel;

/**
 * An exception handler that reports exceptions using a <em>shiny banner</em>
 * (an alert placed on the top of the screen). Once the stack trace is
 * prepared, it is revealed in the banner via a link.
 */
public class ErrorHandler implements GWT.UncaughtExceptionHandler {

  // Use of GWT logging is only intended for sending exception reports to the
  // server, nothing else in the client should use java.util.logging.
  // Please also see WebClientDemo.gwt.xml.
  private static final Logger REMOTE_LOG = Logger.getLogger("REMOTE_LOG");

  /** Next handler in the handler chain. */
  private final GWT.UncaughtExceptionHandler next;

  /**
   * Indicates whether an error has already been reported (at most one error
   * is ever reported by this handler).
   */
  private boolean hasFired;

  private ErrorHandler(GWT.UncaughtExceptionHandler next) {
    this.next = next;
  }

  public static void install() {
    GWT.setUncaughtExceptionHandler(new ErrorHandler(
        GWT.getUncaughtExceptionHandler()));
  }

  @Override
  public void onUncaughtException(Throwable e) {
    if (!hasFired) {
      hasFired = true;
      final ErrorIndicatorPresenter error =
          ErrorIndicatorPresenter.create(RootPanel.get("banner"));
      if (LogLevel.showErrors()) {
        getStackTraceAsync(e, new AsyncHolder.Accessor<SafeHtml>() {
          @Override
          public void use(SafeHtml stack) {
            error.addDetail(stack, null);
            REMOTE_LOG.severe(stack.asString().replace("<br>", "\n"));
          }
        });
      }
    }

    if (next != null) {
      next.onUncaughtException(e);
    }
  }

  private void getStackTraceAsync(final Throwable t,
      final AsyncHolder.Accessor<SafeHtml> whenReady) {
    // TODO: Request stack-trace de-obfuscation. For now, just use the
    // javascript stack trace.
    //
    // Use minimal services here, in order to avoid the chance that reporting
    // the error produces more errors. In particular, do not use WIAB's
    // scheduler to run this command.
    // Also, this code could potentially be put behind a runAsync boundary, to
    // save whatever dependencies it uses from the initial download.
    new Timer() {
      @Override
      public void run() {
        SafeHtmlBuilder stack = new SafeHtmlBuilder();

        Throwable error = t;
        while (error != null) {
          String token = String.valueOf((new Date()).getTime());
          stack.appendHtmlConstant("Token:  " + token + "<br> ");
          stack.appendEscaped(String.valueOf(error.getMessage())).
              appendHtmlConstant("<br>");
          for (StackTraceElement elt : error.getStackTrace()) {
            stack.appendHtmlConstant("\t at ")
                .appendEscaped(maybe(elt.getClassName(), "??"))
                .appendHtmlConstant(".") //
                .appendEscaped(maybe(elt.getMethodName(), "??"))
                .appendHtmlConstant("(") //
                .appendEscaped(maybe(elt.getFileName(), "??"))
                .appendHtmlConstant(":") //
                .appendEscaped(maybe(elt.getLineNumber(), "??"))
                .appendHtmlConstant(")") //
                .appendHtmlConstant("<br>");
          }
          error = error.getCause();
          if (error != null) {
            stack.appendHtmlConstant("Caused by: ");
          }
        }

        whenReady.use(stack.toSafeHtml());
      }
    }.schedule(1);
  }

  private static String maybe(String value, String otherwise) {
    return value != null ? value : otherwise;
  }

  private static String maybe(int value, String otherwise) {
    return value != -1 ? String.valueOf(value) : otherwise;
  }
}