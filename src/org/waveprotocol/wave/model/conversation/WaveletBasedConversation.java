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

import org.waveprotocol.wave.model.document.Doc;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.document.ObservableDocument;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.Nindo;
import org.waveprotocol.wave.model.document.util.Point;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletIdSerializer;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.StringMap;
import org.waveprotocol.wave.model.util.ValueUtils;
import org.waveprotocol.wave.model.wave.Blip;
import org.waveprotocol.wave.model.wave.ObservableWavelet;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.SourcesEvents;
import org.waveprotocol.wave.model.wave.Wavelet;
import org.waveprotocol.wave.model.wave.WaveletListener;
import org.waveprotocol.wave.model.wave.opbased.WaveletListenerImpl;

import java.util.Set;

/**
 * A {@link Conversation} implemented in terms of a {@link Wavelet}.
 *
 * @author anorth@google.com (Alex North)
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public final class WaveletBasedConversation implements ObservableConversation {

  /**
   * Provides wavelet based collaborators with construction of and access to the
   * underlying wavelet and events.
   */
  final class ComponentHelper {

    /** Gets the conversation for this helper. */
    WaveletBasedConversation getConversation() {
      return WaveletBasedConversation.this;
    }

    /** Creates a new thread id. */
    String createThreadId() {
      // TODO(user): stop using the blip id when wave panel and rusty doesn't
      // rely on it.
      return idGenerator.peekBlipId();
      //return idGenerator.newUniqueToken();
    }

    /**
     * Creates and initialises a blip object in the wavelet.
     *
     * @param content initial content for the new blip, or {@code null} for
     *        default content
     */
    Blip createBlip(DocInitialization content) {
      Blip blip = wavelet.createBlip(idGenerator.newBlipId());
      if (content != null) {
        blip.getDocument().hackConsume(Nindo.fromDocOp(content, false));
      } else {
        Document doc = blip.getDocument();
        doc.insertXml(Point.<Doc.N> end(doc.getDocumentElement()),
            Blips.INITIAL_CONTENT);
      }
      return blip;
    }

    ObservableWavelet getWavelet() {
      return wavelet;
    }

    Blip getBlip(String blipId) {
      return wavelet.getBlip(blipId);
    }

    Blip getOrCreateBlip(String blipId) {
      Blip blip = wavelet.getBlip(blipId);
      if (blip == null) {
        blip = wavelet.createBlip(blipId);
      }
      return blip;
    }

    /**
     * Gets the source of wavelet-level events.
     */
    SourcesEvents<WaveletListener> getWaveletEventSource() {
      return wavelet;
    }
  }

  /**
   * Listens to threads in this conversation and forwards events to
   * conversation listeners.
   */
  private final WaveletBasedConversationThread.Listener threadListener =
      new WaveletBasedConversationThread.Listener() {

    @Override
    public void onBlipAdded(WaveletBasedConversationBlip blip, WaveletOperationContext opContext) {
      observe(blip);

      triggerOnBlipAdded(blip, opContext);
    }

    @Override
    public void onBeforeBlipRemoved(WaveletBasedConversationBlip blip,
        WaveletOperationContext opContext) {
      triggerOnBeforeBlipRemoved(blip, opContext);
    }

    @Override
    public void onBlipRemoved(WaveletBasedConversationBlip blip,
        WaveletOperationContext opContext) {
      blip.clearListeners();
      blips.remove(blip.getId());

      triggerOnBlipRemoved(blip, opContext);
    }
  };

  /**
   * Listens to blips in this conversation and forwards events to
   * conversation listeners.
   */
  private final class BlipListenerAggregator implements WaveletBasedConversationBlip.Listener {

    private final WaveletBasedConversationBlip blip;

    BlipListenerAggregator(WaveletBasedConversationBlip blip) {
      this.blip = blip;
    }

    @Override
    public void onReplyAdded(WaveletBasedConversationThread reply,
        WaveletOperationContext opContext) {
      observe(reply);

      triggerOnReplyAdded(reply, opContext);
    }

    @Override
    public void onBeforeReplyRemoved(WaveletBasedConversationThread reply,
        WaveletOperationContext opContext) {
      triggerOnBeforeReplyRemoved(reply, opContext);
    }

    @Override
    public void onReplyRemoved(WaveletBasedConversationThread reply,
        WaveletOperationContext opContext) {
      reply.clearListeners();
      threads.remove(reply.getId());

      triggerOnReplyRemoved(reply, opContext);
    }

    @Override
    public void onContributorAdded(ParticipantId contributor) {
      triggerOnBlipContributorAdded(blip, contributor);
    }

    @Override
    public void onContributorRemoved(ParticipantId contributor) {
      triggerOnBlipContributorRemoved(blip, contributor);
    }

    @Override
    public void onSubmitted() {
      triggerOnBlipSubmitted(blip);
    }

    @Override
    public void onTimestampChanged(long oldTimestamp, long newTimestamp) {
      triggerOnBlipTimestampChanged(blip, oldTimestamp, newTimestamp);
    }
  }

  /** Forwards wavelet events to the conversation listeners. */
  private final WaveletListener waveletListener = new WaveletListenerImpl() {

    @Override
    public void onParticipantAdded(ObservableWavelet wavelet, ParticipantId participant,
        WaveletOperationContext opContext) {
      triggerOnParticipantAdded(participant, opContext);
    }

    @Override
    public void onParticipantRemoved(ObservableWavelet wavelet, ParticipantId participant,
        WaveletOperationContext opContext) {
      triggerOnParticipantRemoved(participant, opContext);
    }

    @Override
    public void onTagAdded(ObservableWavelet wavelet, String tag,
        WaveletOperationContext opContext) {
      triggerOnTagAdded(tag, opContext);
    }

    @Override
    public void onTagRemoved(ObservableWavelet wavelet, String tag,
        WaveletOperationContext opContext) {
      triggerOnTagRemoved(tag, opContext);
    }
  };

  /** Forwards manifest events to conversation listeners. */
  private final ObservableManifest.Listener manifestListener = new ObservableManifest.Listener() {

    @Override
    public void onAnchorChanged(AnchorData oldAnchor, AnchorData newAnchor) {
      triggerOnAnchorChanged(oldAnchor, newAnchor);
    }
  };

  /** Wave containing this conversation. */
  private final WaveBasedConversationView wave;

  /** Wavelet backing this conversation. */
  private final ObservableWavelet wavelet;

  /** Generator for component ids. */
  private final IdGenerator idGenerator;

  /** Value holding anchor information. */
  private final ObservableManifest manifest;

  /** The root conversation thread. */
  private final WaveletBasedConversationThread rootThread;

  /** Listeners. */
  private final CopyOnWriteSet<AnchorListener> anchorListeners = CopyOnWriteSet.create();
  private final CopyOnWriteSet<ReplyListener> replyListeners = CopyOnWriteSet.create();
  private final CopyOnWriteSet<BlipListener> blipListeners = CopyOnWriteSet.create();
  private final CopyOnWriteSet<ParticipantListener> participantListeners = CopyOnWriteSet.create();
  private final CopyOnWriteSet<TagListener> tagListeners = CopyOnWriteSet.create();
  private final CopyOnWriteSet<BlipContentListener> blipContentListeners = CopyOnWriteSet.create();

  /** Blips in this conversation. */
  private final StringMap<WaveletBasedConversationBlip> blips = CollectionUtils.createStringMap();

  /** Threads in this conversation. */
  private final StringMap<ObservableConversationThread> threads = CollectionUtils.createStringMap();

  /** Whether this conversation is still active. */
  private boolean isUsable = true;

  /**
   * Checks whether a wavelet has conversation structure.
   */
  public static boolean waveletHasConversation(Wavelet wavelet) {
    return DocumentBasedManifest.documentHasManifest(getManifestDocument(wavelet));
  }

  /**
   * Builds empty conversation structure on a wavelet.
   *
   * @throws IllegalStateException if the wavelet already has conversation
   *         structure
   */
  public static void makeWaveletConversational(Wavelet wavelet) {
    DocumentBasedManifest.initialiseDocumentManifest(getManifestDocument(wavelet));
  }

  /**
   * Computes the conversation id for a wavelet.
   */
  public static String idFor(WaveletId wavelet) {
    return WaveletIdSerializer.INSTANCE.toString(wavelet);
  }

  /**
   * Computes a wavelet id for a conversation.
   */
  public static WaveletId widFor(String conversation) {
    return WaveletIdSerializer.INSTANCE.fromString(conversation);
  }

  /**
   * Builds a conversation model.
   *
   * @param view view containing this conversation
   * @param wavelet wavelet on which to build the conversation
   * @param manifest manifest describing the conversation structure
   * @param idGenerator generator for new identifiers
   * @return a new conversation model
   */
  static WaveletBasedConversation create(WaveBasedConversationView view,
      ObservableWavelet wavelet, ObservableManifest manifest, IdGenerator idGenerator) {
    WaveletBasedConversation conversation = new WaveletBasedConversation(view, wavelet, manifest,
        idGenerator);
    wavelet.addListener(conversation.waveletListener);
    manifest.addListener(conversation.manifestListener);
    conversation.observe(conversation.rootThread);
    return conversation;
  }

  /**
   * Gets the document on which the conversation manifest is constructed.
   */
  @VisibleForTesting
  static ObservableDocument getManifestDocument(Wavelet wavelet) {
    return wavelet.getDocument(IdConstants.MANIFEST_DOCUMENT_ID);
  }

  /**
   * Constructs a new conversation backed by a wavelet.
   */
  WaveletBasedConversation(WaveBasedConversationView wave, ObservableWavelet wavelet,
      ObservableManifest manifest, IdGenerator idGenerator) {
    Preconditions.checkNotNull(wavelet, "Null wavelet");
    Preconditions.checkNotNull(manifest, "Null conversation manifest");
    this.wave = wave;
    this.wavelet = wavelet;
    this.manifest = manifest;
    this.idGenerator = idGenerator;

    try {
      this.rootThread = WaveletBasedConversationThread.create(manifest.getRootThread(), null,
          new ComponentHelper());
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Failed to create conversation on wavelet "
          + wavelet.getWaveId() + " " + wavelet.getId(), e);
    }
  }

  @Override
  public boolean hasAnchor() {
    // True if the manifest specifies an anchor to a wavelet in view.
    return isValidAnchor(getAnchorWaveletId(), getAnchorBlipId());
  }

  @Override
  public void delete() {
    getRootThread().delete();
    DocumentBasedManifest.delete(getManifestDocument(wavelet));
    Preconditions.checkState(!isUsable, "Conversation still usable after delete");
  }

  @Override
  public Anchor getAnchor() {
    return maybeMakeAnchor(getAnchorWaveletId(), getAnchorBlipId());
  }

  @Override
  public void setAnchor(Anchor newAnchor) {
    checkIsUsable();
    if (newAnchor != null) {
      Preconditions.checkArgument(newAnchor.getConversation().getClass() == getClass(),
          "Anchor must not refer to a different conversation class");
      Preconditions.checkArgument(newAnchor.getConversation() != this,
          "Anchor must not refer to a different anchored conversation");

      WaveletBasedConversation conv = (WaveletBasedConversation) newAnchor.getConversation();
      String blipId = newAnchor.getBlip().getId();
      manifest.setAnchor(new AnchorData(idFor(conv.getWaveletId()), blipId));
    } else {
      manifest.setAnchor(new AnchorData(null, null));
    }
  }

  @Override
  public Anchor createAnchor(ConversationBlip blip) {
    checkIsUsable();
    return new Anchor(this, blip);
  }

  @Override
  public WaveletBasedConversationThread getRootThread() {
    return rootThread;
  }

  @Override
  public WaveletBasedConversationBlip getBlip(String id) {
    return blips.get(id);
  }

  @Override
  public WaveletBasedConversationThread getThread(String threadId) {
    return (WaveletBasedConversationThread) threads.get(threadId);
  }

  @Override
  public ObservableDocument getDataDocument(String name) {
    if (IdUtil.isBlipId(name)) {
      Preconditions.illegalArgument("Cannot fetch blip document " + name + " as a data document");
    } else if (IdConstants.MANIFEST_DOCUMENT_ID.equals(name)) {
      Preconditions.illegalArgument("Cannot fetch conversation manifest as a data document");
    }
    return wavelet.getDocument(name);
  }

  @Override
  public Set<ParticipantId> getParticipantIds() {
    return wavelet.getParticipantIds();
  }

  @Override
  public void addParticipantIds(Set<ParticipantId> participants) {
    checkIsUsable();
    wavelet.addParticipantIds(participants);
  }

  @Override
  public void addParticipant(ParticipantId participant) {
    checkIsUsable();
    wavelet.addParticipant(participant);
  }

  @Override
  public void removeParticipant(ParticipantId participant) {
    checkIsUsable();
    wavelet.removeParticipant(participant);
  }

  @Override
  public Set<String> getTags() {
    return wavelet.getTags();
  }

  @Override
  public void addTag(String tag) {
    checkIsUsable();
    final Set<String> tags = wavelet.getTags();
    if (tags == null || !tags.contains(tag)) {
      wavelet.addTag(tag);
      triggerOnTagAdded(tag, null);
    }
  }

  @Override
  public void removeTag(String tag) {
    checkIsUsable();
    final Set<String> tags = wavelet.getTags();
    if (tags != null && tags.contains(tag)) {
      wavelet.removeTag(tag);
      triggerOnTagRemoved(tag, null);
    }
  }

  @Override
  public boolean isRoot() {
    // Root conversation is the only one in conversation view without an anchor.
    return !hasAnchor();
  }
  
  @Override
  public String getId() {
    return idFor(getWaveletId());
  }

  //
  // Listeners
  //

  @Override
  public void addReplyListener(ReplyListener listener) {
    replyListeners.add(listener);
  }

  @Override
  public void removeReplyListener(ReplyListener listener) {
    replyListeners.remove(listener);
  }

  @Override
  public void addBlipListener(BlipListener listener) {
    blipListeners.add(listener);
  }

  @Override
  public void removeBlipListener(BlipListener listener) {
    blipListeners.remove(listener);
  }

  @Override
  public void addParticipantListener(ParticipantListener listener) {
    participantListeners.add(listener);
  }

  @Override
  public void removeParticipantListener(ParticipantListener listener) {
    participantListeners.remove(listener);
  }

  @Override
  public void addTagListener(TagListener listener) {
    tagListeners.add(listener);
  }

  @Override
  public void removeTagListener(TagListener listener) {
    tagListeners.remove(listener);
  }

  @Override
  public void addBlipContentListener(BlipContentListener listener) {
    blipContentListeners.add(listener);
  }

  @Override
  public void removeBlipContentListener(BlipContentListener listener) {
    blipContentListeners.remove(listener);
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

  @Override
  public void addAnchorListener(AnchorListener listener) {
    anchorListeners.add(listener);
  }

  @Override
  public void removeAnchorListener(AnchorListener listener) {
    anchorListeners.remove(listener);
  }

  @Override
  public String toString() {
    return "C " + wavelet.getWaveId() + wavelet.getId();
  }

  /**
   * Gets the wavelet backing this conversation.
   */
  public ObservableWavelet getWavelet() {
    return wavelet;
  }

  /**
   * Destroys this conversation and detaches listeners. The conversation may
   * still be inspected but may not be mutated and will not generate any further
   * events.
   */
  void destroy() {
    checkIsUsable();
    wavelet.removeListener(waveletListener);
    manifest.removeListener(manifestListener);

    replyListeners.clear();
    blipListeners.clear();
    participantListeners.clear();
    tagListeners.clear();
    blipContentListeners.clear();
    anchorListeners.clear();

    rootThread.destroy();
    isUsable = false;
  }

  /**
   * Gets the manifest describing this conversation.
   */
  @VisibleForTesting
  ObservableManifest getManifest() {
    return manifest;
  }

  private WaveletId getWaveletId() {
    return wavelet.getId();
  }

  private WaveletId getAnchorWaveletId() {
    // may be null
    String anchorWaveletId = manifest.getAnchor().getConversationId();
    return widFor(anchorWaveletId);
  }

  private String getAnchorBlipId() {
    return manifest.getAnchor().getBlipId();
  }

  /**
   * Checks whether a wavelet id and blip id specify a valid anchor.
   */
  private boolean isValidAnchor(WaveletId waveletId, String blipId) {
    // True if the fields are non-null, the anchoring wavelet is in view, and
    // the conversation has the blip.
    boolean isValid = false;
    if ((waveletId != null) && (blipId != null)) {
      WaveletBasedConversation conversation = wave.getConversation(waveletId);
      if (conversation != null) {
        isValid = (conversation.getBlip(blipId) != null);
      }
    }
    return isValid;
  }

  /**
   * Deserialises a wavelet id if it's not null, else returns null;
   */
  private WaveletId maybeMakeWaveletId(String idString) {
    return widFor(idString);
  }

  /**
   * Builds an anchor if the wave has a specific wavelet and blip, else returns
   * null.
   */
  private Anchor maybeMakeAnchor(WaveletId waveletId, String blipId) {
    Anchor anchor = null;
    if (isValidAnchor(waveletId, blipId)) {
      WaveletBasedConversation anchorConversation = wave.getConversation(waveletId);
      ConversationBlip anchorBlip = anchorConversation.getBlip(blipId);
      anchor = new Anchor(anchorConversation, anchorBlip);
    }
    return anchor;
  }

  /**
   * Throws {@link IllegalStateException} if this conversation has been
   * destroyed.
   */
  public void checkIsUsable() {
    Preconditions.checkState(isUsable, "Cannot use destroyed conversation");
  }

  private void observe(WaveletBasedConversationBlip blip) {
    blips.put(blip.getId(), blip);
    blip.addListener(new BlipListenerAggregator(blip));
    for (WaveletBasedConversationThread thread : blip.getReplyThreads()) {
      observe(thread);
    }
  }

  private void observe(WaveletBasedConversationThread thread) {
    threads.put(thread.getId(), thread);
    thread.addListener(threadListener);
    for (WaveletBasedConversationBlip blip : thread.getBlips()) {
      observe(blip);
    }
  }

  //
  // Event triggers
  //

  private void triggerOnParticipantAdded(ParticipantId participant,
      WaveletOperationContext opContext) {
    for (ParticipantListener l : participantListeners) {
      l.onParticipantAdded(participant, opContext);
    }
  }

  private void triggerOnParticipantRemoved(ParticipantId participant,
      WaveletOperationContext opContext) {
    for (ParticipantListener l : participantListeners) {
      l.onParticipantRemoved(participant, opContext);
    }
  }

  private void triggerOnTagAdded(String tag, WaveletOperationContext opContext) {
    for (TagListener l : tagListeners) {
      l.onTagAdded(tag, opContext);
    }
  }

  private void triggerOnTagRemoved(String tag, WaveletOperationContext opContext) {
    for (TagListener l : tagListeners) {
      l.onTagRemoved(tag, opContext);
    }
  }

  private void triggerOnAnchorChanged(AnchorData oldMAnchor, AnchorData newMAnchor) {
    WaveletId oldWaveletId = maybeMakeWaveletId(oldMAnchor.getConversationId());
    WaveletId newWaveletId = maybeMakeWaveletId(newMAnchor.getConversationId());
    Anchor oldAnchor = maybeMakeAnchor(oldWaveletId, oldMAnchor.getBlipId());
    Anchor newAnchor = maybeMakeAnchor(newWaveletId, newMAnchor.getBlipId());
    triggerOnAnchorChanged(oldAnchor, newAnchor);
  }

  private void triggerOnAnchorChanged(Anchor oldAnchor, Anchor newAnchor) {
    if (ValueUtils.notEqual(oldAnchor, newAnchor)) {
      for (AnchorListener l : anchorListeners) {
        l.onAnchorChanged(oldAnchor, newAnchor);
      }
    }
  }

  private void triggerOnBlipAdded(ObservableConversationBlip blip,
      WaveletOperationContext opContext) {
    for (BlipListener l : blipListeners) {
      l.onBlipAdded(blip, opContext);
    }
  }

  private boolean triggerOnBeforeBlipRemoved(ObservableConversationBlip blip,
      WaveletOperationContext opContext) {
    for (BlipListener l : blipListeners) {
      l.onBeforeBlipRemoved(blip, opContext);
    }
    return true;
  }

  private void triggerOnBlipRemoved(ObservableConversationBlip blip,
      WaveletOperationContext opContext) {
    for (BlipListener l : blipListeners) {
      l.onBlipRemoved(blip, opContext);
    }
  }

  private void triggerOnReplyAdded(ObservableConversationThread thread,
      WaveletOperationContext opContext) {
    for (ReplyListener l : replyListeners) {
      l.onReplyAdded(thread, opContext);
    }
  }

  private void triggerOnBeforeReplyRemoved(ObservableConversationThread thread,
      WaveletOperationContext opContext) {
    for (ReplyListener l : replyListeners) {
      l.onBeforeReplyRemoved(thread, opContext);
    }
  }

  private void triggerOnReplyRemoved(ObservableConversationThread thread,
      WaveletOperationContext opContext) {
    for (ReplyListener l : replyListeners) {
      l.onReplyRemoved(thread, opContext);
    }
  }

  private void triggerOnBlipContributorAdded(ObservableConversationBlip blip,
      ParticipantId contributor) {
    for (BlipContentListener l : blipContentListeners) {
      l.onBlipContributorAdded(blip, contributor);
    }
  }

  private void triggerOnBlipContributorRemoved(ObservableConversationBlip blip,
      ParticipantId contributor) {
    for (BlipContentListener l : blipContentListeners) {
      l.onBlipContributorRemoved(blip, contributor);
    }
  }

  private void triggerOnBlipSubmitted(ObservableConversationBlip blip) {
    for (BlipContentListener l : blipContentListeners) {
      l.onBlipSubmitted(blip);
    }
  }

  private void triggerOnBlipTimestampChanged(ObservableConversationBlip blip, long oldTimestamp,
      long newTimestamp) {
    for (BlipContentListener l : blipContentListeners) {
      l.onBlipTimestampChanged(blip, oldTimestamp, newTimestamp);
    }
  }
}
