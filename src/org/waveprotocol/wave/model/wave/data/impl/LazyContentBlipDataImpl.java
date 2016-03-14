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
import org.waveprotocol.wave.model.wave.data.LazyContentBlipData;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.BlipOperation;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.OperationRuntimeException;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.SilentOperationSink;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.impl.DocOpUtil;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.DocumentOperationSink;
import org.waveprotocol.wave.model.wave.data.DocumentFactory;
import org.waveprotocol.wave.model.raw.RawFragment;
import org.waveprotocol.wave.model.raw.RawBlipSnapshot;
import org.waveprotocol.wave.model.raw.RawOperation;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.IdUtil;

import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;

import java.util.List;
import java.util.Collection;
import java.util.Iterator;
import org.waveprotocol.wave.model.document.util.EmptyDocument;

/**
 * Implementation of blip with deferred content creation.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class LazyContentBlipDataImpl implements LazyContentBlipData {

  private static class PreInitParams {
    private static class RawContent {
      RawBlipSnapshot snapshot;
      List<RawOperation> adjustOperations = CollectionUtils.newLinkedList();
      List<RawOperation> diffOperations = CollectionUtils.newLinkedList();
    }

    ParticipantId author;
    Collection<ParticipantId> contributors;
    long creationTime;
    long creationVersion;
    long lastModifiedTime;
    long lastModifiedVersion;
    RawContent rawContent;
    DocInitialization docInit = EmptyDocument.EMPTY_DOCUMENT;
    SilentOperationSink<? super DocOp> outputSink;
    final List<BlipOperation> operations = CollectionUtils.newLinkedList();

    boolean isConsistant() {
      if (rawContent != null) {
        return true;
      }
      return docInit.size() != 0 || operations.isEmpty() || isFirstOperation(operations.get(0));
    }

    boolean hasContent() {
      if (rawContent != null) {
        return true;
      }
      return docInit.size() != 0 || !operations.isEmpty();
    }

    boolean isEmpty() {
      return rawContent == null && docInit.size() == 0 && operations.isEmpty();
    }
  }

  private final WaveletId waveletId;
  private final String blipId;
  private final WaveletDataListenerManager listenerManager;
  private final DocumentFactory<?> contentFactory;

  private PreInitParams preInit = new PreInitParams();
  private BlipDataImpl content;

  LazyContentBlipDataImpl(WaveletId waveletId, String blipId,
      WaveletDataListenerManager listenerManager, DocumentFactory<?> contentFactory) {
    this.waveletId = waveletId;
    this.blipId = blipId;
    this.listenerManager = listenerManager;
    this.contentFactory = contentFactory;
  }

  public void setDocInitialization(DocInitialization docInit) {
    Preconditions.checkArgument(!isContentInitialized(), "Already initialized");
    preInit.docInit = docInit;
  }

  public void setAuthor(ParticipantId author) {
    Preconditions.checkArgument(!isContentInitialized(), "Already initialized");
    preInit.author = author;
  }

  public void setContributors(Collection<ParticipantId> contributors) {
    Preconditions.checkArgument(!isContentInitialized(), "Already initialized");
    preInit.contributors = contributors;
  }

  public void setCreationTime(long creationTime) {
    preInit.creationTime = creationTime;
  }

  public void setCreationVersion(long creationVersion) {
    preInit.creationVersion = creationVersion;
  }

  @Override
  public void init(SilentOperationSink<? super DocOp> outputSink) {
    if (!isContentInitialized()) {
      preInit.outputSink = outputSink;
    } else {
      content.init(outputSink);
    }
  }

  @Override
  public boolean isConsistent() {
    return isContentInitialized() || preInit.isConsistant();
  }

  @Override
  public boolean hasContent() {
    return isContentInitialized() || preInit.hasContent();
  }

  @Override
  public boolean isContentInitialized() {
    return content != null;
  }

  @Override
  public void initalizeSnapshot() {
    if (content == null) {
      Timer timer = Timing.start("LazyContentBlipDataImpl.initalizeSnapshot");
      try {
        Preconditions.checkNotNull(preInit, "No initial data");
        Preconditions.checkArgument(isConsistent(), "Not consistent");
        if (preInit.rawContent != null) {
          Preconditions.checkNotNull(preInit.rawContent.snapshot, "No snapshot");
          RawBlipSnapshot snapshot = (RawBlipSnapshot)preInit.rawContent.snapshot;
          DocumentOperationSink contentSink = contentFactory.create(waveletId, blipId, snapshot.getContent());
          content = new BlipDataImpl(snapshot.getId(), listenerManager,
              snapshot.getAuthor(), snapshot.getContributors(), contentSink,
              snapshot.getCreationTime(), snapshot.getCreationVersion(),
              snapshot.getLastModifiedTime(), snapshot.getLastModifiedVersion());
        } else {
          DocumentOperationSink contentSink = contentFactory.create(waveletId, blipId, preInit.docInit);
          content = new BlipDataImpl(blipId, listenerManager,
              preInit.author, preInit.contributors, contentSink,
            preInit.creationTime, preInit.creationVersion,
            preInit.lastModifiedTime, preInit.lastModifiedVersion);
        }
        if (preInit.outputSink != null) {
          content.init(preInit.outputSink);
        }
      } finally {
        Timing.stop(timer);
      }
    }
  }

  @Override
  public void processDiffs() throws OperationException {
    if (preInit != null) {
      Timer timer = Timing.start("LazyContentBlipDataImpl.processDiffs");
      try {
        Preconditions.checkNotNull(content, "No content");
        PreInitParams init = preInit;
        preInit = null;
        if (init.rawContent != null) {
          processRawOperations(init.rawContent.adjustOperations);
          processRawOperations(init.rawContent.diffOperations);
        }
        if (content.getContent().getMutableDocument().size() != 0) {
          Iterator<BlipOperation> it = init.operations.iterator();
          while (it.hasNext()) {
            if (it.next().getContext().getSegmentVersion() <= content.getLastModifiedVersion()) {
              it.remove();
            }
          }
        }
        for (BlipOperation op : init.operations) {
          content.consume(op);
        }
      } finally {
        Timing.stop(timer);
      }
    }
  }

  @Override
  public void applyRawFragment(RawFragment fragment) throws OperationException {
    if (isContentInitialized()) {
      Preconditions.checkArgument(!fragment.hasSnapshot(), "Duplicate snapshot");
      processRawOperations(fragment.getAdjustOperations());
      processRawOperations(fragment.getDiffOperations());
    } else {
      if (fragment.hasSnapshot()) {
        Preconditions.checkArgument(preInit.rawContent == null, "Duplicate snapshot");
        preInit.rawContent = new PreInitParams.RawContent();
        preInit.rawContent.snapshot = fragment.getBlipSnapshot();
      }
      if (preInit.rawContent != null) {
        for (RawOperation rawOp : fragment.getAdjustOperations()) {
          preInit.rawContent.adjustOperations.add(rawOp);
        }
        for (RawOperation rawOp : fragment.getDiffOperations()) {
          preInit.rawContent.diffOperations.add(rawOp);
        }
      } else {
        init();
        processRawOperations(fragment.getAdjustOperations());
        processRawOperations(fragment.getDiffOperations());
      }
    }
  }

  @Override
  public void consume(BlipOperation operation) throws OperationException {
    WaveletOperationContext context = operation.getContext();
    if (!isContentInitialized() && !IdUtil.isBlipId(blipId)) {
      Preconditions.checkNotNull(preInit, "No initial data");
      if (preInit.isEmpty()) {
        init();
      }
    }
    if (isContentInitialized()) {
      content.consume(operation);
    } else {
      BlipContentOperation contentOperation = (BlipContentOperation)operation;
      preInit.operations.add(new BlipContentOperation(
          new WaveletOperationContext(context.getCreator(), context.getTimestamp(), context.getSegmentVersion()),
          contentOperation.getContentOp(), contentOperation.getContributorMethod()));
      if (contentOperation.isWorthyOfAttribution(blipId)) {
        setLastModifiedTime(context.getTimestamp());
        setLastModifiedVersion(context.getSegmentVersion());
      }
    }
  }

  @Override
  public String getId() {
    return blipId;
  }

  @Override
  public DocumentOperationSink getContent() {
    if (!isContentInitialized()) {
      init();
    }
    return content.getContent();
  }

  @Override
  public ParticipantId getAuthor() {
    if (!isContentInitialized()) {
      init();
    }
    return content.getAuthor();
  }

  @Override
  public ImmutableSet<ParticipantId> getContributors() {
    if (!isContentInitialized()) {
      init();
    }
    return content.getContributors();
  }

  @Override
  public long getCreationTime() {
    if (!isContentInitialized()) {
      return preInit.creationTime;
    }
    return content.getCreationTime();
  }

  @Override
  public long getCreationVersion() {
    if (!isContentInitialized()) {
      return preInit.creationVersion;
    }
    return content.getCreationVersion();
  }

  @Override
  public long getLastModifiedTime() {
    if (!isContentInitialized()) {
      return preInit.lastModifiedTime;
    }
    return content.getLastModifiedTime();
  }

  @Override
  public long getLastModifiedVersion() {
    if (!isContentInitialized()) {
      return preInit.lastModifiedVersion;
    }
    return content.getLastModifiedVersion();
  }

  @Override
  public void submit() {
    if (!isContentInitialized()) {
      init();
    }
    content.submit();
  }

  @Override
  public void addContributor(ParticipantId participant) {
    if (!isContentInitialized()) {
      init();
    }
    content.addContributor(participant);
  }

  @Override
  public void removeContributor(ParticipantId participant) {
    if (!isContentInitialized()) {
      init();
    }
    content.removeContributor(participant);
  }

  @Override
  public long setLastModifiedTime(long lastModifiedTime) {
    if (!isContentInitialized()) {
      long preModifiedTime =  preInit.lastModifiedTime;
      preInit.lastModifiedTime = lastModifiedTime;
      listenerManager.onBlipDataTimestampModified(this, preModifiedTime, lastModifiedTime);
      return preModifiedTime;
    } else {
      return content.setLastModifiedTime(lastModifiedTime);
    }
  }

  @Override
  public long setLastModifiedVersion(long lastModifiedVersion) {
    if (!isContentInitialized()) {
      long preVersion =  preInit.lastModifiedVersion;
      preInit.lastModifiedVersion = lastModifiedVersion;
      listenerManager.onBlipDataVersionModified(this, preVersion, lastModifiedVersion);
      return preVersion;
    } else {
      return content.setLastModifiedVersion(lastModifiedVersion);
    }
  }

  @Override
  public void onTagAdded(String tag, WaveletOperationContext opContext) {
    if (!isContentInitialized()) {
      init();
    }
    content.onTagAdded(tag, opContext);
  }

  @Override
  public void onTagRemoved(String tag, WaveletOperationContext opContext) {
    if (!isContentInitialized()) {
      init();
    }
    content.onTagRemoved(tag, opContext);
  }

  @Override
  public void onRemoteContentModified() {
    listenerManager.onRemoteBlipDataContentModified(this);
  }

  private void init() {
    initalizeSnapshot();
    try {
      processDiffs();
    } catch (OperationException ex) {
      throw new OperationRuntimeException("Operation applying error", ex);
    }
  }

  private void processRawOperations(List<RawOperation> rawOperations) throws OperationException {
    for (RawOperation rawOp : rawOperations) {
      for (WaveletOperation op : rawOp.getOperations()) {
        try {
          content.consume(((WaveletBlipOperation)op).getBlipOp());
        } catch (OperationException ex) {
          throw new OperationException("Blip operation applying exception, blip " + blipId +
            ", operation " + op.toString(), ex);
        }
      }
    }
  }

  private static boolean isFirstOperation(BlipOperation operation) {
    return operation instanceof BlipContentOperation &&
        DocOpUtil.isFirst(((BlipContentOperation)operation).getContentOp());
  }
}
