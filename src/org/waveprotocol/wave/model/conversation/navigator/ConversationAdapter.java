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

import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationBlip;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationThread;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Adapter for conversation structure.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 * @author dyukon@gmail.com (D. Konovalchik)
 */
public class ConversationAdapter implements Adapter<ConversationThread, ConversationBlip> {
  
  /**
   * Structure to keep neighbors.
   * 
   * @param <T> type of neighbor
   */
  private static class Neighbors<T> {
    
    private final T previous;
    private final T next;
    
    public Neighbors(T previous, T next) {
      this.previous = previous;
      this.next = next;
    }
    
    public T getPrevious() {
      return previous;
    }
    
    public T getNext() {
      return next;
    }
  }
  
  private final ObservableQuasiConversation conversation;

  public ConversationAdapter(ObservableQuasiConversation conversation) {
    this.conversation = conversation;
  }

  @Override
  public void addListener(final Listener<ConversationThread, ConversationBlip> listener) {
    
    conversation.addReplyListener(new ObservableQuasiConversation.ReplyListener.Impl() {
      
      @Override
      public void onReplyAdded(ObservableQuasiConversationThread thread) {
        Neighbors<ConversationThread> neighborThreads = getNeighborThreads(thread);
        listener.onReplyAdded(thread.getParentBlip(), neighborThreads.getPrevious(), thread,
            neighborThreads.getNext());
      }

      @Override
      public void onReplyRemoved(ObservableQuasiConversationThread thread) {
        listener.onReplyRemoved(thread);
      }
    });
      
    conversation.addBlipListener(new ObservableQuasiConversation.BlipListener.Impl() {
    
      @Override
      public void onBlipAdded(ObservableQuasiConversationBlip blip) {
        Neighbors<ConversationBlip> neighborBlips = getNeighborBlips(blip);
        listener.onBlipAdded(blip.getThread(), neighborBlips.getPrevious(), blip,
            neighborBlips.getNext());
      }

      @Override
      public void onBlipRemoved(ObservableQuasiConversationBlip blip) {
        listener.onBlipRemoved(blip);
      }
    });
  }

  //
  // Adapter interface implementation
  //
  
  @Override
  public ConversationThread getRootThread() {
    return conversation.getRootThread();
  }
  
  @Override
  public Iterator<? extends ConversationBlip> getBlips(ConversationThread thread) {
    return thread.getBlips().iterator();
  }

  @Override
  public Iterator<? extends ConversationThread> getThreads(ConversationBlip blip) {
    return blip.getReplyThreads().iterator();
  }

  @Override
  public boolean isThreadInline(ConversationThread thread) {
    return thread.isInline();
  }
  
  //
  // Private methods
  //
  
  private static Neighbors<ConversationBlip> getNeighborBlips(ConversationBlip blip) {
    ConversationBlip previousBlip = null;
    ConversationBlip nextBlip = null;
    Iterator<? extends ConversationBlip> it = blip.getThread().getBlips().iterator();
    while (it.hasNext()) {
      ConversationBlip b = it.next();
      if (b == blip) {
        nextBlip = it.hasNext() ? it.next() : null;
        break;
      }
      previousBlip = b;
    }
    return new Neighbors<>(previousBlip, nextBlip);
  }  
  
  private static Neighbors<ConversationThread> getNeighborThreads(ConversationThread thread) {
    ConversationThread previousThread = null;
    ConversationThread nextThread = null;
    if (!thread.isRoot()) {
      Iterator<? extends ConversationThread> it = thread.getParentBlip().getReplyThreads().iterator();
      while (it.hasNext()) {
        ConversationThread t = it.next();
        if (t == thread) {
          nextThread = it.hasNext() ? it.next() : null;
          break;
        }
        previousThread = t;
      }
    }
    return new Neighbors<>(previousThread, nextThread);
  }
  
  private static boolean isDiff(WaveletOperationContext opContext) {
    return opContext != null && !opContext.isAdjust() && opContext.hasSegmentVersion();
  }
}
