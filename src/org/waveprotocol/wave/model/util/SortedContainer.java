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

import java.util.List;

/**
 * Container for elements keeping them ordered according to values calculated by them.
 * Allows to get collection of elements whose vertical interval intersects with the given range.
 *
 * @param <E> type of elements kept in container
 * @param <V> type of element's values used for sorting
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public interface SortedContainer<E, V extends Comparable> {

  /**
   * @return true, if the element is contained in the container
   */
  boolean contains(E element);
  
  /**
   * Adds an element to the container.
   * 
   * @param element element to be added
   */
  void add(E element);

  /**
   * Removes an element from the container.
   * 
   * @param element element to be removed
   */
  void remove(E element);

  /**
   * @param minValue minimum value
   * @param maxValue maximum value
   * @return the list of elements with values lying between minimum and maximum values
   */
  List<E> findElements(V minValue, V maxValue);

  /**
   * @param minValue minimum value
   * @param maxValue maximum value
   * @return the first found element the value of which lies between minimum and maximum values.
   * If no such element found, returns null.
   */
  E findFirstElement(V minValue, V maxValue);
  
  /**
   * @return the sorted list of elements of this container.
   */
  List<E> getElements();

  /**
   * Compares two elements for insertion.
   * @param element1
   * @param element2
   * @return a negative integer, zero, or a positive integer as the first argument
   * is less than, equal to, or greater than the second
   */
  int insertionCompare(E element1, E element2);  
  
  /**
   * @param element the element on which the start sorting value must be calculated
   * @return the start sorting value by the element.
   * Should be implemented in the successor classes.
   */
  V getStartValue(E element);

  /**
   * @param element the element on which the finish sorting value must be calculated
   * @return the finish sorting value by the element.
   * Should be implemented in the successor classes.
   */
  V getFinishValue(E element);
}
