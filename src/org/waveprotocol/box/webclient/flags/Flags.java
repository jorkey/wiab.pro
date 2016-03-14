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

import com.google.common.annotations.VisibleForTesting;

import org.waveprotocol.box.clientflags.ClientFlags;
import org.waveprotocol.box.clientflags.ClientFlagsHelper;
import org.waveprotocol.box.clientflags.TypedSource;

import java.util.ArrayList;
import java.util.List;

/**
 * This class extends the generated ClientFlagsBase to provide user access to
 * flags from WFE.
 *
 * The reason we extend ClientFlagsBase is to extract constants and methods from
 * the generated file.
 */
public final class Flags extends ClientFlags {

  private static Flags instance = null;
  
  private static native ExtendedJSObject getJSObj() /*-{
    if ($wnd.__client_flags) {
      return $wnd.__client_flags;
    }
    return null;
  }-*/;

  private Flags(ClientFlagsHelper helper) {
    super(helper);
  }

  /**
   * Inject a TypedSource object for the purpose of testing.
   */
  @VisibleForTesting
  public static void resetWithSourceForTesting(TypedSource source) {
    instance = new Flags(new ClientFlagsHelper(source));
  }

  /**
   * Returns an instance of a ClientFlagsBase object, which allows users to
   * access flags passed from WFE.
   *
   * If we are running in hosted mode, fall back to using default flags.
   */
  public static Flags get() {
    if (instance == null) {
      List<TypedSource> sources = new ArrayList<>();      
      
      // Parameters got from URL have the biggest priority.
      try {
        TypedSource sourceUrl = UrlParameters.get();
        if (sourceUrl != null) {
          sources.add(sourceUrl);
        }
      } catch (Exception e) {
        // do nothing
      }        

      try {
        // Parameters got from JS have lower priority.
        ExtendedJSObject jsObj = getJSObj();
        if (jsObj != null) {
          sources.add(new WrappedJSObject(jsObj));
        }
      } catch (Exception e) {
        // do nothing
      }
      
      instance = new Flags(new ClientFlagsHelper(sources));
    }
    return instance;
  }
}
