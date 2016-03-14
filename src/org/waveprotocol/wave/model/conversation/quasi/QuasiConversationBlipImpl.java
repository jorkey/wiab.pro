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

import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationThread;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.Wavelet;
import org.waveprotocol.wave.model.wave.data.DocumentOperationSink;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.StringMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Conversation blip with quasi-deletion support.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class QuasiConversationBlipImpl implements ObservableQuasiConversationBlip {

  /**
   * A located reply thread  which specializes the thread type to
   * QuasiConversationThreadImpl.
   */
  final class LocatedReplyThread
      extends ConversationBlip.LocatedReplyThread<QuasiConversationThreadImpl> {

    LocatedReplyThread(QuasiConversationThreadImpl thread, int location) {
      super(thread, location);
    }
  }
  
  private final ObservableConversationBlip baseBlip;
  private final QuasiConversationThreadImpl parentThread;
  private final QuasiConversationImpl conversation;
  private final String id;
  
  private WaveletOperationContext quasiDeletionContext;
  
  private final List<QuasiConversationThreadImpl> threadList = new ArrayList<>();  
  private final StringMap<QuasiConversationThreadImpl> threadMap = CollectionUtils.createStringMap();  
  private final CopyOnWriteSet<Listener> listeners = CopyOnWriteSet.create();
  
  //
  // Public methods
  //
  
  public QuasiConversationBlipImpl(ObservableConversationBlip baseBlip,
      QuasiConversationThreadImpl parentThread, QuasiConversationImpl conversation) {
    this.baseBlip = baseBlip;
    this.parentThread = parentThread;
    this.conversation = conversation;
    
    id = baseBlip.getId();
  }
  
  //
  // QuasiDeletable
  //  
  
  @Override
  public void makeQuasiDeleted(WaveletOperationContext quasiDeletionContext) {
    this.quasiDeletionContext = quasiDeletionContext;
  }
  
  @Override
  public boolean isQuasiDeleted() {
    return getQuasiDeletionContext() != null;
  }

  @Override
  public WaveletOperationContext getQuasiDeletionContext() {
    return quasiDeletionContext != null ? quasiDeletionContext
        : parentThread.getQuasiDeletionContext();
  }

  //
  // QuasiConversationBlip
  //

  @Override
  public ObservableConversationBlip getBaseBlip() {
    return baseBlip;
  }
  
  //
  // ConversationBlip
  //
  
  @Override
  public Wavelet getWavelet() {
    return baseBlip.getWavelet();
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public QuasiConversationImpl getConversation() {
    return conversation;
  }

  @Override
  public QuasiConversationThreadImpl getThread() {
    return parentThread;
  }

  @Override
  public QuasiConversationThreadImpl getReplyThread(String id) {
    return threadMap.get(id);
  }

  @Override
  public Iterable<QuasiConversationThreadImpl> getReplyThreads() {
    return threadList;
  }  
  
  @Override
  public Iterable<? extends LocatedReplyThread> locateReplyThreads() {
    List<LocatedReplyThread> locatedThreads = new ArrayList<>();
    int pos = 0; // symbolic location
    for (QuasiConversationThreadImpl thread : threadList) {
      locatedThreads.add(new LocatedReplyThread(thread, pos++));
    }
    return locatedThreads;
  }

  @Override
  public QuasiConversationThreadImpl addReplyThread() {
    ObservableConversationThread thread = baseBlip.addReplyThread();
    return threadMap.get(thread.getId());
  }

  @Override
  public QuasiConversationThreadImpl addReplyThread(int location) {
    ObservableConversationThread thread = baseBlip.addReplyThread(location);
    return threadMap.get(thread.getId());
  }

  @Override
  public boolean hasContent() {
    return baseBlip.hasContent();
  }

  @Override
  public boolean isContentInitialized() {
    return baseBlip.isContentInitialized();
  }

  @Override
  public void initializeSnapshot() {
    baseBlip.initializeSnapshot();
  }

  @Override
  public void processDiffs() throws OperationException {
    baseBlip.processDiffs();
  }
  
  @Override
  public <T extends DocumentOperationSink> T getContent() {
    return baseBlip.getContent();
  }

  @Override
  public Document getDocument() {
    return baseBlip.getDocument();
  }

  @Override
  public ParticipantId getAuthorId() {
    return baseBlip.getAuthorId();
  }

  @Override
  public Set<ParticipantId> getContributorIds() {
    return baseBlip.getContributorIds();
  }

  @Override
  public long getCreationTime() {
    return baseBlip.getCreationTime();
  }

  @Override
  public long getCreationVersion() {
    return baseBlip.getCreationVersion();
  }

  @Override
  public long getLastModifiedTime() {
    return baseBlip.getLastModifiedTime();
  }

  @Override
  public long getLastModifiedVersion() {
    return baseBlip.getLastModifiedVersion();
  }

  @Override
  public void delete() {
    if (!isQuasiDeleted()) {
      baseBlip.delete();
    } else {
      triggerOnTerminated();
    }  
  }

  //
  // Listeners
  //

  @Override
  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }
  
  //
  // Object
  //

  @Override
  public String toString() {
    return baseBlip.toString();
  }
  
  //
  // Package-private methods
  //
  
  /**
   * Copies child reply collection. Used in methods modifying initial collection.
   * 
   * @return copy of child reply collection
   */
  Iterable<QuasiConversationThreadImpl> getCopyOfReplyThreads() {
    return new ArrayList<>(threadList);
  }
  
  /**
   * Scans base blip recursively and creates quasi thread for any child thread.
   */
  void scanBaseBlip() {
    Iterator<? extends ObservableConversationThread> it = baseBlip.getReplyThreads().iterator();
    while (it.hasNext()) {
      QuasiConversationThreadImpl quasiThread = new QuasiConversationThreadImpl(it.next(), this,
          conversation);
      
      threadList.add(quasiThread);
      threadMap.put(quasiThread.getId(), quasiThread);
      conversation.addChildThread(quasiThread);
      
      quasiThread.scanBaseThread();      
    }
  }  
      
  void addChildThread(QuasiConversationThreadImpl thread,
      ObservableQuasiConversationThread previous) {
    Preconditions.checkNotNull(thread, "Added quasi thread must not be null");
    
    int previousIndex = previous != null ? threadList.indexOf(previous) : -1;
    threadList.add(previousIndex + 1, thread);
    threadMap.put(thread.getId(), thread);
  }
  
  void removeChildThread(QuasiConversationThreadImpl thread) {
    Preconditions.checkState(threadMap.containsKey(thread.getId()),
        "Parent blip must contain removed child thread");
    
    threadMap.remove(thread.getId());
    threadList.remove(thread);
  }
  
  //
  // Listener events
  //
  
  private void triggerOnTerminated() {
    for (Listener l : listeners) {
      l.onTerminated();
    }
  }
}