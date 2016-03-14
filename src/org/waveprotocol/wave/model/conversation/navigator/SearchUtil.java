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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Queue;
import java.util.Set;

/**
 * Search methods library.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class SearchUtil {
  
  //
  // Breadth-first search
  //

  /**
   * Breadth-first blip search from the given start blip.
   *
   * @param <T> thread type
   * @param <B> blip type
   * @param startBlip start blip
   * @param blipReceiver receiver to process found blips
   * @param inSameRow true, if all found blips must be in the same row
   */    
  public static <T, B> void breadthFirstSearchFromBlip(Blip<T, B> startBlip,
      Receiver<B> blipReceiver, boolean inSameRow) {
    Queue<Blip<T, B>> blipQueue = new LinkedList<>();
    blipQueue.add(startBlip);
    breadthFirstSearchInner(blipQueue, blipReceiver, inSameRow);
  }    

  /**
   * Breadth-first blip search from the given start thread.
   *
   * @param <T> thread type
   * @param <B> blip type
   * @param startThread start thread
   * @param blipReceiver receiver to process found blips
   * @param inSameRow true, if all found blips must be in the same row
   */    
  public static <T, B> void breadthFirstSearchFromThread(Thread<T, B> startThread,
      Receiver<B> blipReceiver, boolean inSameRow) {
    Queue<Blip<T, B>> blipQueue = new LinkedList<>();
    Iterator<Blip<T, B>> it = startThread.getChildBlips();
    while (it.hasNext()) {
      blipQueue.add(it.next());
    }
    breadthFirstSearchInner(blipQueue, blipReceiver, inSameRow);      
  }

  //
  // Depth-first search
  //

  /**
   * Depth-first blip search from the given start blip.
   *
   * @param <T> thread type
   * @param <B> blip type
   * @param startBlip start blip
   * @param blipReceiver receiver to process found blips
   * @param inSameRow true, if all found blips must be in the same row
   */
  public static <T, B> void depthFirstSearch(Blip<T, B> startBlip,
      Receiver<B> blipReceiver, boolean inSameRow) {
    depthFirstSearchInner(startBlip, blipReceiver, inSameRow);
  }    

  /**
   * Depth-first blip search from the given start thread.
   *
   * @param <T> thread type
   * @param <B> blip type
   * @param startThread start thread
   * @param blipReceiver receiver to process found blips
   * @param inSameRow true, if all found blips must be in the same row
   */
  public static <T, B> void depthFirstSearch(Thread<T, B> startThread,
      Receiver<B> blipReceiver, boolean inSameRow) {
    Iterator<Blip<T, B>> it = startThread.getChildBlips();
    while (it.hasNext()) {
      if (!depthFirstSearchInner(it.next(), blipReceiver, inSameRow)) {
        break;
      }
    }
  }

  //
  // Neighbor blips search
  //
  
  /**
   * Finds neighbor blips for given start blips.
   * 
   * @param <T> thread type
   * @param <B> blip type
   * @param startBlips start blip list
   * @param blipReceiver receiver processing found blips
   * @param inSameRow true, if neighbors must be found in the same row as start blips
   */
  public static <T, B> void findNeighborBlips(Iterable<Blip<T, B>> startBlips,
      Receiver<B> blipReceiver, boolean inSameRow) {
    Set<Blip<T, B>> foundBlips = new HashSet<>();

    // init previous and next id's for each start blip
    List<Blip<T, B>> previousBlips = new ArrayList<>();
    List<Blip<T, B>> nextBlips = new ArrayList<>();
    for (Blip<T, B> blip : startBlips) {
      foundBlips.add(blip);
      previousBlips.add(inSameRow ? blip.getPreviousBlipInRow() : blip.getPreviousBlip());
      nextBlips.add(inSameRow ? blip.getNextBlipInRow() : blip.getNextBlip());
    }

    int oldFoundBlipCount;
    do {
      oldFoundBlipCount = foundBlips.size();

      // process previous blips
      for (ListIterator<Blip<T, B>> it = previousBlips.listIterator(); it.hasNext(); ) {
        Blip<T, B> blip = it.next();
        if (blip != null && !foundBlips.contains(blip)) {
          if (!blipReceiver.put(blip.getInner())) {
            return;
          }
          foundBlips.add(blip);
          it.set(inSameRow ? blip.getPreviousBlipInRow() : blip.getPreviousBlip());
        } else {
          it.remove();
        }  
      }

      // process next blips
      for (ListIterator<Blip<T, B>> it = nextBlips.listIterator(); it.hasNext(); ) {
        Blip<T, B> blip = it.next();
        if (blip != null && !foundBlips.contains(blip)) {
          if (!blipReceiver.put(blip.getInner())) {
            return;
          }
          foundBlips.add(blip);
          it.set(inSameRow ? blip.getNextBlipInRow() : blip.getNextBlip());
        } else {
          it.remove();
        }  
      }
    } while (foundBlips.size() > oldFoundBlipCount);      
  }  
  
  //
  // Private methods
  //

  private static <T, B> void breadthFirstSearchInner(Queue<Blip<T, B>> blipQueue,
      Receiver<B> blipReceiver, boolean inSameRow) {
    while (!blipQueue.isEmpty()) {
      Blip<T, B> blip = blipQueue.poll();
      if (blipReceiver.put(blip.getInner())) {
        Iterator<Thread<T, B>> threads = blip.getChildThreads();
        while (threads.hasNext()) {
          Thread<T, B> thread = threads.next();
          if (!inSameRow || !thread.isInline()) {
            Iterator<Blip<T, B>> blips = thread.getChildBlips();
            while (blips.hasNext()) {
              blipQueue.add(blips.next());
            }
          }
        }
      }
    }
  }
  
  private static <T, B> boolean depthFirstSearchInner(Blip<T, B> startBlip,
      Receiver<B> blipReceiver, boolean inSameRow) {
    Iterator<Thread<T, B>> threads = startBlip.getChildThreads();
    while (threads.hasNext()) {
      Thread<T, B> thread = threads.next();
      if (!inSameRow || !thread.isInline()) {
        Iterator<Blip<T, B>> blips = thread.getChildBlips();
        while (blips.hasNext()) {
          if (!depthFirstSearchInner(blips.next(), blipReceiver, inSameRow)) {
            return false;
          }
        }
      }
    }
    return blipReceiver.put(startBlip.getInner());
  }  
}
