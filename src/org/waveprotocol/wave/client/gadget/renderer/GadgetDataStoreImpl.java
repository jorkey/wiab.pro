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

package org.waveprotocol.wave.client.gadget.renderer;

import com.google.gwt.core.client.Duration;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;

import org.waveprotocol.box.webclient.flags.Flags;
import org.waveprotocol.wave.client.gadget.GadgetLog;
import org.waveprotocol.wave.model.id.WaveletName;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Gadget data store implementation.
 *
 * Please see the initial metadata call spec at:
 * https://cwiki.apache.org/SHINDIG/shindigs-metadata-call.html
 *
 *<p>
 * <li>TODO(user): Add unit tests.</li>
 * <li>TODO(vadimg): Consider batching gadget requests to improve performance.
 * </li>
 * <li>TODO(vadimg): Work out how to gracefully renew expired security tokens.
 * </li>
 *
 */
public class GadgetDataStoreImpl implements GadgetDataStore {

  /**
   * Cache element class that contains both cached gadget metadata and expiration time.
   */
  private static class CacheElement {
    
    private final GadgetMetadata metadata;
    private final double expirationTime;

    CacheElement(GadgetMetadata metadata) {
      this.metadata = metadata;
      expirationTime = Duration.currentTimeMillis() + Flags.get().gadgetMetadataLifetimeMs();
    }

    GadgetMetadata getMetadata() {
      return metadata;
    }

    double getExpirationTime() {
      return expirationTime;
    }
  }
  
  /** Gadget metadata path. */
  public static final String GADGET_METADATA_PATH = "/gadgets/metadata";  
  
  // Request keys.
  private static final String REQUEST_URL_KEY = "url";
  private static final String REQUEST_CONTAINER_KEY = "container";
  private static final String REQUEST_CONTAINER_KEY_WAVE = "wave";  
  private static final String REQUEST_CONTEXT_KEY = "context";
  private static final String REQUEST_GADGETS_KEY = "gadgets";
  
  // Response keys.
  private static final String RESPONSE_GADGETS_KEY = "gadgets";  
  
  private static GadgetDataStoreImpl singleton = null;

  /** Metadata cache. Maps the gadget instance key to metadata cache elements. */
  private final Map<String, CacheElement> metadataCache = new HashMap<>();  
  
  /** Callback cahce. Maps the gadget instance key to metadata callback lists. */
  private final Map<String, Set<DataCallback>> callbackCache = new HashMap<>();
  
  /** Private singleton constructor. */
  private GadgetDataStoreImpl() {
  }

  /**
   * Retrieves the class singleton.
   *
   * @return singleton instance of the class.
   */
  static GadgetDataStore getInstance() {
    if (singleton == null) {
      singleton = new GadgetDataStoreImpl();
    }
    return singleton;
  }

  @Override
  public void getGadgetData(final String gadgetSpecUrl, WaveletName waveletName, int instanceId,
      final DataCallback callback) {
    GadgetLog.log("GadgetDataStoreImpl.getGadgetData: gadgetSpecUrl=" + gadgetSpecUrl);
    // Check if metadata is cached
    cleanupExpiredCache();
    CacheElement cacheElement = metadataCache.get(gadgetSpecUrl);
    if (cacheElement != null) {
      callback.onDataReady(cacheElement.getMetadata());
      return;
    }

    // Check if callback is cached
    Set<DataCallback> callbacks = callbackCache.get(gadgetSpecUrl);
    if (callbacks != null) {
      callbacks.add(callback);
      return;
    }
    callbacks = new HashSet<>();
    callbacks.add(callback);
    callbackCache.put(gadgetSpecUrl, callbacks);

    JSONObject request = new JSONObject();
    JSONObject context = new JSONObject();
    JSONArray gadgets = new JSONArray();
    JSONObject gadget = new JSONObject();
    try {
      gadget.put(REQUEST_URL_KEY, new JSONString(gadgetSpecUrl));
      gadgets.set(0, gadget);
      context.put(REQUEST_CONTAINER_KEY, new JSONString(REQUEST_CONTAINER_KEY_WAVE));
      request.put(REQUEST_CONTEXT_KEY, context);
      request.put(REQUEST_GADGETS_KEY, gadgets);

      RequestBuilder builder = new RequestBuilder(RequestBuilder.POST, GADGET_METADATA_PATH);
      GadgetLog.log("builder.sendRequest: request=" + request.toString());
      builder.sendRequest(request.toString(), new RequestCallback() {        

        @Override
        public void onError(Request request, Throwable exception) {
          triggerOnError(gadgetSpecUrl, "Error retrieving metadata from the server.", exception);
        }

        @Override
        public void onResponseReceived(Request request, Response response) {
          try {
            String responseText = response.getText();              
            if (responseText.isEmpty()) {
              triggerOnError(gadgetSpecUrl, "Response is empty.", null);
              return;
            }

            GadgetLog.log("GadgetDataStoreImpl.getGadgetData.RequestCallback.onResponseReceived: "
                + "response=" + responseText);
            JSONObject gadgetMetadata = JSONParser.parseLenient(response.getText()).isObject()
                .get(RESPONSE_GADGETS_KEY).isArray().get(0).isObject();              
            GadgetMetadata metadata = new GadgetMetadata(gadgetMetadata);

            metadataCache.put(gadgetSpecUrl, new CacheElement(metadata));

            triggerOnDataReady(gadgetSpecUrl, metadata);
          } catch (NullPointerException exception) {
            callback.onError("Error in gadget metadata JSON.", exception);
          }
        }
      });
    } catch (RequestException e) {
      callback.onError("Unable to process gadget request.", e);
    }
  }
  
  private void cleanupExpiredCache() {
    GadgetLog.log("GadgetDataStoreImpl.cleanupExpiredCache");
    double currentTime = Duration.currentTimeMillis();
    Iterator<CacheElement> it = metadataCache.values().iterator();
    while (it.hasNext()) {
      if (currentTime > it.next().getExpirationTime()) {
        it.remove();
      }
    }
  }
  
  private void triggerOnError(String gadgetSpecUrl, String message, Throwable exception) {
    for (DataCallback cb : callbackCache.get(gadgetSpecUrl)) {
      cb.onError(message, exception);
    }
    callbackCache.remove(gadgetSpecUrl);
  }
  
  private void triggerOnDataReady(String gadgetSpecUrl, GadgetMetadata metadata) {
    for (DataCallback cb : callbackCache.get(gadgetSpecUrl)) {
      cb.onDataReady(metadata);
    }
    callbackCache.remove(gadgetSpecUrl);
  }
}
