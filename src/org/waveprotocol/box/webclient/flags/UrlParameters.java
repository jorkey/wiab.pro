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

package org.waveprotocol.box.webclient.flags;

import com.google.gwt.http.client.URL;

import org.waveprotocol.box.clientflags.FlagConstants;
import org.waveprotocol.box.clientflags.TypedSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.waveprotocol.wave.model.util.ValueUtils;

/**
 * NOTE(user): Strictly speaking the initial '?' is not part of the query
 * string, but we treat it as part of the query string in this class for
 * convenience.
 */
public class UrlParameters implements TypedSource {

  private static UrlParameters singleton;

  private final HashMap<String, String> map = new HashMap<>();

  private static native String getQueryString() /*-{
    return $wnd.location.search;
  }-*/;

  UrlParameters(String query) {
    if (query.length() > 1) {
      String[] keyvalpairs = query.substring(1, query.length()).split("&");
      for (String pair : keyvalpairs) {
        String[] keyval = pair.split("=");
        // Some basic error handling for invalid query params.
        String paramUnderlineName = URL.decodeQueryString(keyval[0]);
        String paramCamelName = ValueUtils.toCamelCase(paramUnderlineName, "_", false);
        String key = FlagConstants.getShortName(paramCamelName);
        if (keyval.length == 2) {
          String value = URL.decodeQueryString(keyval[1]);
          map.put(key, value);
        } else if (keyval.length == 1) {
          map.put(key, "");
        }
      }
    }
  }

  public String getParameter(String name) {
    return map.get(name);
  }

  public static UrlParameters get() {
    if (singleton == null) {
      singleton = new UrlParameters(getQueryString());
    }
    return singleton;
  }

  /** {@inheritDoc} */
  @Override
  public Boolean getBoolean(String key) {
    String value = getParameter(key);
    if (value == null) {
      return null;
    }
    return Boolean.valueOf(value);
  }

  /** {@inheritDoc} */
  @Override
  public Double getDouble(String key) {
    String value = getParameter(key);
    if (value == null) {
      return null;
    }

    try {
      return Double.valueOf(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public Integer getInteger(String key) {
    String value = getParameter(key);
    if (value == null) {
      return null;
    }

    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getString(String key) {
    String value = getParameter(key);
    return value;
  }

  /**
   * Build a query string out of a map of key/value pairs.
   * @param queryEntries
   */
  public static String buildQueryString(Map<String, String> queryEntries) {
    StringBuilder sb = new StringBuilder();
    boolean firstIteration = true;
    for (Entry<String, String> e : queryEntries.entrySet()) {
      if (firstIteration) {
        sb.append('?');
      } else {
        sb.append('&');
      }
      String encodedName = URL.encodeQueryString(e.getKey());
      sb.append(encodedName);

      sb.append('=');

      String encodedValue = URL.encodeQueryString(e.getValue());
      sb.append(encodedValue);
      firstIteration = false;
    }
    return sb.toString();
  }
}
