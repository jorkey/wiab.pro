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

package org.waveprotocol.wave.client.editor.content;

import org.waveprotocol.wave.model.document.MutableDocumentImpl;
import org.waveprotocol.wave.model.document.indexed.IndexedDocument;
import org.waveprotocol.wave.model.document.operation.Nindo;
import org.waveprotocol.wave.model.operation.OperationSequencer;
import org.waveprotocol.wave.model.util.CopyOnWriteSet;
import org.waveprotocol.wave.model.wave.SourcesEvents;

/**
 * Editor's concrete implementation of MutableDocument.
 *
 * Provides a method for turning on selection preservation.
 */
public class CMutableDocument
    extends MutableDocumentImpl<ContentNode, ContentElement, ContentTextNode>
    implements SourcesEvents<CMutableDocument.EditingModeChangeListener> {

  /**
   * Listener for the change of the document editing mode.
   */
  public interface EditingModeChangeListener {
    
    /**
     * Called when document editing mode has been changed.
     * 
     * @param editingMode true, if the document is in editing mode
     */
    void onEditingModeChanged(boolean editingMode);
  }

  private final static CopyOnWriteSet<EditingModeChangeListener> EMPTY_LISTENERS =
      CopyOnWriteSet.create();
  private CopyOnWriteSet<EditingModeChangeListener> listeners = EMPTY_LISTENERS;
  
  public CMutableDocument(
      OperationSequencer<Nindo> sequencer,
      IndexedDocument<ContentNode, ContentElement, ContentTextNode> document) {
    super(sequencer, document);
  }
  
  /**
   * Sets editing mode of the document.
   * 
   * @param editingMode editing mode
   */
  public void setEditingMode(boolean editingMode) {
    if (editingMode != isEditingMode()) {
      getDocumentElement().setProperty(AnnotationPainter.DOCUMENT_MODE, editingMode);
      
      fireOnEditingModeChanged(editingMode);
    }
  }
  
  /**
   * @return true, if the document is in editing mode.
   */
  public boolean isEditingMode() {
    Boolean value = getDocumentElement().getProperty(AnnotationPainter.DOCUMENT_MODE);
    return value != null && value;
  }

  //
  // SourcesEvents
  //
  
  @Override
  public void addListener(EditingModeChangeListener listener) {
    if (listeners == EMPTY_LISTENERS) {
      listeners = CopyOnWriteSet.create();
    }
    listeners.add(listener);
  }

  @Override
  public void removeListener(EditingModeChangeListener listener) {
    listeners.remove(listener);
  }
  
  private void fireOnEditingModeChanged(boolean editingMode) {
    for (EditingModeChangeListener l : listeners) {
      l.onEditingModeChanged(editingMode);
    }
  }
}
