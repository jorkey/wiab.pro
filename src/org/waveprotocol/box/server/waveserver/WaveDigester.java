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

package org.waveprotocol.box.server.waveserver;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.wave.api.ApiIdSerializer;
import com.google.wave.api.SearchResult;
import com.google.wave.api.SearchResult.Digest;

import org.waveprotocol.box.common.Snippets;
import org.waveprotocol.wave.model.conversation.BlipIterators;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.conversation.TitleHelper;
import org.waveprotocol.wave.model.conversation.WaveletBasedConversation;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.supplement.PrimitiveSupplement;
import org.waveprotocol.wave.model.supplement.PrimitiveSupplementImpl;
import org.waveprotocol.wave.model.supplement.SupplementedWave;
import org.waveprotocol.wave.model.supplement.SupplementedWaveImpl;
import org.waveprotocol.wave.model.supplement.SupplementedWaveImpl.DefaultFollow;
import org.waveprotocol.wave.model.supplement.WaveDigestSupplement;
import org.waveprotocol.wave.model.supplement.WaveDigestWithSupplements;
import org.waveprotocol.wave.model.supplement.WaveletBasedSupplement;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.WaveDigest;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.data.WaveletData;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates digests for the search service.
 *
 * @author yurize@apache.org
 */
public class WaveDigester {

  private final org.waveprotocol.box.server.robots.util.ConversationUtil conversationUtil;
  private static final int DIGEST_SNIPPET_LENGTH = 250;

  @Inject
  public WaveDigester(org.waveprotocol.box.server.robots.util.ConversationUtil conversationUtil) {
    this.conversationUtil = conversationUtil;
  }

  public SearchResult generateSearchResult(ParticipantId viewer, String query,
      Collection<WaveViewData> waves) {
    SearchResult result = new SearchResult(query);
    if (waves != null) {
      for (WaveViewData wave : waves) {
        WaveDigestWithSupplements digest = generateDigestWithSupplements(wave);
        result.addDigest(new Digest(digest.getDigest(), digest.getSupplements().get(viewer)));
      }
    }
    return result;
  }

  public WaveDigest generateDigest(ObservableWaveletData waveletData) {
    ConversationView conversations = null;
    OpBasedWavelet wavelet = OpBasedWavelet.createReadOnly(waveletData);
    if (WaveletBasedConversation.waveletHasConversation(wavelet)) {
      conversations = conversationUtil.buildConversation(wavelet);
    }
    if (conversations != null) {
      return generateDigest(conversations, waveletData);
    } else {
      return emptyDigest(waveletData.getWaveId());
    }
  }

  public WaveDigestWithSupplements generateDigestWithSupplements(WaveViewData wave) {
    // Note: the indexing infrastructure only supports single-conversation
    // waves, and requires raw wavelet access for snippeting.
    ObservableWaveletData rootWavelet = null;
    ObservableWaveletData otherWavelet = null;
    List<ObservableWaveletData> userDataWavelets = new ArrayList<>();
    for (ObservableWaveletData waveletData : wave.getWavelets()) {
      WaveletId waveletId = waveletData.getWaveletId();
      if (IdUtil.isConversationRootWaveletId(waveletId)) {
        rootWavelet = waveletData;
      } else if (IdUtil.isConversationalId(waveletId)) {
        otherWavelet = waveletData;
      } else if (IdUtil.isUserDataWavelet(waveletId)) {
        userDataWavelets.add(waveletData);
      }
    }

    ObservableWaveletData convWavelet = rootWavelet != null ? rootWavelet : otherWavelet;
    Map<ParticipantId, SupplementedWave> supplements = new HashMap<>();
    ConversationView conversations = null;
    if (convWavelet != null) {
      OpBasedWavelet wavelet = OpBasedWavelet.createReadOnly(convWavelet);
      if (WaveletBasedConversation.waveletHasConversation(wavelet)) {
        conversations = conversationUtil.buildConversation(wavelet);
        for (ObservableWaveletData udw : userDataWavelets) {
          supplements.put(udw.getCreator(), buildSupplement(udw.getCreator(), conversations, udw));
        }
      }
    }
    WaveDigest digest;
    Map<ParticipantId, WaveDigestSupplement> digestSupplements;
    if (conversations != null) {
      // This is a conversational wave. Produce a conversational digest.
      digest = generateDigest(conversations, convWavelet);
      digestSupplements = generateDigestSupplements(convWavelet.getParticipants(), conversations, supplements);
    } else {
      // It is unknown how to present this wave.
      digest = generateEmptyOrUnknownDigest(wave);
      digestSupplements = new HashMap<ParticipantId, WaveDigestSupplement>();
    }
    return new WaveDigestWithSupplements(digest, digestSupplements);
  }

  /**
   * Produces a digest for a set of conversations. Never returns null.
   *
   * @param conversations the conversation.
   * @param rawWaveletData the waveletData from which the digest is generated.
   *        This wavelet is a copy.
   * @return the server representation of the digest for the query.
   */
  public WaveDigest generateDigest(ConversationView conversations,
      WaveletData rawWaveletData) {
    String title = TitleHelper.extractTitle(conversations);
    String snippet = Snippets.renderSnippet(conversations, DIGEST_SNIPPET_LENGTH, title);
    String waveId = ApiIdSerializer.instance().serialiseWaveId(rawWaveletData.getWaveId());
    List<String> participants = CollectionUtils.newArrayList();
    for (ParticipantId p : rawWaveletData.getParticipants()) {
      participants.add(p.getAddress());
    }
    int blipCount = 0;
    Conversation rootConversation = conversations.getRoot();    
    for (ConversationBlip blip : BlipIterators.breadthFirst(rootConversation)) {
      blipCount++;
    }
    return new WaveDigest(waveId, title, snippet,
        rawWaveletData.getCreator().getAddress(), participants, blipCount,
        rawWaveletData.getCreationTime(), rawWaveletData.getLastModifiedTime());
  }

  /**
   * Produces a digest supplements.
   *
   * @param conversations the conversation.
   * @param supplements allows to easily perform various
   *        queries on user related state of the wavelet.
   * @return digest supplements.
   */
  public Map<ParticipantId, WaveDigestSupplement> generateDigestSupplements(Set<ParticipantId> participants,
      ConversationView conversations, Map<ParticipantId, SupplementedWave> supplements) {
    Conversation rootConversation = conversations.getRoot();
    Map<ParticipantId, WaveDigestSupplement> digestSupplements = new HashMap<>();
    for (ParticipantId participant : participants) {
      String folder;
      int unreadCount = 0;
      SupplementedWave supplement = supplements.get(participant);
      if (supplement != null) {
        folder = WaveDigestSupplement.FOLDER_INBOX;
        if (supplement.isInbox()) {
          folder = WaveDigestSupplement.FOLDER_INBOX;
        } else if (supplement.isArchived()) {
          folder = WaveDigestSupplement.FOLDER_ARCHIVE;
        } else if (supplement.isTrashed()) {
          folder = WaveDigestSupplement.FOLDER_TRASH;
        }  
        for (ConversationBlip blip : BlipIterators.breadthFirst(rootConversation)) {
          if (supplement.isUnread(blip)) {
            unreadCount++;
          }
        }
      } else {
        folder = WaveDigestSupplement.FOLDER_INBOX;
        for (ConversationBlip blip : BlipIterators.breadthFirst(rootConversation)) {
          unreadCount++;
        }
      }
      digestSupplements.put(participant, new WaveDigestSupplement(folder, unreadCount));
    }
    return digestSupplements;
  }

  /** @return a digest for an empty wave. */
  private WaveDigest emptyDigest(WaveId waveId) {
    String title = ModernIdSerialiser.INSTANCE.serialiseWaveId(waveId);
    String id = ApiIdSerializer.instance().serialiseWaveId(waveId);
    return new WaveDigest(id, title, "(empty)", null, Collections.<String> emptyList(), 0, -1L, -1L);
  }

  /** @return a digest for an unrecognised type of wave. */
  private WaveDigest unknownDigest(WaveViewData wave) {
    String title = ModernIdSerialiser.INSTANCE.serialiseWaveId(wave.getWaveId());
    String id = ApiIdSerializer.instance().serialiseWaveId(wave.getWaveId());
    long lmt = -1L;
    long created = -1L;
    int docs = 0;
    String creator = null;
    List<String> participants = new ArrayList<String>();
    for (WaveletData data : wave.getWavelets()) {
      lmt = Math.max(lmt, data.getLastModifiedTime());
      created = Math.max(lmt, data.getCreationTime());
      docs += data.getDocumentIds().size();
      if (creator == null) {
        creator = data.getCreator().getAddress();
      }
      for (ParticipantId p : data.getParticipants()) {
        participants.add(p.getAddress());
      }
    }
    return new WaveDigest(id, title, "(unknown)", creator, participants, docs, created, lmt);
  }

  /**
   * Generates an empty digest in case the wave is empty, or an unknown digest
   * otherwise.
   *
   * @param wave the wave.
   * @return the generated digest.
   */
  private WaveDigest generateEmptyOrUnknownDigest(WaveViewData wave) {
    boolean empty = !wave.getWavelets().iterator().hasNext();
    WaveDigest digest = empty ? emptyDigest(wave.getWaveId()) : unknownDigest(wave);
    return digest;
  }

  /**
   * Builds the supplement model from a wave. Never returns null.
   *
   * @param viewer the participant for which the supplement is constructed.
   * @param conversations conversations in the wave
   * @param udw the user data wavelet for the logged user.
   * @return the wave supplement.
   */
  @VisibleForTesting
  private SupplementedWave buildSupplement(ParticipantId viewer, ConversationView conversations,
      ObservableWaveletData udw) {
    // Use mock state if there is no UDW.
    PrimitiveSupplement udwState =
        udw != null ? WaveletBasedSupplement.create(OpBasedWavelet.createReadOnly(udw))
            : new PrimitiveSupplementImpl();
    return SupplementedWaveImpl.create(udwState, conversations, viewer, DefaultFollow.ALWAYS);
  }
}
