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

import junit.framework.TestCase;

import java.util.Collection;

/**
 * Test for ArrayListSortedContainer class.
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class ArrayListSortedContainerTest extends TestCase {

  private class Element {

    private int begin;
    private int end;

    public Element(int begin, int end) {
      this.begin = begin;
      this.end = end;
    }

    public Element(Element src) {
      this(src.getBegin(), src.getEnd());
    }
    
    public int getBegin() {
      return begin;
    }

    public int getEnd() {
      return end;
    }

    public void set(int begin, int end) {
      this.begin = begin;
      this.end = end;
    }

    @Override
    public String toString() {
      return "[" + begin + "," + end + "]";
    }
  }

  private class ElementContainer extends ArrayListSortedContainer<Element, Integer> {

    @Override
    public int insertionCompare(Element element1, Element element2) {
      return getStartValue(element1).compareTo(getStartValue(element2));
    }
    
    @Override
    public Integer getStartValue(Element element) {
      return element.getBegin();
    }

    @Override
    public Integer getFinishValue(Element element) {
      return element.getEnd();
    }

    @Override
    public String toString() {
      return ArrayListSortedContainerTest.toString(getElements());
    }
  }

  private ElementContainer ec;
  private Element[] elements;

  public void testContains() {
    ec = new ElementContainer();
    elements = getTestElementArray();    
    for (int i = 0; i < elements.length; i++) {
      ec.add(elements[i]);
    }
    for (int i = 0; i < elements.length; i++) {
      assertTrue(ec.contains(elements[i]));
    }
    assertFalse(ec.contains(new Element(-2, -1)));
    assertFalse(ec.contains(new Element(elements[0])) );
  }
  
  public void testAdd() {
    ec = new ElementContainer();
    elements = getTestElementArray();
    for (int i = 0; i < elements.length; i++) {
      ec.add(elements[i]);
    }
    assertEquals("[[1,10],[10,12],[13,19],[24,27],[30,33],[38,44],[74,81],[84,88],[93,95],[97,99]]",
        ec.toString());
  }

  public void testRemoveAllAndAddChanged() {
    ec = new ElementContainer();
    elements = getTestElementArray();
    for (int i = 0; i < elements.length; i++) {
      ec.add(elements[i]);
    }
    for (Element element : ec.getElements()) {
      ec.remove(element);
    }
    assertEquals("[]", ec.toString());

    Element e1 = new Element(142, 1146);
    ec.add(e1);
    assertEquals("[[142,1146]]", ec.toString());
    Element e2 = new Element(1147, 2150);
    ec.add(e2);
    assertEquals("[[142,1146],[1147,2150]]", ec.toString());
    e2.set(1245, 2249);
    assertEquals("[[142,1146],[1245,2249]]", ec.toString());
    Element e3 = new Element(1177, 1231);
    ec.add(e3);
    assertEquals("[[142,1146],[1177,1231],[1245,2249]]", ec.toString());
  }

  public void testRemove() {
    ec = new ElementContainer();
    elements = new Element[] {
      new Element(-226, -124), // 0th
      new Element(-21, 31), // 1st
      new Element(113, 165), // 2nd
      new Element(614, 766), // 3rd
      new Element(826, 1928) // 4th
    };
    for (int i = 0; i < elements.length; i++) {
      ec.add(elements[i]);
    }
    assertEquals("[[-226,-124],[-21,31],[113,165],[614,766],[826,1928]]", ec.toString());
    ec.remove(elements[1]);
    assertEquals("[[-226,-124],[113,165],[614,766],[826,1928]]", ec.toString());
  }

  public void testFindElementsConstant() {
    ec = new ElementContainer();
    elements = getTestElementArray();
    for (int i = 0; i < elements.length; i++) {
      ec.add(elements[i]);
    }
    assertEquals(toString(ec.findElements(15, 48)), "[[13,19],[24,27],[30,33],[38,44]]");
    assertEquals(toString(ec.findElements(41, 94)), "[[38,44],[74,81],[84,88],[93,95]]");
    assertEquals(toString(ec.findElements(44, 74)), "[[38,44],[74,81]]");
    assertEquals(toString(ec.findElements(45, 73)), "[]");
    assertEquals(toString(ec.findElements(0, 100)), toString(ec.findElements(1, 99)) );
  }

  public void testFindElementsChanged() {
    ec = new ElementContainer();
    elements = getTestElementArray();
    for (int i = 0; i < elements.length; i++) {
      ec.add(elements[i]);
    }
    elements[3].set(63, 72); // from [38,44]
    elements[1].set(20, 23); // from [13,19]
    elements[8].set(50, 51); // from [30,33]
    assertEquals("[[1,10],[10,12],[20,23],[24,27],[50,51],[63,72],[74,81],[84,88],[93,95],[97,99]]",
        ec.toString());
    assertEquals(toString(ec.findElements(0, 20)), "[[1,10],[10,12],[20,23]]");
    assertEquals(toString(ec.findElements(21, 50)), "[[20,23],[24,27],[50,51]]");
    assertEquals(toString(ec.findElements(51, 100)), "[[50,51],[63,72],[74,81],[84,88],[93,95],[97,99]]");
    assertEquals(toString(ec.findElements(30, 33)), "[]");
  }

  private static String toString(Collection<Element> elements) {
    String s = "[";
    boolean comma = false;
    for (Element element : elements) {
      if (!comma) {
        comma = true;
      } else {
        s += ",";
      }
      s += element;
    }
    s += "]";
    return s;
  }
  
  private Element[] getTestElementArray() {
    return new Element[] {
      new Element(84, 88), // 0th
      new Element(13, 19), // 1st
      new Element(97, 99), // 2nd
      new Element(38, 44), // 3rd
      new Element(74, 81), // 4th
      new Element(10, 12), // 5th
      new Element(93, 95), // 6th
      new Element(24, 27), // 7th
      new Element(30, 33), // 8th
      new Element(1, 10)   // 9th
    };    
  }
}
