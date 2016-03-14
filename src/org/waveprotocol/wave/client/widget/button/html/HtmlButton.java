/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.wave.client.widget.button.html;

import com.google.gwt.uibinder.client.UiConstructor;
import org.waveprotocol.wave.client.widget.button.text.TextButton;

/**
 * Template for button with HTML label
 * @author fwnd80@gmail.com (Nikolay Liber)
 */
public class HtmlButton extends TextButton {
  @UiConstructor
  public HtmlButton(String html, TextButtonStyle style, String tooltip) {
    super("", style, tooltip);
    setHtml(html);
  } 
}
