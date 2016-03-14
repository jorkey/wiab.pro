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

package org.waveprotocol.wave.model.raw;

import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.communication.Blob;

import com.google.common.collect.ImmutableSet;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;

/**
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class RawBlipSnapshot implements RawSnapshot {

  public interface Serializer {
    public interface Writer {
      void setAuthor(ParticipantId author);

      void setContributors(ImmutableSet<ParticipantId> contributors);

      void setCreationTime(long creationTime);

      void setCreationVersion(long creationVersion);

      void setLastModifiedTime(long lastModifiedTime);

      void setLastModifiedVersion(long lastModifiedVersion);

      void setContent(DocInitialization content);
    }

    Blob serializeBlip(RawBlipSnapshot snapshot);
    void deserializeBlip(Blob serialized, Writer writer);
  };

  private final Serializer serializer;
  private final String documentId;

  private Blob serialized;
  private ParticipantId author;
  private ImmutableSet<ParticipantId> contributors;
  private long creationTime;
  private long creationVersion;
  private long lastModifiedTime;
  private long lastModifiedVersion;
  private DocInitialization content;

  public RawBlipSnapshot(Serializer serializer, Blob serialized, String documentId) {
    this.serializer = serializer;
    this.serialized = serialized;
    this.documentId = documentId;
  }

  public RawBlipSnapshot(Serializer serializer, String documentId, ParticipantId author,
      ImmutableSet<ParticipantId> contributors, DocInitialization content,
      long creationTime, long creationVersion,
      long lastModifiedTime, long lastModifiedVersion) {
    this.serializer = serializer;
    this.documentId = documentId;
    this.author = author;
    this.contributors = contributors;
    this.content = content;
    this.creationTime = creationTime;
    this.creationVersion = creationVersion;
    this.lastModifiedTime = lastModifiedTime;
    this.lastModifiedVersion = lastModifiedVersion;
  }

  public long getWaveletVersion() {
    return 0;
  }

  public String getId() {
    return documentId;
  }

  public ParticipantId getAuthor() {
    deserialize();
    return author;
  }

  public ImmutableSet<ParticipantId> getContributors() {
    deserialize();
    return contributors;
  }

  public DocInitialization getContent() {
    deserialize();
    return content;
  }

  public long getCreationTime() {
    deserialize();
    return creationTime;
  }

  public long getCreationVersion() {
    deserialize();
    return creationVersion;
  }

  public long getLastModifiedTime() {
    deserialize();
    return lastModifiedTime;
  }

  public long getLastModifiedVersion() {
    deserialize();
    return lastModifiedVersion;
  }

  @Override
  public Blob serialize() {
    if (serialized == null) {
      serialized = serializer.serializeBlip(this);
    }
    return serialized;
  }

  @Override
  public String toString() {
    deserialize();
    StringBuilder sb = new StringBuilder();
    sb.append("content: ").append(content);
    sb.append("\nauthor: ").append(author);
    if (contributors != null) {
      sb.append("\ncontributors:");
      for (ParticipantId contributor : contributors) {
        sb.append(" ").append(contributor.toString());
      }
    }
    sb.append("\ncreationTime: ").append(creationTime);
    sb.append("\ncreationVersion: ").append(creationVersion);
    sb.append("\nlastModifiedTime: ").append(lastModifiedTime);
    sb.append("\nlastModifiedVersion: ").append(lastModifiedVersion);
    return sb.toString();
  }

  private void deserialize() {
    if (author == null) {
      Timer timer = Timing.start("RawBlipSnapshot.deserialize");
      try {
        serializer.deserializeBlip(serialized, new Serializer.Writer() {

          public void setAuthor(ParticipantId author) {
            RawBlipSnapshot.this.author = author;
          }

          public void setContributors(ImmutableSet<ParticipantId> contributors) {
            RawBlipSnapshot.this.contributors = contributors;
          }

          @Override
          public void setCreationTime(long creationTime) {
            RawBlipSnapshot.this.creationTime = creationTime;
          }

          @Override
          public void setCreationVersion(long creationVersion) {
            RawBlipSnapshot.this.creationVersion = creationVersion;
          }

          @Override
          public void setLastModifiedTime(long lastModifiedTime) {
            RawBlipSnapshot.this.lastModifiedTime = lastModifiedTime;
          }

          @Override
          public void setLastModifiedVersion(long lastModifiedVersion) {
            RawBlipSnapshot.this.lastModifiedVersion = lastModifiedVersion;
          }

          @Override
          public void setContent(DocInitialization content) {
            RawBlipSnapshot.this.content = content;
          }
        });
      } finally {
        Timing.stop(timer);
      }
    }
  }
}
