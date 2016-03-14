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

import com.google.inject.Inject;
import com.google.wave.api.Annotation;
import com.google.wave.api.Annotations;
import com.google.wave.api.Element;
import com.google.wave.api.ElementType;
import com.google.wave.api.Gadget;

import org.waveprotocol.box.server.rpc.render.web.template.Templates;
import org.waveprotocol.wave.model.conversation.AnnotationConstants;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A utility class that converts blip content into html.
 *
 * @author David Byttow
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class ContentRenderer {

  /**
   * Represents a marker in content that is either the start or end of
   * an annotation or a single element.
   */
  static class Marker implements Comparable<Marker> {
    static Marker fromAnnotation(Annotation annotation, int index, boolean isEnd) {
      return new Marker(annotation, index, isEnd);
    }

    static Marker fromElement(Element element, int index) {
      return new Marker(element, index);
    }

    private final int index;
    private final Annotation annotation;
    private final Element element;
    boolean isEnd;

    private Marker(Annotation annotation, int index, boolean isEnd) {
      this.annotation = annotation;
      this.element = null;
      this.index = index;
      this.isEnd = isEnd;
    }

    private Marker(Element element, int index) {
      this.element = element;
      this.annotation = null;
      this.index = index;
    }

    boolean isElement() {
      return this.element != null;
    }

    boolean isAnnotation() {
      return this.annotation != null;
    }

    @Override
    public int compareTo(Marker that) {
      int value = Integer.signum(this.index - that.index);
      if (value == 0) {
        if (this.isElement() != that.isElement()) {
          // At boundaries, annotations should wrap elements.
          final Marker annotationMarker = this.isAnnotation() ? this : that;
          return annotationMarker.isEnd ? -1 : 1;
        }
      }
      return value;
    }

    public void emit(StringBuilder builder) {
      if (annotation.getName().startsWith(AnnotationConstants.LINK_PREFIX)) {
        emitHrefAnnotation(builder);
      } else if (annotation.getName().startsWith(AnnotationConstants.STYLE_PREFIX)) {
        emitStyleAnnotation(builder);
      }
    }

    private void emitStyleAnnotation(StringBuilder builder) {
      if (isEnd) {
        builder.append("</span>");
      } else {
        // Transform name into dash-separated css property rather than lower camel case.
        String name = Markup.sanitize(annotation.getName());
        String value = Markup.sanitize(annotation.getValue());

        // Title annotations are translated as bold.
        name = name.substring(annotation.getName().indexOf("/") + 1);
        name = Markup.toDashedStyle(name);

        builder.append("<span style='");
        builder.append(name);
        builder.append(':');
        builder.append(value);
        builder.append("'>");
      }
    }

    private void emitHrefAnnotation(StringBuilder builder) {
      if (isEnd) {
        builder.append("</a>");
      } else {
        String value = Markup.sanitize(annotation.getValue());
        builder.append("<a href='");
        builder.append(value);
        builder.append("' target='_blank'>");
      }
    }
  }

  private final GadgetRenderer gadgetRenderer;

  @Inject
  public ContentRenderer(GadgetRenderer gadgetRenderer) {
    this.gadgetRenderer = gadgetRenderer;
  }

  public ContentRenderer() {
    this.gadgetRenderer = new GadgetRenderer() {

      @Override
      public void render(Gadget gadget, List<String> contributors, StringBuilder builder) {
        builder.append("<div>Gadget: ").append(gadget.getUrl()).append("</div>");
      }
    };
  }

  /**
   * Takes content and applies style and formatting to it based on its
   * annotations and elements.
   */
  public String renderHtml(String content, Annotations annotations,
      SortedMap<Integer, Element> elements, List<String> contributors) {
    StringBuilder builder = new StringBuilder();

    // NOTE(dhanji): This step is enormously important!
    char[] raw = content.toCharArray();

    SortedSet<Marker> markers = new TreeSet<Marker>();

    // First add annotations sorted by range.
    for (Annotation annotation : annotations.asList()) {
      // Ignore anything but style or title annotations.
      String annotationName = annotation.getName();
      if (annotationName.startsWith(AnnotationConstants.STYLE_PREFIX)) {
        markers.add(Marker.fromAnnotation(annotation, annotation.getRange().getStart(), false));
        markers.add(Marker.fromAnnotation(annotation, annotation.getRange().getEnd(), true));
      } else if(annotationName.startsWith(AnnotationConstants.LINK_PREFIX)) {
        markers.add(Marker.fromAnnotation(annotation, annotation.getRange().getStart(), false));
        markers.add(Marker.fromAnnotation(annotation, annotation.getRange().getEnd(), true));
      }
    }

    // Now add elements sorted by index.
    for (Map.Entry<Integer, Element> entry : elements.entrySet()) {
      markers.add(Marker.fromElement(entry.getValue(), entry.getKey()));
    }

    int cursor = 0;
    for (Marker marker : markers) {
      if (marker.index > cursor) {
        int to = Math.min(raw.length, marker.index);
        builder.append(Markup.sanitize(new String(raw, cursor, to - cursor)));
      }

      cursor = marker.index;

      if (marker.isElement()) {
        renderElement(marker.element, marker.index, contributors, builder);
      } else {
        marker.emit(builder);
      }
    }

    // add any tail bits
    if (cursor < raw.length - 1) {
      builder.append(Markup.sanitize(new String(raw, cursor, raw.length - cursor)));
    }

    // Replace empty paragraphs. (TODO expensive and silly)
    return builder.toString().replace("<p>\n</p>", "<p><br/></p>");
  }

  private void renderElement(Element element, int index, List<String> contributors,
      StringBuilder builder) {
    ElementType type = element.getType();
    switch (type) {
      case LINE:
        // TODO(anthonybaxter): need to handle <line t="li"> and <line t="li" i="3">
        // TODO(anthonybaxter): also handle H1 &c
        // Special case: If this is the first LINE element at position 0,
        // ignore it because we've already appended the first <p> tag.
        if (index > 0) {
          builder.append("<br/>");
        }
        break;
      case ATTACHMENT:
        /* By Andrew Kaplanov
        Attachment attachment = (Attachment) element;
        String url = Markup.sanitizeAndEncode(attachment.getAttachmentUrl());
        String caption = Markup.sanitize(element.getProperty("caption"));
        if (caption == null) {
          caption = "";
        }
        // TODO: Revisit this questionable html.
        builder.append("<table class=\"attachment-element\"><tr><td>")
            .append("<a class=\"lightbox\" title=\"")
            .append(caption)
            .append("\" href=\"")
            .append(url)
            .append("\"><img src=\"")
            .append(url)
            .append("\"/></a></td></tr></td></tr><tr><td><div class=\"caption\">")
            .append(caption)
            .append("</div></td></tr></table>");
        */
        break;
      case IMAGE:
        String imageUrl = element.getProperty("url");

        /* By Andrew Kaplanov
        if (Markup.isTrustedImageUrl(imageUrl)) {
          imageUrl = Markup.sanitizeAndEncode(imageUrl);*/
          builder.append("<img src=\"").append(imageUrl).append("\"/>");
        /*}*/
        break;
      case GADGET:
        gadgetRenderer.render((Gadget) element, contributors, builder);
        break;
      case INLINE_BLIP:
        String id = element.getProperty("id");
        builder.append(Templates.makeAnchorTag(id) );
        break;
      default:
        // Ignore all others.
    }
  }
}
