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

/**
 * Supplement-like interface relevant for a single wavelet in a conversation.
 * Exposes the overriding first-look version and per-blip versions.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
interface WaveletLookState {
  
  void setWaveletFirstLookVersion(long version);  
  void setBlipFirstLookVersion(String blipId, long version);
  long getWaveletFirstLookVersion();  
  long getBlipFirstLookVersion(String blipId);
  void clearWaveletLookState();
  Iterable<String> getLookedBlips();  
  void clearBlipLookState(String blipId);
  void remove();
}