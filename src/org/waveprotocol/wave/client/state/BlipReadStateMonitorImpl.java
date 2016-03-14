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

package org.waveprotocol.wave.client.state;

import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.model.conversation.BlipIterators;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationThread;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversation;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationBlip;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationThread;
import org.waveprotocol.wave.model.conversation.quasi.ObservableQuasiConversationView;
import org.waveprotocol.wave.model.supplement.ObservableSupplementedWave;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;
import org.waveprotocol.wave.model.util.IdentitySet;
import org.waveprotocol.wave.model.util.Preconditions;

import java.util.Collection;
import java.util.List;

/**
 * Eagerly monitors the read/unread state of all blips in all conversations in a
 * wave, broadcasting events when the number of read and/or unread blips
 * changes.
 */
public final class BlipReadStateMonitorImpl implements BlipReadStateMonitor {

  /**
   * Logging for state changes. Added June 2010; note that many log statements
   * output the current read/unread state, which is actually an O(n) operation
   * in the number of blips since the JS implementation IdentityMap counts all
   * entries (albeit very cheaply).
   */
  private static final DomLogger LOG = new DomLogger("blip-read-state");

  private final IdentitySet<ConversationBlip> readBlips = CollectionUtils.createIdentitySet();
  private final IdentitySet<ConversationBlip> unreadBlips = CollectionUtils.createIdentitySet();
  
  // IdentitySet has no O(1) size() method, so sizes must be maintained
  // manually.
  private int read;
  private int unread;
  
  private final CopyOnWriteSet<BlipReadStateMonitor.Listener> listeners = CopyOnWriteSet.create();
  
  private final ObservableSupplementedWave supplementedWave;  
  private ObservableQuasiConversation conversation;
  
  private final ObservableQuasiConversation.BlipListener blipListener =
      new ObservableQuasiConversation.BlipListener.Impl() {
    
    @Override
    public void onBlipAdded(ObservableQuasiConversationBlip blip) {
      logChange("added", blip);
      handleBlipAdded(blip);
    }

    @Override
    public void onBlipRemoved(ObservableQuasiConversationBlip blip) {
      logChange("deleted", blip);
      handleBlipRemoved(blip);
    }
  };  
  
  private final ObservableQuasiConversation.ReplyListener replyListener =
      new ObservableQuasiConversation.ReplyListener.Impl() {
  
    @Override
    public void onReplyAdded(ObservableQuasiConversationThread thread) {
      handleThreadAdded(thread);
    }

    @Override
    public void onReplyRemoved(ObservableQuasiConversationThread thread) {
      handleThreadRemoved(thread);
    }
  };
  
  /**
   * @return a new BlipReadStateMonitor
   * 
   * @param supplementedWave supplemented wave
   * @param conversationView conversation view
   */
  public static BlipReadStateMonitorImpl create(ObservableSupplementedWave supplementedWave,
      ObservableQuasiConversationView conversationView) {
    BlipReadStateMonitorImpl monitor = new BlipReadStateMonitorImpl(supplementedWave);
    monitor.init(conversationView);
    return monitor;
  }

  private BlipReadStateMonitorImpl(ObservableSupplementedWave supplementedWave) {
    Preconditions.checkNotNull(supplementedWave, "supplementedWave cannot be null");
    this.supplementedWave = supplementedWave;
  }

  private void init(ObservableQuasiConversationView conversationView) {
    Preconditions.checkNotNull(conversationView, "conversationView cannot be null");    
    
    // Listens to change of blip and wavelet read status.
    supplementedWave.addListener(new ObservableSupplementedWave.ListenerImpl() {
    
      @Override
      public void onMaybeBlipReadChanged(ObservableConversationBlip blip) {
        // We only care about blips that we already know about.
        if (readBlips.contains(blip) || unreadBlips.contains(blip)) {
          if (updateOrInsertReadUnread(blip)) {
            logChange("read changed", blip);
          }
        }
      }

      @Override
      public void onMaybeWaveletReadChanged(ObservableConversation conversation) {
        countBlips();
        notifyListeners();
      }
    });
    
    // Listens to conversation adding or removing.
    conversationView.addListener(new ObservableQuasiConversationView.Listener() {

      @Override
      public void onConversationAdded(ObservableQuasiConversation conversation) {
        if (conversation.isRoot()) {
          bindConversation(conversation);
        }  
      }

      @Override
      public void onConversationRemoved(ObservableQuasiConversation conversation) {
        if (conversation.isRoot()) {
          unbindConversation(conversation);
        }  
      }
    });
    
    // Listens to conversation structure changes.
    conversation = conversationView.getRoot();
    if (conversation != null) {
      conversation.addBlipListener(blipListener);
      conversation.addReplyListener(replyListener);
    }
    
    // Counts the existing blips. This will also set haveCountedBlips to true
    countBlips();
  }

  //
  // Debugging (for DebugMenu).
  //

  public Collection<String> debugGetReadBlips() {
    final List<String> result = CollectionUtils.newArrayList();
    readBlips.each(new IdentitySet.Proc<ConversationBlip>() {
      
      @Override
      public void apply(ConversationBlip blip) {
        result.add(blip.getId());
      }
    });
    return result;
  }

  public Collection<String> debugGetUnreadBlips() {
    final List<String> result = CollectionUtils.newArrayList();
    unreadBlips.each(new IdentitySet.Proc<ConversationBlip>() {
      
      @Override
      public void apply(ConversationBlip blip) {
        result.add(blip.getId());
      }
    });
    return result;
  }

  //
  // BlipReadStateMonitor
  //

  @Override
  public int getReadCount() {
    assert read == readBlips.countEntries();
    return read;
  }

  @Override
  public int getUnreadCount() {
    assert unread == unreadBlips.countEntries();
    return unread;
  }

  @Override
  public void addListener(BlipReadStateMonitor.Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(BlipReadStateMonitor.Listener listener) {
    listeners.remove(listener);
  }

  //
  // Helpers.
  //

  /**
   * Populates {@link #readBlips} and {@link #unreadBlips} by counting all blips.
   */
  private void countBlips() {
    readBlips.clear();
    read = 0;
    unreadBlips.clear();
    unread = 0;

    if (conversation != null) {
      for (ConversationBlip blip : BlipIterators.breadthFirst(conversation)) {
        if (supplementedWave.isUnread(blip)) {
          unreadBlips.add(blip);
          unread++;
        } else {
          readBlips.add(blip);
          read++;
        }
      }
    }
  }

  /**
   * Inserts the blip into the correct read/unread set and removes from the
   * other, and notifies listeners as needed.
   */
  private boolean updateOrInsertReadUnread(ConversationBlip blip) {
    boolean changed = false;
    if (isUnread(blip)) {
      if (readBlips.contains(blip)) {
        readBlips.remove(blip);
        read--;
        changed = true;
      }
      if (!unreadBlips.contains(blip)) {
        unreadBlips.add(blip);
        unread++;
        changed = true;
      }
    } else {
      if (unreadBlips.contains(blip)) {
        unreadBlips.remove(blip);
        unread--;
        changed = true;
      }
      if (!readBlips.contains(blip)) {
        readBlips.add(blip);
        read++;
        changed = true;
      }
    }
    if (changed) {
      notifyListeners();
    }
    return changed;
  }

  /**
   * Removes the blip from all possible locations in the read and unread set
   * and notifies listeners as needed.
   */
  private void removeReadUnread(ConversationBlip blip) {
    boolean changed = false;
    if (readBlips.contains(blip)) {
      readBlips.remove(blip);
      read--;
      changed = true;
    }
    if (unreadBlips.contains(blip)) {
      unreadBlips.remove(blip);
      unread--;
      changed = true;
    }
    if (changed) {
      notifyListeners();
    }
  }

  /**
   * Determines whether the given blip is unread.
   */
  private boolean isUnread(ConversationBlip blip) {
    return supplementedWave.isUnread(blip);
  }

  /**
   * Notifies listeners of a change.
   */
  private void notifyListeners() {
    LOG.trace().log("notifying read/unread change ", read, "/", unread);
    for (Listener listener : listeners) {
      listener.onReadStateChanged();
    }
  }

  /**
   * Log some action with the blip information and read/unread state.
   */
  private void logChange(String action, ConversationBlip blip) {
    LOG.trace().log(blip, ": ", action, " now ", getReadCount(), "/", getUnreadCount());
  }

  private void handleBlipAdded(ObservableConversationBlip blip) {
    // Add this blip.
    updateOrInsertReadUnread(blip);

    // Add all replies.
    for (ObservableConversationThread replyThread : blip.getReplyThreads()) {
      handleThreadAdded(replyThread);
    }
  }

  private void handleBlipRemoved(ObservableConversationBlip blip) {
    // Remove this blip.
    removeReadUnread(blip);

    // Remove all inline replies (non-inline replies will just be reanchored).
    for (ObservableConversationThread replyThread : blip.getReplyThreads()) {
      handleThreadRemoved(replyThread);
    }
  }

  /**
   * Recursively adds a thread to be monitored.
   */  
  private void handleThreadAdded(ObservableConversationThread thread) {
    // Add all direct blips.  Descendant blips will be added recursively.
    for (ObservableConversationBlip blip : thread.getBlips()) {
      handleBlipAdded(blip);
    }
  }

  /**
   * Recursively removes a thread, the inverse of {@link #handleThreadAdded}.
   */
  private void handleThreadRemoved(ObservableConversationThread thread) {
    // Remove all direct blips.  Descendant blips will be removed recursively.
    for (ObservableConversationBlip blip : thread.getBlips()) {
      handleBlipRemoved(blip);
    }
  }
  
  private void bindConversation(ObservableQuasiConversation conversation) {
    Preconditions.checkNotNull(conversation, "conversation cannot be null");
    Preconditions.checkState(conversation.isRoot(), "conversation must be root");
    
    handleThreadAdded(conversation.getRootThread());
    conversation.addBlipListener(blipListener);
    conversation.addReplyListener(replyListener);
    this.conversation = conversation;
  }
  
  private void unbindConversation(ObservableQuasiConversation conversation) {
    Preconditions.checkNotNull(conversation, "conversation cannot be null");
    Preconditions.checkState(conversation.isRoot(), "conversation must be root");

    conversation.removeBlipListener(blipListener);
    conversation.removeReplyListener(replyListener);
    handleThreadRemoved(conversation.getRootThread());
    this.conversation = null;    
  }  
}
