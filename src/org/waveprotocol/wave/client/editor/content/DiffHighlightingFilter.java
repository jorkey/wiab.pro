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

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.Style;

import org.waveprotocol.wave.client.common.util.DomHelper;
import org.waveprotocol.wave.client.doodad.diff.DiffAnnotationHandler;
import org.waveprotocol.wave.client.editor.content.misc.StyleAnnotationHandler;
import org.waveprotocol.wave.client.editor.content.paragraph.LineRendering;
import org.waveprotocol.wave.client.editor.impl.DiffManager;
import org.waveprotocol.wave.client.editor.impl.DiffManager.DiffType;
import org.waveprotocol.wave.model.conversation.AnnotationConstants;
import org.waveprotocol.wave.model.conversation.Blips;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.document.AnnotationInterval;
import org.waveprotocol.wave.model.document.MutableAnnotationSet;
import org.waveprotocol.wave.model.document.RangedAnnotation;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.AttributesUpdate;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.DocOpCursor;
import org.waveprotocol.wave.model.document.operation.ModifiableDocument;
import org.waveprotocol.wave.model.document.operation.impl.DocOpUtil;
import org.waveprotocol.wave.model.document.util.Annotations;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.OperationRuntimeException;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.IntMap;
import org.waveprotocol.wave.model.util.Preconditions;
import org.waveprotocol.wave.model.util.ReadableIntMap;
import org.waveprotocol.wave.model.util.ReadableStringMap;
import org.waveprotocol.wave.model.util.ReadableStringSet;
import org.waveprotocol.wave.model.util.StringMap;

import java.util.Iterator;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;

/**
 * A wrapper for a content document, for the purpose of displaying diffs.
 *
 * Operations applied will be rendered as diffs.
 *
 * @author danilatos@google.com (Daniel Danilatos)
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class DiffHighlightingFilter implements ModifiableDocument {

  /**
   * Dependencies for implementing the diff filter.
   */
  public interface DiffHighlightTarget extends MutableAnnotationSet<Object>, ModifiableDocument {

    /**
     * To be called during application of an operation, to interleave local annotations
     * in with the operation. Will only be called with local keys.
     */
    void startLocalAnnotation(String key, Object value);

    /**
     * To be called during application of an operation, to interleave local annotations
     * in with the operation. Will only be called with local keys.
     */
    void endLocalAnnotation(String key);

    /**
     * IndexedDocumentImpl's "currentNode"
     *
     * This method breaks encapsulation, think of a better way to do this later.
     */
    ContentNode getCurrentNode();

    /**
     * @return true only if the operation is currently being applied to the
     *         document itself - false otherwise (so we don't do the diff logic
     *         for, e.g. pretty printing or validation cursors)
     */
    boolean isApplyingToDocument();
  }

  private class Filter implements DocOpCursor {

    private final WaveletOperationContext opContext;
    private final boolean isDiff;

    public Filter(WaveletOperationContext opContext) {
      this.opContext = opContext;
      isDiff = !diffsSuppressed && isDiff(opContext);
    }

    @Override
    public void elementStart(String tagName, Attributes attributes) {
      if (diffDepth == 0) {
        inner.startLocalAnnotation(DIFF_INSERT_KEY, isDiff ? opContext : null);
      }

      diffDepth++;

      target.elementStart(tagName, attributes);
      currentLocation++;
    }

    @Override
    public void elementEnd() {
      target.elementEnd();
      currentLocation++;

      diffDepth--;

      if (diffDepth == 0) {
        inner.endLocalAnnotation(DIFF_INSERT_KEY);
      }
    }

    @Override
    public void characters(String characters) {
      if (diffDepth == 0) {
        inner.startLocalAnnotation(DIFF_INSERT_KEY, isDiff ? opContext : null);
      }

      target.characters(characters);
      currentLocation += characters.length();

      if (diffDepth == 0) {
        inner.endLocalAnnotation(DIFF_INSERT_KEY);
      }
    }

    @Override
    public void deleteElementStart(String type, Attributes attrs) {
      String deletedThreadId = null;
      boolean isInlineThread = Blips.THREAD_INLINE_ANCHOR_TAGNAME.equals(type);
      if (isInlineThread) {
        deletedThreadId = attrs.get("id");
        if (!isThreadDeletionDiff(deletedThreadId)) {
          target.deleteElementStart(type, attrs);
          return;
        }
      } else {
        if (!isDiff) {
          target.deleteElementStart(type, attrs);
          return;
        }
      }

      // deletion annotations within insertion annotations are annihilated
      // exception: deletion of inline threads
      if (diffDepth == 0 && (isOutsideInsertionAnnotation() || isInlineThread)) {
        ContentElement currentElement = (ContentElement) inner.getCurrentNode();
        Element e = currentElement.getImplNodelet();

        // HACK(danilatos): Line rendering is somewhat special, so special case it
        // for now. Once there are more use cases, we can figure out an appropriate
        // generalisation for this.
        if (LineRendering.isLineElement(currentElement)) {
          // This loses paragraph-level formatting, but is better than nothing.
          // Indentation and direction inherit from the pervious line, which is
          // quite acceptable.
          e = Document.get().createBRElement();
        }

        if (e != null) {
          if (isInlineThread) {
            // Move reply to the temporary place outside the document implementation
            // where it can be found by id
            Document.get().getDocumentElement().appendChild(e);
          } else
          {
            e = e.cloneNode(true).cast();
            deletify(e);
          }
          updateDeleteInfo();
          getCurrentDeleteInfo().add(e, deletedThreadId);
        }
      }

      diffDepth++;

      target.deleteElementStart(type, attrs);
    }

    @Override
    public void deleteElementEnd() {
      target.deleteElementEnd();

      diffDepth--;
    }

    @Override
    public void deleteCharacters(String text) {

      if (!isDiff) {
        target.deleteCharacters(text);
        return;
      }

      if (diffDepth == 0 && isOutsideInsertionAnnotation()) {
        int location = currentLocation;
        int endLocation = location + text.length();

        updateDeleteInfo();

        int scanLocation = location;
        int nextScanLocation;

        String hint = DiffAnnotationHandler.formatOperationContext(DIFF_DELETE_KEY, opContext);
        do {
          DeleteInfo surroundedInfo = (DeleteInfo) inner.getAnnotation(scanLocation, DIFF_DELETE_KEY);
          nextScanLocation = inner.firstAnnotationChange(
              scanLocation, endLocation, DIFF_DELETE_KEY, surroundedInfo);
          if (nextScanLocation == -1) {
            nextScanLocation = endLocation;
          }

          saveDeletedText(text, currentLocation, scanLocation, nextScanLocation, hint);

          if (surroundedInfo != null) {
            getCurrentDeleteInfo().addAll(surroundedInfo);
          }

          scanLocation = nextScanLocation;

        } while (nextScanLocation < endLocation);
      }

      target.deleteCharacters(text);
    }

    @Override
    public void annotationBoundary(AnnotationBoundaryMap map) {
      target.annotationBoundary(map);
    }

    @Override
    public void replaceAttributes(Attributes oldAttrs, Attributes newAttrs) {
      currentLocation++;
      target.replaceAttributes(oldAttrs, newAttrs);
    }

    @Override
    public void retain(int itemCount) {
      currentLocation += itemCount;
      target.retain(itemCount);
    }

    @Override
    public void updateAttributes(AttributesUpdate attrUpdate) {
      currentLocation++;
      target.updateAttributes(attrUpdate);
    }

    private void updateDeleteInfo() {
      if (currentLocation != currentDeleteLocation || currentDeleteInfo == null) {
        maybeSavePreviousDeleteInfo();
        currentDeleteInfo = (DeleteInfo) inner.getAnnotation(currentLocation, DIFF_DELETE_KEY);
      }
      currentDeleteLocation = currentLocation;
    }

    private boolean isOutsideInsertionAnnotation() {
      int location = currentLocation;
      return inner.firstAnnotationChange(
          location, location + 1, DIFF_INSERT_KEY, null) == -1;
    }

    private void deletify(Element element) {
      if (element == null) {
        // NOTE(danilatos): Not handling the case where the content element
        // is transparent w.r.t. the rendered view, but has visible children.
        return;
      }

      DiffManager.styleElement(element, DiffType.DELETE);
      DomHelper.makeUnselectable(element);

      for (Node n = element.getFirstChild(); n != null; n = n.getNextSibling()) {
        if (!DomHelper.isTextNode(n)) {
          deletify(n.<Element> cast());
        }
      }
    }

    /**
     * Creates text spans reflecting every combination of text formatting annotation values.
     *
     * @param text text to be saved
     * @param textLocation location of the text beginning in the document
     * @param startLocation start location of the deleted block
     * @param finishLocation finish location of the deleted block
     * @param hint hint to be displayed above text
     */
    private void saveDeletedText(String text, int textLocation, int startLocation,
        int finishLocation, String hint) {
      // TODO(dyukon): This solution supports only text styles (weight, decoration, font etc.)
      // which can be applied to text SPANs.
      // It's necessary to add support for paragraph styles (headers ordered/numbered lists,
      // indents) which cannot be kept in text SPANs.
      Iterator<AnnotationInterval<Object>> aiIterator = inner.annotationIntervals(
          startLocation, finishLocation, AnnotationConstants.DELETED_STYLE_KEYS).iterator();
      if (aiIterator.hasNext()) { // Some annotations are changed throughout deleted text
        while (aiIterator.hasNext()) {
          AnnotationInterval<Object> ai = aiIterator.next();
          createDeleteElement(text.substring(ai.start() - textLocation, ai.end() - textLocation),
              ai.annotations(), hint);
        }
      } else { // No annotations are changed throughout deleted text
        createDeleteElement(text.substring(startLocation - textLocation, finishLocation - textLocation),
            findDeletedStyleAnnotations(startLocation), hint);
      }
    }

    private ReadableStringMap<Object> findDeletedStyleAnnotations(final int location) {
      final StringMap<Object> annotations = CollectionUtils.createStringMap();
      AnnotationConstants.DELETED_STYLE_KEYS.each(new ReadableStringSet.Proc() {

        @Override
        public void apply(String key) {
          annotations.put(key, inner.getAnnotation(location, key));
        }
      });
      return annotations;
    }

    private void createDeleteElement(String innerText, ReadableStringMap<Object> annotations,
        String hint) {
      Timer timer = Timing.start("DiffHighlightingFilter.createDeleteElement");
      Element element = Document.get().createSpanElement();
      applyAnnotationsToElement(element, annotations);
      DiffManager.styleElement(element, DiffType.DELETE);
      element.setInnerText(innerText);
      element.setTitle(hint);
      getCurrentDeleteInfo().add(element);
      Timing.stop(timer);
    }

    private void applyAnnotationsToElement(Element element, ReadableStringMap<Object> annotations) {
      final Style style = element.getStyle();
      annotations.each(new ReadableStringMap.ProcV<Object>() {

        @Override
        public void apply(String key, Object value) {
          if (value != null && value instanceof String) {
            String styleValue = (String) value;
            if (!styleValue.isEmpty()) {
              style.setProperty(StyleAnnotationHandler.suffix(key), styleValue);
            }
          }
        }
      });
    }

    private boolean isThreadDeletionDiff(String deletedThreadId) {
      return conversation.getThread(deletedThreadId) != null && isDiff(opContext);
    }

    private DeleteInfo getCurrentDeleteInfo() {
      if (currentDeleteInfo == null) {
        currentDeleteInfo = new DeleteInfo();
      }
      return currentDeleteInfo;
    }

    private boolean isDiff(WaveletOperationContext opContext) {
      return opContext != null && !opContext.isAdjust() && opContext.hasSegmentVersion();
    }
  };


  /**
   * Prefix for diff local annotations
   */
  public static final String DIFF_KEY = Annotations.makeUniqueLocal("diff");

  /**
   * Diff annotation marking inserted content
   */
  public static final String DIFF_INSERT_KEY = DIFF_KEY + "/ins";

  /**
   * Diff annotation whose left boundary represents deleted content, the content
   * being stored in the annotation value as a DeleteInfo.
   */
  public static final String DIFF_DELETE_KEY = DIFF_KEY + "/del";

  private final DiffHighlightTarget inner;
  private final Conversation conversation;

  // Munging to wrap the op

  private DocOpCursor target;

  private DocOp operation;

  // Diff state

  private int diffDepth = 0;
  private DeleteInfo currentDeleteInfo = null;
  private int currentDeleteLocation = 0;
  private IntMap<Object> deleteInfos;
  private int currentLocation = 0;
  private boolean diffsSuppressed;

  public DiffHighlightingFilter(DiffHighlightTarget contentDocument, Conversation conversation) {
    this.inner = contentDocument;
    this.conversation = conversation;
  }

  @Override
  public void consume(DocOp op) throws OperationException {
    Preconditions.checkState(target == null, "Diff inner target not initialised. " +
        "op: " + (op != null ? op.toString() : "null") + ", " +
        "opContext: " + (op != null && op.getContext() != null ? op.getContext().toString() : "null"));

    operation = op;

    // To avoid striking out all content when the whole blip is deleted,
    // the marking isn't done in this situation.
    if (op.getContext() != null && DocOpUtil.doesDeleteAllContent(op)) {
      return;
    }

    inner.consume(opWrapper);

    final int size = inner.size();

    deleteInfos.each(new ReadableIntMap.ProcV<Object>() {

      @Override
      public void apply(int location, Object _item) {
        assert location <= size;

        if (location == size) {
          // TODO(danilatos): Figure out a way to render this.
          // For now, do nothing, which is better than crashing.
          return;
        }

        DeleteInfo item = (DeleteInfo) _item;
        Object obj = inner.getAnnotation(location, DIFF_DELETE_KEY);
        if (obj != null) {
          item.addAll((DeleteInfo) obj);
        }
        inner.setAnnotation(location, location + 1, DIFF_DELETE_KEY, item);
      }
    });
  }

  private final DocOp opWrapper =
      new DiffOpWrapperBase("The document isn't expected to call this method") {

    @Override
    public void apply(DocOpCursor innerCursor) {
      if (!inner.isApplyingToDocument()) {
        operation.apply(innerCursor);
        return;
      }

      target = innerCursor;
      deleteInfos = CollectionUtils.createIntMap();
      currentDeleteInfo = null;
      currentDeleteLocation = -1;
      currentLocation = 0;

      operation.apply(new Filter(operation.getContext()));

      maybeSavePreviousDeleteInfo();

      target = null;
    }

    @Override
    public WaveletOperationContext getContext() {
      return operation.getContext();
    }

    @Override
    public String toString() {
      return "DiffOpWrapper(" + operation + ")";
    }
  };

  public void startDiffSuppression(boolean clearDeletedReplies) {
    diffsSuppressed = true;
    clearDiffs(clearDeletedReplies);
  }

  public void stopDiffSuppression() {
    diffsSuppressed = false;
  }

  public void clearDiffs(boolean clearDeletedReplies) {
    // Guards to prevent setting the annotation when there is nothing
    // to do, thus saving a repaint
    Annotations.guardedResetAnnotation(inner, 0, inner.size(), DIFF_INSERT_KEY, null);

    if (clearDeletedReplies) {
      Annotations.guardedResetAnnotation(inner, 0, inner.size(), DIFF_DELETE_KEY, null);
    } else {
      transformDeleteInfos(inner, new DeleteInfo.FilteredTransformer() {

        @Override
        public boolean isRetained(DeleteInfo.Item item) {
          return item.isReply();
        }
      });
    }
  }

  /**
   * Save previous delete info - assumes currentDeleteLocation and
   * currentDeleteInfo still reflect the previous info.
   */
  private void maybeSavePreviousDeleteInfo() {
    if (currentDeleteInfo != null) {
      deleteInfos.put(currentDeleteLocation, currentDeleteInfo);
    }
  }

  private void transformDeleteInfos(DiffHighlightTarget target,
      DeleteInfo.Transformer transformer) {
    for (RangedAnnotation annotation : target.rangedAnnotations(0, target.size(),
        CollectionUtils.newStringSet(DiffHighlightingFilter.DIFF_DELETE_KEY)) ) {
      Object value = annotation.value();
      if (value instanceof DeleteInfo) {
        DeleteInfo initialInfo = (DeleteInfo) value;
        DeleteInfo processedInfo = transformer.transform(initialInfo);
        if (processedInfo != initialInfo) {
          target.setAnnotation(annotation.start(), annotation.end(), DIFF_DELETE_KEY, processedInfo);
        }
      }
    }
  }
}
