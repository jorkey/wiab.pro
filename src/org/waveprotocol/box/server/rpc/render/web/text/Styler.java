/**
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.waveprotocol.box.server.rpc.render.web.text;

import java.util.Set;


/**
 * A Utility for generating CSS styles.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class Styler {
  
  /**
   * Simple interface for providing parameters.
   *
   * @author David Byttow
   */
  interface Params {
    /**
     * @return the value of the given key.
     */
    String get(String name);

    /**
     * @return set of all parameter names.
     */
    Set<String> nameSet();
  }
  
  /**
   * Takes a bunch of params from the embed api and generates an HTML style.
   * <pre>
   * bgcolor: "white",
   * color: "black",
   * font: "Arial",
   * fontSize: "8pt",
   * header: false,
   * footer: false,
   * toolbar: false,
   * rootUrl: WAVE_ROOT_URL,
   * width: 481,
   * height: 500
   * </pre>
   */
  public static String fromEmbedOptions(Params params) {
    StringBuilder builder = new StringBuilder();
    builder.append(".styleable {");
    addStyleRule(builder, "color", "color", params);
    addStyleRule(builder, "bgcolor", "background-color", params);
    addStyleRule(builder, "font", "font-family", params);
    addStyleRule(builder, "fontSize", "font-size", params);
    builder.append("}");
    return builder.toString();
  }

  private static void addStyleRule(StringBuilder builder, String param, String style,
                                   Params params) {
    String value = params.get(param);
    if (null != value) {
      builder.append(style);
      builder.append(":");
      builder.append(value);
      builder.append(";");
    }
  }
}
