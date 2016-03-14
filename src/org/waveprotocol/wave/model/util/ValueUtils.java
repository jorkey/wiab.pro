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

import java.util.Arrays;

/**
 * Utilities related to the document-based value classes
 *
 * @author anorth@google.com (Alex North)
 */
public final class ValueUtils {

  /**
   * @return true iff a and b are both null or are equal
   */
  public static <T> boolean equal(T a, T b) {
    return (a == null) ? (b == null) : a.equals(b);
  }

  /**
   * @return true iff a and b are not both null and are not equal
   */
  public static <T> boolean notEqual(T a, T b) {
    return (a == null) ? (b != null) : !a.equals(b);
  }

  /**
   * @return {@code value} if it is not null, {@code def} otherwise
   */
  public static <T> T valueOrDefault(T value, T def) {
    return value != null ? value : def;
  }

  /**
   * Returns the first {@code size} characters of the given string
   */
  public static String abbrev(String longString, int size) {
    if (longString == null) {
      return null;
    }

    return longString.length() <= size ? longString
        : new String(longString.substring(0, size));
  }
  
  /**
   * Turns the first letter of the string into capital letter
   * @param s source string
   * @return capitalized string
   */
  public static String toCapitalCase(String s) {
    String ss = s;
    if (s != null && s.length()>0) {
      ss = s.substring(0, 1).toUpperCase();
      if (s.length() > 1) {
        ss += s.substring(1);
      }
    }
    return ss;
  }
  
  /**
   * Turns the string into CamelCase.
   * @param s source string
   * @param separator the separator between string parts
   * @param capitalizeFirstPart true, if the first string part must be capitalized
   * @return string in CamelCase.
   */  
  public static String toCamelCase(String s, String separator, boolean capitalizeFirstPart) {
    String[] parts = s.split(separator);
    String result = capitalizeFirstPart ? toCapitalCase(parts[0]) : parts[0];
    for (int i = 1; i < parts.length; i++) {
      result += toCapitalCase(parts[i]);
    }
    return result;
  }

  
  /**
   * Embraces string with "'" symbols
   * @param s source string
   * @return embraced string
   */  
  public static String embrace(Object obj) {
    return embraceWith("'", obj, "'");
  }

  /**
   * Embraces object's string representation with given brace strings
   * @param leftBrace lft brace string
   * @param s source string
   * @param rightBrace right brace string
   * @return embraced string
   */
  public static String embraceWith(String leftBrace, Object obj, String rightBrace) {
    return obj != null ? (leftBrace + obj.toString() + rightBrace) : "null";
  }  

  /**
   * @param s given string
   * @param brace brace string
   * @return true, if the given string is embraced with given brace strings
   */
  public static boolean isEmbracedWith(String s, String brace) {
    return isEmbracedWith(s, brace, brace);
  }
  
  /**
   * @param s given string
   * @param leftBrace left brace string
   * @param rightBrace right brace string
   * @return true, if the given string is embraced with given brace strings
   */
  public static boolean isEmbracedWith(String s, String leftBrace, String rightBrace) {
    int leftPos = s.indexOf(leftBrace);
    int rightPos = s.lastIndexOf(rightBrace);
    return leftPos == 0 && rightPos + rightBrace.length() == s.length();
  }  
  
  /**
   * Strips the string from equal braces.
   * @param s source string
   * @param brace left/right brace
   * @return the string stripped from braces, or the string itself, if stripping is impossible
   */
  public static String stripFrom(String s, String brace) {
    return stripFrom(s, brace, brace);
  }  
  
  /**
   * Strips the string from braces.
   * @param s source string
   * @param leftBrace left brace
   * @param rightBrace right brace
   * @return the string stripped from braces, or the string itself, if stripping is impossible
   */
  public static String stripFrom(String s, String leftBrace, String rightBrace) {
    int leftPos = s.indexOf(leftBrace);
    int rightPos = s.lastIndexOf(rightBrace);
    return leftPos != -1 && rightPos != -1 ? s.substring(leftPos + leftBrace.length(), rightPos) : s;
  }
  
  /**
   * Enlists items dividing them with given separator
   * @param separator 
   * @param items
   * @return items list
   */
  public static String enlistWith(String separator, Object... items) {
    String s = "";
    for (int i = 0; i < items.length; i++) {
      final Object item = items[i];
      s += (item != null ? item.toString() : "null");
      if (i < items.length-1) {
        s += separator;
      }
    }
    return s;
  }
  
  /**
   * Enlists items dividing them with ", "
   * @param items
   * @return items list
   */  
  public static String enlist(Object... items) {
    return enlistWith(", ", items);
  }
  
  /**
   * For given parameter name and its value returns string "param: value"
   * @param param parameter name
   * @param value parameter value   * 
   */
  public static String paramValue(String param, Object value) {
    return param + ": " + embrace(value);
  }
  
  public static String paramValue(String param, long value) {
    return param + ": " + value;
  }
  
  /**
   * @return string consisting of the same symbols
   * 
   * @param c char the string consists of
   * @param length length of the string
   */
  public static String stringFromChar(char c, int length) {
    char[] array = new char[length];
    Arrays.fill(array, c);
    return new String(array);
  }  
}
