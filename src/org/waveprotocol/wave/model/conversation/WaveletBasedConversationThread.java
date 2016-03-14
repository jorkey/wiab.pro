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

package org.waveprotocol.wave.model.conversation;

import com.google.common.annotations.VisibleForTesting;

import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.ReadableStringMap.ProcV;
import org.waveprotocol.wave.model.util.StringMap;
import org.waveprotocol.wave.model.wave.Blip;
import org.waveprotocol.wave.model.wave.SourcesEvents;

import java.util.Iterator;

/**
 * A conversation thread backed by a region of a wavelet's manifest document.
 *
 * @author anorth@google.com (Alex North)
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
final public class WaveletBasedConversationThread implements ObservableConversationThread,
    SourcesEvents<WaveletBasedConversationThread.Listener> {

  /** Receives events on a conversation thread. */
  interface Listener {
    
    /**
     * Notifies this listener that a blip has been added to this thread.
     *
     * @param blip the new blip
     * @param opContext operation context
     */
    void onBlipAdded(WaveletBasedConversationBlip blip, WaveletOperationContext opContext);

    /**
     * Notifies this listener that the thread is going to be removed from the conversation.
     * No further methods may be called on the thread.
     *
     * @param blip blip to be deleted
     * @param opContext operation context
     */
    void onBeforeBlipRemoved(WaveletBasedConversationBlip blip, WaveletOperationContext opContext);

    /**
     * Notifies this listener that the thread was removed from the conversation.
     * No further methods may be called on the thread.
     *
     * @param blip deleted blip
     * @param opContext operation context
     */
    void onBlipRemoved(WaveletBasedConversationBlip blip, WaveletOperationContext opContext);
  }

  /** Manifest entry for this thread. */
  private final ObservableManifestThread manifestThread;

  /** Blip to which this thread is a reply (null for root thread). */
  private final WaveletBasedConversationBlip parentBlip;

  /** Helper for wavelet access. */
  private final WaveletBasedConversation.ComponentHelper helper;

  /** Blips in this thread. */
  private final StringMap<WaveletBasedConversationBlip> blips = CollectionUtils.createStringMap();

  /** Whether this thread is safe to use. Set false when deleted. */
  private boolean isUsable = true;

  private final CopyOnWriteSet<Listener> listeners = CopyOnWriteSet.create();

  /**
   * Listener to ObservableManifestThread.
   * These methods update local data structures in response to changes in
   * the underlying data, either synchronously in local methods or from
   * remote changes. They don't make further changes to the data.
   */  
  private final ObservableManifestThread.Listener manifestListener =
      new ObservableManifestThread.Listener() {

    @Override
    public void onBlipAdded(ObservableManifestBlip manifestBlip, ObservableManifestBlip previous,
        WaveletOperationContext opContext) {
      Blip blip = helper.getOrCreateBlip(manifestBlip.getId());
      WaveletBasedConversationBlip convBlip = adaptBlip(manifestBlip, blip);
      triggerOnBlipAdded(convBlip, opContext);
    }

    @Override
    public void onBeforeBlipRemoved(ObservableManifestBlip blip, WaveletOperationContext opContext) {
      WaveletBasedConversationBlip blipToRemove = blips.get(blip.getId());
      if (blipToRemove != null) {
        triggerOnBeforeBlipRemoved(blipToRemove, opContext);
      }
    }

    @Override
    public void onBlipRemoved(ObservableManifestBlip blip, WaveletOperationContext opContext) {
      WaveletBasedConversationBlip blipToRemove = blips.get(blip.getId());
      if (blipToRemove != null) {
        forgetBlip(blipToRemove, opContext);
      }
    }        
  };
  
  /**
   * Creates a new conversation thread.
   *
   * @param manifestThread data for the thread
   * @param parentBlip blip to which this thread is a reply (null for root)
   * @param helper provides conversation components
   * @param opContextFactory operation context factory
   */
  static WaveletBasedConversationThread create(ObservableManifestThread manifestThread,
      WaveletBasedConversationBlip parentBlip, WaveletBasedConversation.ComponentHelper helper) {
    WaveletBasedConversationThread thread = new WaveletBasedConversationThread(manifestThread,
        parentBlip, helper);
    for (ObservableManifestBlip manifestBlip : manifestThread.getBlips()) {
      Blip blip = helper.getOrCreateBlip(manifestBlip.getId());
      thread.adaptBlip(manifestBlip, blip);
    }

    manifestThread.addListener(thread.manifestListener);
    return thread;
  }

  private WaveletBasedConversationThread(ObservableManifestThread manifestThread,
      WaveletBasedConversationBlip parentBlip, WaveletBasedConversation.ComponentHelper helper) {
    Preconditions.checkNotNull(manifestThread,
        "WaveletBasedConversationThread received null manifest thread");
    this.manifestThread = manifestThread;
    this.helper = helper;
    this.parentBlip = parentBlip;
  }

  @Override
  public WaveletBasedConversation getConversation() {
    return helper.getConversation();
  }

  @Override
  public WaveletBasedConversationBlip getParentBlip() {
    return parentBlip;
  }

  @Override
  public Iterable<WaveletBasedConversationBlip> getBlips() {
    final Iterable<? extends ObservableManifestBlip> manifestBlips = manifestThread.getBlips();
    return new Iterable<WaveletBasedConversationBlip>() {
      
      @Override
      public Iterator<WaveletBasedConversationBlip> iterator() {
        return WrapperIterator.create(manifestBlips.iterator(), blips);
      }
    };
  }

  @Override
  public WaveletBasedConversationBlip getFirstBlip() {
    WaveletBasedConversationBlip result = null;
    if (!blips.isEmpty()) {
      ObservableManifestBlip manifestBlip = manifestThread.getBlip(0);
      result = blips.get(manifestBlip.getId());
      if (result == null) {
        // Very uncommon case: the first blip in the manifest doesn't have a
        // corresponding blip object.  Fall back to iterating since iteration
        // handles this correctly.
        for (WaveletBasedConversationBlip firstBlip : getBlips()) {
          result = firstBlip;
          break;
        }
      }
    }
    return result;
  }

  @Override
  public WaveletBasedConversationBlip appendBlip() {
    checkIsUsable();
    return appendBlipWithContent(null);
  }

  @Override
  public WaveletBasedConversationBlip appendBlip(DocInitialization content) {
    Preconditions.checkNotNull(content, "initialization is null");
    checkIsUsable();
    return appendBlipWithContent(content);
  }

  @Override
  public WaveletBasedConversationBlip insertBlip(ConversationBlip neighbor, boolean beforeNeighbor) {
    checkIsUsable();
    if (!blips.containsKey(neighbor.getId())) {
      Preconditions.illegalArgument(
          "Can't insert a blip: the blip " + neighbor + " is not from this thread");
    }
    WaveletBasedConversationBlip neighborBlip = (WaveletBasedConversationBlip) neighbor;
    int index = manifestThread.indexOf(neighborBlip.getManifestBlip());
    if (!beforeNeighbor) {
      index++;
    }
    Blip blip = helper.createBlip(null);
    String blipId = blip.getId();
    manifestThread.insertBlip(index, blipId);
    return blips.get(blipId);
  }

  @Override
  public String getId() {
    return isRoot() ? "" : manifestThread.getId();
  }

  @Override
  public void delete() {
    if (isRoot()) {
      deleteBlips(true);
    } else {
      parentBlip.deleteThread(this);
    }
  }

  @Override
  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public void clearListeners() {
    listeners.clear();
  }

  @Override
  public boolean isRoot() {
    return getConversation().getRootThread() == this;
  }

  @Override
  public boolean isInline() {
    return manifestThread.isInline();
  }

  @Override
  public String toString() {
    return isRoot() ? "<root>" : manifestThread.getId();
  }

  // Package-private methods for WaveletBasedConversationBlip.

  ManifestThread getManifestThread() {
    return manifestThread;
  }

  /**
   * Deletes a blip from this thread, deleting that blip's replies.
   *
   * @param shouldDeleteSelfIfEmpty if true, deletes itself if has no blips any longer
   * @param silently if true, creates no outgoing operations
   */
  void deleteBlip(WaveletBasedConversationBlip blipToDelete, boolean shouldDeleteSelfIfEmpty) {
    Preconditions.checkArgument(blips.containsKey(blipToDelete.getId()),
        "Can't delete blip not from this thread");
    blipToDelete.deleteThreads(shouldDeleteSelfIfEmpty);
    blipToDelete.clearContent();
    manifestThread.removeBlip(blipToDelete.getManifestBlip());
    if (shouldDeleteSelfIfEmpty) {
      deleteSelfIfEmpty();
    }
  }

  /**
  * Deletes all blips from this thread.
  *
  * @see #deleteBlip(WaveletBasedConversationBlip, boolean)
  */
  void deleteBlips(boolean shouldDeleteSelfIsEmpty) {
    for (WaveletBasedConversationBlip replyBlip : CollectionUtils.valueList(blips)) {
      deleteBlip(replyBlip, shouldDeleteSelfIsEmpty);
    }
  }

  void deleteSelfIfEmpty() {
    if (blips.isEmpty() && !isRootThread()) {
      parentBlip.deleteThread(this);
    }
  }

  /**
   * Invalidates this thread. It may no longer be accessed.
   */
  void invalidate() {
    checkIsUsable();
    manifestThread.removeListener(manifestListener);
    isUsable = false;
  }

  /**
   * Recursively invalidates this thread and its blips.
   */
  void destroy() {
    blips.each(new ProcV<WaveletBasedConversationBlip>() {
        
      @Override
      public void apply(String key, WaveletBasedConversationBlip blip) {
        blip.destroy();
      }
    });
    invalidate();
    listeners.clear();
  }

  /**
   * Checks that this thread is safe to access.
   */
  @VisibleForTesting
  void checkIsUsable() {
    if (!isUsable) {
      Preconditions.illegalState("Deleted thread is not usable: " + this);
    }
  }

  // Private methods

  /**
   * Checks whether this is the root thread.
   */
  private boolean isRootThread() {
    return parentBlip == null;
  }

  /**
   * Appends a blip.
   *
   * @param content  optional content
   * @return new blip.
   */
  private WaveletBasedConversationBlip appendBlipWithContent(DocInitialization content) {
    Blip blip = helper.createBlip(content);
    String blipId = blip.getId();
    manifestThread.appendBlip(blipId);
    return blips.get(blipId);
  }

  /**
   * Creates a conversation blip backed by a manifest blip and adds it to blips.
   */
  private WaveletBasedConversationBlip adaptBlip(ObservableManifestBlip manifestBlip, Blip blip) {
    WaveletBasedConversationBlip convBlip =
        WaveletBasedConversationBlip.create(manifestBlip, blip, this, helper);
    blips.put(manifestBlip.getId(), convBlip);
    return convBlip;
  }

  public void forgetBlip(WaveletBasedConversationBlip blipToRemove,
      WaveletOperationContext opContext) {
    String idToRemove = blipToRemove.getId();
    assert blips.containsKey(idToRemove);
    blips.remove(idToRemove);
    blipToRemove.invalidate();
    
    triggerOnBlipRemoved(blipToRemove, opContext);
  }

  //
  // Event triggers
  //
  
  private void triggerOnBlipAdded(WaveletBasedConversationBlip blip,
      WaveletOperationContext opContext) {
    for (Listener l : listeners) {
      l.onBlipAdded(blip, opContext);
    }
  }

  private void triggerOnBeforeBlipRemoved(WaveletBasedConversationBlip blip,
      WaveletOperationContext opContext) {
    for (Listener l : listeners) {
      l.onBeforeBlipRemoved(blip, opContext);
    }
  }

  private void triggerOnBlipRemoved(WaveletBasedConversationBlip blip,
      WaveletOperationContext opContext) {
    for (Listener l : listeners) {
      l.onBlipRemoved(blip, opContext);
    }
  }
}
