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
 * Wrapper for thread and its navigational data.
 * 
 * @param <T> thread type
 * @param <B> blip type
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
class Thread<T, B> {
  
  private final T inner;
  private final boolean root;
  private final boolean inline;
  private final List<Blip<T, B>> childBlips = new ArrayList<>();    

  private Blip<T, B> parentBlip;
  private int level;

  public Thread(T inner, boolean root, boolean inline) {
    this.inner = inner;
    this.root = root;
    this.inline = inline;
  }

  //
  // Setters and modifiers
  //

  public void setParentBlip(Blip<T, B> parentBlip) {
    this.parentBlip = parentBlip;
  }

  public void clearChildBlips() {
    childBlips.clear();
  }
  
  public void addChildBlip(Blip<T, B> blip) {
    childBlips.add(blip);
  }

  public void addChildBlip(Blip<T, B> previous, Blip<T, B> blip) {
    int index = previous == null ? 0 : childBlips.indexOf(previous) + 1;
    childBlips.add(index, blip);
  }

  public void removeChildBlip(Blip<T, B> blip) {
    childBlips.remove(blip);
  }

  public void setLevel(int level) {
    this.level = level;
  }

  //
  // Getters
  //

  public T getInner() {
    return inner;
  }

  public boolean isRoot() {
    return root;
  }

  public boolean isInline() {
    return inline;
  }

  public boolean isRowOwner() {
    return root || inline;
  }

  public Blip<T, B> getParentBlip() {
    return parentBlip;
  }

  public Thread<T, B> getParentThread() {
    return Blip.getParentThread(parentBlip);
  }
  
  public boolean isPredecessorOfThread(Thread<T, B> thread) {
    if (thread == null) {
      return false;
    }
    for (Thread<T, B> t = thread.getParentThread(); t != null; t = t.getParentThread()) {
      if (t == this) {
        return true;
      }
    }
    return false;
  }

  public boolean isPredecessorOfBlip(Blip<T, B> blip) {
    if (blip == null) {
      return false;
    }
    Thread<T, B> t = blip.getParentThread();
    return t == this || (t != null && isPredecessorOfThread(t));
  }
  
  public Iterator<Blip<T, B>> getChildBlips() {
    return childBlips.iterator();
  }    

  public int getChildBlipCount() {
    return childBlips.size();
  }  
  
  public boolean hasChildBlips() {
    return !childBlips.isEmpty();
  }
  
  public int getIndexOfChildBlip(Blip<T, B> blip) {
    return childBlips.indexOf(blip);
  }
  
  public int getIndexInParentBlip() {
    return parentBlip != null ? parentBlip.getIndexOfChildThread(this) : -1;
  }  
  
  public Blip<T, B> getChildBlipByIndex(int index) {
    return index >= 0 && index < childBlips.size() ? childBlips.get(index) : null;
  }  
  
  public Blip<T, B> getFirstChildBlip() {
    return childBlips.isEmpty() ? null : childBlips.get(0);
  }

  public Blip<T, B> getLastChildBlip() {
    return childBlips.isEmpty() ? null : childBlips.get(childBlips.size() - 1);
  }

  public Blip<T, B> getPreviousChildBlip(Blip<T, B> nextBlip) {
    int prevIndex = nextBlip == null ? childBlips.size() - 1 : childBlips.indexOf(nextBlip) - 1;
    return prevIndex >= 0 ? childBlips.get(prevIndex) : null;
  }    

  public Blip<T, B> getNextChildBlip(Blip<T, B> previousBlip) {
    int nextIndex = previousBlip == null ? 0 : childBlips.indexOf(previousBlip) + 1;
    return nextIndex < childBlips.size() ? childBlips.get(nextIndex) : null;
  }

  public Thread<T, B> getPreviousThreadInParentBlip() {
    return parentBlip == null ? null : parentBlip.getPreviousChildThread(this);
  }

  public Thread<T, B> getNextThreadInParentBlip() {
    return parentBlip == null ? null : parentBlip.getNextChildThread(this);
  }

  public Blip<T, B> getPreviousBlip() {
    Blip<T, B> firstBlip = getFirstChildBlip();
    return firstBlip != null ? firstBlip.getPreviousBlip() : null;
  }
  
  public Blip<T, B> getNextBlip() {
    Blip<T, B> lastBlip = getLastBlipInTree(false);
    return lastBlip != null ? lastBlip.getNextBlip() : null;
  }

  public Blip<T, B> getPreviousBlipInRow() {
    Blip<T, B> firstBlip = getFirstChildBlip();
    return firstBlip != null ? firstBlip.getPreviousBlipInRow() : null;
  }
  
  public Blip<T, B> getNextBlipInRow() {
    Blip<T, B> lastBlip = getLastBlipInTree(true);
    return lastBlip != null ? lastBlip.getNextBlipInRow() : null;
  }  
  
  public int getLevel() {
    return level;
  }

  public Blip<T, B> getRowOwnerBlip() {
    return parentBlip == null || isRowOwner() ? parentBlip : parentBlip.getRowOwnerBlip();
  }

  public Thread<T, B> getRowOwnerThread() {
    return parentBlip != null ? parentBlip.getRowOwnerThread() : null;
  }
  
  public Blip<T, B> getLastBlipInTree(boolean inSameRow) {
    Blip<T, B> lastBlip = getLastChildBlip();
    return lastBlip != null ? lastBlip.getLastBlipInTree(inSameRow) : null;
  }    

  /**
   * @return count of the thread's child blips which must not be bigger than value of limit.
   *
   * @param limit the limit of count value
   */  
  public int getChildBlipCount(int limit) {
    int count = 0;
    Iterator<Blip<T, B>> it = getChildBlips();
    while (it.hasNext()) {
      Blip<T, B> blip = it.next();
      count++;
      limit--;
      int delta = blip.getChildBlipCount(limit, true);
      count += delta;
      limit -= delta;
      if (limit <= 0) {
        return count + limit;
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
        ", parentB=" + Blip.toString(parentBlip) +
        ", level=" + level +
        ", prevT=" + getInner(getPreviousThreadInParentBlip()) +
        ", nextT=" + getInner(getNextThreadInParentBlip()) + "]";
  }

  public static <T, B> String toString(Thread<T, B> thread) {
    return thread != null ? thread.getInner().toString() : "null";
  }

  public static <T, B> T getInner(Thread<T, B> thread) {
    return thread != null ? thread.getInner() : null;
  }

  public static <T, B> Blip<T, B> getParentBlip(Thread<T, B> thread) {
    return thread != null ? thread.getParentBlip() : null;
  }    
}
