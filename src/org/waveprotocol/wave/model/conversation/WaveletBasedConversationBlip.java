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

import org.waveprotocol.wave.model.conversation.WaveletBasedConversation.ComponentHelper;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.document.MutableDocument;
import org.waveprotocol.wave.model.document.MutableDocument.Action;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.document.util.DocIterate;
import org.waveprotocol.wave.model.document.util.Point;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.StringMap;
import org.waveprotocol.wave.model.wave.Blip;
import org.waveprotocol.wave.model.wave.ObservableWavelet;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.SourcesEvents;
import org.waveprotocol.wave.model.wave.WaveletListener;
import org.waveprotocol.wave.model.wave.opbased.WaveletListenerImpl;
import org.waveprotocol.wave.model.wave.Wavelet;
import org.waveprotocol.wave.model.wave.data.DocumentOperationSink;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A conversation blip backed by a region of a wavelet's manifest document.
 *
 * NOTE(anorth): at present this in in fact backed by a {@link Blip} but we are
 * migrating all blip meta-data into documents.
 *
 * @author anorth@google.com (Alex North)
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
final public class WaveletBasedConversationBlip implements ObservableConversationBlip,
    SourcesEvents<WaveletBasedConversationBlip.Listener> {

  /**
   * Receives events on a conversation blip.
   */
  interface Listener {
    /**
     * Notifies this listener that a reply thread has been added to this blip.
     *
     * @param reply the new thread
     * @param opContext operation context
     */
    void onReplyAdded(WaveletBasedConversationThread thread, WaveletOperationContext opContext);

    /**
     * Notifies this listener that the blip is going to be removed from the conversation.
     * No further methods may be called on the blip.
     *
     * @param thread thread thread to be deleted
     * @param opContext operation context
     */
    void onBeforeReplyRemoved(WaveletBasedConversationThread thread, WaveletOperationContext opContext);

    /**
     * Notifies this listener that the blip was removed from the conversation.
     * No further methods may be called on the blip.
     *
     * @param thread deleted thread
     * @param opContext operation context
     */
    void onReplyRemoved(WaveletBasedConversationThread thread, WaveletOperationContext opContext);

    /**
     * Notifies this listener that a contributor was added to the blip.
     */
    void onContributorAdded(ParticipantId contributor);

    /**
     * Notifies this listener that a contributor was removed from the blip.
     */
    void onContributorRemoved(ParticipantId contributor);

    /**
     * Notifies the listener that the blip was submitted.
     */
    void onSubmitted();

    /**
     * Notifies the listener that the blip timestamp changed.
     */
    void onTimestampChanged(long oldTimestamp, long newTimestamp);
  }

  /**
   * A located reply thread  which specializes the thread type to
   * WaveletBasedConversationThread.
   */
  final class LocatedReplyThread
      extends ConversationBlip.LocatedReplyThread<WaveletBasedConversationThread> {

    LocatedReplyThread(WaveletBasedConversationThread thread, int location) {
      super(thread, location);
    }
  }

  /**
   * Redirects old-style blip events to the conversation listeners.
   */
  private final WaveletListener waveletListener = new WaveletListenerImpl() {
    
    @Override
    public void onBlipContributorAdded(ObservableWavelet wavelet, Blip eventBlip,
        ParticipantId contributor) {
      if (eventBlip == blip) {
        triggerOnContributorAdded(contributor);
      }
    }

    @Override
    public void onBlipContributorRemoved(ObservableWavelet wavelet, Blip eventBlip,
        ParticipantId contributor) {
      if (eventBlip == blip) {
        triggerOnContributorRemoved(contributor);
      }
    }

    @Override
    public void onBlipSubmitted(ObservableWavelet wavelet, Blip eventBlip) {
      if (eventBlip == blip) {
        triggerOnSubmitted();
      }
    }

    @Override
    public void onBlipTimestampModified(ObservableWavelet wavelet, Blip eventBlip, long oldTime,
        long newTime) {
      if (eventBlip == blip) {
        triggerOnTimestampModified(oldTime, newTime);
      }
    }
  };

  /** Manifest entry for this blip. */
  private final ObservableManifestBlip manifestBlip;

  /** Blip object containing content and metadata. */
  private final Blip blip;

  /** Thread containing this blip. */
  private final WaveletBasedConversationThread parentThread;

  /** Helper for wavelet access. */
  private final ComponentHelper helper;

  /** Replies keyed by id. */
  private final StringMap<WaveletBasedConversationThread> replies = CollectionUtils.createStringMap();

  /** Whether this blip is safe to access. Set false when deleted. */
  private boolean isUsable = true;

  private final CopyOnWriteSet<Listener> listeners;

  /**
   * Listener to ObservableManifestBlip.
   * These methods update local data structures in response to changes in
   * the underlying data, either synchronously in local methods or from
   * remote changes. They don't make further changes to the data.
   */
  final private ObservableManifestBlip.Listener manifestListener = new ObservableManifestBlip.Listener() {

    @Override
    public void onReplyAdded(ObservableManifestThread thread, ObservableManifestThread previous,
        WaveletOperationContext opContext) {
      WaveletBasedConversationThread convThread = adaptThread(thread);
      triggerOnReplyAdded(convThread, opContext);
    }

    @Override
    public void onBeforeReplyRemoved(ObservableManifestThread thread, WaveletOperationContext opContext) {
      triggerOnBeforeReplyRemoved(replies.get(thread.getId()), opContext);
    }

    @Override
    public void onReplyRemoved(ObservableManifestThread thread, WaveletOperationContext opContext) {
      WaveletBasedConversationThread convThread = replies.get(thread.getId());
      forgetThread(convThread, opContext);
    }    
  };
  
  static WaveletBasedConversationBlip create(ObservableManifestBlip manifestBlip, Blip backingBlip,
      WaveletBasedConversationThread thread, ComponentHelper helper) {
    WaveletBasedConversationBlip blip = new WaveletBasedConversationBlip(manifestBlip, backingBlip,
        thread, helper);

    for (ObservableManifestThread reply : manifestBlip.getReplies()) {
      blip.adaptThread(reply);
    }

    manifestBlip.addListener(blip.manifestListener);
    helper.getWaveletEventSource().addListener(blip.waveletListener);
    return blip;
  }

  private WaveletBasedConversationBlip(ObservableManifestBlip manifestBlip, Blip blip,
      WaveletBasedConversationThread thread, ComponentHelper helper) {
    Preconditions.checkNotNull(manifestBlip,
        "WaveletBasedConversationBlip received null manifest blip");
    if (blip == null) {
      Preconditions.nullPointer("WaveletBasedConversationBlip " + manifestBlip.getId()
          + " received null blip");
    }
    this.manifestBlip = manifestBlip;
    this.blip = blip;
    this.helper = helper;
    this.parentThread = thread;

    listeners = CopyOnWriteSet.create();
  }

  @Override
  public Wavelet getWavelet() {
    return helper.getWavelet();
  }

  @Override
  public WaveletBasedConversation getConversation() {
    return helper.getConversation();
  }

  @Override
  public WaveletBasedConversationThread getThread() {
    return parentThread;
  }

  @Override
  public Iterable<LocatedReplyThread> locateReplyThreads() {
    // NOTE(anorth): We must recalculate the anchor locations on each
    // call as the document does not provide stable elements. However, we
    // calculate the list of anchor locations on demand.
    StringMap<Integer> replyLocations = null;
    List<LocatedReplyThread> inlineReplyThreads = CollectionUtils.newArrayList();
    for (WaveletBasedConversationThread reply : getReplyThreads()) {
      if (replyLocations == null) {
        replyLocations = findAnchors();
      }
      Integer location = replyLocations.get(reply.getId());
      inlineReplyThreads.add(new LocatedReplyThread(reply,
          (location != null) ? location : Blips.INVALID_INLINE_LOCATION));
    }
    // The location alone is not enough for identification of the reply, because it can be placed
    // at the same location with one or several quasi-deleted replies.
    // So, additional parameter (index) is needed.
    final Map<LocatedReplyThread, Integer> replyToIndex = new HashMap<>();
    for (int i = 0; i < inlineReplyThreads.size(); i++) {
      replyToIndex.put(inlineReplyThreads.get(i), i);
    }
    Collections.sort(inlineReplyThreads, new Comparator<LocatedReplyThread>() {

      @Override
      public int compare(LocatedReplyThread reply1, LocatedReplyThread reply2) {
        if (reply1.getLocation() == reply2.getLocation()) {
          return replyToIndex.get(reply1) - replyToIndex.get(reply2);
        }
        if (reply1.getLocation() == Blips.INVALID_INLINE_LOCATION) {
          return 1;
        }
        if (reply2.getLocation() == Blips.INVALID_INLINE_LOCATION) {
          return -1;
        }
        return reply1.getLocation() - reply2.getLocation();
      }
    });
    return Collections.unmodifiableList(inlineReplyThreads);
  }

  /**
   * {@inheritDoc}
   *
   * The 'history of appends' corresponds to the manifest order of replies.
   */
  @Override
  public Iterable<WaveletBasedConversationThread> getReplyThreads() {
    final Iterable<? extends ObservableManifestThread> manifestThreads = manifestBlip.getReplies();
    return new Iterable<WaveletBasedConversationThread>() {
      
      @Override
      public Iterator<WaveletBasedConversationThread> iterator() {
        return WrapperIterator.create(manifestThreads.iterator(), replies);
      }
    };
  }

  @Override
  public WaveletBasedConversationThread addReplyThread() {
    checkIsUsable();
    String id = helper.createThreadId();
    manifestBlip.appendReply(id, false);
    return replies.get(id);
  }

  @Override
  public WaveletBasedConversationThread addReplyThread(final int location) {
    checkIsUsable();
    String threadId = helper.createThreadId();
    createInlineReplyAnchor(threadId, location);
    manifestBlip.appendReply(threadId, true);
    return replies.get(threadId);
  }

  @Override
  public boolean isContentInitialized() {
    return blip.isContentInitialized();
  }

  @Override
  public void initializeSnapshot() {
    blip.initializeSnapshot();
  }

  @Override
  public void processDiffs() throws OperationException {
    blip.processDiffs();
  }

  @Override
  public <T extends DocumentOperationSink> T getContent() {
    return (T)blip.getContent();
  }

  @Override
  public boolean hasContent() {
    return blip.hasContent();
  }

  @Override
  public Document getDocument() {
    return blip.getDocument();
  }

  @Override
  public ParticipantId getAuthorId() {
    return blip.getAuthorId();
  }

  @Override
  public Set<ParticipantId> getContributorIds() {
    return blip.getContributorIds();
  }

  @Override
  public long getCreationTime() {
    return blip.getCreationTime();
  }

  @Override
  public long getCreationVersion() {
    return blip.getCreationVersion();
  }

  @Override
  public long getLastModifiedTime() {
    return blip.getLastModifiedTime();
  }

  @Override
  public long getLastModifiedVersion() {
    return blip.getLastModifiedVersion();
  }

  @Override
  public void delete() {
    checkIsUsable();
    Collection<WaveletBasedConversationThread> allReplies = CollectionUtils.createQueue();
    CollectionUtils.copyValuesToJavaCollection(replies, allReplies);
    // Delete reply threads.
    // TODO(anorth): Move this loop to WBCT, where it can delete all the
    // inline reply anchors in one pass.
    for (WaveletBasedConversationThread replyThread : allReplies) {
      deleteThread(replyThread);
    }

    // All replies have been deleted, so remove this empty blip.
    parentThread.deleteBlip(this, true);
  }

  @Override
  public String getId() {
    return manifestBlip.getId();
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
  public String toString() {
    return manifestBlip.getId();
  }

  @Override
  public ObservableConversationThread getReplyThread(String id) {
    return replies.get(id);
  }

  ManifestBlip getManifestBlip() {
    return manifestBlip;
  }

  /**
   * Returns map of thread ids and locations in the document of the threads.
   */
  StringMap<Integer> findAnchors() {
    final StringMap<Integer> anchors = CollectionUtils.createStringMap();
    if (isContentInitialized()) {
      getDocument().with(new Action() {

        @Override
        public <N, E extends N, T extends N> void exec(MutableDocument<N, E, T> doc) {
          for (E el : DocIterate.deepElements(doc, doc.getDocumentElement(), null)) {
            if (Blips.THREAD_INLINE_ANCHOR_TAGNAME.equals(doc.getTagName(el))) {
              String threadId = doc.getAttribute(el, Blips.THREAD_INLINE_ANCHOR_ID_ATTR);
              if ((threadId != null) && !anchors.containsKey(threadId)) {
                anchors.put(threadId, doc.getLocation(el));
              }
            }
          }
        }
      });
    } else {
      // Sets fictitious location if blip content is not available.
      int location = 0;
      // Add inline threads first.
      for (ManifestThread thread : manifestBlip.getReplies()) {
        if (thread.isInline()) {
          anchors.put(thread.getId(), location++);
        }  
      }
      // Add outline thread then.
      for (ManifestThread thread : manifestBlip.getReplies()) {
        if (!thread.isInline()) {
          anchors.put(thread.getId(), location++);
        }  
      }
    }
    return anchors;
  }

  /**
   * Deletes a thread from this blip, deleting that thread's blips.
   */
  void deleteThread(WaveletBasedConversationThread threadToDelete) {
    threadToDelete.deleteBlips(false);
    manifestBlip.removeReply(threadToDelete.getManifestThread());
    clearInlineReplyAnchors(Arrays.asList(threadToDelete.getId()));
  }

  /**
   * Deletes all threads from this blip.
   *
   * @see WaveletBasedConversationBlip#deleteThread(WaveletBasedConversationThread)
   */
  void deleteThreads(boolean shouldDeleteSelfIfEmpty) {
    // deleteThread() equivalent is inline here so we can do only one
    // document traversal to remove inline reply anchors.
    List<WaveletBasedConversationThread> threads =
        CollectionUtils.newArrayList(getReplyThreads());
    List<String> threadIds = null;
    for (WaveletBasedConversationThread threadToDelete : threads) {
      threadToDelete.deleteBlips(shouldDeleteSelfIfEmpty);
      manifestBlip.removeReply(threadToDelete.getManifestThread());
    }

    clearInlineReplyAnchors(threadIds);
  }

  /**
   * Invalidates this blip. It may no longer be accessed.
   */
  void invalidate() {
    checkIsUsable();
    manifestBlip.removeListener(manifestListener);
    helper.getWaveletEventSource().removeListener(waveletListener);
    isUsable = false;
  }

  /**
   * Recursively invalidates this blip and its replies.
   */
  void destroy() {
    for (WaveletBasedConversationThread thread : CollectionUtils.valueList(replies)) {
      thread.destroy();
    }
    invalidate();
    listeners.clear();
  }

  /**
   * Checks that this blip is safe to access.
   */
  @VisibleForTesting
  void checkIsUsable() {
    if (!isUsable) {
      Preconditions.illegalState("Deleted blip is not usable: " + this);
    }
  }

  // Private methods

  /**
   * Creates a conversation thread backed by a manifest thread and inserts it in
   * {@code replies}.
   */
  private WaveletBasedConversationThread adaptThread(ObservableManifestThread manifestThread) {
    WaveletBasedConversationThread thread =
        WaveletBasedConversationThread.create(manifestThread, this, helper);
    String id = thread.getId();
    replies.put(id, thread);
    return thread;
  }

  public void forgetThread(WaveletBasedConversationThread threadToRemove,
      WaveletOperationContext opContext) {
    String id = threadToRemove.getId();
    assert replies.containsKey(id);
    replies.remove(id);
    threadToRemove.invalidate();
    
    triggerOnReplyRemoved(threadToRemove, opContext);
  }

  public void clearContent() {
    Document content = blip.getDocument();
    if (content != null && content.size() != 0) {
      content.emptyElement(content.getDocumentElement());
    }
  }

  /**
   * Inserts an inline reply anchor element in the blip document.
   *
   * @param threadId id of the reply thread
   * @param location location at which to insert anchor
   */
  private void createInlineReplyAnchor(final String threadId, final int location) {
    blip.getDocument().with(new Action() {

      @Override
      public <N, E extends N, T extends N> void exec(MutableDocument<N, E, T> doc) {
        Point<N> point = doc.locate(location);
        doc.createElement(point, Blips.THREAD_INLINE_ANCHOR_TAGNAME,
            Collections.singletonMap(Blips.THREAD_INLINE_ANCHOR_ID_ATTR, threadId));
      }
    });
  }

  /**
   * Deletes inline reply anchor elements for a list of threads from the blip document.
   *
   * @param threadIds ids of anchors to be deleted
   */
  private void clearInlineReplyAnchors(final List<String> threadIds) {
    if (threadIds == null) {
      return;
    }
    blip.getDocument().with(new Action() {

      @Override
      public <N, E extends N, T extends N> void exec(MutableDocument<N, E, T> doc) {
        List<E> elementsToDelete = CollectionUtils.newArrayList();
        for (E el : DocIterate.deepElements(doc, doc.getDocumentElement(), null)) {
          if (Blips.THREAD_INLINE_ANCHOR_TAGNAME.equals(doc.getTagName(el))) {
            String elId = doc.getAttribute(el, Blips.THREAD_INLINE_ANCHOR_ID_ATTR);
            if (threadIds.contains(elId)) {
              elementsToDelete.add(el);
            }
          }
        }
        // Reverse elements to delete so we always delete bottom up if one
        // contains another (which would be really weird anyway).
        Collections.reverse(elementsToDelete);
        for (E el : elementsToDelete) {
          doc.deleteNode(el);
        }
      }
    });
  }

  //
  // Event triggers
  //

  private void triggerOnReplyAdded(WaveletBasedConversationThread thread, WaveletOperationContext opContext) {
    for (Listener l : listeners) {
      l.onReplyAdded(thread, opContext);
    }
  }

  private void triggerOnBeforeReplyRemoved(WaveletBasedConversationThread thread,
      WaveletOperationContext opContext) {
    for (Listener l : listeners) {
      l.onBeforeReplyRemoved(thread, opContext);
    }
  }

  private void triggerOnReplyRemoved(WaveletBasedConversationThread thread, WaveletOperationContext opContext) {
    for (Listener l : listeners) {
      l.onReplyRemoved(thread, opContext);
    }
  }

  private void triggerOnContributorAdded(ParticipantId contributor) {
    for (Listener l : listeners) {
      l.onContributorAdded(contributor);
    }
  }

  private void triggerOnContributorRemoved(ParticipantId contributor) {
    for (Listener l : listeners) {
      l.onContributorRemoved(contributor);
    }
  }

  private void triggerOnSubmitted() {
    for (Listener l : listeners) {
      l.onSubmitted();
    }
  }

  private void triggerOnTimestampModified(long oldTimestamp, long newTimestamp) {
    for (Listener l : listeners) {
      l.onTimestampChanged(oldTimestamp, newTimestamp);
    }
  }
}
