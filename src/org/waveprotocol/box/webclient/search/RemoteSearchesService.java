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

package org.waveprotocol.box.webclient.search;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;

import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.common.logging.LoggerBundle;
import org.waveprotocol.wave.communication.gwt.JsonMessage;
import org.waveprotocol.wave.communication.json.JsonException;
import org.waveprotocol.wave.model.util.CollectionUtils;

import org.waveprotocol.box.searches.jso.SearchesItemJsoImpl;
import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.searches.impl.SearchesItemImpl;
import org.waveprotocol.box.searches.jso.SearchesJsoImpl;

import java.util.List;

/**
 * Manage search patterns list on server.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public final class RemoteSearchesService implements SearchesService {

  private static final LoggerBundle LOG = new DomLogger(RemoteSearchesService.class.getName());

  private static final String SEARCHES_URL_BASE = "/searches";

  public RemoteSearchesService() {
  }

  @Override
  public void storeSearches(List<SearchesItem> searches, final StoreCallback callback) {
    RequestBuilder requestBuilder = new RequestBuilder(RequestBuilder.POST, SEARCHES_URL_BASE);

    requestBuilder.setRequestData(serializeSearches(searches).toJson());

    LOG.trace().log("Store searches");

    requestBuilder.setCallback(new RequestCallback() {
      @Override
      public void onResponseReceived(Request request, Response response) {
        if (response.getStatusCode() != Response.SC_OK) {
          callback.onFailure("Got back status code " + response.getStatusCode());
        } else {
          LOG.error().log("Searches was stored");
        }
      }

      @Override
      public void onError(Request request, Throwable exception) {
        LOG.error().log("Storing searches error: ", exception);
        callback.onFailure(exception.getMessage());
      }
    });

    try {
      requestBuilder.send();
    } catch (RequestException e) {
      callback.onFailure(e.getMessage());
    }
  }

  @Override
  public void getSearches(final GetCallback callback) {

    String url = SEARCHES_URL_BASE;
    LOG.trace().log("Getting searches");

    RequestBuilder requestBuilder = new RequestBuilder(RequestBuilder.GET, url);

    requestBuilder.setCallback(new RequestCallback() {
      @Override
      public void onResponseReceived(Request request, Response response) {
        LOG.trace().log("Searches was received: ", response.getText());
        if (response.getStatusCode() != Response.SC_OK) {
          callback.onFailure("Got back status code " + response.getStatusCode());
        } else if (!response.getHeader("Content-Type").startsWith("application/json")) {
          callback.onFailure("Search service did not return json");
        } else {
          SearchesJsoImpl searchPatterns;
          try {
            searchPatterns = JsonMessage.parse(response.getText());
          } catch (JsonException e) {
            callback.onFailure(e.getMessage());
            return;
          }
          List<SearchesItem> searches = deserializeSearches(searchPatterns);
          callback.onSuccess(searches);
        }
      }

      @Override
      public void onError(Request request, Throwable exception) {
        LOG.error().log("Getting searches error: ", exception);
        callback.onFailure(exception.getMessage());
      }
    });

    try {
      requestBuilder.send();
    } catch (RequestException e) {
      callback.onFailure(e.getMessage());
    }
  }

  private static SearchesJsoImpl serializeSearches(List<SearchesItem> searches) {
    SearchesJsoImpl protoSearches = SearchesJsoImpl.create();
    for (SearchesItem search : searches) {
      SearchesItemJsoImpl protoSearch = SearchesItemJsoImpl.create();
      protoSearch.setName(search.getName());
      protoSearch.setQuery(search.getQuery());
      protoSearches.addSearch(protoSearch);
    }
    return protoSearches;
  }

  private static List<SearchesItem> deserializeSearches(SearchesJsoImpl protoSearches) {
    List<SearchesItem> searches = CollectionUtils.newArrayList();
    for (SearchesItem protoSearch : protoSearches.getSearch()) {
      SearchesItem search = new SearchesItemImpl(protoSearch);
      searches.add(search);
    }
    return searches;
  }
}
