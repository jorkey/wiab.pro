/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.wave.client.widget.button.html;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;

/**
 * Template for text button with hotkey hint shown in label
 * 
 * @author fwnd80@gmail.com (Nikolay Liber)
 */
public class HtmlButtonWithHotkeyHint extends HtmlButton {
  public interface Resources extends ClientBundle {
    @Source("HtmlButtonWithHotkeyHint.css")
    Css css();
    
    interface Css extends CssResource {
      String regularLabel();
      String boldLabel();
      String hotkey();
    }
  }
  
  static final Resources res = GWT.create(Resources.class);
  
  {
    StyleInjector.inject(res.css().getText());
  }

  public HtmlButtonWithHotkeyHint(String label, boolean boldLabel, String hotKey, TextButtonStyle style, String tooltip) {
    super("<span class=\"" + (boldLabel ? res.css().boldLabel() : res.css().regularLabel()) + "\">" +
            label + "</span> <span class=\"" + res.css().hotkey() + "\">[" + hotKey + "]</span>", style, tooltip);
  }
}
