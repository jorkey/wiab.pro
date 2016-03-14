/**
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.waveprotocol.box.server.rpc.render.view.builder;

import static org.waveprotocol.box.server.rpc.render.uibuilder.BuilderHelper.nonNull;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.appendSpan;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.close;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.closeSpan;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.open;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.openSpan;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.openWith;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import org.waveprotocol.box.server.rpc.render.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.box.server.rpc.render.uibuilder.BuilderHelper.Component;
import org.waveprotocol.box.server.rpc.render.uibuilder.HtmlClosure;
import org.waveprotocol.box.server.rpc.render.uibuilder.UiBuilder;
import org.waveprotocol.box.server.rpc.render.view.TypeCodes;
import org.waveprotocol.box.server.rpc.render.view.View.Type;
import org.waveprotocol.box.server.rpc.render.view.builder.WavePanelResources.WaveImageResource;

/**
 * Holds resources and view-building logic for a collapsible callout box.
 *
 */
public final class CollapsibleBuilder implements UiBuilder {
  /** Name of the attribute that stores collapsed state (must be HTML safe). */
  public final static String COLLAPSED_ATTRIBUTE = "c";

  /** {@link #COLLAPSED_ATTRIBUTE}'s value for collapsed (must be HTML safe). */
  public final static String COLLAPSED_VALUE = "c";

  /** Name of the attribute that stores the total blips. */
  public final static String TOTAL_BLIPS_ATTRIBUTE = "t";

  /** Name of the attribute that stores the number of unread blips. */
  public final static String UNREAD_BLIPS_ATTRIBUTE = "u";

  /** Resources used by this widget. */
  public interface Resources {
    Css css();
    WaveImageResource callout();

    WaveImageResource expandedRead();

    WaveImageResource expandedUnread();

    WaveImageResource collapsedRead();

    WaveImageResource collapsedUnread();
  }

  /** CSS class names for this widget. */
  public interface Css {
    
    String collapsible();

    String toggle();

    String arrow();

    String count();

    String collapsed();

    String expanded();

    String read();

    String unread();

    String chrome();

    String dropContainer();

    String drop();
  }

  public enum Components implements Component {
    /** The toggle container. */
    TOGGLE("T"),
    /** The arrow icon */
    ARROW("A"),
    /** The blip number container. */
    COUNT("N"),
    /** The total number of blips in the subtree. */
    COUNT_TOTAL("NT"),
    /** The number of unread blips in the subtree. */
    COUNT_UNREAD("NU"),
    /** The chrome element, also the container for contents. */
    CHROME("C"),
    /** The container of the callout triangle. */
    DROP_CONTAINER("D"), ;

    private final String suffix;

    Components(String suffix) {
      this.suffix = suffix;
    }

    @Override
    public String getDomId(String baseId) {
      return baseId + suffix;
    }

    /** @return the base id of a DOM id for this component. */
    public String getBaseId(String domId) {
      Preconditions.checkArgument(domId.endsWith(suffix), "Not a toggle id: ", domId);
      return domId.substring(0, domId.length() - suffix.length());
    }
  }

  /**
   * A unique id for this builder.
   */
  private final String id;
  private final Css css;

  //
  // Intrinsic state.
  //

  private boolean collapsed;
  private int totalBlipCount;
  private int unreadBlipCount;

  //
  // Structural components.
  //

  private final HtmlClosure content;
  private final String kind;

  /**
   */
  public static CollapsibleBuilder create(WavePanelResources resources, String id,
      String kind, HtmlClosure contents) {
    // must not contain ', it is especially troublesome because it cause
    // security issues.
    Preconditions.checkArgument(!id.contains("\'"));
    return new CollapsibleBuilder(id, nonNull(contents), resources.getCollapsible().css(), kind);
  }

  @VisibleForTesting
  CollapsibleBuilder(String id, HtmlClosure content, Css css, String kind) {
    this.id = id;
    this.content = content;
    this.css = css;
    this.kind = kind;
  }

  public void setCollapsed(boolean collapsed) {
    this.collapsed = collapsed;
  }

  public boolean isCollapsed() {
    return collapsed;
  }

  public void setTotalBlipCount( int totalBlipCount ) {
    this.totalBlipCount = totalBlipCount;
  }

  public void setUnreadBlipCount( int unreadBlipCount ) {
    this.unreadBlipCount = unreadBlipCount;
  }

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    // All container elements must be block-level HTML elements (styles do not
    // make a difference) in order to validate with contents that are block level.
    //
    // <div thread>
    //   <span toggle expanded|collapsed unread|read >
    //     <span arrow />
    //     <span count>
    //       <span>10</span>
    //       <span>(2)</span>
    //       <span dropContainer>
    //         <span drop/>
    //       </span>
    //     </span>
    //   </span>
    //   <div chrome expanded|collapsed>
    // ...
    //   </div>
    // </div>
    String readStateCss = " " + ((unreadBlipCount > 0) ? css.unread() : css.read());
    String collapsedStateCss = " " + (collapsed ? css.collapsed() : css.expanded());
    String unselectable = false ? "unselectable='on'" : null;
    String extra = " " + (collapsed ? COLLAPSED_ATTRIBUTE + "='" + COLLAPSED_VALUE + "'" : "") +
        " " + TOTAL_BLIPS_ATTRIBUTE + "='" + totalBlipCount + "'" +
        " " + UNREAD_BLIPS_ATTRIBUTE + "='" + unreadBlipCount + "'";

    openWith(output, id, css.collapsible(), kind, extra);
    {
      open(output, Components.TOGGLE.getDomId(id), css.toggle() + readStateCss +
          collapsedStateCss, TypeCodes.kind(Type.TOGGLE));
      {
        appendSpan(output, Components.ARROW.getDomId(id), css.arrow(), null);
        openSpan(output, Components.COUNT.getDomId(id), css.count(), null);
        {
//          openSpan(output, Components.COUNT_TOTAL.getDomId(id), null, null);
//          output.append(totalBlipCount);
//          closeSpan(output);

          String unreadExtra = unreadBlipCount <= 0 ? " style='display: none;'" : "";
//          openSpanWith(output, Components.COUNT_UNREAD.getDomId(id), null, null, unreadExtra);
//          output.appendEscaped("(" + unreadBlipCount + ")");
//          closeSpan(output);

          openSpan(output, Components.DROP_CONTAINER.getDomId(id), css.dropContainer() +
              collapsedStateCss, null);
          appendSpan(output, null, css.drop(), null);
          closeSpan(output);
        }
        closeSpan(output);
      }
      close(output);
      openWith(output, Components.CHROME.getDomId(id), css.chrome() + collapsedStateCss, null,
          unselectable);
      {
        content.outputHtml(output);
      }
      close(output);
    }
    close(output);
  }

}
