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

import org.waveprotocol.wave.model.conversation.ConversationThread;

/**
 * Test element for thread.
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public abstract class ThreadTestElement extends TestElement {

  private ConversationThread thread;
  private static int threadCount = 0;

  public ThreadTestElement(ConversationThread thread) {
    super("T" + (threadCount++));
    this.thread = thread;
  }

  public BlipTestElement getParentBlip() {
    if (parent instanceof BlipTestElement) {
      return (BlipTestElement) parent;
    }
    return null;
  }

  @Override
  public String getId() {
    return thread.getId();
  }

  @Override
  protected void changeHeight() {
    int childrenHeight = 0;
    for (TestElement child : children) {
      childrenHeight += child.getHeight();
    }
    height = childrenHeight;
  }

  @Override
  protected void arrangeChildren() {
    int childTop = 0;
    for (TestElement child : children) {
      child.setRelativeTop(childTop);
      childTop += child.getHeight();
    }
  }
}