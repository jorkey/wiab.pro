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

import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;

/**
 * Renderer of tags.
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class TagUpdateRenderer {

  /**
   * Reader for tags using supplement.
   */
  public interface TagReader {

    /**
     * Checks if tags were ever read.
     *
     * @return true, if tags were ever read
     */
    boolean wasTagsEverRead();
  }

  public static TagUpdateRenderer create(ModelAsViewProvider modelAsViewProvider,
      TagReader tagReader, ObservableConversationView conversationView) {
    TagUpdateRenderer tpr = new TagUpdateRenderer(modelAsViewProvider, tagReader);
    tpr.bindConversation(conversationView.getRoot());
    conversationView.addListener(tpr.conversationViewListener);
    return tpr;
  }

  private final ModelAsViewProvider modelAsViewProvider;
  private final TagReader tagReader;

  private ObservableConversation conversation;

  private final ObservableConversation.TagListener tagListener =
      new ObservableConversation.TagListener() {

    @Override
    public void onTagAdded(String tag, WaveletOperationContext opContext) {
      // Added tag is shown as diff if operation is diff and tags have been seen before.
      modelAsViewProvider.getTagsView(conversation).appendTag(conversation, tag, opContext,
          isDiff(opContext));
    }

    @Override
    public void onTagRemoved(String tag, WaveletOperationContext opContext) {
      modelAsViewProvider.getTagsView(conversation).removeTag(conversation, tag, opContext,
          isDiff(opContext));
    }
  };

  private final ObservableConversationView.Listener conversationViewListener =
      new ObservableConversationView.Listener() {

    @Override
    public void onConversationAdded(ObservableConversation conversation) {
      if (conversation.isRoot()) {
        bindConversation(conversation);
      }
    }

    @Override
    public void onConversationRemoved(ObservableConversation conversation) {
      if (conversation.isRoot()) {
        unbindConversation();
      }
    }
  };

  //
  // Private methods
  //

  private TagUpdateRenderer(ModelAsViewProvider modelAsViewProvider, TagReader tagReader) {
    this.modelAsViewProvider = modelAsViewProvider;
    this.tagReader = tagReader;
  }

  private void bindConversation(ObservableConversation conversation) {
    if (conversation != null) {
      conversation.addTagListener(tagListener);

      this.conversation = conversation;
    }
  }

  private void unbindConversation() {
    if (conversation != null) {
      conversation.removeTagListener(tagListener);
      conversation = null;
    }
  }
  
  private static boolean isDiff(WaveletOperationContext opContext) {
    return opContext != null && !opContext.isAdjust() && opContext.hasSegmentVersion();
  }
}
