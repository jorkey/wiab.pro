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

/**
 * Test element for placeholder.
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class PlaceholderTestElement extends TestElement {

  private static final int INITIAL_HEIGHT_PX = 10;

  public PlaceholderTestElement() {
    super("P");
    height = INITIAL_HEIGHT_PX;
  }

  public void setPixelHeight(int pixelHeight) {
    height = pixelHeight;
  }

  @Override
  public Kind getKind() {
    return Kind.PLACEHOLDER;
  }

  @Override
  public String getId() {
    return null;
  }

  @Override
  protected void changeWidth() {
    width = getParent().getWidth();
  }

  @Override
  protected void changeHeight() {
    // do nothing here
  }

  @Override
  protected void arrangeChildren() {
    // do nothing here
  }

  @Override
  protected String getShortName() {
    return "P";
  }
}