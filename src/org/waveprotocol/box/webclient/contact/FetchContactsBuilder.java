/**
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

package org.waveprotocol.box.webclient.contact;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;
import org.waveprotocol.box.contact.ContactRequest;
import org.waveprotocol.box.contact.jso.ContactRequestJsoImpl;
import org.waveprotocol.box.contact.jso.ContactResponseJsoImpl;
import org.waveprotocol.box.webclient.contact.FetchContactsService.Callback;
import org.waveprotocol.wave.client.account.impl.AbstractContactManager;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.communication.gwt.JsonMessage;
import org.waveprotocol.wave.communication.json.JsonException;

/**
 * Helper class to fetch contacts.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public final class FetchContactsBuilder {
  private static final LoggerBundle LOG = new DomLogger("FetchContactsBuilder");

  /** The base contact URL. */
  private static final String CONTACT_URL_BASE = "/contact";

  /** Holds contact request data. */
  private ContactRequest contactRequest;

  private FetchContactsBuilder() {
  }

  public FetchContactsBuilder setTimestamp(long timestamp) {
    contactRequest.setTimestamp(timestamp);
    return this;
  }

  /** Static factory method */
  public static FetchContactsBuilder create() {
    return new FetchContactsBuilder();
  }

  public FetchContactsBuilder newFetchContactsRequest() {
    contactRequest = ContactRequestJsoImpl.create();
    return this;
  }

  public FetchContactsBuilder setContactsManager(AbstractContactManager contactManager) {
    return this;
  }

  public void fetchContacts(final Callback callback) {
    String url = getUrl(contactRequest);
    LOG.trace().log("Fetching contacts, timestamp " + contactRequest.getTimestamp());

    RequestBuilder requestBuilder = new RequestBuilder(RequestBuilder.GET, url);
    requestBuilder.setCallback(new RequestCallback() {
      @Override
      public void onResponseReceived(Request request, Response response) {
        LOG.trace().log("Contact response received: ", response.getText());
        if (response.getStatusCode() != Response.SC_OK) {
          callback.onFailure("Got back status code " + response.getStatusCode());
        } else if (!response.getHeader("Content-Type").startsWith("application/json")) {
          callback.onFailure("Contact service did not return json");
        } else {
          ContactResponseJsoImpl contactResponse;
          try {
            contactResponse = JsonMessage.parse(response.getText());
          } catch (JsonException e) {
            callback.onFailure(e.getMessage());
            return;
          }
          callback.onSuccess(contactResponse);
        }
      }

      @Override
      public void onError(Request request, Throwable e) {
        callback.onFailure(e.getMessage());
      }
    });

    try {
      requestBuilder.send();
    } catch (RequestException e) {
      LOG.error().log(e.getMessage());
    }
  }

  private static String getUrl(ContactRequest contactRequest) {
    String params = "?timestamp=" + contactRequest.getTimestamp();
    return CONTACT_URL_BASE + "/" + URL.encode(params);
  }
}
