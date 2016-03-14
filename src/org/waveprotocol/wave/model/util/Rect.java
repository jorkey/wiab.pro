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

package org.waveprotocol.wave.model.util;

/**
 * Rectangle consisting of integer values.
 */
public class Rect {

  private int left;
  private int top;
  private int width;
  private int height;

  public Rect(int left, int top, int width, int height) {
    this.left = left;
    this.top = top;
    this.width = width;
    this.height = height;
  }

  public Rect(Rect rect) {
    this(rect.left, rect.top, rect.width, rect.height);
  }

  public Rect(Interval hSize, Interval vSize) {
    this(hSize.getBegin(), vSize.getBegin(), hSize.getLength(), vSize.getLength());
  }

  public int getLeft() {
    return left;
  }

  public int getTop() {
    return top;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public int getRight() {
    return left + width - 1;
  }

  public int getBottom() {
    return top + height - 1;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof Rect)) {
      return false;
    }
    Rect r = (Rect) obj;
    return r.left == left && r.top == top && r.width == width && r.height == height;
  }
  
  @Override
  public String toString() {
    return "(left: " + left + ", top: " + top + ", width: " + width + ", height:" + height + ")";
  }
}
