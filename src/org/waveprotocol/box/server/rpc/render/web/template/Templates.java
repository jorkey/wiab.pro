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

package org.waveprotocol.box.server.rpc.render.web.template;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.collect.MapMaker;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.servlet.ServletContext;

/**
 * Handles all our html templates, loading, parsing and processing them.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
@Singleton
public class Templates {

  // Template file names go here.
  public static final String OUTER_TEMPLATE = "start.html.fragment";
  public static final String WAVELIST_TEMPLATE = "wavelist.html.fragment";
  public static final String AVATAR_TEMPLATE = "avatar.html.fragment";
  public static final String DIGEST_TEMPLATE = "digest.html.fragment";
  public static final String BLIP_TEMPLATE = "blip.html.fragment";
  public static final String ANCHOR_TEMPLATE = "anchor.html.fragment";
  public static final String HEADER_TEMPLATE = "header.html.fragment";
  public static final String FEED_TEMPLATE = "feed.html.fragment";
  public static final String PERMALINK_WAVE_TEMPLATE = "permalink_client.html";
  public static final String CLIENT_TEMPLATE = "full_client.html";
  public static final String MOBILE_TEMPLATE = "mobile_client.html";
  public static final String WAVE_NOT_FOUND_TEMPLATE = "wave_not_found.html.fragment";

  public static String GA_FRAGMENT(String analyticAccount) {
   return "<script type=\"text/javascript\">\n"
       + "  var _gaq = _gaq || [];\n"
       + "  _gaq.push(['_setAccount', \"" + analyticAccount + "\"]);\n"
       + "\n"
       + "  _gaq.push(['_trackPageview']);\n"
       + "\n"
       + "  (function() {\n"
       + "    var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;\n"
       + "    ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';\n"
       + "    var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);\n"
       + "  })();\n"
       + "</script>"; }

  private static boolean PRODUCTION_MODE = false;

  private static final Logger log = Logger.getLogger(Templates.class.getName());

  public static String convertStreamToString(InputStream is) throws IOException {
    /*
     * To convert the InputStream to String we use the Reader.read(char[]
     * buffer) method. We iterate until the Reader return -1 which means there's
     * no more data to read. We use the StringWriter class to produce the
     * string.
     */
    if (is != null) {
      Writer writer = new StringWriter();

      char[] buffer = new char[1024];
      try {
        Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        int n;
        while ((n = reader.read(buffer)) != -1) {
          writer.write(buffer, 0, n);
        }
      } finally {
        is.close();
      }
      return writer.toString();
    } else {
      return "";
    }
  }

  public static String makeAnchorTag(String id) {
    return "<div class='anchor' id='" + id + "'></anchor>";
  }

  public static void insertIntoAnchor(String id, String inner, StringBuilder outer) {
    String anchorPrefix = "<div class='anchor' id='" + id + "'>";
    int offset = outer.indexOf(anchorPrefix);
    outer.insert(offset + anchorPrefix.length(), inner);
  }

  /**
   * file name of template -> compiled template lazy cache.
   */
  private final Cache<String, String> templates = CacheBuilder.newBuilder().build(
      new CacheLoader<String, String>() {

    @Override
    public String load(@Nullable String template) throws Exception {
          return loadTemplate(template);
    }
      });

  private final Provider<ServletContext> servletContext;

  @Inject
  public Templates(Provider<ServletContext> servletContext) {
    this.servletContext = servletContext;
  }

  private String loadTemplate(String template) {
    InputStream is = openResource(template);
    Preconditions.checkArgument(is != null, "Could not find template named: " + template);
    try {
      return convertStreamToString(is);
    } catch (IOException e) {
      log.warning(e.toString());
    } finally {
      try { is.close(); } catch (Exception ex) {}
    }
    return "";
  }

  /**
   * Opens a packaged resource from the file system.
   *
   * @param file The name of the file/resource to open.
   * @return An {@linkplain InputStream} to the named file, if found
   */
  public InputStream openResource(String file) {
    InputStream stream =
        PRODUCTION_MODE ? Templates.class.getResourceAsStream(file) : servletContext.get()
            .getResourceAsStream("/templates/" + file);

    // load + compile templates on-demand
    if (null == stream) {
      log.info("Could not find resource named: " + file);
    }
    return stream;
  }

  /**
   * Loads templates if necessary.
   *
   * @param template Name of the template file. example: "blip.html.fragment"
   * @param context an object to process against
   * @return the processed, filled-in template.
   */
  public String process(String template, Object[] context) {
    // Reload template each time for development mode.
    String pattern = PRODUCTION_MODE ? templates.getIfPresent(template) : loadTemplate(template);
    return MessageFormat.format(pattern, context);
  }

  public static void main(String[] args) {
    Templates templates = new Templates(null);
    String out = templates.process(BLIP_TEMPLATE, new String[] {"\'0\'", "\'1\'", "2", "3", "4"});
    System.out.println(out);
  }
}
