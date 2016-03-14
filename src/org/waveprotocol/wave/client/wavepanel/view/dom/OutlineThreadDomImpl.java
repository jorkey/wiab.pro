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

package org.waveprotocol.wave.client.wavepanel.view.dom;

import com.google.gwt.dom.client.Element;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicOutlineThreadView;

/**
 * DOM implementation of an inline thread.
 */
public final class OutlineThreadDomImpl
    implements DomView, IntrinsicOutlineThreadView {

  public static OutlineThreadDomImpl of(Element e) {
    return new OutlineThreadDomImpl(e);
  }

  private final Element self;

  OutlineThreadDomImpl(Element e) {
    self = e;
  }

  public void remove() {
    // do nothing here
  }

  // DomView nature

  @Override
  public Element getElement() {
    return self;
  }

  @Override
  public String getId() {
    return null;
  }

  // Structure

  public Element getBlipContainer() {
    return self;
  }
  
  // Equality

  @Override
  public boolean equals(Object obj) {
    return DomViewHelper.equals(this, obj);
  }

  @Override
  public int hashCode() {
    return DomViewHelper.hashCode(this);
  }
  
  @Override
  public void setTotalBlipCount(int totalBlipCount) {
  }

  @Override
  public void setUnreadBlipCount(int unreadBlipCount) {
  }  
}