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
package org.waveprotocol.wave.model.supplement;

import org.waveprotocol.wave.model.util.Serializer;

/**
 * A serializer for ScreenPosition objects.
 */
public final class ScreenPositionSerializer {

  private final static String TILDA = "~";
  
  public static final Serializer<ScreenPosition> INSTANCE = new Serializer<ScreenPosition>() {
        
    @Override
    public ScreenPosition fromString(String s) {
      if (s == null) {
        return null;
      }

      int tildaPos1 = s.indexOf(TILDA);      
      String blipId = s.substring(0, tildaPos1);
      int tildaPos2 = s.indexOf(TILDA, tildaPos1 + 1);
      if (tildaPos2 == -1) {
        return new ScreenPosition(blipId);
      }

      int paragraphOffset = -1;
      double relativeOffset = 0;
      try {
        paragraphOffset = Integer.parseInt(s.substring(tildaPos1 + 1, tildaPos2));        
        relativeOffset = Double.parseDouble(s.substring(tildaPos2 + 1));
      } catch (NumberFormatException e) {        
      }
      return new ScreenPosition(blipId, paragraphOffset, relativeOffset);
    }

    @Override
    public ScreenPosition fromString(String s, ScreenPosition defaultValue) {
      return s != null ? fromString(s) : defaultValue;
    }

    @Override
    public String toString(ScreenPosition x) {
      return x == null ? null : x.getBlipId() + TILDA + x.getParagraphOffset() + TILDA
          + x.getRelativeOffset();
    }
  };

  private ScreenPositionSerializer() {
  }
}
