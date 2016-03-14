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

package org.waveprotocol.wave.client.wavepanel.render;

/**
 * Measurer of rendered elements.
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 * 
 * @param <T> type of rendered elements
 */
public interface ElementMeasurer<T> {
  
  /**
   * @param element
   * @return top coordinate of the element
   */
  int getTop(T element);

  /**
   * @param element
   * @return height of the element, in pixels
   */
  int getHeight(T element);  
  
  /**
   * @param element
   * @return bottom coordinate of the element
   */
  int getBottom(T element);  
}
