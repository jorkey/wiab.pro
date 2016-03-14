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

import org.waveprotocol.wave.model.util.CopyOnWriteSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import junit.framework.TestCase;

/**
 * Test for NavigatorImpl.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class NavigatorTest extends TestCase {

  private static class Blip {
    
    public static Blip create(String id) {
      return new Blip(id);
    }
    
    private final String id;
    private final List<Thread> threads = new ArrayList<>();
    private Thread parent;
   
    private Blip(String id) {
      this.id = id;
    }
    
    public String getId() {
      return id;
    }
    
    public Iterator<Thread> getThreads() {
      return threads.iterator();
    }
    
    public void addThread(Thread thread) {
      Thread previousThread = threads.isEmpty() ? null : threads.get(threads.size() - 1);
      addThread(previousThread, thread);
    }
    
    public void addThread(Thread previousThread, Thread thread) {
      assertFalse(threads.contains(thread));
      assertTrue(previousThread == null || previousThread.isInline || !thread.isInline());
      
      int index = previousThread != null ? threads.indexOf(previousThread) + 1 : 0;
      threads.add(index, thread);
      thread.setParent(this);
      
      TestAdapter a = getAdapter();
      if (a != null) {
        int nextIndex = index + 1;
        Thread nextThread = nextIndex < threads.size() ? threads.get(nextIndex) : null;
        a.triggerOnReplyAdded(this, previousThread, thread, nextThread);
      }
    }
    
    public void removeThread(Thread thread) {      
      threads.remove(thread);
      thread.setParent(null);
      
      TestAdapter a = getAdapter();
      if (a != null) {
        a.triggerOnReplyRemoved(thread);
      }      
    }
    
    public void placeThreadAfter(Thread thread, Thread previousThread) {
      threads.remove(thread);
      int index = previousThread != null ? threads.indexOf(previousThread) + 1 : 0;
      threads.add(index, thread);
    }
    
    void setParent(Thread parent) {
      this.parent = parent;
    }
    
    private TestAdapter getAdapter() {
      return parent != null ? parent.getAdapter() : null;
    }

    @Override
    public String toString() {
      return id;
    }
  }

  private static class Thread {
    
    public static Thread createRoot(TestAdapter adapter) {
      return new Thread("", false, adapter);
    }

    public static Thread createInline(String id) {
      return new Thread(id, true, null);
    }

    public static Thread createOutline(String id) {
      return new Thread(id, false, null);
    }
    
    private final String id;
    private final boolean isInline;
    private final TestAdapter adapter;
    private final List<Blip> blips = new ArrayList<>();
    private Blip parent;
    
    private Thread(String id, boolean isInline, TestAdapter adapter) {
      this.id = id;
      this.isInline = isInline;
      this.adapter = adapter;
    }
    
    public String getId() {
      return id;
    }
    
    public boolean isInline() {
      return isInline;
    }
    
    public Iterator<Blip> getBlips() {
      return blips.iterator();
    }

    public void addBlip(Blip blip) {
      Blip previousBlip = blips.isEmpty() ? null : blips.get(blips.size() - 1);
      addBlip(previousBlip, blip);
    }    
    
    public void addBlip(Blip previousBlip, Blip blip) {
      assertFalse(blips.contains(blip));
      
      int index = previousBlip != null ? blips.indexOf(previousBlip) + 1 : 0;
      blips.add(index, blip);
      blip.setParent(this);
      
      TestAdapter a = getAdapter();
      if (a != null) {
        int nextIndex = index + 1;
        Blip nextBlip = nextIndex < blips.size() ? blips.get(nextIndex) : null;
        a.triggerOnBlipAdded(this, previousBlip, blip, nextBlip);
      }      
    }
    
    public void removeBlip(Blip blip) {      
      blips.remove(blip);
      blip.setParent(null);
      
      TestAdapter a = getAdapter();
      if (a != null) {
        a.triggerOnBlipRemoved(blip);
      }
    }    
    
    void setParent(Blip parent) {
      this.parent = parent;
    }
    
    private TestAdapter getAdapter() {
      return parent != null ? parent.getAdapter() : adapter;
    }
    
    @Override
    public String toString() {
      return id;
    }    
  }  
  
  private class TestAdapter implements Adapter<Thread, Blip> {

    private final Thread rootThread = new Thread("R", false, this);
    private final CopyOnWriteSet<Listener> listeners = CopyOnWriteSet.create();
    
    public TestAdapter() {
    }
    
    //
    // Adapter interface implementation
    //
    
    @Override
    public Thread getRootThread() {
      return rootThread;
    }

    @Override
    public Iterator getBlips(Thread thread) {
      return thread.getBlips();
    }

    @Override
    public Iterator getThreads(Blip blip) {
      return blip.getThreads();
    }

    @Override
    public boolean isThreadInline(Thread thread) {
      return thread.isInline;
    }

    @Override
    public void addListener(final Listener listener) {
      listeners.add(listener);
    }

    //
    // Public methods
    //
    
    public int getBlipCount() {
      return getChildBlipCount(rootThread);
    }

    public int getThreadCount() {
      return getChildThreadCount(rootThread) + 1;
    }
    
    public Blip findBlip(String blipId) {
      return findBlip(rootThread, blipId);
    }
    
    public Thread findThread(String threadId) {
      return findThread(rootThread, threadId);
    }
    
    //
    // Package private methods
    //
    
    void triggerOnReplyAdded(Blip parentBlip, Thread previousThread, Thread thread,
        Thread nextThread) {
      for (Listener l : listeners) {
        l.onReplyAdded(parentBlip, previousThread, thread, nextThread);
      }
    }
    
    void triggerOnReplyRemoved(Thread thread) {
      for (Listener l : listeners) {
        l.onReplyRemoved(thread);
      }
    }
    
    void triggerOnBlipAdded(Thread parentThread, Blip previousBlip, Blip blip, Blip nextBlip) {
      for (Listener l : listeners) {
        l.onBlipAdded(parentThread, previousBlip, blip, nextBlip);
      }
    }
    
    void triggerOnBlipRemoved(Blip blip) {
      for (Listener l : listeners) {
        l.onBlipRemoved(blip);
      }
    }
    
    //
    // Private methods
    //
    
    private int getChildBlipCount(Thread thread) {
      int count = 0;
      Iterator<? extends Blip> it = getBlips(thread);
      while (it.hasNext()) {      
        count += getChildBlipCount(it.next()) + 1;
      }
      return count;
    }

    private int getChildBlipCount(Blip blip) {
      int count = 0;
      Iterator<? extends Thread> it = getThreads(blip);
      while (it.hasNext()) {      
        count += getChildBlipCount(it.next());
      }
      return count;    
    }

    private int getChildThreadCount(Thread thread) {
      int count = 0;
      Iterator<? extends Blip> it = getBlips(thread);
      while (it.hasNext()) {      
        count += getChildThreadCount(it.next());
      }
      return count;
    }

    private int getChildThreadCount(Blip blip) {
      int count = 0;
      Iterator<? extends Thread> it = getThreads(blip);
      while (it.hasNext()) {
        count += getChildThreadCount(it.next()) + 1;
      }
      return count;    
    }    
    
    private Blip findBlip(Thread parentThread, String blipId) {
      Iterator<? extends Blip> it = parentThread.getBlips();
      while (it.hasNext()) {
        Blip blip = it.next();
        if (blip.getId().equals(blipId)) {
          return blip;
        }
        blip = findBlip(blip, blipId);
        if (blip != null) {
          return blip;
        }
      }
      return null;
    }

    private Blip findBlip(Blip parentBlip, String blipId) {
      Iterator<? extends Thread> it = parentBlip.getThreads();
      while (it.hasNext()) {
        Blip blip = findBlip(it.next(), blipId);
        if (blip != null) {
          return blip;
        }
      }
      return null;
    }    

    private Thread findThread(Blip parentBlip, String threadId) {
      Iterator<? extends Thread> it = parentBlip.getThreads();
      while (it.hasNext()) {
        Thread thread = it.next();
        if (thread.getId().equals(threadId)) {
          return thread;
        }
        thread = findThread(thread, threadId);
        if (thread != null) {
          return thread;
        }
      }
      return null;
    }

    private Thread findThread(Thread parentThread, String threadId) {
      Iterator<? extends Blip> it = parentThread.getBlips();
      while (it.hasNext()) {
        Thread thread = findThread(it.next(), threadId);
        if (thread != null) {
          return thread;
        }
      }
      return null;
    }
  }  
  
  private class TestNavigator extends NavigatorImpl<Thread, Blip> {
    
    public TestNavigator(Adapter<Thread, Blip> adapter) {
      super();
      init(adapter);
    }
    
    /** @return number of index records about blips. */
    public int getBlipIndexSize() {
      return blipIndex.size();
    }

    /** @return number of index records about threads. */
    public int getThreadIndexSize() {
      return threadIndex.size();
    }
  }
  
  private TestAdapter adapter;
  private TestNavigator navigator;
  
  //
  // Public methods
  //
    
  public void testImmediateIndex() {
    makeImmediateIndex();
    checkTraverse();
    checkTraverseInRow();
    checkIndexSizes();
  }
  
  public void testGradualIndex() {
    makeGradualIndex();
    checkTraverse();
    checkTraverseInRow();
    checkIndexSizes();
  }  
  
  public void testIndexEquality() {
    makeImmediateIndex();
    String immediateIndex = navigator.indexToString();
    makeGradualIndex();
    String gradualIndex = navigator.indexToString();
    assertEquals(immediateIndex, gradualIndex);
  }
  
  public void testBigImmediateIndex() {
    makeBigImmediateIndex();
    checkTraverse();
    checkTraverseInRow();
    checkIndexSizes();
  }
    
  public void testBigGradualIndex() {
    makeBigGradualIndex();    
    checkTraverse();
    checkTraverseInRow();
    checkIndexSizes();    
  }

  public void testBigIndexEquality() {
    makeBigImmediateIndex();
    String immediateIndex = navigator.indexToString();
    makeBigGradualIndex();
    String gradualIndex = navigator.indexToString();
    assertEquals(immediateIndex, gradualIndex);
  }  
  
  public void testReindex() {
    adapter = new TestAdapter();
    Thread rootThread = adapter.getRootThread();    
      Blip blipA = Blip.create("A"); rootThread.addBlip(blipA);
        Thread threadA1 = Thread.createInline("A1"); blipA.addThread(threadA1);
          Blip blipA1A = Blip.create("A1A"); threadA1.addBlip(blipA1A);
        Thread threadA2 = Thread.createInline("A2"); blipA.addThread(threadA2);
          Blip blipA2A = Blip.create("A2A"); threadA2.addBlip(blipA2A);
          Blip blipA2B = Blip.create("A2B"); threadA2.addBlip(blipA2B);
        Thread threadA3 = Thread.createInline("A3"); blipA.addThread(threadA3);
        Thread threadA4 = Thread.createInline("A4"); blipA.addThread(threadA4);
          Blip blipA4A = Blip.create("A4A"); threadA4.addBlip(blipA4A);
          Blip blipA4B = Blip.create("A4B"); threadA4.addBlip(blipA4B);
          Blip blipA4C = Blip.create("A4C"); threadA4.addBlip(blipA4C);
        Thread threadAa = Thread.createOutline("Aa"); blipA.addThread(threadAa);
        Thread threadAb = Thread.createOutline("Ab"); blipA.addThread(threadAb);      
          Blip blipAbA = Blip.create("AbA"); threadAb.addBlip(blipAbA);
      Blip blipB = Blip.create("B"); rootThread.addBlip(blipB);
        
    navigator = new TestNavigator(adapter);
    checkTraverse();
    checkTraverseInRow();
    checkIndexSizes();
    
    blipA.placeThreadAfter(threadA3, threadA4);
    blipA.placeThreadAfter(threadA2, threadA3);
    blipA.placeThreadAfter(threadA1, threadA2);
    navigator.reindexChildInlineThreads(blipA);
    checkTraverse();
    checkTraverseInRow();
    checkIndexSizes();    
  }  
  
  public void testReplyAddition() {
    makeImmediateIndex();
    
    Thread threadA2_ = Thread.createInline("A2_");
      Blip blipA2_A = Blip.create("A2_A"); threadA2_.addBlip(blipA2_A);
      Blip blipA2_C = Blip.create("A2_C"); threadA2_.addBlip(blipA2_C);
        Thread threadA2_C2 = Thread.createInline("A2_C2"); blipA2_C.addThread(threadA2_C2);
          Blip blipA2_C2A = Blip.create("A2_C2A"); threadA2_C2.addBlip(blipA2_C2A);
        Thread threadA2_C1 = Thread.createInline("A2_C1"); blipA2_C.addThread(null, threadA2_C1);
          Blip blipA2_C1A = Blip.create("A2_C1A"); threadA2_C1.addBlip(blipA2_C1A);            
      Blip blipA2_B = Blip.create("A2_B"); threadA2_.addBlip(blipA2_A, blipA2_B);       
    Blip blipA = adapter.findBlip("A");
    Thread threadA2 = adapter.findThread("A2");    
    blipA.addThread(threadA2, threadA2_);
    
    checkTraverse();
    checkTraverseInRow();
    checkIndexSizes();    
  }
    
  public void testReplyRemoval() {
    makeImmediateIndex();
    
    Blip blipA = adapter.findBlip("A");
    Thread threadA2 = adapter.findThread("A2");
    blipA.removeThread(threadA2);
    
    checkTraverse();
    checkTraverseInRow();
    checkIndexSizes();
  } 
  
  public void testBlipAddition() {
    makeImmediateIndex();
    
    Blip blipA1B_ = new Blip("A1B_");
      Thread threadA1B_1 = Thread.createInline("A1B_1");   blipA1B_.addThread(threadA1B_1);
        Blip blipA1B_1A = Blip.create("A1B_1A");   threadA1B_1.addBlip(blipA1B_1A);
        Blip blipA1B_1B = Blip.create("A1B_1B");   threadA1B_1.addBlip(blipA1B_1B);
      Thread threadA1B_2 = Thread.createInline("A1B_2");   blipA1B_.addThread(threadA1B_2);
        Blip blipA1B_2A = Blip.create("A1B_2A");   threadA1B_2.addBlip(blipA1B_2A);
        Blip blipA1B_2B = Blip.create("A1B_2B");   threadA1B_2.addBlip(blipA1B_2B);
      Thread threadA1B_a = Thread.createInline("A1B_a");   blipA1B_.addThread(threadA1B_a);
        Blip blipA1B_aA = Blip.create("A1B_aA");   threadA1B_a.addBlip(blipA1B_aA);
        Blip blipA1B_aB = Blip.create("A1B_aB");   threadA1B_a.addBlip(blipA1B_aB);
      Thread threadA1B_b = Thread.createInline("A1B_b");   blipA1B_.addThread(threadA1B_b);
        Blip blipA1B_bA = Blip.create("A1B_bA");   threadA1B_b.addBlip(blipA1B_bA);
        Blip blipA1B_bB = Blip.create("A1B_bB");   threadA1B_b.addBlip(blipA1B_bB);
    Thread threadA1 = adapter.findThread("A1");
    Blip blipA1B = adapter.findBlip("A1B");
    threadA1.addBlip(blipA1B, blipA1B_);
    
    checkTraverse();
    checkTraverseInRow();
    checkIndexSizes();    
  }
  
  public void testBlipRemoval() {
    makeImmediateIndex();
    
    Thread threadRoot = adapter.getRootThread();
    Blip blipB = adapter.findBlip("B");
    threadRoot.removeBlip(blipB);
    
    checkTraverse();
    checkTraverseInRow();
    checkIndexSizes();
  }
  
  public void testComplexAddition() {
    makeImmediateIndex();
    
    // Addition to the begin.
    
    Thread rootThread = adapter.getRootThread();
    Blip blip_X = Blip.create("X");
      Thread thread_X1 = Thread.createInline("X1");
        Blip blip_X1A = Blip.create("X1A"); thread_X1.addBlip(blip_X1A);
      blip_X.addThread(thread_X1);
      Thread thread_Xa = Thread.createOutline("Xa");
        Blip blip_XaA = Blip.create("XaA"); thread_Xa.addBlip(blip_XaA);
      blip_X.addThread(thread_Xa);
    rootThread.addBlip(null, blip_X);
    
    Thread thread_X2 = Thread.createInline("X2");
      Blip blip_X2A = Blip.create("X2A"); thread_X2.addBlip(blip_X2A);
    blip_X.addThread(thread_X1, thread_X2);
    
    Blip X2B = Blip.create("X2B");
    thread_X2.addBlip(X2B);
    
    Blip blip_X2C = Blip.create("X2C");
    thread_X2.addBlip(blip_X2C);
    
    Thread thread_X2Ba = Thread.createOutline("X2Ba");
      Blip blip_X2BaA = Blip.create("X2BaA"); thread_X2Ba.addBlip(blip_X2BaA);
    X2B.addThread(thread_X2Ba);
    
    Thread thread_X2Ca = Thread.createOutline("X2Ca");
      Blip blip_X2CaA = Blip.create("X2CaA"); thread_X2Ca.addBlip(blip_X2CaA);
    blip_X2C.addThread(thread_X2Ca);
    
    Thread thread_X2Aa = Thread.createOutline("X2Aa");
      Blip blip_X2AaA = Blip.create("X2AaA"); thread_X2Aa.addBlip(blip_X2AaA);
    blip_X2A.addThread(thread_X2Aa);
    
    // Addition to the end.
    
    Blip blip_D = Blip.create("D");
    rootThread.addBlip(blip_D);
    
    Thread thread_Da = Thread.createOutline("Da");
      Blip blip_DaA = Blip.create("DaA"); thread_Da.addBlip(blip_DaA);
      Blip blip_DaB = Blip.create("DaB"); thread_Da.addBlip(blip_DaB);
    blip_D.addThread(thread_Da);

    Thread thread_Db = Thread.createOutline("Db");
      Blip blip_DbA = Blip.create("DbA"); thread_Db.addBlip(blip_DbA);
    blip_D.addThread(thread_Db);
    
    checkTraverse();
    checkTraverseInRow();
    checkIndexSizes();    
  }
  
  //
  // Private methods
  //  
  
  private void makeImmediateIndex() {
    adapter = new TestAdapter();
    fillStructure();
    navigator = new TestNavigator(adapter);
  }

  private void makeBigImmediateIndex() {
    adapter = new TestAdapter();
    fillBigStructure();
    navigator = new TestNavigator(adapter);
  }
  
  private void makeGradualIndex() {
    adapter = new TestAdapter();
    navigator = new TestNavigator(adapter);
    fillStructure();
  }  

  private void makeBigGradualIndex() {
    adapter = new TestAdapter();
    navigator = new TestNavigator(adapter);
    fillBigStructure();
  }  
  
  private void fillStructure() {
    Thread rootThread = adapter.getRootThread();    
    Blip blipA = Blip.create("A");   rootThread.addBlip(blipA);
      Thread threadA1 = Thread.createInline("A1");   blipA.addThread(threadA1);
        Blip blipA1A = Blip.create("A1A");   threadA1.addBlip(blipA1A);
        Blip blipA1B = Blip.create("A1B");   threadA1.addBlip(blipA1B);
        Blip blipA1C = Blip.create("A1C");   threadA1.addBlip(blipA1C);
      Thread threadA2 = Thread.createInline("A2");   blipA.addThread(threadA2);
        Blip blipA2A = Blip.create("A2A");   threadA2.addBlip(blipA2A);
        Blip blipA2B = Blip.create("A2B");   threadA2.addBlip(blipA2B);
        Blip blipA2C = Blip.create("A2C");   threadA2.addBlip(blipA2C);
      Thread threadAa = Thread.createOutline("Aa");   blipA.addThread(threadAa);
        Blip blipAaA = Blip.create("AaA");   threadAa.addBlip(blipAaA);
        Blip blipAaB = Blip.create("AaB");   threadAa.addBlip(blipAaB);
      Thread threadAb = Thread.createOutline("Ab");   blipA.addThread(threadAb);
        Blip blipAbA = Blip.create("AbA");   threadAb.addBlip(blipAbA);
        Blip blipAbB = Blip.create("AbB");   threadAb.addBlip(blipAbB);
    Blip blipB = Blip.create("B");
        Thread threadB1 = Thread.createInline("B1");   blipB.addThread(threadB1);
          Blip blipB1A = Blip.create("B1A");   threadB1.addBlip(blipB1A);
          Blip blipB1B = Blip.create("B1B");   threadB1.addBlip(blipB1B);
    rootThread.addBlip(blipB);                
    Blip blipC = Blip.create("C");
      Thread threadCa = Thread.createOutline("Ca");   blipC.addThread(threadCa);
        Blip blipCaA = Blip.create("CaA");   threadCa.addBlip(blipCaA);
        Blip blipCaB = Blip.create("CaB");   threadCa.addBlip(blipCaB);
    rootThread.addBlip(blipC);                
  }
  
  int blipNumber;
  int threadNumber;
  
  private void fillBigStructure() {
    final int ROOT_BLIP_COUNT = 2;
    
    blipNumber = 0;
    threadNumber = 0;

    Thread rootThread = adapter.getRootThread();
    for (int i = 0; i < ROOT_BLIP_COUNT; i++) {
      createBlipForBigStructure(0, rootThread);
    }    
  }
  
  private void createBlipForBigStructure(int level, Thread parentThread) {
    final int LEVEL_COUNT = 2;
    final int INLINE_THREAD_COUNT = 1;
    final int CHILD_BLIP_COUNT = 1;

    blipNumber++;
    Blip blip = Blip.create("B" + blipNumber);
    parentThread.addBlip(blip);
    if (level < LEVEL_COUNT) {
      for (int j = 0; j < INLINE_THREAD_COUNT; j++) {
        threadNumber++;
        Thread inlineThread = Thread.createInline("T" + threadNumber);
        blip.addThread(inlineThread);
        for (int k = 0; k < CHILD_BLIP_COUNT; k++) {
          createBlipForBigStructure(level + 1, inlineThread);
        }
      }
      threadNumber++;
      Thread outlineThread = Thread.createOutline("T" + threadNumber);
      blip.addThread(outlineThread);
      for (int k = 0; k < CHILD_BLIP_COUNT; k++) {
        createBlipForBigStructure(level + 1, outlineThread);
      }
    }
  }  
  
  private void checkIndexSizes() {
    int blipCount = adapter.getBlipCount();
    int blipIndexSize = navigator.getBlipIndexSize();
    assertEquals(blipCount, blipIndexSize);
    
    int threadCount = adapter.getThreadCount();
    int threadIndexSize = navigator.getThreadIndexSize();
    assertEquals(threadCount, threadIndexSize);    
  }
  
  private void checkTraverse() {
    String forward = getForwardTraverse();
    String revBackward = getReversedBackwardTraverse();
    assertEquals(forward, revBackward);
  }
  
  private String getForwardTraverse() {
    List<Blip> blips = new ArrayList<>();
    for (Blip blip = navigator.getFirstBlip(adapter.getRootThread()); blip != null;
        blip = navigator.getNextBlip(blip)) {
      assertFalse(blips.contains(blip));
      blips.add(blip);
    }
    assertEquals(blips.size(), adapter.getBlipCount());    
    return blipsToString(blips);
  }
  
  private String getReversedBackwardTraverse() {
    List<Blip> blips = new ArrayList<>();
    for (Blip blip = navigator.getLastBlipInThreadTree(adapter.getRootThread(), false); blip != null;
        blip = navigator.getPreviousBlip(blip)) {
      assertFalse(blips.contains(blip));
      blips.add(blip);
    }
    assertEquals(blips.size(), adapter.getBlipCount());
    Collections.reverse(blips);    
    return blipsToString(blips);
  }
  
  private void checkTraverseInRow() {
    String forward = getForwardTraverseInRow();
    String revBackward = getReversedBackwardTraverseInRow();
    assertEquals(forward, revBackward);
    
    // Check that all neihbors-in-row have the same row owner thread.
    for (Blip blip = navigator.getFirstBlip(adapter.getRootThread()); blip != null;
        blip = navigator.getNextBlip(blip)) {
      Thread rowOwnerThread = navigator.getBlipRowOwnerThread(blip);
      Blip prevBlip = navigator.getPreviousBlipInRow(blip);
      if (prevBlip != null) {
        Thread prevRowOwnerThread = navigator.getBlipRowOwnerThread(prevBlip);
        assertEquals(rowOwnerThread, prevRowOwnerThread);
      }
      Blip nextBlip = navigator.getNextBlipInRow(blip);
      if (nextBlip != null) {
        Thread nextRowOwnerThread = navigator.getBlipRowOwnerThread(nextBlip);
        assertEquals(rowOwnerThread, nextRowOwnerThread);
      }      
    }    
  }

  private String getForwardTraverseInRow() {
    List<Blip> blips = new ArrayList<>();
    for (Blip blip = navigator.getFirstBlip(adapter.getRootThread()); blip != null;
        blip = navigator.getNextBlipInRow(blip)) {
      assertFalse(blips.contains(blip));      
      blips.add(blip);
    }
    return blipsToString(blips);    
  }
  
  private String getReversedBackwardTraverseInRow() {
    List<Blip> backwardBlipList = new ArrayList<>();
    for (Blip blip = navigator.getLastBlipInThreadTree(adapter.getRootThread(), true); blip != null;
        blip = navigator.getPreviousBlipInRow(blip)) {
      assertFalse(backwardBlipList.contains(blip));      
      backwardBlipList.add(blip);
    }
    Collections.reverse(backwardBlipList);
    return blipsToString(backwardBlipList);    
  }
  
  private static String blipsToString(List<Blip> blips) {
    String s = "[";
    for (int i = 0; i < blips.size(); i++) {
      if (i > 0) {
        s += ", ";
      }
      Blip blip = blips.get(i);
      assertNotNull(blip);
      s += blip.getId();
    }
    s += "]";
    return s;
  }  
}
