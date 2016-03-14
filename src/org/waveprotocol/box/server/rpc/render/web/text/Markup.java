/**
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.waveprotocol.box.server.rpc.render.web.text;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.waveprotocol.box.server.rpc.render.ClientAction;
import org.waveprotocol.box.server.rpc.render.web.template.ProfileStore;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A utility class that converts raw wave documents into html.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
@Singleton
public class Markup {

  private static final DateFormat TIME_MILLIS_FORMATTER = new SimpleDateFormat("s.SSS");
  private static final DateFormat FULL_FORMATTER = new SimpleDateFormat("h:mm MMM dd, yyyy");

  private final ProfileStore profileStore;

  @Inject
  Markup(ProfileStore profileStore) {
    this.profileStore = profileStore;
  }

  /**
   * @return the participant's display name.
   */
  // TODO: This doesn't belong here.
  public String getDisplayName(String participantId) {
    return profileStore.getProfiles(ImmutableSet.of(participantId)).get(participantId).getName();
  }

  /**
   * @return the participant's image url.
   */
  // TODO: This doesn't belong here.
  public String getImageUrl(String participantId) {
    return profileStore.getProfiles(
        ImmutableSet.of(participantId)).get(participantId).getImageUrl();
  }

  /**
   * Helpful utility for templates.
   */
  public String sanitizeHtml(String text) {
    return Markup.sanitize(text);
  }

  /**
   * Formats a given timestamp into a friendly date string. The output will
   * look differently depending on the day and year. If the time given is the
   * same day as "now", then it will only display the time (12:30 PM). If it's
   * in the same year, then it will display the month and day (Jun 01) otherwise
   * it will return the month, day and year (Jun 01, 2009).
   *
   * @param timestamp
   * @return the formatted date time string.
   */
  public static String formatDateTime(long timestamp) {
    Date date = new Date(timestamp);
    Date now = new Date();
    return FULL_FORMATTER.format(date);
  }

  public static String formatMillis(long millis) {
    return TIME_MILLIS_FORMATTER.format(new Date(millis)) + "s";
  }

  public static String toDomId(String id) {
    //HACK to make blip ids work as DOM ids
    return id.replace('+', '-');
  }

  public static String toBlipId(String id) {
    //HACK to make blip ids work as DOM ids
    return id.replace('-', '+');
  }

  public static ClientAction measure(String action, long searchTime) {
    return new ClientAction("measure")
        .html(action + " completed in " + Markup.formatMillis(searchTime));
  }

  public static String embedSnippet(String waveId) {
    // TODO(dhanji): Move to template
      String domain = System.getProperty("DOMAIN");
      String port = System.getProperty("PORT");
    return "&lt;script type=\"text/javascript\"&gt;" +
        "    function load() {" +
        "      var targetDiv = document.getElementById('waveframe');" +
        "      var wavePanel = new google.wave.WavePanel({" +
        "        rootUrl: \"http://" + domain + ":" + port + "/\"," +
        "        target: targetDiv," +
        "        lite: true" +
        "      }).loadWave(\""+ waveId +"\");" +
        "    }" +
        "  &lt;/script&gt;";
  }

  /**
   * Sanitizes untrusted text so that it does not emit HTML markup. This utility
   * is based on a similar one in GWT's SafeHtml.java. It eliminates all predefined
   * entities in HTML/XHTML, replacing them with escape codes:
   * http://en.wikipedia.org/wiki/List_of_XML_and_HTML_character_entity_references
   *
   * @param text The untrusted text to sanitize
   * @return Escaped text that can safely be rendered in a web page.
   */
  public static String sanitize(String text) {
    StringBuilder out = new StringBuilder(text.length());

    char[] chars = text.toCharArray();
    for (char c : chars) {
      switch (c) {
        case '&':
          out.append("&amp;");
          break;
        case '\'':
          out.append("&#39;");
          break;
        case '"':
          out.append("&quot;");
          break;
        case '<':
          out.append("&lt;");
          break;
        case '>':
          out.append("&gt;");
          break;
        default:
          // allow all other characters.
          out.append(c);
      }
    }

    return out.toString();
  }

  /**
   * Checks if the scheme is one of four simple types (see #isSafeUri), if not
   * disallows the URI by reducing it to '#'
   *
   * @param uri An untrusted URI string to sanitize
   * @return Returns a safe URI.
   */
  private static String sanitizeUri(String uri) {
    return isSafeUri(uri) ? uri : "#";
  }


  private static String extractScheme(String uri) {
    if (null == uri) {
      return null;
    }
    int colonPos = uri.indexOf(':');
    if (colonPos < 0) {
      return null;
    }
    String scheme = uri.substring(0, colonPos);
    if (scheme.indexOf('/') >= 0 || scheme.indexOf('#') >= 0) {
      // The URI's prefix up to the first ':' contains other URI special
      // chars, and won't be interpreted as a scheme.
      return null;
    }
    return scheme;
  }

  // TODO(dhanji): Should we allow more schemes? Like im:
  private static boolean isSafeUri(String uri) {
    String scheme = extractScheme(uri);
    return (scheme == null
            || "http".equalsIgnoreCase(scheme)
            || "https".equalsIgnoreCase(scheme)
            || "mailto".equalsIgnoreCase(scheme)
            || "ftp".equalsIgnoreCase(scheme));
  }

  /**
   * Simply converts a lowerCamelCased symbol into a dashed one:
   * <pre>
   *   fontWeight -> font-weight
   * </pre>
   * TODO: perhaps we should intern all these strings to save memory
   * and remove the overhead of string allocation, they could also
   * be perfectly hashed.
   */
  static String toDashedStyle(String name) {
    char[] nameChars = name.toCharArray();

    // Pre allocate buffer, +1 for '-' (2-dashes are uncommon)
    StringBuilder builder = new StringBuilder(nameChars.length + 1);
    for (char nameChar : nameChars) {
      if (Character.isUpperCase(nameChar)) {
        builder.append('-');
        builder.append(Character.toLowerCase(nameChar));
        continue;
      }

      builder.append(nameChar);
    }

    return builder.toString();
  }

  /**
   * Checks if an image URL is safe (i.e. hosted on Google)
   * @param imageUrl A string URL
   * @return True if this image URL is safe to use in a google-hosted page.
   */
  public static boolean isTrustedImageUrl(String imageUrl) {
    String scheme = extractScheme(imageUrl);

    // NOTE(dhanji): the trailing slash is extremely important.
    return (scheme != null) && (imageUrl.startsWith(scheme + "://www.google.com/")
            || imageUrl.startsWith(scheme + "://google.com/"));
  }

  /**
   * First sanitizes the given URI and then encodes it using the UTF-8 character
   * set.
   * @param uri An untrusted URI string
   * @return Sanitized, URL-encoded URI for embedding in HTML
   */
  static String sanitizeAndEncode(String uri) {
    try {
      return URLEncoder.encode(sanitizeUri(uri), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
}
