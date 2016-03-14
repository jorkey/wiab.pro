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

import org.waveprotocol.box.common.Receiver;

import java.util.List;

/**
 * Navigation on the wave conversation structure.
 *
 * @param <T> thread type
 * @param <B> blip type
 * 
 * @author dyukon@gmail.com (D. Konovalchik)
 */
public interface Navigator<T, B> {
  
  //
  // Reindex
  //
  
  /**
   * Remakes index of the given blip's child inline threads.
   * To call after parent blip's document is uploaded.
   * 
   * @param blip the given blip
   */
  void reindexChildInlineThreads(B blip);
  
  //
  // Parents and children
  //

  /**
   * @return the parent thread of the given blip.
   * 
   * @param blip the given blip
   */
  T getBlipParentThread(B blip);  
  
  /**
   * @return the parent blip of the given thread.
   * 
   * @param thread the given thread
   */
  B getThreadParentBlip(T thread);  
  
  /**
   * @return the parent blip of the given blip.
   * 
   * @param blip the given blip
   */
  B getBlipParentBlip(B blip);  

  /**
   * @return the parent thread of the given thread.
   * 
   * @param thread the given thread
   */
  T getThreadParentThread(T thread);  
  
  /**
   * @return the row owner thread for the given blip.
   * 
   * @param blip the given blip
   */
  T getBlipRowOwnerThread(B blip);
  
  /**
   * @return the row owner blip for the given thread.
   * 
   * @param thread the given thread
   */
  B getThreadRowOwnerBlip(T thread);

  /**
   * @return the row owner blip for the given blip.
   * 
   * @param blip the given blip
   */
  B getBlipRowOwnerBlip(B blip);

  /**
   * @return the parent blip of the given blip which belongs to the root thread.
   * 
   * @param blip the given blip
   */
  B getRootParentBlip(B blip);  

  /**
   * @return true, if the parent blip is a parent (at any level) of the child blip.
   * 
   * @param parentBlip parent blip
   * @param childBlip child blip
   */
  boolean isBlipPredecessorOfBlip(B parentBlip, B childBlip);

  /**
   * @return true, if the parent blip is a parent (at any level) of the child thread.
   * 
   * @param parentBlip parent blip
   * @param childThread child thread
   */
  boolean isBlipPredecessorOfThread(B parentBlip, T childThread);

  /**
   * @return true, if the parent thread is a parent (at any level) of the child blip.
   * 
   * @param parentThread parent thread
   * @param childBlip child blip
   */
  boolean isThreadPredecessorOfBlip(T parentThread, B childBlip);

  /**
   * @return true, if the parent thread is a parent (at any level) of the child thread.
   * 
   * @param parentThread parent thread
   * @param childThread child thread
   */
  boolean isThreadPredecessorOfThread(T parentThread, T childThread);  
  
  //
  // Counts and indexes
  //
  
  /**
   * @return child blip count of the given thread.
   * 
   * @param thread the given thread
   */
  int getChildBlipCount(T thread);
  
  /**
   * @return child thread count of the given blip.
   * 
   * @param blip the given blip
   */  
  int getChildThreadCount(B blip);
  
  /**
   * @return child blip of the given thread by its index.
   * 
   * @param thread the given thread
   * @param index blip index
   */  
  B getChildBlipByIndex(T thread, int index);

  /**
   * @return child thread of the given blip by its index.
   * 
   * @param blip the given blip
   * @param index thread index
   */  
  T getChildThreadByIndex(B blip, int index);
  
  /**
   * @return index of the given blip in its parent thread.
   * 
   * @param blip the given blip
   */
  int getIndexOfBlipInParentThread(B blip);
  
  /**
   * @return index of the given thread in its parent blip.
   * 
   * @param thread the given thread
   */  
  int getIndexOfThreadInParentBlip(T thread);
  
  //
  // Navigation
  //
  
  /**
   * @return the first blip in the given thread.
   * 
   * @param thread the given thread
   */
  B getFirstBlip(T thread);
  
  /**
   * @return the last blip in the given thread.
   * 
   * @param thread the given thread
   */
  B getLastBlip(T thread);  
  
  /**
   * @return true, if the given blip is first in its parent thread.
   * 
   * @param blip the given blip
   */
  boolean isBlipFirstInParentThread(B blip);

  /**
   * @return true, if the given blip is last in its parent thread.
   * 
   * @param blip the given blip
   */
  boolean isBlipLastInParentThread(B blip);
  
  /**
   * @return the next blip in the logic order (after the given blip).
   * 
   * @param blip the given blip
   */
  B getNextBlip(B blip);

  /**
   * @return the previous blip in the logic order (before the given blip).
   * 
   * @param blip the given blip
   */
  B getPreviousBlip(B blip);

  /**
   * @return the next blip in the row (after the given blip).
   * 
   * @param blip the given blip
   */
  B getNextBlipInRow(B blip);

  /**
   * @return the previous blip in the row (before the given blip).
   * 
   * @param blip the given blip
   */
  B getPreviousBlipInRow(B blip);
    
  /**
   * @return the next blip in the parent thread (after the given blip).
   * 
   * @param blip the given blip
   */
  B getNextBlipInParentThread(B blip);

  /**
   * @return the previous blip in the parent thread (before the given blip).
   * 
   * @param blip the given blip
   */
  B getPreviousBlipInParentThread(B blip);
  
  /**
   * @return the first thread of the given blip.
   * 
   * @param blip the given blip
   */
  T getFirstThread(B blip);

  /**
   * @return the last thread of the given blip.
   * 
   * @param blip the given blip
   */
  T getLastThread(B blip);  
  
  /**
   * @return the previous sibling thread of the given thread.
   * 
   * @param thread the given thread
   */
  T getPreviousThread(T thread);
  
  /**
   * @return the next sibling thread of the given thread.
   * 
   * @param thread the given thread
   */  
  T getNextThread(T thread);
  
  /**
   * @return the last blip in the given thread's tree
   * 
   * @param thread the given thread
   * @param inSameRow true, if the resulting blip must belong to the same row as the thread's blips
   */
  B getLastBlipInThreadTree(T thread, boolean inSameRow);  
  
  /**
   * @return the last blip in the given blip's tree
   * 
   * @param blip the given blip
   * @param inSameRow true, if the resulting blip must belong to the same row as the given blip
   */
  B getLastBlipInBlipTree(B blip, boolean inSameRow);
  
  //
  // Levels
  //

  /**
   * @return level of the given thread in hierarchy.
   * 
   * @param thread the given thread
   */
  int getThreadLevel(T thread);
  
  /**
   * @return level of the given blip in hierarchy.
   * 
   * @param blip the given blip
   */
  int getBlipLevel(B blip);

  /**
   * @return indentation level of the given blip.
   * 
   * @param blip the given blip
   */
  int getBlipIndentationLevel(B blip);  
  
  //
  // Utility methods
  //
  
  /**
   * @return true, if the given blip belongs to the [start blip, finish blip]
   * interval in logic order.
   * 
   * @param blip the given blip
   * @param startBlip start blip
   * @param finishBlip finish blip
   */
  boolean isBlipBetween(B blip, B startBlip, B finishBlip);
  
  /**
   * Finds neighbor blips for given start blips.
   * 
   * @param startBlips start blip list
   * @param blipReceiver receiver processing found blips
   * @param inSameRow true, if neighbors must be found in the same row as start blips
   */  
  void findNeighborBlips(Iterable<B> startBlips, Receiver<B> blipReceiver, boolean inSameRow);
  
  /**
   * Breadth-first search starting from the given start thread.
   *
   * @param startThread start thread
   * @param blipReceiver receiver to process found blips
   * @param inSameRow true, if all found blips must be in the same row
   */
  void breadthFirstSearchFromThread(T startThread, Receiver<B> blipReceiver, boolean inSameRow);

  /**
   * Breadth-first search starting from the given blip.
   *
   * @param startBlip blip to start search with
   * @param blipReceiver receiver to process found blips
   * @param inSameRow all found blips must be in the same row
   */
  void breadthFirstSearchFromBlip(B startBlip, Receiver<B> blipReceiver, boolean inSameRow);
  
  /**
   * @return recursive count of the blip's child blips which must not be bigger than value of limit.
   *
   * @param blip the parent blip
   * @param limit the limit of count value
   * @param outlineAllowed true, if blips in outline threads are counted, too
   */
  int calculateChildBlipCount(B blip, int limit, boolean outlineAllowed);
  
  /**
   * @return recursive count of the thread's child blips which must not be bigger than value of limit.
   *
   * @param thread the parent thread
   * @param limit the limit of count value
   */
  int calculateChildBlipCount(T thread, int limit);
  
  /**
   * @return recursive count of child blips of blips between start blip and finish blip.
   * The count must not be bigger than value of limit.
   *
   * @param startBlip the start blip
   * @param finishBlip the finish blip
   * @param limit the limit of count value
   */
  int calculateChildBlipCountBetween(B startBlip, B finishBlip, int limit);  
}
