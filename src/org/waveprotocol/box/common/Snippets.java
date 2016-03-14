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

package org.waveprotocol.box.common;

import org.waveprotocol.wave.model.conversation.Blips;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.document.Doc;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.gadget.GadgetConstants;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveletData;

/**
 * Utility methods for rendering snippets.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public final class Snippets {

  public static final int DIGEST_SNIPPET_LENGTH = 140;

  /**
   * Concatenates all of the text for the given documents in
   * {@link WaveletData}.
   *
   * @param wavelet the wavelet for which to concatenate the documents.
   * @return A String containing the characters from all documents.
   */
  public static String collateTextForWavelet(ReadableWaveletData wavelet) {
    StringBuilder resultBuilder = new StringBuilder();
    for (String documentId : wavelet.getDocumentIds()) {
      collateTextForDocument(wavelet.getBlip(documentId).getContent().getMutableDocument(),
          resultBuilder, Integer.MAX_VALUE);
    }
    return resultBuilder.toString();
  }

  /**
   * Concatenates all of the text of the specified blip into a single String.
   *
   * @param doc the document to collate text.
   * @param resultBuilder output buffer.
   * @param maxLength maximum length of output.
   */
  public static void collateTextForDocument(Document doc, StringBuilder resultBuilder, int maxLength) {
    Doc.N node = Blips.getBody(doc);
    if (node != null) {
      node = doc.getFirstChild(node);
    }
    boolean prevNodeWasText = false;
    while (node != null) {
      Doc.T text = doc.asText(node);
      if (text != null) {
        String data = doc.getData(text);
        if (resultBuilder.length() != 0 && !prevNodeWasText) {
          resultBuilder.append(" ");
        }
        int remainLength = maxLength - resultBuilder.length();
        if (data.length() > remainLength) {
          data = data.substring(0, remainLength);
        }
        resultBuilder.append(data);
        if (resultBuilder.length() >= maxLength) {
          break;
        }
        prevNodeWasText = true;
      } else {
        Doc.E element = doc.asElement(node);
        if (element != null) {
          if (GadgetConstants.TAGNAME.equals(doc.getTagName(element))) {
            String title = null, thumbnail = null;
            Doc.N siblingNode = doc.getFirstChild(node);
            while (siblingNode != null) {
              Doc.E siblingElement = doc.asElement(siblingNode);
              if (siblingElement != null
                  && GadgetConstants.STATE_TAGNAME.equals(doc.getTagName(siblingElement)))  {
                String attr = doc.getAttribute(siblingElement, GadgetConstants.KEY_ATTRIBUTE);
                String value = doc.getAttribute(siblingElement, GadgetConstants.VALUE_ATTRIBUTE);
                if (GadgetConstants.STATE_TITLE.equals(attr)) {
                  title = value;
                } else if (GadgetConstants.STATE_THUMBNAIL.equals(attr)) {
                  thumbnail = value;
                }
              }
              siblingNode = doc.getNextSibling(siblingNode);
            }
            if (title != null) {
              if (resultBuilder.length() != 0) {
                resultBuilder.append(" ");
              }
              resultBuilder.append(title);
            }
            if (thumbnail != null) {
              if (resultBuilder.length() != 0) {
                resultBuilder.append(" ");
              }
              resultBuilder.append(thumbnail);
            }
          }
          prevNodeWasText = false;
        }
      }
      node = doc.getNextSibling(node);
    }
  }

  /**
   * Renders a snippet.
   *
   * @param conversation the wave conversation
   * @param maxSnippetLength maximum length of snippet
   * @param title the title
   * @return the snippet.
   */
  public static String renderSnippet(ConversationView conversation, int maxSnippetLength,
      String title) {
    StringBuilder resultBuilder = new StringBuilder();
    for (ConversationBlip blip : conversation.getRoot().getRootThread().getBlips()) {
      if (!blip.isContentInitialized()) {
        break;
      }
      Document doc = blip.getDocument();
      if (doc == null) {
        break;
      }
      collateTextForDocument(doc, resultBuilder, maxSnippetLength);
      if (resultBuilder.length() >= maxSnippetLength) {
        break;
      }
    }
    if (resultBuilder.length() > maxSnippetLength) {
      resultBuilder.delete(maxSnippetLength, resultBuilder.length());
    }
    String snippet = resultBuilder.toString().trim();
    // Strip the title from the snippet if the snippet starts with the title
    if (snippet != null && !snippet.isEmpty() &&
        title != null && !title.isEmpty() &&
        snippet.startsWith(title)) {
      snippet = snippet.substring(title.length()).trim();
    }
    return snippet;
  }

  private Snippets() {
  }
}