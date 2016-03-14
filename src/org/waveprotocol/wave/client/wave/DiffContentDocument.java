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

package org.waveprotocol.wave.client.wave;

import org.waveprotocol.wave.client.editor.content.ContentDocument;
import org.waveprotocol.wave.client.editor.content.DiffHighlightingFilter;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.document.Doc;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.document.MutableDocument;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.util.MutableDocumentProxy;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.SilentOperationSink;
import org.waveprotocol.wave.model.wave.data.DocumentOperationSink;

/**
 * Wraps a {@link ContentDocument}, exposing its diff highlighting capabilities
 * in the language of a {@link DiffSink}.
 *
 * @author hearnden@google.com (David Hearnden)
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public final class DiffContentDocument extends MutableDocumentProxy<Doc.N, Doc.E, Doc.T>
    implements Document, InteractiveDocument, DocumentOperationSink {

  /** Regular document. */
  private final ContentDocument document;

  /** Sink that highlights ops that it consumes. */
  private final DiffHighlightingFilter differ;

  private boolean diffsSuppressed;
  private boolean diffsRetained;

  private DiffContentDocument(ContentDocument document, DiffHighlightingFilter differ) {
    this.document = document;
    this.differ = differ;
  }

  /**
   * Creates a diff-handling wrapper for a content document.
   */
  public static DiffContentDocument create(ContentDocument doc, Conversation conversation) {
    DiffHighlightingFilter differ = new DiffHighlightingFilter(doc.getDiffTarget(), conversation);
    return new DiffContentDocument(doc, differ);
  }

  @Override
  protected MutableDocument<Doc.N, Doc.E, Doc.T> getDelegate() {
    return (MutableDocument)document.getMutableDoc();
  }

  /** @return the underlying document. */
  @Override
  public ContentDocument getDocument() {
    return document;
  }

  @Override
  public Document getMutableDocument() {
    return this;
  }

  @Override
  public void consume(DocOp op) throws OperationException {
    differ.consume(document.addIncomingOpToDraft(op));
  }

  @Override
  public void startDiffSuppression(boolean clearDeletedReplies) {
    if (!diffsSuppressed) {
      differ.startDiffSuppression(clearDeletedReplies);
      diffsSuppressed = true;
    }
  }

  @Override
  public void stopDiffSuppression() {
    if (diffsSuppressed) {
      differ.stopDiffSuppression();
      diffsSuppressed = false;
    }
  }

  @Override
  public void startDiffRetention() {
    diffsRetained = true;
  }

  @Override
  public void stopDiffRetention() {
    diffsRetained = false;
  }

  @Override
  public void clearDiffs(boolean clearDeletedReplies) {
    // Clearing diffs is necessary and desirable
    if (!diffsSuppressed && !diffsRetained) {
      differ.clearDiffs(clearDeletedReplies);
    }
  }

  @Override
  public void init(SilentOperationSink<? super DocOp> outputSink) {
    document.setOutgoingSink(outputSink);
  }

  @Override
  public DocInitialization asOperation() {
    return document.asOperation();
  }
}
