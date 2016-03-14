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

import java.util.ArrayList;
import java.util.List;

/**
 * Simple element implementation for testing purpose.
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public abstract class TestElement {

  public enum Kind {
    ROOT_THREAD,
    PLACEHOLDER,
    BLIP,
    INLINE_THREAD
  }

  final protected String name;

  protected final List<TestElement> children = new ArrayList<TestElement>();

  protected TestElement parent;
  protected int relativeTop;
  protected int width;
  protected int height;
  protected int zIndex;

  public TestElement(String name) {
    this.name = name;
  }

  public abstract Kind getKind();

  public abstract String getId();

  public String getName() {
    return name;
  }

  public int getHeight() {
    return height;
  }

  public int getWidth() {
    return width;
  }

  public int getRelativeTop() {
    return relativeTop;
  }

  public int getRelativeBottom() {
    return getRelativeTop() + getHeight();
  }

  public int getAbsoluteTop() {
    int parentTop = parent != null ? parent.getAbsoluteTop() : 0;
    return parentTop + getRelativeTop();
  }

  public int getAbsoluteBottom() {
    return getAbsoluteTop() + getHeight();
  }

  public TestElement getParent() {
    return parent;
  }

  public TestElement getSibling(boolean before) {
    TestElement sibling = null;
    if (parent != null) {
      List<TestElement> siblings = parent.getChildren();
      int index = siblings.indexOf(this);
      int siblingIndex = before ? index - 1 : index + 1;
      if (siblingIndex >= 0 && siblingIndex < siblings.size()) {
        sibling = siblings.get(siblingIndex);
      }
    }
    return sibling;
  }

  public void setParent(TestElement parent) {
    this.parent = parent;
  }

  public void setRelativeTop(int relativeTop) {
    this.relativeTop = relativeTop;
  }

  public void setZIndex(int zIndex) {
    this.zIndex = zIndex;
  }
  
  public int getZIndex() {
    return zIndex;
  }
  
  public void remove() {
    if (parent != null) {
      parent.removeChild(this);
    }
  }

  public void appendChild(TestElement child) {
    children.add(child);
    child.setParent(this);
  }

  public void insertChild(TestElement child, TestElement neighbor, boolean beforeNeighbor) {
    int index;
    if (beforeNeighbor) {
      index = neighbor != null ? children.indexOf(neighbor) : children.size();
    } else {
      index = neighbor != null ? children.indexOf(neighbor) + 1 : 0;
    }
    children.add(index, child);
    child.setParent(this);
  }

  public void removeChild(TestElement child) {
    children.remove(child);
  }

  List<TestElement> getChildren() {
    return children;
  }

  public void adjustSize() {
    adjustHorizontally();
    adjustVertically();
  }

  @Override
  public String toString() {
    return getName() + "(" + getAbsoluteTop() + "," + getAbsoluteBottom() + ") z:" + zIndex;
  }

  // Protected methods

  protected void adjustHorizontally() {
    changeWidth();
    for (TestElement child : children) {
      child.adjustHorizontally();
    }
  }

  protected void adjustVertically() {
    for (TestElement child : children) {
      child.adjustVertically();
    }
    changeHeight();
    arrangeChildren();
  }

  protected abstract String getShortName();

  protected abstract void changeWidth();

  protected abstract void changeHeight();

  protected abstract void arrangeChildren();
}
