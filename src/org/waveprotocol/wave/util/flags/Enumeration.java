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

import java.util.ArrayList;
import java.util.List;

/**
 * Enumeration type definition for client flags parameter.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class Enumeration {

  private final String name;
  private final List<String> values;

  /**
   * Creates enumeration by name and line.
   * 
   * @param name enumeration name
   * @param line enumeration definition line
   * @return 
   */
  public static Enumeration create(String name, String line) {
    String[] valueStrings = line.split(" ");
    List<String> values = new ArrayList<>();
    for (String valueString : valueStrings) {
      values.add(valueString.trim());
    }
    return new Enumeration(name, values);
  }  
  
  public Enumeration(String name, List<String> values) {
    this.name = name;
    this.values = values;
  }

  public String getName() {
    return name;
  }

  public List<String> getValues() {
    return values;
  }  
}
