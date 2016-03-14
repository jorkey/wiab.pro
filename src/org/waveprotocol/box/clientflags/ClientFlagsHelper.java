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

package org.waveprotocol.box.clientflags;

import java.util.Arrays;
import java.util.List;

/**
 * Helper for client flags working with several sources.
 */
public class ClientFlagsHelper {

  private final List<TypedSource> sources;

  /**
   * @param sources source list ordered by priority descending
   */
  public ClientFlagsHelper(List<TypedSource> sources) {
    this.sources = sources;
  }

  /**
   * @param sources sources ordered by priority descending
   */
  public ClientFlagsHelper(TypedSource... sources) {
    this.sources = Arrays.asList(sources);
  }

  /**
   * Gets the parameter from sources, if null return default value.
   */
  public boolean getBoolean(String tag, boolean defaultValue) {
    for (TypedSource source : sources) {
      Boolean value = source.getBoolean(tag);
      if (value != null) {
        return value;
      }
    }
    return defaultValue;
  }

  /**
   * Gets the parameter from sources, if null return default value.
   */
  public String getString(String tag, String defaultValue) {
    for (TypedSource source : sources) {
      String value = source.getString(tag);
      if (value != null) {
        return value;
      }
    }
    return defaultValue;
  }

  /**
   * Gets the parameter from sources, if null return default value.
   */
  public int getInt(String tag, int defaultValue) {
    for (TypedSource source : sources) {
      Integer value = source.getInteger(tag);
      if (value != null) {
        return value;
      }
    }
    return defaultValue;
  }

  /**
   * Gets the parameter from sources, if null return default value.
   */
  public double getDouble(String tag, double defaultValue) {
    for (TypedSource source : sources) {
      Double value = source.getDouble(tag);
      if (value != null) {
        return value;
      }
    }
    return defaultValue;
  }
  
  public List<TypedSource> getSources() {
    return sources;
  }
}
