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
 * Boxes an value which can't be changed.
 *
 * @author danilatos@google.com (Daniel Danilatos)
 * @author dyukon@gmail.com (Denis Konovalchik)
 *
 * @param <T>
 */
public class ImmutableBox<T> {

  /**
   * Value kept in a box.
   */
  private final T boxed;

  /**
   * Convenience factory method.
   */
  public static <T> ImmutableBox<T> create() {
    return new ImmutableBox<T>();
  }

  /**
   * Convenience factory method.
   */
  public static <T> ImmutableBox<T> create(T initial) {
    return new ImmutableBox<T>(initial);
  }

  /** No initial value. */
  public ImmutableBox() {
    this(null);
  }

  /**
   * @param boxed initial value.
   */
  public ImmutableBox(T boxed) {
    this.boxed = boxed;
  }

  /**
   * @return the boxed value.
   */
  public T get() {
    return this.boxed;
  }
}
