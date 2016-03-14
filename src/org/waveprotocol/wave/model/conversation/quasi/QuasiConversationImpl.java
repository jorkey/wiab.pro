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

package org.waveprotocol.wave.model.conversation.quasi;

import org.waveprotocol.wave.model.conversation.focus.ObservableFocusFramePresenter;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationBlip.LocatedReplyThread;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationThread;
import org.waveprotocol.wave.model.conversation.WaveletBasedConversation;
import org.waveprotocol.wave.model.document.ObservableDocument;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.supplement.SupplementedWave;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Conversation with quasi-deletion support.
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class QuasiConversationImpl implements ObservableQuasiConversation {

  public static QuasiConversationImpl create(ObservableConversation baseConversation,
      SupplementedWave supplement, ObservableFocusFramePresenter focusFrame) {
    QuasiConversationImpl conversation = new QuasiConversationImpl(baseConversation, supplement,
        focusFrame);
    conversation.initialize();
    return conversation;
  }

  private final ObservableConversation baseConversation;
  private final SupplementedWave supplement;
  private final ObservableFocusFramePresenter focusFrame;
  private final String id;

  private final Map<String, QuasiConversationBlipImpl> blipMap = new HashMap<>();
  private final Map<String, QuasiConversationThreadImpl> threadMap = new HashMap<>();

  /** Listeners. */
  private final CopyOnWriteSet<ReplyListener> quasiReplyListeners = CopyOnWriteSet.create();
  private final CopyOnWriteSet<BlipListener> quasiBlipListeners = CopyOnWriteSet.create();
  private final CopyOnWriteSet<BlipContentListener>
      quasiBlipContentListeners = CopyOnWriteSet.create();
  private final CopyOnWriteSet<ObservableConversation.ReplyListener>
      baseReplyListeners = CopyOnWriteSet.create();
  private final CopyOnWriteSet<ObservableConversation.BlipListener>
      baseBlipListeners = CopyOnWriteSet.create();
  private final CopyOnWriteSet<ObservableConversation.BlipContentListener>
      baseBlipContentListeners = CopyOnWriteSet.create();

  private QuasiConversationThreadImpl rootThread;

  /**
   * Listens to blips in this conversation and forwards events to blip listener.
   */
  private class BlipListenerAggregator implements ObservableQuasiConversationBlip.Listener {
    
    private final QuasiConversationBlipImpl blip;
    
    public BlipListenerAggregator(QuasiConversationBlipImpl blip) {
      this.blip = blip;
    }

    @Override
    public void onTerminated() {
      beforeRemoveBlip(blip, null);
      removeBlip(blip, null);
    }
  }

  /**
   * Listens to threads in this conversation and forwards events to reply listener.
   */
  private class ThreadListenerAggregator implements ObservableQuasiConversationThread.Listener {
    
    private final QuasiConversationThreadImpl thread;
    
    public ThreadListenerAggregator(QuasiConversationThreadImpl thread) {
      this.thread = thread;
    }

    @Override
    public void onTerminated() {
      beforeRemoveThread(thread, null);
      removeThread(thread, null);
    }
  }
  
  /**
   * Listens to the base conversation's replies.
   */
  private final ObservableConversation.ReplyListener replyListener =
      new ObservableConversation.ReplyListener() {

    @Override
    public void onReplyAdded(ObservableConversationThread thread,
        WaveletOperationContext opContext) {
      ConversationBlip parentBlip = thread.getParentBlip();
      QuasiConversationBlipImpl parentQuasiBlip =
          parentBlip != null ? blipMap.get(parentBlip.getId()) : null;
      QuasiConversationThreadImpl quasiThread =
          new QuasiConversationThreadImpl(thread, parentQuasiBlip, QuasiConversationImpl.this);

      if (parentQuasiBlip == null) {
        rootThread = quasiThread;
      } else {
        ConversationThread previousThread = getPreviousThread(thread);
        QuasiConversationThreadImpl previousQuasiThread =
            previousThread != null ? threadMap.get(previousThread.getId()) : null;
        parentQuasiBlip.addChildThread(quasiThread, previousQuasiThread);
      }

      addChildThread(quasiThread);
      quasiThread.scanBaseThread();

      triggerOnReplyAdded(quasiThread, opContext);
      triggerOnReplyAdded(quasiThread);
    }

    @Override
    public void onBeforeReplyRemoved(ObservableConversationThread thread,
        WaveletOperationContext opContext) {      
      beforeRemoveThread(threadMap.get(thread.getId()), opContext);
    }

    @Override
    public void onReplyRemoved(ObservableConversationThread thread,
        WaveletOperationContext opContext) {      
      removeThread(threadMap.get(thread.getId()), opContext);
    }
  };

  /**
   * Listens to the base conversation's blips.
   */
  private final ObservableConversation.BlipListener blipListener =
      new ObservableConversation.BlipListener() {

    @Override
    public void onBlipAdded(ObservableConversationBlip blip, WaveletOperationContext opContext) {
      QuasiConversationThreadImpl parentQuasiThread = threadMap.get(blip.getThread().getId());
      QuasiConversationBlipImpl quasiBlip =
          new QuasiConversationBlipImpl(blip, parentQuasiThread, QuasiConversationImpl.this);
      ConversationBlip previousBlip = getPreviousBlip(blip);
      QuasiConversationBlipImpl previousQuasiBlip =
          previousBlip != null ? blipMap.get(previousBlip.getId()) : null;
      parentQuasiThread.addChildBlip(quasiBlip, previousQuasiBlip);

      addChildBlip(quasiBlip);
      quasiBlip.scanBaseBlip();

      triggerOnBlipAdded(quasiBlip, opContext);      
      triggerOnBlipAdded(quasiBlip);
    }

    @Override
    public void onBeforeBlipRemoved(ObservableConversationBlip blip,
        WaveletOperationContext opContext) {
      beforeRemoveBlip(blipMap.get(blip.getId()), opContext);
    }

    @Override
    public void onBlipRemoved(ObservableConversationBlip blip, WaveletOperationContext opContext) {
      removeBlip(blipMap.get(blip.getId()), opContext);
    }
  };

  /**
   * Listens to the base conversation's blip contents.
   */  
  private final ObservableConversation.BlipContentListener blipContentListener =
      new ObservableConversation.BlipContentListener() {

    @Override
    public void onBlipContributorAdded(ObservableConversationBlip blip, ParticipantId contributor) {
      triggerOnBlipContributorAdded(blipMap.get(blip.getId()), contributor);
    }

    @Override
    public void onBlipContributorRemoved(ObservableConversationBlip blip,
        ParticipantId contributor) {
      triggerOnBlipContributorRemoved(blipMap.get(blip.getId()), contributor);
    }

    @Override
    public void onBlipSubmitted(ObservableConversationBlip blip) {
      triggerOnBlipSubmitted(blipMap.get(blip.getId()));
    }

    @Override
    public void onBlipTimestampChanged(ObservableConversationBlip blip, long oldTimestamp,
        long newTimestamp) {
      triggerOnBlipTimestampChanged(blipMap.get(blip.getId()), oldTimestamp, newTimestamp);
    }
  };

  /**
   * Listens to the base conversation's focus move.
   */    
  private final ObservableFocusFramePresenter.Listener focusListener =
      new ObservableFocusFramePresenter.Listener() {

    @Override
    public void onFocusOut(ConversationBlip oldFocused) {
      if (((QuasiConversationBlip) oldFocused).isQuasiDeleted() &&
          !supplement.isUnread(oldFocused)) {
        ConversationThread parentThread = oldFocused.getThread();
        if (doesThreadHaveSingleBlip(parentThread)) {
          parentThread.delete();
        } else {
          oldFocused.delete();
        }  
      }
    }

    @Override
    public void onFocusIn(ConversationBlip newFocused) {}
    
    private boolean doesThreadHaveSingleBlip(ConversationThread thread) {
      Iterator<? extends ConversationBlip> it = thread.getBlips().iterator();
      if (!it.hasNext()) {
        return false;
      }
      it.next();
      return !it.hasNext();
    }
  };
  
  //
  // Public methods
  //

  public QuasiConversationImpl(ObservableConversation baseConversation,
      SupplementedWave supplement, ObservableFocusFramePresenter focusFrame) {
    this.baseConversation = baseConversation;
    this.supplement = supplement;
    this.focusFrame = focusFrame;
    id = baseConversation.getId();
  }

  public void initialize() {
    baseConversation.addReplyListener(replyListener);
    baseConversation.addBlipListener(blipListener);
    baseConversation.addBlipContentListener(blipContentListener);

    if (focusFrame != null) {
      focusFrame.addListener(focusListener);
    }  
    
    scanBaseConversation();
  }

  //
  // QuasiConversation
  //

  @Override
  public WaveletBasedConversation getBaseConversation() {
    return (WaveletBasedConversation) baseConversation;
  }

  @Override
  public void terminateQuasiDeleted() {
    terminateQuasiDeletedChildren(rootThread);
  }
  
  //
  // Conversation
  //

  @Override
  public boolean hasAnchor() {
    return baseConversation.hasAnchor();
  }

  @Override
  public Anchor getAnchor() {
    return baseConversation.getAnchor();
  }

  @Override
  public void setAnchor(Anchor newAnchor) {
    baseConversation.setAnchor(newAnchor);
  }

  @Override
  public Anchor createAnchor(ConversationBlip blip) {
    return baseConversation.createAnchor(blip);
  }

  @Override
  public ObservableQuasiConversationThread getRootThread() {
    return rootThread;
  }

  @Override
  public ObservableQuasiConversationBlip getBlip(String blipId) {
    return blipMap.get(blipId);
  }

  @Override
  public ObservableQuasiConversationThread getThread(String threadId) {
    return threadMap.get(threadId);
  }

  @Override
  public ObservableDocument getDataDocument(String name) {
    return baseConversation.getDataDocument(name);
  }

  @Override
  public void delete() {
    baseConversation.delete();
  }

  @Override
  public Set<ParticipantId> getParticipantIds() {
    return baseConversation.getParticipantIds();
  }

  @Override
  public void addParticipantIds(Set<ParticipantId> participants) {
    baseConversation.addParticipantIds(participants);
  }

  @Override
  public void addParticipant(ParticipantId participant) {
    baseConversation.addParticipant(participant);
  }

  @Override
  public void removeParticipant(ParticipantId participant) {
    baseConversation.removeParticipant(participant);
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public Set<String> getTags() {
    return baseConversation.getTags();
  }

  @Override
  public void addTag(String tag) {
    baseConversation.addTag(tag);
  }

  @Override
  public void removeTag(String tag) {
    baseConversation.removeTag(tag);
  }

  @Override
  public boolean isRoot() {
    return baseConversation.isRoot();
  }
  
  //
  // QuasiConversation listeners
  //

  @Override
  public void addReplyListener(ReplyListener listener) {
    quasiReplyListeners.add(listener);
  }

  @Override
  public void removeReplyListener(ReplyListener listener) {
    quasiReplyListeners.remove(listener);
  }

  @Override
  public void addBlipListener(BlipListener listener) {
    quasiBlipListeners.add(listener);
  }

  @Override
  public void removeBlipListener(BlipListener listener) {
    quasiBlipListeners.remove(listener);
  }

  @Override
  public void addBlipContentListener(BlipContentListener listener) {
    quasiBlipContentListeners.add(listener);
  }

  @Override
  public void removeBlipContentListener(BlipContentListener listener) {
    quasiBlipContentListeners.add(listener);
  }

  @Override
  public void addListener(Listener listener) {
    addReplyListener(listener);
    addBlipListener(listener);
    addParticipantListener(listener);
    addTagListener(listener);
    addBlipContentListener(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    removeReplyListener(listener);
    removeBlipListener(listener);
    removeParticipantListener(listener);
    removeTagListener(listener);
    removeBlipContentListener(listener);
  }

  //
  // Conversation listeners
  //

  @Override
  public void addReplyListener(ObservableConversation.ReplyListener listener) {
    baseReplyListeners.add(listener);
  }

  @Override
  public void removeReplyListener(ObservableConversation.ReplyListener listener) {
    baseReplyListeners.remove(listener);
  }

  @Override
  public void addBlipListener(ObservableConversation.BlipListener listener) {
    baseBlipListeners.add(listener);
  }

  @Override
  public void removeBlipListener(ObservableConversation.BlipListener listener) {
    baseBlipListeners.remove(listener);
  }

  @Override
  public void addParticipantListener(ParticipantListener listener) {
    baseConversation.addParticipantListener(listener);
  }

  @Override
  public void removeParticipantListener(ParticipantListener listener) {
    baseConversation.removeParticipantListener(listener);
  }

  @Override
  public void addTagListener(TagListener listener) {
    baseConversation.addTagListener(listener);
  }

  @Override
  public void removeTagListener(TagListener listener) {
    baseConversation.removeTagListener(listener);
  }

  @Override
  public void addBlipContentListener(ObservableConversation.BlipContentListener listener) {
    baseBlipContentListeners.add(listener);
  }

  @Override
  public void removeBlipContentListener(ObservableConversation.BlipContentListener listener) {
    baseBlipContentListeners.remove(listener);
  }

  @Override
  public void addListener(ObservableConversation.Listener listener) {
    addReplyListener(listener);
    addBlipListener(listener);
    addParticipantListener(listener);
    addTagListener(listener);
    addBlipContentListener(listener);
  }

  @Override
  public void removeListener(ObservableConversation.Listener listener) {
    removeReplyListener(listener);
    removeBlipListener(listener);
    removeParticipantListener(listener);
    removeTagListener(listener);
    removeBlipContentListener(listener);
  }

  @Override
  public void addAnchorListener(AnchorListener listener) {
    baseConversation.addAnchorListener(listener);
  }

  @Override
  public void removeAnchorListener(AnchorListener listener) {
    baseConversation.removeAnchorListener(listener);
  }

  //
  // Package-private methods
  //

  void addChildBlip(QuasiConversationBlipImpl blip) {
    blipMap.put(blip.getId(), blip);
    blip.addListener(new BlipListenerAggregator(blip));
  }

  void addChildThread(QuasiConversationThreadImpl thread) {
    threadMap.put(thread.getId(), thread);
    thread.addListener(new ThreadListenerAggregator(thread));    
  }

  void removeChildBlip(QuasiConversationBlipImpl blip) {
    blipMap.remove(blip.getId());
  }

  void removeChildThread(QuasiConversationThreadImpl thread) {
    threadMap.remove(thread.getId());
  }

  //
  // Private methods
  //

  private void scanBaseConversation() {
    ObservableConversationThread baseRootThread = baseConversation.getRootThread();
    if (baseRootThread != null) {
      rootThread = new QuasiConversationThreadImpl(baseRootThread, null, this);
      threadMap.put(baseConversation.getRootThread().getId(), rootThread);
      rootThread.scanBaseThread();
    }
  }

  private static ConversationBlip getPreviousBlip(ConversationBlip blip) {
    ConversationBlip previousBlip = null;
    Iterator<? extends ConversationBlip> it = blip.getThread().getBlips().iterator();
    while (it.hasNext()) {
      ConversationBlip b = it.next();
      if (b == blip) {
        break;
      }
      previousBlip = b;
    }
    return previousBlip;
  }

  private ConversationThread getPreviousThread(ConversationThread thread) {
    ConversationThread previousThread = null;
    if (!thread.isRoot()) {
      // Use located threads because we need threads in the proper order.
      Iterator<? extends LocatedReplyThread<? extends ConversationThread>> it =
          thread.getParentBlip().locateReplyThreads().iterator();
      while (it.hasNext()) {
        LocatedReplyThread<? extends ConversationThread> locatedThread = it.next();
        ConversationThread t = locatedThread.getThread();
        if (t == thread) {
          break;
        }
        previousThread = t;
      }
    }
    return previousThread;
  }

  private boolean isThreadQuasiRemoved(QuasiConversationThread thread,
      WaveletOperationContext opContext) {
    if (isDiff(opContext)) {
      for (QuasiConversationBlip blip : thread.getBlips()) {
        if (isBlipQuasiRemoved(blip, opContext)) {
          return true;
        }
      }
    }  
    return false;
  }

  private boolean isBlipQuasiRemoved(QuasiConversationBlip blip,
      WaveletOperationContext opContext) {
    return isDiff(opContext) &&
        (supplement.isUnread(blip) && supplement.isBlipLooked(blip) ||
        blip == getFocusedBlip());
  }

  private static boolean isDiff(WaveletOperationContext opContext) {
    return opContext != null && !opContext.isAdjust() && opContext.hasSegmentVersion();
  }
  
  private ConversationBlip getFocusedBlip() {
    return focusFrame != null ? focusFrame.getFocusedBlip() : null;
  }
  
  private void beforeRemoveThread(QuasiConversationThreadImpl thread,
      WaveletOperationContext opContext) {
    if (!thread.isQuasiDeleted()) {
      triggerOnBeforeReplyRemoved(thread, opContext);
    }  
    
    boolean isQuasi = isThreadQuasiRemoved(thread, opContext);
    if (isQuasi) {
      thread.makeQuasiDeleted(opContext);
    }    
    for (QuasiConversationBlipImpl blip : thread.getBlips()) {
      beforeRemoveBlip(blip, opContext);
    }
    
    if (isQuasi) {
      triggerOnBeforeReplyQuasiRemoved(thread);
    } else {
      triggerOnBeforeReplyRemoved(thread);
    }
  }

  private void removeThread(QuasiConversationThreadImpl thread,
      WaveletOperationContext opContext) {
    if (!thread.isQuasiDeleted()) {
      triggerOnReplyRemoved(thread, opContext);
    }  
    
    for (QuasiConversationBlipImpl blip : thread.getCopyOfBlips()) {
      removeBlip(blip, opContext);
    }

    if (isThreadQuasiRemoved(thread, opContext)) {
      triggerOnReplyQuasiRemoved(thread);
    } else {
      QuasiConversationBlipImpl parentBlip = (QuasiConversationBlipImpl) thread.getParentBlip();
      if (parentBlip != null) {
        parentBlip.removeChildThread(thread);
        threadMap.remove(thread.getId());
      } else {
        rootThread = null;
      }

      triggerOnReplyRemoved(thread);
    }
  }

  private void beforeRemoveBlip(QuasiConversationBlipImpl blip, WaveletOperationContext opContext) {
    if (!blip.isQuasiDeleted()) {
      triggerOnBeforeBlipRemoved(blip, opContext);
    }  
        
    boolean isQuasi = isBlipQuasiRemoved(blip, opContext);
    if (isQuasi) {
      blip.makeQuasiDeleted(opContext);
    }
    
    // Don't remove focused blip.
    if (blip == getFocusedBlip()) {
      return;
    }
    
    for (QuasiConversationThreadImpl thread : blip.getReplyThreads()) {
      beforeRemoveThread(thread, opContext);
    }
    
    if (isQuasi) {
      triggerOnBeforeBlipQuasiRemoved(blip);
    } else {
      triggerOnBeforeBlipRemoved(blip);
    }
  }

  private void removeBlip(QuasiConversationBlipImpl blip, WaveletOperationContext opContext) {
    if (!blip.isQuasiDeleted()) {
      triggerOnBlipRemoved(blip, opContext);
    }  
    
    for (QuasiConversationThreadImpl thread : blip.getCopyOfReplyThreads()) {
      removeThread(thread, opContext);
    }

    if (isBlipQuasiRemoved(blip, opContext)) {
      triggerOnBlipQuasiRemoved(blip);
    } else {
      QuasiConversationThreadImpl parentThread = (QuasiConversationThreadImpl) blip.getThread();
      parentThread.removeChildBlip(blip);
      blipMap.remove(blip.getId());

      triggerOnBlipRemoved(blip);
    }
  }

  private void terminateQuasiDeletedChildren(QuasiConversationThreadImpl thread) {
    for (QuasiConversationBlipImpl blip : thread.getCopyOfBlips()) {
      if (blip.isQuasiDeleted()) {
        beforeRemoveBlip(blip, null);        
        removeBlip(blip, null);
      } else {
        terminateQuasiDeletedChildren(blip);
      }  
    }
  }

  private void terminateQuasiDeletedChildren(QuasiConversationBlipImpl blip) {
    for (QuasiConversationThreadImpl thread : blip.getCopyOfReplyThreads()) {
      if (thread.isQuasiDeleted()) {
        beforeRemoveThread(thread, null);
        removeThread(thread, null);
      } else {
        terminateQuasiDeletedChildren(thread);
      }  
    }
  }
  
  //
  // Listener events
  //

  private void triggerOnReplyAdded(ObservableConversationThread thread,
      WaveletOperationContext opContext) {
    for (ObservableConversation.ReplyListener l : baseReplyListeners) {
      l.onReplyAdded(thread, opContext);
    }
  }

  private void triggerOnReplyAdded(ObservableQuasiConversationThread thread) {
    for (ReplyListener l : quasiReplyListeners) {
      l.onReplyAdded(thread);
    }
  }  

  private void triggerOnBeforeReplyRemoved(ObservableConversationThread thread,
      WaveletOperationContext opContext) {
    for (ObservableConversation.ReplyListener l : baseReplyListeners) {
      l.onBeforeReplyRemoved(thread, opContext);
    }
  }

  private void triggerOnBeforeReplyRemoved(ObservableQuasiConversationThread thread) {
    for (ReplyListener l : quasiReplyListeners) {
      l.onBeforeReplyRemoved(thread);
    }
  }

  private void triggerOnBeforeReplyQuasiRemoved(ObservableQuasiConversationThread thread) {
    for (ReplyListener l : quasiReplyListeners) {
      l.onBeforeReplyQuasiRemoved(thread);
    }
  }

  private void triggerOnReplyRemoved(ObservableConversationThread thread,
      WaveletOperationContext opContext) {
    for (ObservableConversation.ReplyListener l : baseReplyListeners) {
      l.onReplyRemoved(thread, opContext);
    }
  }

  private void triggerOnReplyRemoved(ObservableQuasiConversationThread thread) {
    for (ReplyListener l : quasiReplyListeners) {
      l.onReplyRemoved(thread);
    }
  }

  private void triggerOnReplyQuasiRemoved(ObservableQuasiConversationThread thread) {
    for (ReplyListener l : quasiReplyListeners) {
      l.onReplyQuasiRemoved(thread);
    }
  }

  private void triggerOnBlipAdded(ObservableConversationBlip blip,
      WaveletOperationContext opContext) {
    for (ObservableConversation.BlipListener l : baseBlipListeners) {
      l.onBlipAdded(blip, opContext);
    }
  }

  private void triggerOnBlipAdded(ObservableQuasiConversationBlip blip) {
    for (BlipListener l : quasiBlipListeners) {
      l.onBlipAdded(blip);
    }
  }

  private void triggerOnBeforeBlipRemoved(ObservableConversationBlip blip,
      WaveletOperationContext opContext) {
    for (ObservableConversation.BlipListener l : baseBlipListeners) {
      l.onBeforeBlipRemoved(blip, opContext);
    }
  }

  private void triggerOnBeforeBlipRemoved(ObservableQuasiConversationBlip blip) {
    for (BlipListener l : quasiBlipListeners) {
      l.onBeforeBlipRemoved(blip);
    }
  }
  
  private void triggerOnBeforeBlipQuasiRemoved(ObservableQuasiConversationBlip blip) {
    for (BlipListener l : quasiBlipListeners) {
      l.onBeforeBlipQuasiRemoved(blip);
    }
  }

  private void triggerOnBlipRemoved(ObservableConversationBlip blip,
      WaveletOperationContext opContext) {
    for (ObservableConversation.BlipListener l : baseBlipListeners) {
      l.onBlipRemoved(blip, opContext);
    }
  }

  private void triggerOnBlipRemoved(ObservableQuasiConversationBlip blip) {
    for (BlipListener l : quasiBlipListeners) {
      l.onBlipRemoved(blip);
    }
  }

  private void triggerOnBlipQuasiRemoved(ObservableQuasiConversationBlip blip) {
    for (BlipListener l : quasiBlipListeners) {
      l.onBlipQuasiRemoved(blip);
    }
  }  

  private void triggerOnBlipContributorAdded(ObservableQuasiConversationBlip blip,
      ParticipantId contributor) {
    for (ObservableConversation.BlipContentListener l : baseBlipContentListeners) {
      l.onBlipContributorAdded(blip, contributor);
    }
    for (BlipContentListener l : quasiBlipContentListeners) {
      l.onBlipContributorAdded(blip, contributor);
    }
  }

  private void triggerOnBlipContributorRemoved(ObservableQuasiConversationBlip blip,
      ParticipantId contributor) {
    for (ObservableConversation.BlipContentListener l : baseBlipContentListeners) {
      l.onBlipContributorRemoved(blip, contributor);
    }
    for (BlipContentListener l : quasiBlipContentListeners) {
      l.onBlipContributorRemoved(blip, contributor);
    }
  }

  private void triggerOnBlipSubmitted(ObservableQuasiConversationBlip blip) {
    for (ObservableConversation.BlipContentListener l : baseBlipContentListeners) {
      l.onBlipSubmitted(blip);
    }
    for (BlipContentListener l : quasiBlipContentListeners) {
      l.onBlipSubmitted(blip);
    }
  }

  private void triggerOnBlipTimestampChanged(ObservableQuasiConversationBlip blip,
      long oldTimestamp, long newTimestamp) {
    for (ObservableConversation.BlipContentListener l : baseBlipContentListeners) {
      l.onBlipTimestampChanged(blip, oldTimestamp, newTimestamp);
    }
    for (BlipContentListener l : quasiBlipContentListeners) {
      l.onBlipTimestampChanged(blip, oldTimestamp, newTimestamp);
    }
  }
}
