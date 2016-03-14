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

package org.waveprotocol.wave.model.conversation;

import org.waveprotocol.box.common.Snippets;
import org.waveprotocol.wave.model.util.ValueUtils;

/**
 * ConversationPresenter implementation.
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class ConversationPresenterImpl implements ConversationPresenter {

  /**
   * @return ConversationPresenterImpl instance.
   */
  public static ConversationPresenterImpl get() {
    if (instance == null) {
      instance = new ConversationPresenterImpl();
    }
    return instance;
  }
  
  private static ConversationPresenterImpl instance = null;
  
  /** Max length for snippet in the blip's string presentation. */
  private final static int BLIP_SNIPPET_LENGTH = 25;    

  private final static String BRACKET_OPEN = "[";
  private final static String BRACKET_CLOSE = "]";

  private ConversationPresenterImpl() {    
  }
  
  //
  // ConversationPresenter
  //

  @Override
  public String presentConversation(Conversation conversation) {
    if (conversation == null) {
      return "Null conversation";
    }
    StringBuilder builder = new StringBuilder();
    presentConversation(conversation, builder);
    String s = builder.toString();
    return s;
  }

  @Override
  public String presentThread(ConversationThread thread) {
    if (thread == null) {
      return "Null thread";
    }    
    StringBuilder builder = new StringBuilder();
    presentThread(thread, 0, builder);
    String s = builder.toString();
    return s;
  }    

  @Override
  public String presentBlip(ConversationBlip blip) {
    if (blip == null) {
      return "Null blip";
    }    
    StringBuilder builder = new StringBuilder();
    presentBlip(blip, 0, builder);
    String s = builder.toString();
    return s;
  }

  //
  // Protected methods
  //

  /**
   * Represents the given conversation to the string builder.
   * 
   * @param conversation the given conversation
   * @param builder string builder
   */
  protected void presentConversation(Conversation conversation, StringBuilder builder) {
    openBracket(builder);
    presentConversationStart(conversation, builder);
    closeBracket(builder);

    presentThread(conversation.getRootThread(), 0, builder);

    openBracket(builder);
    presentConversationFinish(conversation, builder);
    closeBracket(builder);
  }

  protected void presentConversationStart(Conversation conversation, StringBuilder builder) {
    builder.append(conversation.toString());
  }

  protected void presentConversationFinish(Conversation conversation, StringBuilder builder) {
    builder.append("/C");
  }

  /**
   * Represents the given conversation thread to the string builder.
   * 
   * @param thread the given conversation thread
   * @param level tree level of the conversation thread
   * @param builder string builder
   */    
  protected void presentThread(ConversationThread thread, int level, StringBuilder builder) {
    if (level > 0) {
      builder.append("\n");
    }
    builder.append(ValueUtils.stringFromChar(' ', level));
    openBracket(builder);
    presentThreadStart(thread, level, builder);
    closeBracket(builder);

    for (ConversationBlip blip : thread.getBlips()) {
      presentBlip(blip, level == 0 ? 0 : level + 1, builder);
    }

    openBracket(builder);
    presentThreadFinish(thread, level, builder);
    closeBracket(builder);
  }

  protected void presentThreadStart(ConversationThread thread, int level, StringBuilder builder) {
    builder.append(getThreadPrefix(thread)).append(" ").append(thread.getId());
  }

  protected void presentThreadFinish(ConversationThread thread, int level, StringBuilder builder) {
    builder.append("/").append(getThreadPrefix(thread));
  }    

  /**
   * Represents the given conversation blip to the string builder.
   * 
   * @param blip the given conversation thread
   * @param level tree level of the conversation blip
   * @param builder string builder
   */    
  protected void presentBlip(ConversationBlip blip, int level, StringBuilder builder) {
    builder.append("\n");
    builder.append(ValueUtils.stringFromChar(' ', level));
    openBracket(builder);
    presentBlipStart(blip, level, builder);
    closeBracket(builder);

    for (ConversationBlip.LocatedReplyThread<? extends ConversationThread> locatedReply :
        blip.locateReplyThreads()) {
      presentThread(locatedReply.getThread(), level + 1, builder);
    }

    openBracket(builder);
    presentBlipFinish(blip, level, builder);
    closeBracket(builder);
  }

  protected void presentBlipStart(ConversationBlip blip, int level, StringBuilder builder) {
    builder.append("B ").append(blip.getId());
    if (blip.hasContent()) {
      StringBuilder textBuilder = new StringBuilder();
      Snippets.collateTextForDocument(blip.getDocument(), textBuilder, BLIP_SNIPPET_LENGTH);      
      builder.append(" size=").append(blip.getDocument().size());
      builder.append(" text=\"").append(textBuilder);
    }  
  }

  protected void presentBlipFinish(ConversationBlip blip, int level, StringBuilder builder) {
    builder.append("/B");
  }    

  /**
   * @return iterator on blips of the given conversation.
   * 
   * @param conversation the given conversation
   */
  public static Iterable<ConversationBlip> getBlips(Conversation conversation) {
    return BlipIterators.breadthFirst(conversation);    
  }

  //
  // Private methods
  //

  private static String getThreadPrefix(ConversationThread thread) {
    String prefix;
    if (thread.isRoot()) {
      prefix = "RT";
    } else if (thread.isInline()) {
      prefix = "IT";
    } else {
      prefix = "OT";
    }
    return prefix;
  }

  private void openBracket(StringBuilder builder) {
    builder.append(BRACKET_OPEN);
  }

  private void closeBracket(StringBuilder builder) {
    builder.append(BRACKET_CLOSE);
  }    
}  
