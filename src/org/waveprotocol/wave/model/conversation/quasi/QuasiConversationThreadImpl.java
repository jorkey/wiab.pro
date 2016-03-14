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
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.StringMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Conversation thread with quasi-deletion support.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class QuasiConversationThreadImpl implements ObservableQuasiConversationThread {
  
  private final ObservableConversationThread baseThread;
  private final QuasiConversationBlipImpl parentBlip;
  private final QuasiConversationImpl conversation;
  private final String id;
  
  private WaveletOperationContext quasiDeletionContext;
  
  private final List<QuasiConversationBlipImpl> blipList = new ArrayList<>();
  private final StringMap<QuasiConversationBlipImpl> blipMap = CollectionUtils.createStringMap();  
  private final CopyOnWriteSet<Listener> listeners = CopyOnWriteSet.create();
  
  //
  // Public methods
  //
  
  public QuasiConversationThreadImpl(ObservableConversationThread baseThread,
      QuasiConversationBlipImpl parentBlip, QuasiConversationImpl conversation) {
    this.baseThread = baseThread;
    this.parentBlip = parentBlip;
    this.conversation = conversation;
    
    id = baseThread.getId();
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
        : (parentBlip != null ? parentBlip.getQuasiDeletionContext() : null);
  }
  
  //
  // QuasiConversationThread
  //

  @Override
  public ObservableConversationThread getBaseThread() {
    return baseThread;
  }
  
  //
  // ConversationThread
  //
  
  @Override
  public QuasiConversationImpl getConversation() {
    return conversation;
  }

  @Override
  public QuasiConversationBlipImpl getParentBlip() {
    return parentBlip;
  }

  @Override
  public Iterable<QuasiConversationBlipImpl> getBlips() {
    return blipList;
  }

  @Override
  public QuasiConversationBlipImpl getFirstBlip() {
    return blipList.isEmpty() ? null : blipList.get(0);
  }

  @Override
  public QuasiConversationBlipImpl appendBlip() {
    ObservableConversationBlip blip = baseThread.appendBlip();
    return blipMap.get(blip.getId());
  }

  @Override
  public QuasiConversationBlipImpl appendBlip(DocInitialization content) {
    ObservableConversationBlip blip = baseThread.appendBlip(content);
    return blipMap.get(blip.getId());
  }

  @Override
  public QuasiConversationBlipImpl insertBlip(ConversationBlip neighbor,
      boolean beforeNeighbor) {
    ConversationBlip baseNeighbor = ((QuasiConversationBlipImpl) neighbor).getBaseBlip();
    ObservableConversationBlip blip = baseThread.insertBlip(baseNeighbor, beforeNeighbor);
    return blipMap.get(blip.getId());
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public void delete() {
    if (!isQuasiDeleted()) {
      baseThread.delete();
    } else {
      triggerOnTerminated();
    }  
  }

  @Override
  public boolean isRoot() {
    return baseThread.isRoot();
  }

  @Override
  public boolean isInline() {
    return baseThread.isInline();
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
    return baseThread.toString();
  }
  
  //
  // Package-private methods
  //
  
  /**
   * Copies child blip collection. Used in methods modifying initial collection.
   * 
   * @return copy of child blip collection
   */
  Iterable<QuasiConversationBlipImpl> getCopyOfBlips() {
    return new ArrayList<>(blipList);
  }
  
  /**
   * Scans base thread recursively and creates quasi blip for any child blip.
   * 
   * @return true, if the scanned thread is needed
   */
  void scanBaseThread() {
    Iterator<? extends ObservableConversationBlip> it = baseThread.getBlips().iterator();
    while (it.hasNext()) {
      QuasiConversationBlipImpl quasiBlip = new QuasiConversationBlipImpl(it.next(), this,
          conversation);
      
      blipList.add(quasiBlip);
      blipMap.put(quasiBlip.getId(), quasiBlip);
      conversation.addChildBlip(quasiBlip);
      
      quasiBlip.scanBaseBlip();
    }
  }
    
  void addChildBlip(QuasiConversationBlipImpl blip, ObservableQuasiConversationBlip previous) {
    Preconditions.checkNotNull(blip, "Added child blip must not be null");    
    
    int previousIndex = previous != null ? blipList.indexOf(previous) : -1;
    blipList.add(previousIndex + 1, blip);
    blipMap.put(blip.getId(), blip);
  }
  
  void removeChildBlip(QuasiConversationBlipImpl blip) {
    Preconditions.checkState(blipMap.containsKey(blip.getId()),
        "Parent thread must contain removed child blip");
    
    blipMap.remove(blip.getId());
    blipList.remove(blip);    
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