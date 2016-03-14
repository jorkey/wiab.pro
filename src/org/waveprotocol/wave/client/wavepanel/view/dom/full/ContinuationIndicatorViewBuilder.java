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

package org.waveprotocol.wave.client.wavepanel.view.dom.full;

import com.google.common.annotations.VisibleForTesting;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.uibuilder.BuilderHelper.Component;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.append;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.appendWith;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.close;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.open;
import static org.waveprotocol.wave.client.uibuilder.OutputHelper.openWith;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicContinuationIndicatorView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.i18n.BlipMessages;

/**
 * This class is the view builder for the inline continuation indicator.
 */
public final class ContinuationIndicatorViewBuilder implements UiBuilder,
    IntrinsicContinuationIndicatorView {
 
  public interface Resources extends ClientBundle {
    
    @Source("ContinuationIndicator.css")
    Css css();
    
    @Source("continuation_add_icon.png")
    ImageResource continuationAddIcon();
    
    @Source("continuation_reply_icon.png")
    ImageResource continuationReplyIcon();    
  }

  public interface Css extends CssResource {    
    String indicator();
    String indicatorVisible();
    String indicatorInvisible();
    String button();
    String buttonVisible();
    String buttonInvisible();
    String addIcon();
    String replyIcon();    
    String avatar();
    String text();
    String line();
    String lineInvisible();
  }

  /** An enum for all the components of a reply box view. */
  public enum Components implements Component {
    
    /** The avatar element. */
    AVATAR("A");

    private final String postfix;

    Components(String postfix) {
      this.postfix = postfix;
    }

    @Override
    public String getDomId(String baseId) {
      return baseId + postfix;
    }
  }  

  public static String CONTINUATION_BAR_ID = "cntBar";
  public static String CONTINUATION_ADD_BUTTON_ID = "cntAdd";
  public static String CONTINUATION_REPLY_BUTTON_ID = "cntRpl";
  public static String CONTINUATION_LINE_ID = "cntLine";  
  
  private final Css css;
  private final BlipMessages messages;

  //
  // Intrinsic state.
  //

  /**
   * Creates a new reply box view builder with the given id.
   *
   * @param id unique id for this builder, it must only contains alphanumeric
   *        characters
   */
  public static ContinuationIndicatorViewBuilder create() {
    return new ContinuationIndicatorViewBuilder(
        WavePanelResourceLoader.getContinuationIndicator().css(),
        WavePanelResourceLoader.getBlipMessages());
  }

  @VisibleForTesting
  ContinuationIndicatorViewBuilder(Css css, BlipMessages messages) {
    // must not contain ', it is especially troublesome because it cause
    // security issues.
    this.css = css;
    this.messages = messages;
  }

  //
  // DomImpl nature.
  //

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    openWith(output, CONTINUATION_BAR_ID, css.indicator() + " " + css.indicatorInvisible(),
        TypeCodes.kind(Type.CONTINUATION_BAR), null);
    {
      appendButton(output, CONTINUATION_ADD_BUTTON_ID, messages.add(), css.addIcon());
      appendButton(output, CONTINUATION_REPLY_BUTTON_ID, messages.reply(), css.replyIcon());
      appendWith(output, CONTINUATION_LINE_ID, css.line(), TypeCodes.kind(Type.CONTINUATION_LINE),
          null);
    }
    close(output);
  }
  
  @Override
  public String getId() {
    return CONTINUATION_BAR_ID;
  }

  @Override
  public void enable() {
    setEnabled(true);
  }

  @Override
  public void disable() {
    setEnabled(false);
  }
  
  private void setEnabled( boolean enabled ) {
    // do nothing here
  }
  
  private void appendButton(SafeHtmlBuilder output, String id, String text, String icon) {
    openWith(output, id, css.button(), TypeCodes.kind(Type.CONTINUATION_BUTTON), null);                    
    {
      append(output, null, icon, null); 
      open(output, null, css.text(), TypeCodes.kind(Type.CONTINUATION_TEXT));
      {
        output.appendEscaped(text);
      }
      close(output);
    }
    close(output);    
  }
}