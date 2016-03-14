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
 * Interval consisting of integer values
 * 
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class Interval {

  public static Interval create(Interval source) {
    return new Interval(source);
  }
  
  public static Interval create(int begin, int end) {
    return new Interval(begin, end);
  }
  
  private int begin;
  private int end;

  private Interval(Interval source) {
    this(source.begin, source.end);
  }
  
  private Interval(int begin, int end) {
    this.begin = begin;
    this.end = end;
  }  
  
  public int getBegin() {    
    return begin;
  }

  public int getEnd() {
    return end;
  }

  public boolean isValid() {
    return begin <= end;
  }

  public int getLength() {
    return end - begin;
  }

  public int getMiddle() {
    return (begin + end) / 2;
  }

  public boolean contains(int x) {
    return begin <= x && x <= end;
  }

  public boolean contains (Interval interval) {
    return begin < interval.begin && end > interval.end;
  }

  public void set(int begin, int end) {
    this.begin = begin;
    this.end = end;
  }

  public void setBegin(int begin) {
    this.begin = begin;
  }

  public void setEnd(int end) {
    this.end = end;
  }

  public void move(int delta) {
    begin += delta;
    end += delta;
  }

  public void move(int deltaBegin, int deltaEnd) {
    begin += deltaBegin;
    end += deltaEnd;
  }

  @Override
  public String toString() {
    return "[" + begin + ":" + end + "]";
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof Interval)) {
      return false;
    }
    Interval another = (Interval)obj;
    return begin == another.begin && end == another.end;
  }  
}
