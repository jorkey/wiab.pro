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

package org.waveprotocol.wave.model.wave.data.impl;

import com.google.common.collect.ImmutableSet;
import org.waveprotocol.wave.model.conversation.TagsDocument;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.DocumentFactory;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveletData;
import org.waveprotocol.wave.model.wave.data.BlipData;
import org.waveprotocol.wave.model.wave.data.DocumentOperationSink;
import org.waveprotocol.wave.model.document.operation.DocInitialization;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of {@link WaveletData} which uses {@link BlipDataImpl}
 * to represent blips.
 *
 */
public final class WaveletDataImpl extends AbstractWaveletData<BlipData> {

  /**
   * Factory for constructing wavelet data copies.
   */
  public static final class Factory implements ObservableWaveletData.Factory<WaveletDataImpl> {
    /**
     * @param contentFactory
     *
     * @return A new WaveletDataImpl.Factory using the given content factory.
     * @throws NullPointerException if contentFactory is null.
     */
    public static Factory create(DocumentFactory<?> contentFactory) {
      return new Factory(contentFactory);
    }

    private final DocumentFactory<?> contentFactory;

    private Factory(DocumentFactory<?> contentFactory) {
      Preconditions.checkNotNull(contentFactory, "null DocumentFactory");
      this.contentFactory = contentFactory;
    }

    @Override
    public WaveletDataImpl create(ReadableWaveletData data) {
      WaveletDataImpl waveletData = new WaveletDataImpl(data, contentFactory);
      waveletData.copyParticipants(data);
      waveletData.copyDocuments(data);
      return waveletData;
    }
  }

  /** The list of participants in this wavelet. */
  private final LinkedHashSet<ParticipantId> participants = new LinkedHashSet<ParticipantId>();

  /** The set of documents in this wave, indexed by their identifier. */
  private final Map<String, BlipData> blips = CollectionUtils.newHashMap();

  /**
   * Creates a new wavelet.
   *
   * @param id                id of the wavelet
   * @param creator           creator of the wavelet
   * @param creationTime      timestamp of wavelet creation
   * @param version           initial version of the wavelet
   * @param distinctVersion   initial distinct server version of the wavelet
   * @param lastModifiedTime  initial last-modified time for the wavelet
   * @param waveId            id of the wave containing the wavelet
   * @param contentFactory    factory for creating new documents
   */
  public WaveletDataImpl(WaveletId id, ParticipantId creator, long creationTime, long version,
      HashedVersion distinctVersion, long lastModifiedTime, WaveId waveId,
      DocumentFactory<?> contentFactory) {
    super(id, creator, creationTime, version, distinctVersion, lastModifiedTime, waveId,
        contentFactory);
  }

  /**
   * Creates a copy of the given wavelet data retaining the meta data. No
   * documets or participants are copied.
   *
   * @param data data to copy
   * @param contentFactory factory for creating new documents
   */
  private WaveletDataImpl(ReadableWaveletData data, DocumentFactory<?> contentFactory) {
    super(data, contentFactory);
  }

  @Override
  protected Set<ParticipantId> getMutableParticipants() {
    return participants;
  }

  @Override
  protected BlipDataImpl internalCreateBlip(String docId, ParticipantId author,
      Collection<ParticipantId> contributors, DocInitialization content,
      long creationTime, long creationVersion,
      long lastModifiedTime, long lastModifiedVersion) {
    Preconditions.checkArgument(!blips.containsKey(docId), "Duplicate doc id: %s", docId);

    DocumentOperationSink contentSink = contentFactory.create(id, docId, content);
    BlipDataImpl blip = new BlipDataImpl(docId, getListenerManager(), author, contributors, contentSink,
        creationTime, creationVersion, lastModifiedTime, lastModifiedVersion);
    blips.put(docId, blip);
    return blip;
  }

  @Override
  public BlipData getBlip(String documentName) {
    return blips.get(documentName);
  }

  @Override
  public ImmutableSet<String> getDocumentIds() {
    return ImmutableSet.copyOf(blips.keySet());
  }

  @Override
  public ImmutableSet<String> getTags() {
    BlipData blipDoc = blips.get(IdConstants.TAGS_DOCUMENT_ID);
    if (blipDoc != null) {
      return TagsDocument.getTags(blipDoc.getContent().getMutableDocument());
    }
    return ImmutableSet.of();
  }
}
