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

import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.BlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.BlipData;
import org.waveprotocol.wave.model.wave.data.DocumentOperationSink;

/**
 * Provides a skeleton implementation of a primitive blip except
 * contributors.
 *
 */
public abstract class AbstractBlipData implements BlipData {
  /** This blip's identifier */
  private final String id;

  /** The listener of wavelet in which this blip appears. */
  private final WaveletDataListenerManager waveletListenerManager;

  /** The XML content of this blip. */
  private final DocumentOperationSink content;

  /** The id of the author of this blip. */
  private final ParticipantId author;

  /** The epoch time of the creation of this blip. */
  private long creationTime;

  /** The wavelet version of the creation of this blip. */
  private long creationVersion;
  
  /** The epoch time of the last modification to this blip. */
  private long lastModifiedTime;

  /** The wavelet version of the last modification to this blip. */
  private long lastModifiedVersion;

  /**
   * Creates a blip.
   *
   * @param id the id of this blip
   * @param waveletListenerManager wavelet data listener
   * @param author the author of this blip
   * @param content XML document of this blip
   * @param creationTime the creation time
   * @param creationVersion the creation version
   * @param lastModifiedTime the last modified time of this blip
   * @param lastModifiedVersion the last modified version of this blip
   */
  protected AbstractBlipData(String id,
      WaveletDataListenerManager waveletListenerManager,
      ParticipantId author, DocumentOperationSink content, 
      long creationTime, long creationVersion,
      long lastModifiedTime, long lastModifiedVersion) {
    this.content = content;
    this.id = id;
    this.waveletListenerManager = waveletListenerManager;
    this.author = author;
    this.creationTime = creationTime;
    this.creationVersion = creationVersion;
    this.lastModifiedTime = lastModifiedTime;
    this.lastModifiedVersion = lastModifiedVersion;
  }

  //
  // Accessors
  //

  @Override
  final public String getId() {
    return id;
  }

  @Override
  final public ParticipantId getAuthor() {
    return author;
  }

  @Override
  public long getCreationTime() {
    return creationTime;
  }

  @Override
  public long getCreationVersion() {
    return creationVersion;
  }

  @Override
  final public long getLastModifiedTime() {
    return lastModifiedTime;
  }

  @Override
  final public long getLastModifiedVersion() {
    return lastModifiedVersion;
  }

  @Override
  public boolean isConsistent() {
    return true;
  }

  @Override
  public boolean hasContent() {
    return true;
  }

  @Override
  public boolean isContentInitialized() {
    return true;
  }

  @Override
  public void initalizeSnapshot() {
  }

  @Override
  public void processDiffs() {
  }

  @Override
  final public DocumentOperationSink getContent() {
    return content;
  }

  //
  // Mutators
  //

  /**
   * {@inheritDoc}
   *
   * Tells the wavelet to notify its listeners that this blip has been submitted.
   */
  @Override
  final public void submit() {
    fireBlipDataSubmitted();
  }

  @Override
  final public long setLastModifiedTime(long newTime) {
    if (newTime == lastModifiedTime) {
      return newTime;
    }

    Long oldLastModifiedTime = lastModifiedTime;
    lastModifiedTime = newTime;
    fireBlipDataTimestampModified(oldLastModifiedTime, newTime);
    return oldLastModifiedTime;
  }

  @Override
  final public long setLastModifiedVersion(long newVersion) {
    if (newVersion == lastModifiedVersion) {
      return newVersion;
    }
    Long oldVersion = lastModifiedVersion;
    lastModifiedVersion = newVersion;
    fireBlipDataVersionModified(oldVersion, newVersion);
    return oldVersion;
  }

  @Override
  public void consume(BlipOperation operation) throws OperationException {
    operation.apply(this);
  }

  @Override
  final public String toString() {
    return "Blip state = " +
        "[id:" + id + "] " +
        "[author: " + author + "] " +
        "[contributors: " + getContributors() + "] " +
        "[lastModifiedVersion:" + lastModifiedVersion + "] " +
        "[lastModifiedTime:" + lastModifiedTime + "]";
  }

  @Deprecated
  @Override
  final public void onRemoteContentModified() {
    if (waveletListenerManager != null) {
      waveletListenerManager.onRemoteBlipDataContentModified(this);
    }
  }
  
  @Deprecated
  @Override
  public void onTagAdded(String tag, WaveletOperationContext opContext) {
    if (waveletListenerManager != null) {
      waveletListenerManager.onTagAdded(tag, opContext);
    }
  }

  @Override
  public void onTagRemoved(String tag, WaveletOperationContext opContext) {
    if (waveletListenerManager != null) {
      waveletListenerManager.onTagRemoved(tag, opContext);
    }
  }

  final protected void fireBlipDataSubmitted() {
    if (waveletListenerManager != null) {
      waveletListenerManager.onBlipDataSubmitted(this);
    }
  }

  final protected void fireBlipDataVersionModified(long oldVersion, long newVersion) {
    if (waveletListenerManager != null) {
      waveletListenerManager.onBlipDataVersionModified(this, oldVersion, newVersion);
    }
  }

  final protected void fireBlipDataTimestampModified(long oldTimestamp, long newTimestamp) {
    if (waveletListenerManager != null) {
      waveletListenerManager.onBlipDataTimestampModified(this, oldTimestamp, newTimestamp);
    }
  }

  final protected void fireContributorAdded(ParticipantId contributor) {
    if (waveletListenerManager != null) {
      waveletListenerManager.onBlipDataContributorAdded(this, contributor);
    }
  }

  final protected void fireContributorRemoved(ParticipantId contributor) {
    if (waveletListenerManager != null) {
      waveletListenerManager.onBlipDataContributorRemoved(this, contributor);
    }
  }
}
