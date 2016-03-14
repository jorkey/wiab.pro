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

package org.waveprotocol.wave.model.document.operation;

import java.util.Map;
import org.waveprotocol.wave.model.document.operation.Nindo.NindoCursor;

/**
 * Delegates all methods to a wrapped target (useful for subclassing).
 *
 * @author danilatos@google.com (Daniel Danilatos)
 */
public abstract class NindoCursorDecorator implements NindoCursor {

  /** the target */
  protected final NindoCursor target;

  /**
   * @param target target to delegate to
   */
  protected NindoCursorDecorator(NindoCursor target) {
    this.target = target;
  }

  @Override
  public void begin() {
    target.begin();
  }

  @Override
  public void characters(String s) {
    target.characters(s);
  }

  @Override
  public void deleteCharacters(int n) {
    target.deleteCharacters(n);
  }

  @Override
  public void deleteElementEnd() {
    target.deleteElementEnd();
  }

  @Override
  public void deleteElementStart() {
    target.deleteElementStart();
  }

  @Override
  public void elementEnd() {
    target.elementEnd();
  }

  @Override
  public void elementStart(String type, Attributes attrs) {
    target.elementStart(type, attrs);
  }

  @Override
  public void endAnnotation(String key) {
    target.endAnnotation(key);
  }

  @Override
  public void finish() {
    target.finish();
  }

  @Override
  public void replaceAttributes(Attributes attrs) {
    target.replaceAttributes(attrs);
  }

  @Override
  public void skip(int n) {
    target.skip(n);
  }

  @Override
  public void startAnnotation(String key, String value) {
    target.startAnnotation(key, value);
  }

  @Override
  public void updateAttributes(Map<String, String> attrUpdate) {
    target.updateAttributes(attrUpdate);
  }
}
