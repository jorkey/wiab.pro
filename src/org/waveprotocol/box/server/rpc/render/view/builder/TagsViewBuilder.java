/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.box.server.rpc.render.view.builder;

import com.google.common.annotations.VisibleForTesting;
import com.google.gwt.core.client.GWT;
import org.waveprotocol.box.server.rpc.render.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.box.server.rpc.render.uibuilder.BuilderHelper.Component;
import org.waveprotocol.box.server.rpc.render.uibuilder.HtmlClosureCollection;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.appendSpan;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.appendSpanWith;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.close;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.closeSpan;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.open;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.openSpan;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.openSpanWith;
import org.waveprotocol.box.server.rpc.render.uibuilder.UiBuilder;
import org.waveprotocol.box.server.rpc.render.view.TypeCodes;
import org.waveprotocol.box.server.rpc.render.view.View.Type;
import org.waveprotocol.box.server.rpc.render.view.builder.WavePanelResources.WaveImageResource;
import org.waveprotocol.wave.client.common.util.KeyComboContext;
import org.waveprotocol.wave.client.common.util.KeyComboManager;
import org.waveprotocol.wave.client.common.util.KeyComboTask;
import org.waveprotocol.wave.client.wavepanel.impl.edit.i18n.TagMessages;

/**
 * UiBuilder for a collection of tags.
 *
 */
public final class TagsViewBuilder implements UiBuilder {

  /** Height of the regular (collapsed) panel. */
  final static int COLLAPSED_HEIGHT_PX = 51;

  /** Resources used by this widget. */
  public interface Resources {
    Css css();
    WaveImageResource expandButton();
    WaveImageResource collapseButton();
    WaveImageResource addButton();
    WaveImageResource deleteButton();
  }

  /** CSS for this widget. */
  public interface Css {
    String panel();
    String flow();
    String tag();
    String title();
    String toggleGroup();
    String simple();
    String extra();
    String expandButton();
    String collapseButton();
    String addButton();
    String deleteButton();
  }

  private final static TagMessages messages = GWT.create(TagMessages.class);

  /** An enum for all the components of a tags view. */
  public enum Components implements Component {
    /** Element to which tag UIs are attached. */
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

  public static String ADD_TAG_BUTTON_ID = "btnAddTag";

  private final Css css;
  private final HtmlClosureCollection tagUis;
  private final String id;

  @VisibleForTesting
  TagsViewBuilder(Css css, String id, HtmlClosureCollection tagUis) {
    this.css = css;
    this.id = id;
    this.tagUis = tagUis;
  }

  /**
   * Creates a new TagsViewBuilder.
   *
   * @param id attribute-HTML-safe encoding of the view's HTML id
   */
  public static TagsViewBuilder create(WavePanelResources resources,
      String id, HtmlClosureCollection tagUis) {
    return new TagsViewBuilder(resources.getTags().css(), id, tagUis);
  }

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    String addTagHotKey = KeyComboManager.getKeyComboHintByTask(
        KeyComboContext.WAVE, KeyComboTask.ADD_TAG);
    open(output, id, css.panel(), TypeCodes.kind(Type.TAGS));
    {
      open(output, null, css.flow(), null);
      {
        open(output, Components.CONTAINER.getDomId(id), null, null);
        {
          // Append title
          open(output, null, css.title(), null);
          {
            output.appendPlainText("Tags");
          }
          close(output);

          tagUis.outputHtml(output);

          // Overflow-mode panel.
          openSpan(output, null, css.extra(), null);
          {
            openSpanWith(output, null, css.toggleGroup(), null, "onclick=\"" + onClickJs() + "\"");
            {
              appendSpan(output, null, css.expandButton(), null);
              openSpan(output, null, null, null);
              {
                output.appendPlainText("More");
              }
              closeSpan(output);
            }
            closeSpan(output);
            appendSpanWith(output, null, css.addButton(), TypeCodes.kind(Type.ADD_TAG),
                null, messages.addTagHint(), addTagHotKey);
          }
          closeSpan(output);

          // Single-line mode panel.
          openSpan(output, null, css.simple(), null);
          {
            appendSpanWith(output, ADD_TAG_BUTTON_ID, css.addButton(), TypeCodes.kind(Type.ADD_TAG),
                null, messages.addTagHint(), addTagHotKey);
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
        + "lastChild.innerHTML=x?'" + "More" + "':'" + "Less" + "';" //
        + "firstChild.className=x?'" + css.expandButton() + "':'" + css.collapseButton() + "';" //
        + "parentNode.nextSibling.style.display=x?'':'none';" //
    ;
    // The constructed string has no double-quote characters in it, so it can be
    // double-quoted verbatim.
    assert !js.contains("\"");
    return js;
  }
}
