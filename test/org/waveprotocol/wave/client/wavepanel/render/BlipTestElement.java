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

import org.waveprotocol.wave.model.conversation.ConversationBlip;

/**
 * Test element for blip.
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class BlipTestElement extends TestElement {

  private static final int CONTENT_SQUARE = 10000;
  private static final int INDENT_LEFT = 30;
  private static final int INDENT_RIGHT = 0;

  private static int blipCount = 0;

  private final ConversationBlip blip;
  private final int indentationLevel;
  private int contentHeight;

  public BlipTestElement(ConversationBlip blip, int indentationLevel) {
    super("B" + (++blipCount));
    this.blip = blip;
    this.indentationLevel = indentationLevel;
  }

  public ThreadTestElement getParentThread() {
    if (parent instanceof ThreadTestElement) {
      return (ThreadTestElement) parent;
    }
    return null;
  }

  public BlipTestElement getParentBlip() {
    ThreadTestElement parentThread = getParentThread();
    return parentThread != null ? parentThread.getParentBlip() : null;
  }

  @Override
  public Kind getKind() {
    return Kind.BLIP;
  }

  @Override
  public String getId() {
    return blip.getId();
  }

  @Override
  protected String getShortName() {
    return "B";
  }

  @Override
  protected void changeWidth() {
    width = getParent().getWidth() - indentationLevel * (INDENT_LEFT + INDENT_RIGHT);
  }

  @Override
  protected void changeHeight() {
    contentHeight = CONTENT_SQUARE / width;
    int childrenHeight = 0;
    for (TestElement child : children) {
      childrenHeight += child.getHeight();
    }
    height = contentHeight + childrenHeight;
  }

  @Override
  protected void arrangeChildren() {
    int childDistance = contentHeight / (children.size() + 1);
    int childTop = 0;
    for (TestElement child : children) {
      childTop += childDistance;
      child.setRelativeTop(childTop);
      childTop += child.getHeight();
    }
  }

  @Override
  public void setParent(TestElement parent) {
    super.setParent(parent);
  }
}
