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

package org.waveprotocol.wave.client.doodad.diff;

import com.google.gwt.core.client.GWT;

import org.waveprotocol.wave.client.common.util.DateUtils;
import org.waveprotocol.wave.client.doodad.diff.i18n.DiffMessages;
import org.waveprotocol.wave.client.editor.content.AnnotationPainter;
import org.waveprotocol.wave.client.editor.content.AnnotationPainter.BoundaryFunction;
import org.waveprotocol.wave.client.editor.content.AnnotationPainter.PaintFunction;
import org.waveprotocol.wave.client.editor.content.ContentElement;
import org.waveprotocol.wave.client.editor.content.DeleteInfo;
import org.waveprotocol.wave.client.editor.content.DiffHighlightingFilter;
import org.waveprotocol.wave.client.editor.content.PainterRegistry;
import org.waveprotocol.wave.model.document.AnnotationMutationHandler;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.document.util.AnnotationRegistry;
import org.waveprotocol.wave.model.document.util.DocumentContext;
import org.waveprotocol.wave.model.document.util.LocalDocument;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.ReadableStringSet;
import org.waveprotocol.wave.model.util.ValueUtils;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines behaviour for rendering diffs
 *
 * @author danilatos@google.com (Daniel Danilatos)
 */
public class DiffAnnotationHandler implements AnnotationMutationHandler {
  
  /** Attribute and its value to highlight inserted text. */
  private static final String HIGHLIGHT_COLOR_ATTRIBUTE = "backgroundColor";
  private static final String HIGHLIGHT_COLOR_VALUE = "yellow";

  /** Attribute to put hint over inserted text. */
  private static final String HIGHLIGHT_HINT_ATTRIBUTE = "title";

  /** Annotation key prefix. */
  public static final String PREFIX = DiffHighlightingFilter.DIFF_KEY;

  /** Set of annotation keys that the paint function is interested in. */
  private final static ReadableStringSet PAINT_KEYS =
      CollectionUtils.newStringSet(DiffHighlightingFilter.DIFF_INSERT_KEY);

  /** Set of annotation keys that the boundary function is interested in. */
  private final static ReadableStringSet BOUNDARY_KEYS =
      CollectionUtils.newStringSet(DiffHighlightingFilter.DIFF_DELETE_KEY);

  private static DiffMessages messages = GWT.create(DiffMessages.class);

  /**
   * Create and register a style annotation handler
   *
   * @param annotationRegistry registry to register on
   * @param painterRegistry painter registry to use for rendering
   */
  public static void register(AnnotationRegistry annotationRegistry,
      PainterRegistry painterRegistry) {

    painterRegistry.registerPaintFunction(PAINT_KEYS, paintFunc);
    painterRegistry.registerBoundaryFunction(BOUNDARY_KEYS, boundaryFunc);

    annotationRegistry.registerHandler(PREFIX,
        new DiffAnnotationHandler(painterRegistry.getPainter()));
  }

  /** Painter to access regional repainting of diff areas. */
  private final AnnotationPainter painter;

  /**
   * Paint function for normal diffs, sets the background colour of the new
   * content .
   */
  private static final PaintFunction paintFunc = new PaintFunction() {
    
    @Override
    public Map<String, String> apply(Map<String, Object> from, boolean isEditing) {
      if (from.get(DiffHighlightingFilter.DIFF_INSERT_KEY) != null) {
        Map<String, String> map = new HashMap<>();
        WaveletOperationContext opContext =
            (WaveletOperationContext) from.get(DiffHighlightingFilter.DIFF_INSERT_KEY);
        map.put(HIGHLIGHT_COLOR_ATTRIBUTE, HIGHLIGHT_COLOR_VALUE);
        map.put(HIGHLIGHT_HINT_ATTRIBUTE,
            formatOperationContext(DiffHighlightingFilter.DIFF_INSERT_KEY, opContext));
        return map;
      } else {
        return Collections.emptyMap();
      }
    }
  };

  /** Paint function for diff deletions. */
  private static final BoundaryFunction boundaryFunc = new BoundaryFunction() {
    
    @Override
    public <N, E extends N, T extends N> E apply(LocalDocument<N, E, T> localDoc, E parent,
    N nodeAfter, Map<String, Object> before, Map<String, Object> after, boolean isEditing) {
      Object obj = after.get(DiffHighlightingFilter.DIFF_DELETE_KEY);
      if (obj != null) {
        // HACK(danilatos): Assume the elements are of this implementation.
        assert obj instanceof DeleteInfo : "delete key's value must be a DeleteInfo";

        // find the element, then set internal deleted content in the DOM
        E elt = localDoc.transparentCreate(DiffDeleteRenderer.FULL_TAGNAME,
              Collections.<String,String>emptyMap(), parent, nodeAfter);

        DiffDeleteRenderer.getInstance().setInnards((ContentElement) elt,
            ((DeleteInfo) obj).getDeletedHtmlElements());
        return elt;
      } else {
        return null;
      }
    }
  };

  /**
   * Construct the handler, registering its rendering functions with the painter.
   * @param painter painter to use for rendering
   */
  public DiffAnnotationHandler(AnnotationPainter painter) {
    this.painter = painter;
  }

  @Override
  public <N, E extends N, T extends N> void handleAnnotationChange(DocumentContext<N, E, T> bundle,
      int start, int end, String key, Object newValue) {
    painter.scheduleRepaint(bundle, start, end);
  }

  public static String formatOperationContext(String operationKey, WaveletOperationContext context) {
    String s = null;
    if (context != null) {
      String authorName = getCapitalizedAuthorName(context);
      String timestamp = DateUtils.getInstance().formatPastDate(context.getTimestamp());
      if (DiffHighlightingFilter.DIFF_INSERT_KEY.equals(operationKey)) {
        s = messages.added(authorName, timestamp);
      } else if (DiffHighlightingFilter.DIFF_DELETE_KEY.equals(operationKey)) {
        s = messages.removed(authorName, timestamp);
      }
    }
    return s;
  }
  
  private static String getCapitalizedAuthorName(WaveletOperationContext opContext) {
    ParticipantId author = opContext.getCreator();
    String authorName = author != null ? author.getName() : null;
    return ValueUtils.toCapitalCase(authorName);
  }
}
