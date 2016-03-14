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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Wrapper for blip and its navigational data.
 * 
 * @param <T> thread type
 * @param <B> blip type
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
class Blip<T, B> {
  
  private final B inner;
  private final List<Thread<T, B>> childThreads = new ArrayList<>();    

  private Thread<T, B> parentThread;
  private Thread<T, B> rowOwnerThread;    
  private Blip<T, B> previousBlip;
  private Blip<T, B> nextBlip;
  private Blip<T, B> previousBlipInRow;
  private Blip<T, B> nextBlipInRow;

  public Blip(B inner) {
    this.inner = inner;
  }

  //
  // Setters and modifiers
  //

  public void setParentThread(Thread<T, B> parentThread) {
    this.parentThread = parentThread;
  }

  public void clearChildThreads() {
    childThreads.clear();
  }
  
  public void addChildThread(Thread<T, B> thread) {
    childThreads.add(thread);
  }

  public void addChildThread(Thread<T, B> previous, Thread<T, B> thread) {
    int index = previous == null ? 0 : childThreads.indexOf(previous) + 1;
    childThreads.add(index, thread);
  }

  public void removeChildThread(Thread<T, B> thread) {
    childThreads.remove(thread);
  }

  public void placeChildThreadAfter(Thread<T, B> thread, Thread<T, B> previousThread) {
    childThreads.remove(thread);
    int index = previousThread != null ? childThreads.indexOf(previousThread) + 1 : 0;
    childThreads.add(index, thread);
  }  
  
  public void setRowOwnerThread(Thread<T, B> rowOwnerThread) {
    this.rowOwnerThread = rowOwnerThread;
  }    

  public void setPreviousBlip(Blip<T, B> previousBlip) {
    this.previousBlip = previousBlip;
  }

  public void setNextBlip(Blip<T, B> nextBlip) {
    this.nextBlip = nextBlip;
  }

  public void setPreviousBlipInRow(Blip<T, B> previousBlipInRow) {
    this.previousBlipInRow = previousBlipInRow;
  }

  public void setNextBlipInRow(Blip<T, B> nextBlipInRow) {
    this.nextBlipInRow = nextBlipInRow;
  }    

  //
  // Getters
  //

  public B getInner() {
    return inner;
  }

  public Thread<T, B> getParentThread() {
    return parentThread;
  }

  public Blip<T, B> getParentBlip() {
    return Thread.getParentBlip(parentThread);
  }

  public Blip<T, B> getRootParentBlip() {
    Blip<T, B> b = this;
    while (b != null && !b.getParentThread().isRoot()) {
      b = b.getParentBlip();
    }  
    return b;
  }

  public boolean isPredecessorOfBlip(Blip<T, B> blip) {
    if (blip == null) {
      return false;
    }
    for (Blip<T, B> b = blip.getParentBlip(); b != null; b = b.getParentBlip()) {
      if (b == this) {
        return true;
      }
    }
    return false;
  }

  public boolean isPredecessorOfThread(Thread<T, B> thread) {
    if (thread == null) {
      return false;
    }
    Blip<T, B> b = thread.getParentBlip();
    return b == this || (b != null && isPredecessorOfBlip(b));
  }  
  
  public Iterator<Thread<T, B>> getChildThreads() {
    return childThreads.iterator();
  }

  public int getChildThreadCount() {
    return childThreads.size();
  }
  
  public boolean hasChildThreads() {
    return !childThreads.isEmpty();
  }  
  
  public int getIndexOfChildThread(Thread<T, B> thread) {
    return childThreads.indexOf(thread);
  }
  
  public int getIndexInParentThread() {
    return parentThread.getIndexOfChildBlip(this);
  }
  
  public Thread<T, B> getChildThreadByIndex(int index) {
    return index >= 0 && index < childThreads.size() ? childThreads.get(index) : null;
  }
  
  public Thread<T, B> getFirstChildThread() {
    return childThreads.isEmpty() ? null : childThreads.get(0);
  }

  public Thread<T, B> getLastChildThread() {
    return childThreads.isEmpty() ? null : childThreads.get(childThreads.size() - 1);
  }

  public Thread<T, B> getPreviousChildThread(Thread<T, B> nextThread) {
    int prevIndex = nextThread == null ? childThreads.size() - 1 :
        childThreads.indexOf(nextThread) - 1;
    return prevIndex >= 0 ? childThreads.get(prevIndex) : null;
  }    

  public Thread<T, B> getNextChildThread(Thread<T, B> previousThread) {
    int nextIndex = previousThread == null ? 0 : childThreads.indexOf(previousThread) + 1;
    return nextIndex < childThreads.size() ? childThreads.get(nextIndex) : null;
  }

  public Thread<T, B> getPreviousNonEmptyChildThread(Thread<T, B> nextThread) {
    int index = nextThread == null ? childThreads.size() - 1 : childThreads.indexOf(nextThread) - 1;
    for (int i = index; i >= 0; i--) {
      Thread<T, B> thread = childThreads.get(i);
      if (thread.hasChildBlips()) {
        return thread;
      }
    }
    return null;
  }    

  public Thread<T, B> getNextNonEmptyChildThread(Thread<T, B> previousThread) {
    int index = previousThread == null ? 0 : childThreads.indexOf(previousThread) + 1;
    for (int i = index; i < childThreads.size(); i++) {
      Thread<T, B> thread = childThreads.get(i);
      if (thread.hasChildBlips()) {
        return thread;
      }
    }
    return null;
  }  
  
  public boolean isFirstInParentThread() {
    return parentThread.getFirstChildBlip() == this;
  }

  public boolean isLastInParentThread() {
    return parentThread.getLastChildBlip() == this;
  }  
  
  public Thread<T, B> getRowOwnerThread() {
    return rowOwnerThread;
  }    

  public Blip<T, B> getRowOwnerBlip() {
    return Thread.getParentBlip(rowOwnerThread);
  }

  public Blip<T, B> getPreviousBlip() {
    return previousBlip;
  }

  public Blip<T, B> getNextBlip() {
    return nextBlip;
  }

  public Blip<T, B> getPreviousBlipInRow() {
    return previousBlipInRow;
  }

  public Blip<T, B> getNextBlipInRow() {
    return nextBlipInRow;
  }

  public Blip<T, B> getPreviousBlipInParentThread() {
    return parentThread == null ? null : parentThread.getPreviousChildBlip(this);
  }

  public Blip<T, B> getNextBlipInParentThread() {
    return parentThread == null ? null : parentThread.getNextChildBlip(this);
  }
  
  public Blip<T, B> getLastBlipInTree(boolean inSameRow) {
    for (int i = childThreads.size() - 1; i >= 0; i--) {
      Thread<T, B> thread = childThreads.get(i);
      if ((!inSameRow || !thread.isInline()) && thread.hasChildBlips()) {
        return thread.getLastBlipInTree(inSameRow);
      }  
    }
    return this;
  }

  public Blip<T, B> getNextBlipOfTree() {
    Blip<T, B> lastBlip = getLastBlipInTree(false);
    return lastBlip != null ? lastBlip.getNextBlip() : null;
  }

  public Blip<T, B> getNextBlipInRowOfTree() {
    Blip<T, B> lastBlip = getLastBlipInTree(true);
    return lastBlip != null ? lastBlip.getNextBlipInRow() : null;
  }  
  
  public int getLevel() {
    return parentThread != null ? parentThread.getLevel() : -1;
  }

  public int getIndentationLevel() {
    int level = getLevel();
    Blip<T, B> rowOwnerBlip = getRowOwnerBlip();
    if (rowOwnerBlip != null) {
      level -= (rowOwnerBlip.getLevel() + 1);
    }
    return level;
  }

  /** @return the highest blip's parent in its row or blip itself, if such parent doesn't exist. */
  public Blip<T, B> getHighestParentInRow() {
    Blip<T, B> blip = this;
    Thread<T, B> thread = blip.getParentThread();
    while (thread != null && !thread.isRowOwner()) {
      blip = thread.getParentBlip();
      thread = Blip.getParentThread(blip);
    }
    return blip;
  }

  /**
   * @return true, if the blip is situated within interval [start blip, finish blip] in logic order.
   * 
   * @param <T> thread type
   * @param <B> blip type
   * @param blip the blip
   * @param startBlip start blip
   * @param finishBlip finish blip
   */
  public static <T, B> boolean isBetween(Blip<T, B> blip, Blip<T, B> startBlip,
      Blip<T, B> finishBlip) {
    // Simple cases.
    if (startBlip == null || finishBlip == null) {
      return false;
    }
    if (blip == startBlip || blip == finishBlip) {
      return true;
    }

    // Row owner threads of cluster and blip must be the same.
    Thread<T, B> blipRowOwnerThread = blip.getRowOwnerThread();
    if (startBlip.getRowOwnerThread() != blipRowOwnerThread
        || finishBlip.getRowOwnerThread() != blipRowOwnerThread) {
      return false;
    }

    // Blip is before the start blip or after the finish blip.      
    Blip<T, B> blipHighestParent = blip.getHighestParentInRow();
    Blip<T, B> startHighestParent = startBlip.getHighestParentInRow();
    Blip<T, B> finishHighestParent = finishBlip.getHighestParentInRow();
    if (blipHighestParent == startHighestParent
        && isBetweenInRow(blip, startHighestParent, startBlip)
        || (blipHighestParent == finishHighestParent
        && isBetweenInRow(finishBlip, finishHighestParent, blip)) ) {
      return false;
    }    

    // Blip's highest parent in row must be between highest parents of interval's first and last blips.
    return isBetweenInRow(blipHighestParent, startHighestParent, finishHighestParent);
  }

  /**
   * @return true, if the blip is situated in the row within interval [start blip, finish blip].
   * 
   * @param <T> thread type
   * @param <B> blip type
   * @param blip the blip
   * @param startBlip start blip
   * @param finishBlip finish blip
   */
  public static <T, B> boolean isBetweenInRow(Blip<T, B> blip, Blip<T, B> startBlip,
      Blip<T, B> finishBlip) {
    for (Blip<T, B> b = startBlip; ; b = b.getNextBlipInRow()) {
      if (b == blip) {
        return true;
      }
      if (b == finishBlip) {
        return false;
      }
    }
  }
  
  /**
   * @return count of the blip's child blips which must not be bigger than value of limit.
   *
   * @param limit the limit of count value
   * @param outlineAllowed true, if blips in outline threads are counted, too
   */  
  public int getChildBlipCount(int limit, boolean outlineAllowed) {
    int count = 0;
    Iterator<Thread<T, B>> it = getChildThreads();
    while (it.hasNext()) {
      Thread<T, B> thread = it.next();
      if (outlineAllowed || thread.isInline()) {
        int delta = thread.getChildBlipCount(limit);
        count += delta;
        limit -= delta;
        if (limit <= 0) {
          return count + limit;
        }
      }
    }
    return count;
  }  
  
  //
  // Utils
  //    

  @Override
  public String toString() {
    return "[inner=" + inner +
        ", parentT=" + Thread.toString(parentThread) +
        ", rowOwnerT=" + Thread.toString(rowOwnerThread) +
        ", prevB=" + Blip.toString(previousBlip) +
        ", nextB=" + Blip.toString(nextBlip) +
        ", prevBIR=" + Blip.toString(previousBlipInRow) +
        ", nextBIR=" + Blip.toString(nextBlipInRow) + "]";
  }

  public static <T, B> String toString(Blip<T, B> blip) {
    return blip != null ? blip.getInner().toString() : "null";
  }

  public static <T, B> B getInner(Blip<T, B> blip) {
    return blip != null ? blip.getInner() : null;
  }

  public static <T, B> Thread<T, B> getParentThread(Blip<T, B> blip) {
    return blip != null ? blip.getParentThread() : null;
  }
}
