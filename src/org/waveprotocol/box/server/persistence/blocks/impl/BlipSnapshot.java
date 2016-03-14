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

package org.waveprotocol.box.server.persistence.blocks.impl;

import org.waveprotocol.box.server.persistence.blocks.ReadableBlipSnapshot;
import org.waveprotocol.box.server.persistence.protos.ProtoBlockStore;
import org.waveprotocol.wave.model.document.operation.impl.DocOpUtil;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.BlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.schema.SchemaCollection;
import org.waveprotocol.wave.model.wave.data.DocumentFactory;
import org.waveprotocol.wave.model.wave.data.impl.PluggableMutableDocument;
import org.waveprotocol.wave.model.wave.data.impl.BlipDataImpl;
import org.waveprotocol.wave.model.wave.data.BlipData;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.raw.RawBlipSnapshot;
import org.waveprotocol.wave.model.raw.serialization.RawBlipSerializer;
import org.waveprotocol.wave.model.raw.serialization.GsonSerializer;
import org.waveprotocol.wave.model.raw.serialization.GsonSerializerAdaptor;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;

import org.waveprotocol.wave.communication.Blob;

import com.google.common.collect.ImmutableSet;

import java.util.Collections;
import java.util.List;

/**
 * Snapshot of the document.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class BlipSnapshot extends SegmentSnapshotImpl implements ReadableBlipSnapshot {
  /** The document factory. */
  static private final DocumentFactory<? extends PluggableMutableDocument> documentFactory =
    PluggableMutableDocument.createFactory(SchemaCollection.empty());

  static private final RawBlipSerializer serializer = new RawBlipSerializer(GsonSerializerAdaptor.INSTANCE);

  /** Document Id. */
  private final String documentId;

  /** Raw snapshot. */
  private RawBlipSnapshot rawSnapshot;

  /** Rendered HTML of the document. */
  private String renderedHtml;

  /** Snapshot of the blip. */
  private BlipData mutableBlip;

  public static BlipSnapshot deserialize(ProtoBlockStore.SegmentSnapshotRecord.BlipSnapshot serializedSnapshot,
      String documentId) {
    BlipSnapshot snapshot = new BlipSnapshot(documentId);
    snapshot.rawSnapshot = new RawBlipSnapshot(serializer,
        new Blob(serializedSnapshot.getRawBlipSnapshot()), documentId);
    snapshot.renderedHtml = serializedSnapshot.getRenderedHtml();
    return snapshot;
  }

  BlipSnapshot(String documentId) {
    this.documentId = documentId;
  }

  @Override
  public ParticipantId getAuthor() {
    if (mutableBlip != null) {
      return mutableBlip.getAuthor();
    }
    return rawSnapshot.getAuthor();
  }

  @Override
  public ImmutableSet<ParticipantId> getContributors() {
    if (mutableBlip != null) {
      return mutableBlip.getContributors();
    }
    return rawSnapshot.getContributors();
  }

  @Override
  public DocInitialization getContent() {
    Timer timer = Timing.start("BlipSnapshot.getContent");
    try {
      if (mutableBlip != null) {
        return mutableBlip.getContent().asOperation();
      }
      return rawSnapshot.getContent();
    } finally {
      Timing.stop(timer);
    }
  }

  @Override
  public String getId() {
    if (mutableBlip != null) {
      return mutableBlip.getId();
    }
    return rawSnapshot.getId();
  }

  @Override
  public long getCreationTime() {
    if (mutableBlip != null) {
      return mutableBlip.getCreationTime();
    }
    return rawSnapshot.getCreationTime();
  }

  @Override
  public long getCreationVersion() {
    if (mutableBlip != null) {
      return mutableBlip.getCreationVersion();
    }
    return rawSnapshot.getCreationVersion();
  }

  @Override
  public long getLastModifiedTime() {
    if (mutableBlip != null) {
      return mutableBlip.getLastModifiedTime();
    }
    return rawSnapshot.getLastModifiedTime();
  }

  @Override
  public long getLastModifiedVersion() {
    if (mutableBlip != null) {
      return mutableBlip.getLastModifiedVersion();
    }
    return rawSnapshot.getLastModifiedVersion();
  }

  @Override
  public RawBlipSnapshot getRawSnapshot() {
    if (rawSnapshot == null) {
      rawSnapshot = new RawBlipSnapshot(GsonSerializer.BLIP_SERIALIZER, documentId,
        getAuthor(), getContributors(), getContent(),
        getCreationTime(), getCreationVersion(),
        getLastModifiedTime(), getLastModifiedVersion());
    }
    return rawSnapshot;
  }

  @Override
  public List<? extends WaveletOperation> applyAndReturnReverse(WaveletOperation op) throws OperationException {
    Timer timer = Timing.start("BlipSnapshot.applyAndReturnReverse");
    try {
      nofifyBeforeUpdate();
      WaveletOperationContext context = op.getContext();
      WaveletOperationContext reverseContext;
      Preconditions.checkNotNull(context, "Operation has no context");
      if (op instanceof WaveletBlipOperation) {
        BlipOperation documentOp = ((WaveletBlipOperation)op).getBlipOp();
        if (documentOp instanceof BlipContentOperation) {
          BlipContentOperation contentOp = (BlipContentOperation)documentOp;
          if (mutableBlip == null) {
            if (rawSnapshot != null) {
              mutableBlip = BlipDataImpl.create(documentId, null,
                rawSnapshot.getAuthor(), rawSnapshot.getContributors(),
                documentFactory.create(null, documentId, rawSnapshot.getContent()),
                rawSnapshot.getCreationTime(), rawSnapshot.getCreationVersion(),
                rawSnapshot.getLastModifiedTime(), rawSnapshot.getLastModifiedVersion());
              reverseContext = new WaveletOperationContext(rawSnapshot.getAuthor(),
                rawSnapshot.getLastModifiedTime(), rawSnapshot.getLastModifiedVersion());
              rawSnapshot = null;
              mutableBlip.consume(contentOp);
            } else {
              reverseContext = new WaveletOperationContext(context.getCreator(),
                context.getTimestamp(), context.getSegmentVersion());
              mutableBlip = BlipDataImpl.create(documentId, null,
                context.getCreator(), Collections.singletonList(context.getCreator()),
                documentFactory.create(null, documentId, DocOpUtil.asInitialization(contentOp.getContentOp())),
                context.getTimestamp(), context.getSegmentVersion(),
                context.getTimestamp(), context.getSegmentVersion());
            }
          } else {
            rawSnapshot = null;
            reverseContext = new WaveletOperationContext(mutableBlip.getAuthor(),
              mutableBlip.getLastModifiedTime(), mutableBlip.getLastModifiedVersion());
            mutableBlip.consume(contentOp);
          }
        } else {
          throw new OperationException("Invalid operation for apply to blip snapshot: " + op);
        }
      } else {
        throw new OperationException("Invalid operation for apply to blip snapshot: " + op);
      }
      return op.reverse(reverseContext);
    } finally {
      Timing.stop(timer);
    }
  }

  @Override
  public boolean hasContent() {
    if (mutableBlip != null) {
      return mutableBlip.getContent() != null &&
        mutableBlip.getContent().getMutableDocument() != null &&
        mutableBlip.getContent().getMutableDocument().size() != 0;
    } else if (rawSnapshot != null) {
      return rawSnapshot.getContent() != null &&
        rawSnapshot.getContent().size() != 0;
    }
    return false;
  }

  @Override
  public BlipSnapshot duplicate() {
    Timer timer = Timing.start("BlipSnapshot.duplicate");
    try {
      BlipSnapshot snapshot = new BlipSnapshot(documentId);
      snapshot.rawSnapshot = getRawSnapshot();
      snapshot.renderedHtml = renderedHtml;
      return snapshot;
    } finally {
      Timing.stop(timer);
    }
  }

  @Override
  protected void serialize(ProtoBlockStore.SegmentSnapshotRecord.Builder builder) {
    ProtoBlockStore.SegmentSnapshotRecord.BlipSnapshot.Builder blipBuilder = builder.getBlipSnapshotBuilder();
    blipBuilder.setRawBlipSnapshot(getRawSnapshot().serialize().getData());
    if (renderedHtml != null) {
      blipBuilder.setRenderedHtml(renderedHtml);
    }
  }
}
