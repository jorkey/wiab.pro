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


import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.wave.api.Blip;
import com.google.wave.api.BlipThread;
import com.google.wave.api.Element;
import com.google.wave.api.ParticipantProfile;
import com.google.wave.api.Wavelet;

import org.waveprotocol.box.server.rpc.render.ClientAction;
import org.waveprotocol.box.server.rpc.render.web.text.ContentRenderer;
import org.waveprotocol.box.server.rpc.render.web.text.Markup;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Does the actual conversion of a wavelet/blipdata tree into html, using the
 * conversation-thread model, optionally falling back to the blip-hierarchy
 * model, if so configured.
 * 
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
@Singleton
public class ThreadedWaveRenderer implements SplashWaveRenderer {
  private static final Set<String> HIDDEN_PARTICIPANTS = ImmutableSet.of("public");

  private final boolean isReadOnly;
  private final int charsPerPage;
  private final ContentRenderer renderer;
  private final ProfileStore profileStore;
  private final Templates templates;

  // Ugly, but we do this to avoid polluting all the rendering methods. =(
  private final ThreadLocal<PageTracker> currentPage = new ThreadLocal<PageTracker>();

  @Inject
  public ThreadedWaveRenderer(Templates templates, ContentRenderer renderer,
      ProfileStore profileStore) {
    this.templates = templates;
    this.profileStore = profileStore;
    this.isReadOnly = true;
    this.charsPerPage = 8000;
    this.renderer = renderer;
  }

  private class PageTracker {
    // Whether or not we're trying to deliver the first page.
    private final boolean firstPage;
    private int counter;
    private final List<Integer> markers = Lists.newArrayList();

    // The wavelet we're trying to render in this page.
    private final Wavelet wavelet;


    public PageTracker(int page, Wavelet wavelet) {
      this.wavelet = wavelet;
      firstPage = (page == 0);
    }

    /**
     * Returns true if a page boundary was crossed.
     */
    public boolean track(StringBuilder builder) {
      int length = builder.length();
      if (length - counter >= charsPerPage) {
        markers.add(length);
        counter = length;

        // If this is the first page, short circuit the rendering
        // process so we can deliver the page faster.
        if (firstPage) {
          return true;
        }
      }
      return false;
    }

    public boolean hasPages() {
      return !markers.isEmpty();
    }

    public int marker(int page) {
      return markers.get(page);
    }
  }

  /**
   * Renders an inline reply thread at the correct offset location inside a
   * blip.
   * 
   * @param element The element representing the position of the offset inline
   *        reply
   * @param index
   * @param builder The current HTML content StringBuilder of the wave so far
   */
  @Override
  public void renderInlineReply(Element element, int index, StringBuilder builder) {

    PageTracker pageTracker = currentPage.get();
    BlipThread inlineReplyThread = pageTracker.wavelet.getThread(element.getProperty("id"));

    // inlineReplyThread can be null if a sub-thread was completely deleted.
    // There's still
    // an entry left behind in the conversation that points to nothing.
    if (inlineReplyThread != null && !inlineReplyThread.getBlipIds().isEmpty()) {
      builder.append(" <span class=\"inline-reply\" ir-id=\"");
      builder.append(inlineReplyThread.getId());
      builder
          .append("\"><span class=\"count\" title=\"Click to expand inline replies\"><span class=\"count-inner\">");
      builder.append(sizeOfThreadTree(inlineReplyThread));
      builder.append("</span><span class=\"pointer\"></span></span> ");

      builder.append("</span>"); // Close inline-reply
    }
  }

  // TODO(dhanji): This is expensive, see if we can precompute branch size
  // when constructing the thread tree.
  private static int sizeOfThreadTree(BlipThread inlineReplyThread) {
    List<Blip> blips = inlineReplyThread.getBlips();
    int size = blips.size();

    for (Blip blip : blips) {
      for (BlipThread thread : blip.getReplyThreads()) {
        size += thread.getBlipIds().size();
      }
    }

    return size;
  }

  /**
   * 
   * @param wavelet A wavelet to render as a single html blob.
   * @param page The page number to send back. Use this to implement paging, if
   *        you specify page 1, the client action will only contain the second
   *        page as computed during the current render.
   * @return the client action.
   */
  @Override
  public ClientAction render(Wavelet wavelet, int page) {
    Preconditions.checkState(null == currentPage.get(),
        "A page render is already in progress (this is an algorithm bug)");
    StringBuilder builder = new StringBuilder();
    Blip rootBlip = wavelet.getRootBlip();

    // The pagetracker tracks every page worth of HTML rendered.
    PageTracker pageTracker = new PageTracker(page, wavelet);
    currentPage.set(pageTracker);
    try {
      return renderInternal(wavelet, page, builder, rootBlip, pageTracker);
    } finally {
      currentPage.remove();
    }
  }

  private ClientAction renderInternal(Wavelet wavelet, int page, StringBuilder builder,
      Blip rootBlip, PageTracker pageTracker) {
    boolean stopRender = renderThreads(wavelet.getRootThread(), builder, pageTracker);
    String html = builder.toString();
    if (page != ALL_PAGES && (stopRender || pageTracker.hasPages())) {
      if (page == 0) {
        builder.append("<img id=\"wave-loading\" src=\"images/wave-loading.gif\">");
        html = builder.toString();
      } else {
        // If this is a request for the rest of the wave, start from end of page
        // 0 and get
        // the rest.
        int start = pageTracker.marker(0);
        html = builder.substring(start);
      }
    }
    return new ClientAction("update-wave").version(wavelet.getLastModifiedTime()).html(html);
  }

  /**
   * This method renders the blip thread hierarchy using the new conversation
   * structure.
   * 
   * @return true if we should stop rendering because a page boundary was
   *         reached.
   */
  boolean renderThreads(BlipThread thread, StringBuilder outer, PageTracker pageTracker) {
    List<Blip> blipsInThread = thread.getBlips();
    
    StringBuilder inner = new StringBuilder();
    inner.append("<div id='threadContents' class='chrome expanded'>");
    for (Blip blip : blipsInThread) {
      if (renderBlip(blip, inner, "", pageTracker)) {
        return true;
      }

      // If this blip has any reply threads, they should be rendered indented.
      if (blip.getInlineReplyThreads().size() > 0) {
        int totalShift = 0;
        for (BlipThread childThread : blip.getInlineReplyThreads()) {
          StringBuilder inline = new StringBuilder();
          try {
            inline.append(Templates.convertStreamToString(templates.openResource(Templates.ANCHOR_TEMPLATE)));
          } catch (IOException e) {
            e.printStackTrace();
          }
          boolean isDone = renderThreads(childThread, inline, pageTracker);
          inline.append("</div>");
          if (isDone) {
            inner.append("</div>");
            outer.append(inner);
            return true;
          }
          totalShift += inline.length();
        }
        inner.append("</div>");
        outer.append(inner);
      } else {
        inner.append("</div>");
        outer.append(inner);
      }
    }
    return false;
  }



  /**
   * Renders the header of a wavelet, including participants.
   * 
   * @param profiles A list of profiles for each participant in the wave, in
   *        correct order.
   * @return A {@code ClientAction} that inserts the rendered participant list
   *         in the header portion of the DOM.
   */
  @Override
  public ClientAction renderHeader(List<ParticipantProfile> profiles) {
    int max = Math.min(10, profiles.size());

    List<ParticipantProfile> renderedParticipants = Lists.newArrayListWithExpectedSize(max);
    for (ParticipantProfile p : profiles) {
      // TODO: For some reason the address is an empty string, so
      // we use name here instead.
      if (HIDDEN_PARTICIPANTS.contains(p.getName())) {
        continue;
      }
      if (renderedParticipants.size() >= max) {
        break;
      }
      renderedParticipants.add(p);
    }
    String el = "<div>" + Joiner.on(",").join(renderedParticipants) + "</div>";
    return new ClientAction("update-header").html(el);
  }

  private boolean renderBlip(Blip blip, StringBuilder builder, String title, PageTracker pageTracker) {
    builder.append(toHtml(blip));

    // At the end of each blip, see if we've passed a page worth of content.
    return pageTracker.track(builder);
  }

  /**
   * Renders the content of a blip as html. If a title is specified, renders
   * that specially as the root blip.
   * 
   * @param blipData The blip whose content you want to render
   * @param title The title string if this is a root blip or null
   * @return Rendered HTML string with markup
   */
  @Override
  public String toHtml(Blip blipData) {
    List<String> contributors = blipData.getContributors();
    // TODO (Yuri Z.) Need to send also author image and name etc..
//    List<ParticipantProfile> authors = loadProfiles(contributors);

    Map<String, Object> blip = Maps.newHashMap();
    blip.put("id", blipData.getBlipId());

    String authorIds = Joiner.on(",").join(contributors);

    blip.put("authorString", authorIds);
    blip.put("time", Markup.formatDateTime(blipData.getLastModifiedTime()));
    blip.put("content", renderContent(blipData));

    return renderBlipTemplate(blip);
  }

  String renderBlipTemplate(Map<String, Object> blip) {
    return templates.process(
        Templates.BLIP_TEMPLATE,
        new String[] {String.valueOf("\'" + blip.get("id") + "\'"), "\'/templates/unknown.png\'", "\'unknown\'", String.valueOf(blip.get("time")),
            String.valueOf(blip.get("authorString")), String.valueOf(blip.get("content"))});
  }

  List<ParticipantProfile> loadProfiles(Collection<String> participants) {
    ImmutableList.Builder<ParticipantProfile> result = ImmutableList.builder();
    Map<String, ParticipantProfile> profiles = profileStore.getProfiles(participants);
    for (String address : participants) {
      result.add(profiles.get(address));
    }
    return result.build();
  }

  @Override
  public ClientAction renderNotFound() {
    // TODO: This should be an alert message once that system is
    // implemented.
    return new ClientAction("update-wave").html("NOT FOUND");
  }

  private String renderContent(Blip blip) {
    return renderer.renderHtml(blip.getContent(), blip.getAnnotations(), blip.getElements(),
        blip.getContributors());
  }
}
