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

package org.waveprotocol.box.server.util;

import org.waveprotocol.box.clientflags.ClientFlags;
import org.waveprotocol.box.clientflags.FlagConstants;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.util.ValueUtils;
import org.waveprotocol.wave.util.logging.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Utility methods for work with client flags.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class ClientFlagsUtil {

  /**
   * Extracts parameters from configuration file and converts them to JSON.
   * 
   * @param configFileName configuration file name
   * @param log message log
   * @return resulting JSON object
   */
  public static JSONObject convertParamsFromConfigFileToJson(String configFileName, Log log) {
    List<Pair<String, String>> params = extractParamsFromConfigFile(configFileName, true, log);
    return convertParamsToJson(params, false, log);
  }
  
  /**
   * Extracts parameters from configuration file and writes errors to the log.
   * 
   * @param configFileName configuration file name
   * @param checkParams true, if params should be checked for validity
   * @param log message log
   * @return pairs (name, value) of parameters
   */
  public static List<Pair<String, String>> extractParamsFromConfigFile(
      String configFileName, boolean checkParams, Log log) {
    try {
      List<Pair<String, String>> params = new ArrayList<>();
      PropertiesConfiguration configuration = new PropertiesConfiguration(configFileName);
      Iterator<String> keys = configuration.getKeys();
      while (keys.hasNext()) {
        String key = keys.next().trim();
        String name = ValueUtils.toCamelCase(key, "_", false);
        String value = configuration.getString(key).trim();
        if (checkParams) {
          try {
            checkParameter(name, value);
          } catch (IllegalArgumentException e) {
            warning(log, e.getMessage());
            continue;
          }
        }
        params.add(new Pair<>(name, value));        
      }
      return params;
    } catch (ConfigurationException e) {
      severe(log, "Failed to extract parameters from configuration file: " + e.getMessage());
      return new ArrayList<>();
    }  
  }
  
  /**
   * Converts list of parameters to JSON.
   * 
   * @param params pairs (name, value) of parameters
   * @param checkParams true, if params should be checked for validity
   * @param log message log
   * @return resulting JSON object
   */
  public static JSONObject convertParamsToJson(List<Pair<String, String>> params,
      boolean checkParams, Log log) {
    try {
      JSONObject result = new JSONObject();
      for (Pair<String, String> param : params) {
        String name = param.getFirst();
        String shortName = FlagConstants.getShortName(name);
        if (shortName != null) {
          String value = param.getSecond();
          if (checkParams) {
            try {
              checkParameter(name, value);
            } catch (IllegalArgumentException e) {
              warning(log, e.getMessage());              
            }
          }
          try {
            Method getter = ClientFlags.class.getMethod(name);
            Class<?> retType = getter.getReturnType();
            try {
              if (retType.equals(String.class)) {
                result.put(shortName, ValueUtils.stripFrom(value, "\""));
              } else if (retType.equals(int.class)) {
                result.put(shortName, Integer.parseInt(value));
              } else if (retType.equals(boolean.class)) {
                if (!value.equals("true") && !value.equals("false")) {
                  throw new IllegalArgumentException();
                }
                result.put(shortName, Boolean.parseBoolean(value));
              } else if (retType.equals(double.class)) {
                result.put(shortName, Double.parseDouble(value));
              } else if (retType.isEnum()) {
                result.put(shortName, value);
              } else {
                warning(log, String.format("Ignoring flag %s with unknown return type %s.",
                    name, retType));
              }
            } catch (IllegalArgumentException e) {
              warning(log, String.format("Failed to parse parameter: name=%s, type=%s, value=%s",
                  name, retType, value));
            }
          } catch (NoSuchMethodException e) {
            warning(log, String.format("Failed to find the method %s() in ClientFlags.", name));
          }
        } else {
          warning(log, String.format("Failed to find the flag %s in ClientFlags.", name));
        }
      }
      return result;
    } catch (JSONException e) {
      severe(log, String.format("Failed to create flags JSON: %s", e.getMessage()) );
      return new JSONObject();
    }
  }
  
  /**
   * Checks parameter for validity.
   * 
   * @param name parameter name in CamelCase format
   * @param value parameter value
   * @throws IllegalArgumentException if the parameter is invalid
   */
  public static void checkParameter(String name, String value) throws IllegalArgumentException {
    String shortName = FlagConstants.getShortName(name);
    if (shortName == null) {
      throw new IllegalArgumentException(String.format("Unknown parameter: %s!", name));
    }
    
    Method getter;
    try {
      getter = ClientFlags.class.getMethod(name);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(String.format("Unknown method %s() in ClientFlags class!",
          name));
    }

    Class<?> retType = getter.getReturnType();
    boolean correct = true;
    if (retType.equals(String.class)) {
      correct = ValueUtils.isEmbracedWith(value, "\"");
    } else if (retType.equals(int.class)) {
      try {
        Integer.parseInt(value);
      } catch (NumberFormatException e) {
        correct = false;
      }
    } else if (retType.equals(boolean.class)) {
      correct = value.equals("true") || value.equals("false");
    } else if (retType.equals(double.class)) {
      try {
        Double.parseDouble(value);
      } catch (NumberFormatException e) {
        correct = false;
      }
    } else if (retType.isEnum()) {
      // check enum
      String enumValues = ValueUtils.stripFrom(Arrays.toString(retType.getEnumConstants()), "[", "]");
      correct = false;
      String upperValue = value.toUpperCase();
      for (String enumValue : enumValues.split(",")) {
        if (upperValue.equals(enumValue.trim()) ) {
          correct = true;
          break;
        }  
      }  
    } else {
      throw new IllegalArgumentException(String.format("Unknown parameter type (%s) for parameter %s",
          retType, name));
    }
    
    if (!correct) {
      throw new IllegalArgumentException(String.format("Invalid parameter: name=%s, type=%s, value=%s",
          name, retType, value));
    }
  }
  
  private static void warning(Log log, String message) {
    if (log != null) {
      log.warning(message);
    }
  }
  
  private static void severe(Log log, String message) {
    if (log != null) {
      log.severe(message);
    }
  }
}
