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


package org.waveprotocol.wave.client.uibuilder;

import static org.waveprotocol.wave.client.uibuilder.BuilderHelper.KIND_ATTRIBUTE;

import org.waveprotocol.wave.client.common.safehtml.EscapeUtils;
import org.waveprotocol.wave.client.common.safehtml.SafeHtml;
import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;


/**
 * Helper for UiBuilders that produce HTML strings. This is a temporary utility
 * class for use only while HTML is manually specified, rather than generated
 * though an XML template.
 *
 */
public final class OutputHelper {

  // Utility class
  private OutputHelper() {
  }

  /**
   * Opens a div.
   *
   * @param builder output builder
   * @param id value for the HTML id attribute (or {@code null} for none)
   * @param clazz value for the CSS class attribute (or {@code null} for none)
   * @param kind value for the kind attribute (or {@code null} for none)
   */
  public static void open(SafeHtmlBuilder builder, String id, String clazz, String kind) {
    openWithAllParameters(builder, "div", id, clazz, kind, null, null, null);
  }

  /**
   * Opens a span.
   *
   * @param builder output builder
   * @param id value for the HTML id attribute (or {@code null} for none)
   * @param clazz value for the CSS class attribute (or {@code null} for none)
   * @param kind value for the kind attribute (or {@code null} for none)
   */
  public static void openSpan(SafeHtmlBuilder builder, String id, String clazz, String kind) {
    openWithAllParameters(builder, "span", id, clazz, kind, null, null, null);
  }

  /**
   * Opens a div, with some extra attributes.
   *
   * @param builder output builder
   * @param id value for the HTML id attribute (or {@code null} for none)
   * @param clazz value for the CSS class attribute (or {@code null} for none)
   * @param kind value for the kind attribute (or {@code null} for none)
   * @param extra extra HTML, which must be a compile-time safe string
   */
  public static void openWith(
      SafeHtmlBuilder builder, String id, String clazz, String kind, String extra) {
    openWithAllParameters(builder, "div", id, clazz, kind, extra, null, null);
  }

  public static void openWith(SafeHtmlBuilder builder, String id, String clazz,
      String kind, String extra, String title, String shortcut) {
    openWithAllParameters(builder, "div", id, clazz, kind, extra, title, shortcut);
  }

  /**
   * Opens a span, with some extra attributes.
   *
   * @param builder output builder
   * @param id value for the HTML id attribute (or {@code null} for none)
   * @param clazz value for the CSS class attribute (or {@code null} for none)
   * @param kind value for the kind attribute (or {@code null} for none)
   * @param extra extra HTML, which must be a compile-time safe string
   */
  public static void openSpanWith(
      SafeHtmlBuilder builder, String id, String clazz, String kind, String extra) {
    openWithAllParameters(builder, "span", id, clazz, kind, extra, null, null);
  }

  public static void openSpanWith(SafeHtmlBuilder builder, String id,
      String clazz, String kind, String extra, String title, String shortcut) {
    openWithAllParameters(builder, "span", id, clazz, kind, extra, title, shortcut);
  }

  /**
   * Closes the currently open div.
   */
  public static void close(SafeHtmlBuilder builder) {
    builder.appendHtmlConstant("</div>");
  }

  /**
   * Closes the currently open span.
   */
  public static void closeSpan(SafeHtmlBuilder builder) {
    builder.appendHtmlConstant("</span>");
  }

  /**
   * Opens and closes a div.
   *
   * @see #open(SafeHtmlBuilder, String, String, String)
   */
  public static void append(SafeHtmlBuilder builder, String id, String style, String kind) {
    appendWith(builder, id, style, kind, null, null, null);
  }

  /**
   * Opens and closes a span.
   *
   * @see #open(SafeHtmlBuilder, String, String, String)
   */
  public static void appendSpan(SafeHtmlBuilder builder, String id, String style, String kind) {
    appendSpanWith(builder, id, style, kind, null, null, null);
  }

  public static void appendSpanWith(SafeHtmlBuilder builder, String id,
      String clazz, String kind, String extra, String title, String shortcut) {
    openSpanWith(builder, id, clazz, kind, extra, title, shortcut);
    closeSpan(builder);
  }

  /**
   * Opens and closes a div.
   *
   * @see #openWithAllParameters(SafeHtmlBuilder, String, String, String, String)
   */
  public static void appendWith(
      SafeHtmlBuilder builder, String id, String style, String kind, String extra) {
    appendWith(builder, id, style, kind, extra, null, null);
  }

  public static void appendWith(SafeHtmlBuilder builder, String id,
      String clazz, String kind, String extra, String title, String shortcut) {
    openWith(builder, id, clazz, kind, extra, title, shortcut);
    close(builder);
  }

  /**
   * Appends an image.
   *
   * @param builder
   * @param id
   * @param style
   * @param url attribute-value safe URL
   * @param info attribute-value safe image information
   * @param kind
   * @param extra additional HTML
   */
  public static void image(
      SafeHtmlBuilder builder, String id, String style, SafeHtml url, SafeHtml info, String kind,
      String extra) {
    String safeUrl = url != null ? EscapeUtils.sanitizeUri(url.asString()) : null;
    StringBuilder s = new StringBuilder();
    s.append("<img ");
    if (id != null) {
      s.append("id='").append(id).append("' ");
    }
    if (style != null) {
      s.append("class='").append(style).append("' ");
    }
    if (safeUrl != null) {
      s.append("src='").append(safeUrl).append("' ");
    }
    if (info != null) {
      s.append("alt='").append(info.asString()).append("' title='")
          .append(info.asString()).append("' ");
    }
    if (kind != null) {
      s.append(KIND_ATTRIBUTE).append("='").append(kind).append("'");
    }
    if (extra != null) {
      s.append(extra);
    }
    s.append("></img>");
    builder.appendHtmlConstant(s.toString());
  }

  public static void button(SafeHtmlBuilder builder,
      String id,
      String clazz,
      String kind,
      String title,
      String caption) {
    StringBuilder s = new StringBuilder();
    s.append("<button ");
    if (id != null) {
      s.append("id='").append(id).append("' ");
    }
    if (title != null) {
      s.append("title='").append(title).append("' ");
    }
    if (clazz != null) {
      s.append("class='").append(clazz).append("' ");
    }
    if (kind != null) {
      s.append(KIND_ATTRIBUTE).append("='").append(kind).append("'");
    }
    s.append(">");
    if (caption != null) {
      s.append(caption);
    }
    s.append("</button>");
    builder.appendHtmlConstant(s.toString());
  }

  /**
   * Opens an element.
   *
   * @param tag tag for the element
   * @param builder output builder
   * @param id value for the HTML id attribute (or {@code null} for none)
   * @param clazz value for the CSS class attribute (or {@code null} for none)
   * @param kind value for the kind attribute (or {@code null} for none)
   * @param extra extra HTML, which must be a compile-time safe string
   * @param title title used for popup hint
   */
  private static void openWithAllParameters(SafeHtmlBuilder builder, String tag, String id,
      String clazz, String kind, String extra, String title, String shortcut) {
    StringBuilder s = new StringBuilder();
    s.append("<").append(tag);
    if (id != null) {
      s.append(" id='").append(id).append("'");
    }
    if (clazz != null) {
      s.append(" class='").append(clazz).append("'");
    }
    if (kind != null) {
      s.append(" ").append(BuilderHelper.KIND_ATTRIBUTE).append("='")
          .append(kind).append("'");
    }
    if (title != null || shortcut != null) {
      s.append(" title='");
      if (title != null) {
        s.append(title);
        if (shortcut != null) {
          s.append(" [").append(shortcut).append("]");
        }
      } else {
        s.append(shortcut);
      }
      s.append("'");
    }
    if (extra != null) {
      s.append(" ").append(extra);
    }
    s.append(">");
    builder.appendHtmlConstant(s.toString());
  }
}
