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

package org.waveprotocol.wave.client.wavepanel.render;

import com.google.gwt.dom.client.Document;

import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;
import org.waveprotocol.wave.client.common.util.LogicalPanel;
import org.waveprotocol.wave.client.common.util.StringSequence;
import org.waveprotocol.wave.client.editor.content.AnnotationPainter;
import org.waveprotocol.wave.client.editor.content.ContentDocument;
import org.waveprotocol.wave.client.editor.content.Registries;
import org.waveprotocol.wave.client.wave.DiffContentDocument;
import org.waveprotocol.wave.client.wavepanel.view.AnchorView;
import org.waveprotocol.wave.client.wavepanel.view.InlineThreadView;
import org.waveprotocol.wave.client.wavepanel.view.ViewIdMapper;
import org.waveprotocol.wave.client.wavepanel.view.dom.BlipMetaDomImpl;
import org.waveprotocol.wave.client.wavepanel.view.dom.BlipViewDomImpl;
import org.waveprotocol.wave.client.wavepanel.view.dom.DomAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.impl.BlipMetaViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.impl.BlipViewImpl;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.OperationRuntimeException;

/**
 * Renders blips documents.
 */
public final class BlipDocumentRendererImpl implements BlipDocumentRenderer {

  /** Interprets DOM elements as views. */
  private final DomAsViewProvider domAsViewProvider;

  /** Get the view for a particular model */
  private final ModelAsViewProvider modelAsViewProvider;  

  /** Document registries. */
  private final DocumentRegistries documentRegistries;

  /** Logical panel. */
  private final LogicalPanel logicalPanel;
  
  /**
   * Creates a blip document renderer.
   */
  public static BlipDocumentRendererImpl create(
      DomAsViewProvider domAsViewProvider,
      ModelAsViewProvider modelAsViewProvider,
      DocumentRegistries registries,
      LogicalPanel logicalPanel) {
    return new BlipDocumentRendererImpl(
        domAsViewProvider,
        modelAsViewProvider,
        registries,
        logicalPanel);
  }

  private BlipDocumentRendererImpl(
      DomAsViewProvider domAsViewProvider,
      ModelAsViewProvider modelAsViewProvider,
      DocumentRegistries documentRegistries,
      LogicalPanel logicalPanel) {
    this.domAsViewProvider = domAsViewProvider;
    this.modelAsViewProvider = modelAsViewProvider;
    this.documentRegistries = documentRegistries;
    this.logicalPanel = logicalPanel;
  }

  @Override
  public void renderDocument(ConversationBlip blip) {
    Timer timer = Timing.start("BlipDocumentRendererImpl.renderDocument");
    try {
      BlipViewImpl<BlipViewDomImpl> blipUi =
          (BlipViewImpl<BlipViewDomImpl>) modelAsViewProvider.getBlipView(blip);
      if (blipUi != null) {
        BlipMetaDomImpl metaDom = ((BlipMetaViewImpl<BlipMetaDomImpl>) blipUi.getMeta()).getIntrinsic();
        
        // Very first thing that must be done is to extract and save the DOM of
        // inline threads, since content-document rendering will blast them away.
        saveInlineReplies(metaDom);

        // Clear content before rendering, so that doodad events caused by rendering
        // apply on a fresh state.
        metaDom.clearContent();

        // Initialize document.
        blip.initializeSnapshot();
        
        ContentDocument doc = ((DiffContentDocument)blip.getContent()).getDocument();
        Registries registries = documentRegistries.get(blip);
        doc.setRegistries(registries);
        
        // Apply diff-operations.
        try {
          blip.processDiffs();
        } catch (OperationException ex) {
          throw new OperationRuntimeException("Operation applying exception", ex);
        }

        doc.setInteractive(logicalPanel);        
        
        // ContentDocument annotations aren't rendered synchronously, so we have to flush them,
        // rather than reveal half-rendered content at the end of the event cycle.
        AnnotationPainter.flush(doc.getContext());

        metaDom.setContent(doc.getFullContentView().getDocumentElement().getImplNodelet());
      }
    } finally {
      Timing.stop(timer);
    }
  }

  /**
   * Moves all the inline replies of a blip to their default-anchor locations.
   */
  private void saveInlineReplies(BlipMetaDomImpl metaDom) {
    // Iteration is done via ids, in order to identify the thread to get the
    // inline -> default location mapping.
    StringSequence inlineLocators = metaDom.getInlineLocators();
    String inlineId = inlineLocators.getFirst();
    while (inlineId != null) {
      AnchorView inlineUi = domAsViewProvider.asAnchor(Document.get().getElementById(inlineId));
      InlineThreadView threadUi = inlineUi.getThread();
      if (threadUi != null) {
        // Move to default location.
        String defaultId = ViewIdMapper.defaultOfInlineAnchor(inlineId);
        AnchorView defaultUi = domAsViewProvider.asAnchor(Document.get().getElementById(defaultId));
        inlineUi.detach(threadUi);
        defaultUi.attach(threadUi);
      }
      inlineId = inlineLocators.getNext(inlineId);
    }
  }
}
