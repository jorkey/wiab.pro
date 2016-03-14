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

import com.google.common.annotations.VisibleForTesting;

import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.wave.model.util.Preconditions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Navigator implementation.
 * 
 * @param <T> thread type
 * @param <B> blip type
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class NavigatorImpl<T, B> implements Navigator<T, B> {

  private Adapter<T, B> adapter;
  
  @VisibleForTesting
  final Map<B, Blip<T, B>> blipIndex = new HashMap<>();
  
  @VisibleForTesting
  final Map<T, Thread<T, B>> threadIndex = new HashMap<>();
  
  public NavigatorImpl() {
  }

  public void init(Adapter<T, B> adapter) {
    this.adapter = adapter;
    
    indexAll();
    observeAdapter();    
  }
  
  //
  // Navigator interface implementation
  //

  @Override
  public void reindexChildInlineThreads(B blip) {
    Blip<T, B> parentBlip = getBlipData(blip);
    Blip<T, B> nextBlip = parentBlip.getNextBlipOfTree();
    boolean foundOutlineThread = false;
    Thread<T, B> previousThread = null;
    Blip<T, B> previousBlip = parentBlip;
    
    Iterator<? extends T> it = adapter.getThreads(blip);
    while (it.hasNext()) {
      Thread<T, B> thread = getThreadData(it.next());
      if (thread.isInline()) {
        parentBlip.placeChildThreadAfter(thread, previousThread);
        if (thread.hasChildBlips()) {
          linkBlips(previousBlip, thread.getFirstChildBlip());
          previousBlip = thread.getLastBlipInTree(false);
        }
        previousThread = thread;
      } else if (!foundOutlineThread && thread.hasChildBlips()) {
        nextBlip = thread.getFirstChildBlip();
        foundOutlineThread = true;
      }
    }
    linkBlips(previousBlip, nextBlip);
  }
  
  @Override
  public T getBlipParentThread(B blip) {
    return Thread.getInner(getBlipData(blip).getParentThread());
  }  
  
  @Override
  public B getThreadParentBlip(T thread) {
    return Blip.getInner(getThreadData(thread).getParentBlip());
  }  
  
  @Override
  public B getBlipParentBlip(B blip) {
    return Blip.getInner(getBlipData(blip).getParentBlip());
  }

  @Override
  public T getThreadParentThread(T thread) {
    return Thread.getInner(getThreadData(thread).getParentThread());
  }
  
  @Override
  public T getBlipRowOwnerThread(B blip) {
    return Thread.getInner(getBlipData(blip).getRowOwnerThread());
  }
 
  @Override
  public B getThreadRowOwnerBlip(T thread) {
    return Blip.getInner(getThreadData(thread).getRowOwnerBlip());
  }

  @Override
  public B getBlipRowOwnerBlip(B blip) {
    return Blip.getInner(getBlipData(blip).getRowOwnerBlip());
  }  

  @Override
  public B getRootParentBlip(B blip) {
    return Blip.getInner(getBlipData(blip).getRootParentBlip());
  }

  @Override
  public boolean isBlipPredecessorOfBlip(B parentBlip, B childBlip) {
    return getBlipData(parentBlip).isPredecessorOfBlip(getBlipData(childBlip));
  }

  @Override
  public boolean isBlipPredecessorOfThread(B parentBlip, T childThread) {
    return getBlipData(parentBlip).isPredecessorOfThread(getThreadData(childThread));    
  }

  @Override
  public boolean isThreadPredecessorOfBlip(T parentThread, B childBlip) {
    return getThreadData(parentThread).isPredecessorOfBlip(getBlipData(childBlip));    
  }

  @Override
  public boolean isThreadPredecessorOfThread(T parentThread, T childThread) {
    return getThreadData(parentThread).isPredecessorOfThread(getThreadData(childThread));    
  }
  
  @Override
  public int getChildBlipCount(T thread) {
    return getThreadData(thread).getChildBlipCount();
  }

  @Override
  public int getChildThreadCount(B blip) {
    return getBlipData(blip).getChildThreadCount();
  }

  @Override
  public B getChildBlipByIndex(T thread, int index) {
    return Blip.getInner(getThreadData(thread).getChildBlipByIndex(index));
  }

  @Override
  public T getChildThreadByIndex(B blip, int index) {
    return Thread.getInner(getBlipData(blip).getChildThreadByIndex(index));
  }

  @Override
  public int getIndexOfBlipInParentThread(B blip) {
    return getBlipData(blip).getIndexInParentThread();
  }

  @Override
  public int getIndexOfThreadInParentBlip(T thread) {
    return getThreadData(thread).getIndexInParentBlip();
  }
  
  @Override
  public B getFirstBlip(T thread) {
    return Blip.getInner(getThreadData(thread).getFirstChildBlip());
  }

  @Override
  public B getLastBlip(T thread) {
    return Blip.getInner(getThreadData(thread).getLastChildBlip());
  }  

  @Override
  public boolean isBlipFirstInParentThread(B blip) {
    return getBlipData(blip).isFirstInParentThread();
  }

  @Override
  public boolean isBlipLastInParentThread(B blip) {
    return getBlipData(blip).isLastInParentThread();
  }
  
  @Override
  public B getNextBlip(B blip) {
    return Blip.getInner(getBlipData(blip).getNextBlip());
  }

  @Override
  public B getPreviousBlip(B blip) {
    return Blip.getInner(getBlipData(blip).getPreviousBlip());
  }

  @Override
  public B getNextBlipInRow(B blip) {
    return Blip.getInner(getBlipData(blip).getNextBlipInRow());
  }

  @Override
  public B getPreviousBlipInRow(B blip) {
    return Blip.getInner(getBlipData(blip).getPreviousBlipInRow());
  }

  @Override
  public B getNextBlipInParentThread(B blip) {
    return Blip.getInner(getBlipData(blip).getNextBlipInParentThread());
  }

  @Override
  public B getPreviousBlipInParentThread(B blip) {
    return Blip.getInner(getBlipData(blip).getPreviousBlipInParentThread());    
  }
  
  @Override
  public T getFirstThread(B blip) {
    return Thread.getInner(getBlipData(blip).getFirstChildThread());
  }
  
  @Override
  public T getLastThread(B blip) {
    return Thread.getInner(getBlipData(blip).getLastChildThread());
  }  
  
  @Override
  public T getPreviousThread(T thread) {
    return Thread.getInner(getThreadData(thread).getPreviousThreadInParentBlip());
  }

  @Override
  public T getNextThread(T thread) {
    return Thread.getInner(getThreadData(thread).getNextThreadInParentBlip());
  }

  @Override
  public B getLastBlipInThreadTree(T thread, boolean inSameRow) {
    return Blip.getInner(getThreadData(thread).getLastBlipInTree(inSameRow));
  }  

  @Override
  public B getLastBlipInBlipTree(B blip, boolean inSameRow) {
    return Blip.getInner(getBlipData(blip).getLastBlipInTree(inSameRow));
  }

  @Override
  public int getThreadLevel(T thread) {
    return getThreadData(thread).getLevel();
  }
  
  @Override
  public int getBlipLevel(B blip) {
    return getBlipData(blip).getLevel();
  }

  @Override
  public int getBlipIndentationLevel(B blip) {
    return getBlipData(blip).getIndentationLevel();
  }
  
  @Override
  public boolean isBlipBetween(B blip, B startBlip, B finishBlip) {
    return Blip.isBetween(getBlipData(blip), getBlipData(startBlip),
        getBlipData(finishBlip));
  }
  
  @Override
  public void findNeighborBlips(Iterable<B> startBlips, Receiver<B> blipReceiver, boolean inSameRow) {
    List<Blip<T, B>> startBlipData = new ArrayList<>();
    for (B blip : startBlips) {
      startBlipData.add(getBlipData(blip));
    }
    SearchUtil.findNeighborBlips(startBlipData, blipReceiver, inSameRow);
  }  

  @Override
  public void breadthFirstSearchFromThread(T startThread, Receiver<B> blipReceiver,
      boolean inSameRow) {
    SearchUtil.breadthFirstSearchFromThread(getThreadData(startThread), blipReceiver,
        inSameRow);
  }

  @Override
  public void breadthFirstSearchFromBlip(B startBlip, Receiver<B> blipReceiver, boolean inSameRow) {
    SearchUtil.breadthFirstSearchFromBlip(getBlipData(startBlip), blipReceiver, inSameRow);
  }  

  @Override
  public int calculateChildBlipCount(B blip, int limit, boolean outlineAllowed) {
    return getBlipData(blip).getChildBlipCount(limit, outlineAllowed);
  }

  @Override
  public int calculateChildBlipCount(T thread, int limit) {
    return getThreadData(thread).getChildBlipCount(limit);
  }

  @Override
  public int calculateChildBlipCountBetween(B startBlip, B finishBlip, int limit) {
    if (startBlip == null || finishBlip == null) {
      return 0;
    }
    int count = 0;    
    for (B blip = startBlip; count < limit; blip = getNextBlipInRow(blip)) {
      count++;
      count += calculateChildBlipCount(blip, limit - count, false);
      if (blip == finishBlip) {
        break;
      }
    }
    return count;
  }
  
  //
  // Private methods
  //  
  
  // Blip and thread data
  
  /** Gets indexing data for the given blip. */
  private Blip<T, B> getBlipData(B blip) {
    if (blip == null) {
      return null;
    }
    Blip<T, B> blipData = blipIndex.get(blip);
    
    Preconditions.checkNotNull(blipData, "Blip data not found in index");
    
    return blipData;
  }

  /** Creates indexing data for the given blip. */
  private Blip<T, B> createBlipData(B blip) {    
    Preconditions.checkState(!blipIndex.containsKey(blip), "Index already contains data for blip");
    
    Blip<T, B> blipData = new Blip<>(blip);
    blipIndex.put(blip, blipData);
    return blipData;
  }
  
  /** Removes indexing data for the given blip. */
  private void removeBlipData(B blip) {
    Preconditions.checkState(blipIndex.containsKey(blip), "Index doesn't contain data for blip");
    
    blipIndex.remove(blip);
  }

  /** Gets indexing data for the given thread. */
  private Thread<T, B> getThreadData(T thread) {
    if (thread == null) {
      return null;
    }
    Thread<T, B> threadData = threadIndex.get(thread);
    
    Preconditions.checkNotNull(threadData, "Thread data not found in index");
    
    return threadData;
  }  

  /** Creates indexing data for the given thread. */
  private Thread<T, B> createThreadData(T thread) {
    Preconditions.checkState(!threadIndex.containsKey(thread), "Index already contains data for thread");
    
    Thread<T, B> threadData = new Thread(thread, adapter.getRootThread() == thread,
        adapter.isThreadInline(thread));
    threadIndex.put(thread, threadData);
    return threadData;
  }  
  
  /** Removes indexing data for the given thread. */
  private void removeThreadData(T thread) {
    Preconditions.checkState(threadIndex.containsKey(thread), "Index doesn't contain data for thread");
    
    threadIndex.remove(thread);
  }  
  
  /** Makes "next" and "previous" links between two given blips. */
  private void linkBlips(Blip<T, B> previousBlip, Blip<T, B> nextBlip) {
    if (previousBlip != null) {
      previousBlip.setNextBlip(nextBlip);
    }
    if (nextBlip != null) {
      nextBlip.setPreviousBlip(previousBlip);
    }    
  }

  /** Makes "next" and "previous" links between two given blips in the row. */
  private void linkBlipsInRow(Blip<T, B> previousBlipInRow, Blip<T, B> nextBlipInRow) {
    if (previousBlipInRow != null) {
      previousBlipInRow.setNextBlipInRow(nextBlipInRow);
    }
    if (nextBlipInRow != null) {
      nextBlipInRow.setPreviousBlipInRow(previousBlipInRow);
    }    
  }  

  //
  // Indexing
  //

  /** Indexing of whole structure starting from the root thread. */
  private void indexAll() {
    indexThread(createThreadData(adapter.getRootThread()), null, null,
        IndexContext.<Blip<T, B>>createEmpty());
  }
  
  protected void clearIndex() {
    blipIndex.clear();
    threadIndex.clear();
  }
  
  /**
   * Indexes the given thread.
   * 
   * @param thread the given thread
   * @param parentBlip parent blip
   * @param rowOwnerThread row owner thread
   * @param context the index context
   */
  private void indexThread(Thread<T, B> thread, Blip<T, B> parentBlip, Thread<T, B> rowOwnerThread,
      IndexContext<Blip<T, B>> context) {
    thread.setParentBlip(parentBlip);
    thread.setLevel(parentBlip != null ? parentBlip.getLevel() + 1 : 0);
    thread.clearChildBlips();

    if (thread.isRowOwner()) {
      rowOwnerThread = thread;
      context.pushPreviousBlipInRow(null);
    }
    
    Iterator<? extends B> it = adapter.getBlips(thread.getInner());
    while (it.hasNext()) {
      Blip<T, B> blip = createBlipData(it.next());
      thread.addChildBlip(blip);
      indexBlip(blip, thread, rowOwnerThread, context);
    }

    if (thread.isRowOwner()) {
      context.popPreviousBlipInRow();
    }
  }
  
  /**
   * Indexes the given blip.
   * 
   * @param blip the given blip
   * @param parentThread parent thread
   * @param rowOwnerThread row owner thread
   * @param context the index context
   */
  private void indexBlip(Blip<T, B> blip, Thread<T, B> parentThread, Thread<T, B> rowOwnerThread,
      IndexContext<Blip<T, B>> context) {
    blip.setParentThread(parentThread);
    blip.setRowOwnerThread(rowOwnerThread);
    blip.clearChildThreads();
    
    linkBlips(context.getPreviousBlip(), blip);    
    context.setPreviousBlip(blip);
    linkBlipsInRow(context.popPreviousBlipInRow(), blip);
    context.pushPreviousBlipInRow(blip);    
    
    Iterator<? extends T> it = adapter.getThreads(blip.getInner());
    while (it.hasNext()) {
      Thread<T, B> thread = createThreadData(it.next());
      blip.addChildThread(thread);
      indexThread(thread, blip, rowOwnerThread, context);
    }
  }
  
  /** Removes thread data and all its children from the index. */
  private void unindexThread(Thread<T, B> thread) {
    Iterator<Blip<T, B>> it = thread.getChildBlips();
    while (it.hasNext()) {
      unindexBlip(it.next());
    }    
    removeThreadData(thread.getInner());
  }
  
  /** Removes blip data and all its children from the index. */
  private void unindexBlip(Blip<T, B> blip) {
    Iterator<Thread<T, B>> it = blip.getChildThreads();
    while (it.hasNext()) {
      unindexThread(it.next());
    }    
    removeBlipData(blip.getInner());
  }

  private void indexThreadAndMakeLinks(Blip<T, B> parentBlip, Thread<T, B> previousThread,
      Thread<T, B> thread, Thread<T, B> nextThread) {
    Blip<T, B> previousBlip;
    Blip<T, B> nextBlip;
    Blip<T, B> previousBlipInRow = null;
    Blip<T, B> nextBlipInRow = null;
    
    // Calculate previous and next blips by neighbor threads skipping empty threads.
    Thread<T, B> nt = nextThread;
    while (nt != null && !nt.hasChildBlips()) {
      nt = nt.getNextThreadInParentBlip();
    }
    if (nt != null) {
      nextBlip = nt.getFirstChildBlip();
      previousBlip = nextBlip.getPreviousBlip();
    } else {
      Thread<T, B> pt = previousThread;
      while (pt != null && !pt.hasChildBlips()) {
        pt = pt.getPreviousThreadInParentBlip();
      }
      if (pt != null) {
        previousBlip = pt.getLastBlipInTree(false);
        nextBlip = previousBlip.getNextBlip();
      } else {
        previousBlip = parentBlip;
        nextBlip = parentBlip.getNextBlip();
      }      
    }
    
    Thread<T, B> rowOwnerThread = thread.isRowOwner() ? thread : parentBlip.getRowOwnerThread();    
    if (!thread.isInline()) {
      if (previousBlip != null) {
        previousBlipInRow = previousBlip.getRowOwnerThread() == rowOwnerThread ? previousBlip
            : parentBlip;
      }
      if (nextBlip != null) {
        nextBlipInRow = nextBlip.getRowOwnerThread() == rowOwnerThread ? nextBlip
            : parentBlip.getNextBlipInRow();
      }
    }  
    
    if (parentBlip != null) {
      parentBlip.addChildThread(previousThread, thread);
    }
    indexThread(thread, parentBlip, rowOwnerThread, IndexContext.create(previousBlip, previousBlipInRow));
    if (thread.hasChildBlips()) {
      linkBlips(thread.getLastBlipInTree(false), nextBlip);
      linkBlipsInRow(thread.getLastBlipInTree(true), nextBlipInRow);
    }  
  }
  
  private void unindexThreadAndBreakLinks(Thread<T, B> thread) {
    Blip<T, B> parentBlip = thread.getParentBlip();    
    if (parentBlip != null) {
      parentBlip.removeChildThread(thread);
    }    
    linkBlips(thread.getPreviousBlip(), thread.getNextBlip());
    linkBlipsInRow(thread.getPreviousBlipInRow(), thread.getNextBlipInRow());
    unindexThread(thread);
  }
  
  private void indexBlipAndMakeLinks(Thread<T, B> parentThread, Blip<T, B> previousBlipInThread,
      Blip<T, B> blip, Blip<T, B> nextBlipInThread) {
    Blip<T, B> previousBlip = null;
    Blip<T, B> nextBlip = null;
    Blip<T, B> previousBlipInRow = null;
    Blip<T, B> nextBlipInRow = null;
    if (nextBlipInThread != null) {
      nextBlip = nextBlipInThread;
      previousBlip = nextBlip.getPreviousBlip();
      nextBlipInRow = nextBlipInThread;
      previousBlipInRow = nextBlipInRow.getPreviousBlipInRow();
    } else if (previousBlipInThread != null) {
      previousBlip = previousBlipInThread.getLastBlipInTree(false);
      nextBlip = previousBlip.getNextBlip();
      previousBlipInRow = previousBlipInThread.getLastBlipInTree(true);
      nextBlipInRow = previousBlipInRow.getNextBlipInRow();
    } else {
      Blip<T, B> parentBlip = parentThread.getParentBlip();
      if (parentBlip != null) {
        Thread<T, B> prevParentThread = parentBlip.getPreviousNonEmptyChildThread(parentThread);
        if (prevParentThread != null) {
          previousBlip = prevParentThread.getLastBlipInTree(false);
        } else {
          previousBlip = parentBlip;
        }
        nextBlip = previousBlip.getNextBlip();
        if (!parentThread.isInline()) {
          if (prevParentThread != null && !prevParentThread.isInline()) {
            previousBlipInRow = previousBlip;
          } else {
            previousBlipInRow = parentBlip;
          }  
          nextBlipInRow = previousBlipInRow.getNextBlipInRow();
        }
      }
    }
    
    parentThread.addChildBlip(previousBlipInThread, blip);
    Thread<T, B> rowOwnerThread = parentThread.isRowOwner() ? parentThread
        : parentThread.getRowOwnerThread();    
    indexBlip(blip, parentThread, rowOwnerThread, IndexContext.create(previousBlip, previousBlipInRow));
    linkBlips(blip.getLastBlipInTree(false), nextBlip);
    linkBlipsInRow(blip.getLastBlipInTree(true), nextBlipInRow);
  }
  
  private void unindexBlipAndBreakLinks(Blip<T, B> blip) {
    linkBlips(blip.getPreviousBlip(), blip.getNextBlipOfTree());
    linkBlipsInRow(blip.getPreviousBlipInRow(), blip.getNextBlipInRowOfTree());
    blip.getParentThread().removeChildBlip(blip);    
    unindexBlip(blip);
  }
  
  //
  // Observing adapter
  //
  
  private void observeAdapter() {
    adapter.addListener(new Adapter.Listener<T, B>() {

      @Override
      public void onReplyAdded(B parentBlip, T previousThread, T thread, T nextThread) {
        indexThreadAndMakeLinks(getBlipData(parentBlip), getThreadData(previousThread),
            createThreadData(thread), getThreadData(nextThread));
      }

      @Override
      public void onReplyRemoved(T thread) {
        unindexThreadAndBreakLinks(getThreadData(thread));
      }

      @Override
      public void onBlipAdded(T parentThread, B previousBlip, B blip, B nextBlip) {
        indexBlipAndMakeLinks(getThreadData(parentThread), getBlipData(previousBlip),
            createBlipData(blip), getBlipData(nextBlip));
      }

      @Override
      public void onBlipRemoved(B blip) {
        unindexBlipAndBreakLinks(getBlipData(blip));
      }
    });    
  }
  
  //
  // Logging
  //
  
  @VisibleForTesting
  String indexToString() {
    StringBuilder sb = new StringBuilder();
    sb.append("BlipIndex\n").append(blipIndexToString())
        .append("\nThreadIndex\n").append(threadIndexToString());
    return sb.toString();
  }
  
  String getForwardTraverse() {
    List<B> blips = new ArrayList<>();
    for (B blip = getFirstBlip(adapter.getRootThread()); blip != null; blip = getNextBlip(blip)) {
      blips.add(blip);
    }
    return blipsToString(blips);
  }  

  String getBackwardTraverse() {
    List<B> blips = new ArrayList<>();
    for (B blip = getLastBlipInThreadTree(adapter.getRootThread(), false); blip != null;
        blip = getPreviousBlip(blip)) {
      blips.add(blip);
    }
    return blipsToString(blips);
  }  

  String getForwardTraverseInRow() {
    List<B> blips = new ArrayList<>();
    for (B blip = getFirstBlip(adapter.getRootThread()); blip != null; blip = getNextBlipInRow(blip)) {
      blips.add(blip);
    }
    return blipsToString(blips);
  }

  String getBackwardTraverseInRow() {
    List<B> blips = new ArrayList<>();
    for (B blip = getLastBlipInThreadTree(adapter.getRootThread(), true); blip != null;
        blip = getPreviousBlipInRow(blip)) {
      blips.add(blip);
    }
    return blipsToString(blips);
  }  
  
  String blipIndexToString() {
    StringBuilder sb = new StringBuilder();
    Blip<T, B> startBlip = getBlipData(getFirstBlip(adapter.getRootThread()) );
    for (Blip<T, B> blip = startBlip; blip != null; blip = blip.getNextBlip()) {
      sb.append(blip).append("\n");
    }
    return sb.toString();
  }
  
  String threadIndexToString() {
    StringBuilder sb = new StringBuilder();
    Blip<T, B> startBlip = getBlipData(getFirstBlip(adapter.getRootThread()) );
    for (Blip<T, B> blip = startBlip; blip != null; blip = blip.getNextBlip()) {
      Iterator<Thread<T, B>> threads = blip.getChildThreads();
      while (threads.hasNext()) {
        sb.append(threads.next()).append("\n");
      }  
    }
    return sb.toString();
  }
  
  private static <T, B> String blipsToString(List<B> blips) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < blips.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      B blip = blips.get(i);
      sb.append(blip);
    }
    sb.append("]");
    return sb.toString();
  }  
}
