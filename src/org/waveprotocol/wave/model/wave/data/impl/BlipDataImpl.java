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
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.BlipData;
import org.waveprotocol.wave.model.wave.data.DocumentOperationSink;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.operation.SilentOperationSink;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.waveprotocol.wave.model.util.Preconditions;

/**
 * Provides a skeleton implementation of a primitive blip.
 *
 */
public final class BlipDataImpl extends AbstractBlipData {
  /** The participants who have contributed to this blip's content. */
  private final LinkedHashSet<ParticipantId> contributors;

  /**
   * Creates a blip.
   */
  static public BlipData create(String id,
      WaveletDataListenerManager waveletListenerManager,
      ParticipantId author, Collection<ParticipantId> contributors, DocumentOperationSink operationSink,
      long creationTime, long creationVersion, long lastModifiedTime, long lastModifiedVersion) {
    return new BlipDataImpl(id, waveletListenerManager, author, contributors, operationSink,
      creationTime, creationVersion, lastModifiedTime, lastModifiedVersion);
  }

  /**
   * Creates a blip.
   *
   * @param id the id of this blip
   * @param waveletInterface the interface to wavelet containing this blip
   * @param author the author of this blip
   * @param contributors the contributors of this blip
   * @param content XML document of this blip
   * @param lastModifiedTime the last modified time of this blip
   * @param lastModifiedVersion the last modified version of this blip
   */
  BlipDataImpl(String id, WaveletDataListenerManager listenerManager,
      ParticipantId author, Collection<ParticipantId> contributors, DocumentOperationSink content,
      long creationTime, long creationVersion,
      long lastModifiedTime, long lastModifiedVersion) {
    super(id, listenerManager, author, content, creationTime, creationVersion, lastModifiedTime, lastModifiedVersion);
    this.contributors = new LinkedHashSet<>();
    if (contributors != null) {
      for (ParticipantId contributor : contributors) {
        Preconditions.checkNotNull(contributor, "contributor is null");
        this.contributors.add(contributor);
      }
    }
  }

  @Override
  public void init(SilentOperationSink<? super DocOp> outputSink) {
    getContent().init(outputSink);
  }

  @Override
  public ImmutableSet<ParticipantId> getContributors() {
    return ImmutableSet.copyOf(contributors);
  }

  //
  // Mutators
  //

  @Override
  public void addContributor(ParticipantId participant) {
    Preconditions.checkNotNull(participant, "contributor is null");
    if (contributors.add(participant)) {
      fireContributorAdded(participant);
    }
  }

  @Override
  public void removeContributor(ParticipantId participant) {
    boolean removed = contributors.removeAll(Arrays.asList(participant));
    if (removed) {
      fireContributorRemoved(participant);
    }
  }
}
