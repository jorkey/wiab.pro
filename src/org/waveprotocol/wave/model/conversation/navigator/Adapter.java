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

package org.waveprotocol.wave.model.conversation.navigator;

import java.util.Iterator;

/**
 * Adapter for conversation structure.
 * 
 * @param <T> thread type
 * @param <B> blip type
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 * @author dyukon@gmail.com (D. Konovalchik)
 */
public interface Adapter<T, B> {
  
  /**
   * Handles changes on conversation structure.
   * 
   * @param <T> thread type
   * @param <B> blip type
   */
  public interface Listener<T, B> {
    
    /**
     * Notifies this listener that a thread was added.
     * 
     * @param parentBlip parent blip of the added thread
     * @param previousThread the previous thread
     * @param thread the added thread
     * @param nextThread the next thread
     */
    void onReplyAdded(B parentBlip, T previousThread, T thread, T nextThread);

    /**
     * Notifies this listener that a thread was removed.
     * 
     * @param thread the removed thread
     */
    void onReplyRemoved(T thread);

    /**
     * Notifies this listener that a blip was added.
     * 
     * @param parentThread parent thread of the added blip
     * @param previousBlip the previous bliop
     * @param blip the added blip
     * @param nextBlip the next blip
     */
    void onBlipAdded(T parentThread, B previousBlip, B blip, B nextBlip);

    /**
     * Notifies this listener that a blip was removed.
     * 
     * @param blip the removed blip
     */
    void onBlipRemoved(B blip);
  }

  /**
   * @return root thread of the structure
   */
  T getRootThread();
  
  /**
   * @return iterator over blips of the given thread
   * 
   * @param thread the given thread
   */
  Iterator<? extends B> getBlips(T thread);

  /**
   * @return iterator over threads of the given blip
   * 
   * @param blip the given blip
   */  
  Iterator<? extends T> getThreads(B blip);
  
  /**
   * @return true, if the given thread is inline
   * 
   * @param thread the given thread
   */
  boolean isThreadInline(T thread);
  
  /**
   * Adds listener.
   * 
   * @param listener
   */
  public void addListener(final Listener<T, B> listener);
}
