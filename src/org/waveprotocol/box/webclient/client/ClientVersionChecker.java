package org.waveprotocol.box.webclient.client;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;
import org.waveprotocol.wave.common.logging.LoggerBundle;

/**
 * Checks last modified time of client code on the server.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class ClientVersionChecker {

  /**
   * Calls when client code is updated.
   */
  public interface Listener {
    void onClientUpdated();
  }

  private static final String START_GWT_JS = "webclient/webclient.nocache.js";

  private final Listener listener;
  private final LoggerBundle logger;

  private String currentLastModifiedTime;

  public ClientVersionChecker(Listener listener, LoggerBundle logger) {
    this.listener = listener;
    this.logger = logger;
  }

  public void checkClientUpdated() {
    RequestBuilder request = new RequestBuilder(RequestBuilder.HEAD, URL.encodeQueryString(START_GWT_JS));
    request.setCallback(new RequestCallback() {

      @Override
      public void onResponseReceived(Request request, Response response) {
        String lastModifiedTime = response.getHeader("Last-Modified");
        if (lastModifiedTime != null) {
          if (currentLastModifiedTime == null) {
            currentLastModifiedTime = lastModifiedTime;
          } else if (!currentLastModifiedTime.equals(lastModifiedTime)) {
            listener.onClientUpdated();
          }
        }
      }

      @Override
      public void onError(Request request, Throwable ex) {
        logger.error().log("Getting of client last modified time error", ex);
      }
    });
    try {
      request.send();
    } catch (RequestException ex) {
      logger.error().log("Getting of client last modified time error", ex);
    }
  }
}
