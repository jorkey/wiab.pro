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
package org.waveprotocol.wave.util.flags;

import org.waveprotocol.wave.model.util.ValueUtils;

/**
 * Client flags parameter definition.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class Parameter {

  // Parameter types.
  public static final String TYPE_BOOLEAN = "boolean";
  public static final String TYPE_DOUBLE = "double";
  public static final String TYPE_INT = "int";
  public static final String TYPE_STRING = "String";

  private final String key;
  private final String value;
  private final Enumeration enumeration;
  private final String comment;
  
  private String type;
  private String smallCamelCaseKey;
  private String bigCamelCaseKey;
  private String capitalCaseType;

  /**
   * Creates parameter by key, line and comment.
   * 
   * @param key parameter key
   * @param line parameter line
   * @param comment parameter comment
   * @return new parameter
   */
  public static Parameter create(String key, String line, String comment) {
    String value;
    Enumeration enumeration = null;
    if (!line.contains("\"") && line.contains(" ")) {
      int spacePos = line.indexOf(" ");
      value = line.substring(0, spacePos).trim();
      String enumLine = line.substring(spacePos + 1).trim();
      enumeration = Enumeration.create(ValueUtils.toCamelCase(key, "_", true),
          ValueUtils.stripFrom(enumLine, "{", "}"));
    } else {
      value = line;
    }
    return new Parameter(key, value, enumeration, comment);
  }  
  
  public Parameter(String key, String value, Enumeration enumeration, String comment) {
    this.key = key;
    this.value = value;
    this.enumeration = enumeration;
    this.comment = comment;
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }

  public Enumeration getEnumeration() {
    return enumeration;
  }

  public String getComment() {
    return comment;
  }

  public String getType() {
    if (type == null) {
      if (enumeration != null) {
        type = enumeration.getName();
      } else if (value.startsWith("\"") && value.endsWith("\"")) {
        type = TYPE_STRING;
      } else if (value.equals("true") || value.equals("false")) {
        type = TYPE_BOOLEAN;
      } else {
        type = value.contains(".") ? TYPE_DOUBLE : TYPE_INT;
      }  
    }
    return type;
  }
  
  public String getSmallCamelCaseKey() {
    if (smallCamelCaseKey == null) {
      smallCamelCaseKey = ValueUtils.toCamelCase(key, "_", false);
    }
    return smallCamelCaseKey;
  }
  
  public String getBigCamelCaseKey() {
    if (bigCamelCaseKey == null) {
      bigCamelCaseKey = ValueUtils.toCamelCase(key, "_", true);
    }
    return bigCamelCaseKey;
  }
  
  public String getCapitalCaseType() {
    if (capitalCaseType == null) {
      capitalCaseType = ValueUtils.toCapitalCase(type);
    }
    return capitalCaseType;
  }
}
