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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Sorted container implementation using array list for keeping elements.
 *
 * @param <E> - element type
 * @param <V> - sorting value type
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public abstract class ArrayListSortedContainer<E, V extends Comparable>
    implements SortedContainer<E, V> {

  private final List<E> elements = new ArrayList<>();

  @Override
  public boolean contains(E element) {
    V minValue = getStartValue(element);
    V maxValue = getFinishValue(element);
    int startIndex = getSearchIndex(minValue);

    // try elements to left
    for (int i = startIndex - 1; i >= 0; i--) {
      E e = elements.get(i);
      if (minValue.compareTo(getFinishValue(e)) > 0) {
        break;
      }
      if (e == element) {
        return true;
      }
    }

    // try elements to right
    for (int i = startIndex; i < elements.size(); i++) {
      E e = elements.get(i);
      if (maxValue.compareTo(getStartValue(e)) < 0) {
        break;
      }
      if (e == element) {
        return true;
      }
    }

    return false;
  }
  
  @Override
  public void add(E element) {
    elements.add(getInsertionIndex(element), element);
  }

  @Override
  public void remove(E element) {
    elements.remove(element);
  }

  @Override
  public List<E> findElements(V minValue, V maxValue) {
    LinkedList<E> found = new LinkedList<>();
    int startIndex = getSearchIndex(minValue);

    // try elements to left
    for (int i = startIndex - 1; i >= 0; i--) {
      E element = elements.get(i);
      if (minValue.compareTo(getFinishValue(element)) > 0) {
        break;
      }
      found.addFirst(element);
    }

    // try elements to right
    for (int i = startIndex; i < elements.size(); i++) {
      E element = elements.get(i);
      if (maxValue.compareTo(getStartValue(element)) < 0) {
        break;
      }
      found.addLast(element);
    }

    return found;
  }

  @Override
  public E findFirstElement(V minValue, V maxValue) {
    int startIndex = getSearchIndex(minValue);

    // try elements to left
    for (int i = startIndex - 1; i >= 0; i--) {
      E element = elements.get(i);
      if (minValue.compareTo(getFinishValue(element)) > 0) {
        break;
      }
      return element;
    }

    // try elements to right
    for (int i = startIndex; i < elements.size(); i++) {
      E element = elements.get(i);
      if (maxValue.compareTo(getStartValue(element)) < 0) {
        break;
      }
      return element;
    }

    return null;
  }
  
  @Override
  public List<E> getElements() {
    return new ArrayList<>(elements);
  }

  // Private methods

  /**
   * Returns insertion index for the element.
   *
   * @param element element to be inserted
   */
  private int getInsertionIndex(E element) {
    int beginIndex = 0;
    int endIndex = elements.size() - 1;
    V value = getStartValue(element);
    while (endIndex >= beginIndex) {
      int middleIndex = beginIndex + (endIndex - beginIndex) / 2;
      E middleElement = elements.get(middleIndex);
      switch (insertionCompare(element, middleElement)) {
        case 1:
          beginIndex = middleIndex + 1;
          break;
        case -1:
          endIndex = middleIndex - 1;
          break;
        default:
          throw new RuntimeException("Element with value=" + value + " is already kept in the list!");
      }
    }
    return beginIndex;
  }  
  
  /** Returns search index for the value.
   *
   * @param value searched value of the element
   */
  private int getSearchIndex(V value) {
    int beginIndex = 0;
    int endIndex = elements.size() - 1;
    while (endIndex >= beginIndex) {
      int middleIndex = beginIndex + (endIndex - beginIndex) / 2;
      E middleElement = elements.get(middleIndex);
      V middleValue = getStartValue(middleElement);
      switch (value.compareTo(middleValue)) {
        case 1:
          beginIndex = middleIndex + 1;
          break;
        case -1:
          endIndex = middleIndex - 1;
          break;
        default:
          return middleIndex;
      }
    }
    return beginIndex;
  }
}
