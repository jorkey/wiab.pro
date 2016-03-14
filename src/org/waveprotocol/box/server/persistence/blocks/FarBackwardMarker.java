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

package org.waveprotocol.box.server.persistence.blocks;

/**
 * Links to previous far version.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public interface FarBackwardMarker extends Marker {
  /**
   * Gets distance to previous far version.
   */
  long getDistanceToPreviousFarVersion();
  
  /**
   * Gets offset of top marker of previous far version.
   */
  int getPreviousFarMarkerOffset();

  /**
   * Gets offset of aggregated operation from far previous version or -1 if not defined.
   */
  int getFromPreviousFarVersionOperationOffset();
}
