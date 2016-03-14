/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.box.server.rpc.render.view.builder;

import com.google.common.annotations.VisibleForTesting;
import org.waveprotocol.box.server.rpc.render.common.safehtml.SafeHtmlBuilder;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.close;
import static org.waveprotocol.box.server.rpc.render.uibuilder.OutputHelper.open;
import org.waveprotocol.box.server.rpc.render.uibuilder.UiBuilder;
import org.waveprotocol.box.server.rpc.render.view.IntrinsicTagView;
import org.waveprotocol.box.server.rpc.render.view.TypeCodes;
import org.waveprotocol.box.server.rpc.render.view.View.Type;
import org.waveprotocol.box.server.rpc.render.view.builder.TagsViewBuilder.Css;

/**
 * UiBuilder for a tag.
 *
 */
public final class TagViewBuilder implements IntrinsicTagView, UiBuilder {

  private String id;
  private String name;
  private final Css css;

  @VisibleForTesting
  TagViewBuilder(String id, Css css) {
    this.id = id;
    this.css = css;
  }

  public static TagViewBuilder create(WavePanelResources resources, String id) {
    return new TagViewBuilder(id, resources.getTags().css());
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    open(output, id, css.tag(), TypeCodes.kind(Type.TAG));
    {
      output.appendEscaped(name);
    }  
    close(output);
  }
}