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
 */
package org.waveprotocol.box.server.rpc.render.view.builder;

import com.google.common.annotations.VisibleForTesting;
import org.waveprotocol.box.server.rpc.render.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.box.server.rpc.render.uibuilder.BuilderHelper.Component;
import org.waveprotocol.box.server.rpc.render.uibuilder.HtmlClosureCollection;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.appendSpan;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.close;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.closeSpan;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.open;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.openSpan;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.openSpanWith;
import org.waveprotocol.box.server.rpc.render.uibuilder.UiBuilder;
import org.waveprotocol.box.server.rpc.render.view.TypeCodes;
import org.waveprotocol.box.server.rpc.render.view.View.Type;
import org.waveprotocol.box.server.rpc.render.view.builder.WavePanelResources.WaveImageResource;

/**
 * UiBuilder for a collection of participants.
 *
 */
public final class ParticipantsViewBuilder implements UiBuilder {

  /** Height of the regular (collapsed) panel. */
  public final static int COLLAPSED_HEIGHT_PX = 51;

  /** Resources used by this widget. */
  public interface Resources {
    /** CSS */
    Css css();
    WaveImageResource expandButton();
    WaveImageResource collapseButton();
    WaveImageResource addButton();
  }

  /** CSS for this widget. */
  public interface Css {
    String participant();
    String panel();
    String flow();
    String extra();
    String toggleGroup();
    String simple();
    String expandButton();
    String collapseButton();
    String addButton();
  }

  /** An enum for all the components of a participants view. */
  public enum Components implements Component {
    /** Element to which participant UIs are attached. */
    CONTAINER("C"),
    /** Add button. */
    ADD("A");
    private final String postfix;

    Components(String postfix) {
      this.postfix = postfix;
    }

    @Override
    public String getDomId(String baseId) {
      return baseId + postfix;
    }
  }

  private final Css css;
  private final HtmlClosureCollection participantUis;
  private final String id;
  @VisibleForTesting
  ParticipantsViewBuilder(Css css, String id, HtmlClosureCollection participantUis) {
    this.css = css;
    this.id = id;
    this.participantUis = participantUis;
  }

  /**
   * Creates a new ParticipantsViewBuilder.
   * 
   * @param id attribute-HTML-safe encoding of the view's HTML id
   */
  public static ParticipantsViewBuilder create(WavePanelResources resources, String id,
      HtmlClosureCollection participantUis) {
    return new ParticipantsViewBuilder(resources.getParticipants().css(), id, participantUis);
  }

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    open(output, id, css.panel(), TypeCodes.kind(Type.PARTICIPANTS));
    {
      open(output, null, css.flow(), null);
      {
        open(output, Components.CONTAINER.getDomId(id), null, null);
        {
          participantUis.outputHtml(output);

          // Overflow-mode panel.
          openSpan(output, null, css.extra(), null);
          {
            openSpanWith(output, null, css.toggleGroup(), null,
                "onclick=\"" + onClickJs() + "\"");
            {
              appendSpan(output, null, css.expandButton(), null);
              openSpan(output, null, null, null);
              {
                output.appendPlainText("more");
              }
              closeSpan(output);
            }
            closeSpan(output);
            appendSpan(output, null, css.addButton(),
                TypeCodes.kind(Type.ADD_PARTICIPANT));
          }
          closeSpan(output);

          // Single-line mode panel.
          openSpan(output, null, css.simple(), null);
          {
            appendSpan(output, null, css.addButton(),
                TypeCodes.kind(Type.ADD_PARTICIPANT));
          }
          closeSpan(output);
        }
        close(output);
      }
      close(output);
    }
    close(output);
  }

  // Rather than install a regular handler, this is an experiment at injecting
  // JS directly, so that this piece of UI is functional from the initial
  // rendering, without needing to wait for any scripts to load (like the GWT
  // app).
  /** @return a JS click handler for toggling expanded and collapsed modes. */
  private String onClickJs() {
    String js = "" //
        + "var p=document.getElementById('" + id + "');" //
        + "var x=p.getAttribute('s')=='e';" //
        + "var l=this.lastChild;" //
        + "p.style.height=x?'':'auto';" //
        + "p.setAttribute('s',x?'':'e');" //
        + "lastChild.innerHTML=x?'more':'less';" //
        + "firstChild.className=x?'" + css.expandButton() + "':'" + css.collapseButton() + "';" //
        + "parentNode.nextSibling.style.display=x?'':'none';" //
    ;
    // The constructed string has no double-quote characters in it, so it can be
    // double-quoted verbatim.
    assert !js.contains("\"");
    return js;
  }
}
