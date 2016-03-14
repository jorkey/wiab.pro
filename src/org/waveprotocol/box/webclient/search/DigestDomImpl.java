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

package org.waveprotocol.box.webclient.search;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;

import org.waveprotocol.box.webclient.search.i18n.DigestDomMessages;
import org.waveprotocol.wave.client.account.Profile;
import org.waveprotocol.wave.client.common.safehtml.SafeHtml;
import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.uibuilder.BuilderHelper;

/**
 * DOM implementation of a digest view.
 *
 * @author hearnden@google.com (David Hearnden)
 */
public final class DigestDomImpl implements DigestView {

  /** Resources used by this widget. */
  interface Resources extends ClientBundle {
    /** CSS */
    @Source("mock/digest.css")
    Css css();
  }

  interface Css extends CssResource {
    String digest();
    String inner();
    String check();
    String checkBox();
    String avatars();
    String avatar();
    String text();
    String title();
    String time();
    String info();
    String unread();
    String unreadCount();
    String selected();
    String checked();
    String touched();
    String hidden();
  }

  interface Binder extends UiBinder<Element, DigestDomImpl> {
  }

  private static final DigestDomMessages messages = GWT.create(DigestDomMessages.class);

  /** HTML attribute used to hold an id unique within digest widgets. */
  static String DIGEST_ID_ATTRIBUTE = "di";

  public static final String KIND_CHECK = "check";
  public static final String KIND_DIGEST = "digest";

  private static final String MDASH = "\u2014";

  @UiField(provided = true)
  final static Css css = SearchPanelResourceLoader.getDigest().css();

  private final static Binder BINDER = GWT.create(Binder.class);
  private static int idCounter;

  private final SearchPanelWidget container;
  private final Element self;

  private String titleText;
  private String snippetText;
  private boolean selected = false;
  private boolean checked = false;
  private boolean touched = false;
  private boolean read = false;

  //Inner structure

  @UiField
  Element check;

  @UiField
  InputElement checkBox;

  @UiField
  Element avatars;

  @UiField
  Element info;

  @UiField
  Element time;

  @UiField
  Element msgs;

  @UiField
  Element text;

  @UiField
  Element title;

  @UiField
  Element snippet;

  public DigestDomImpl(SearchPanelWidget container) {
    this.container = container;
    this.self = BINDER.createAndBindUi(this);
    self.setAttribute(BuilderHelper.KIND_ATTRIBUTE, KIND_DIGEST);
    self.setAttribute(DIGEST_ID_ATTRIBUTE, "D" + idCounter);
    check.setAttribute(BuilderHelper.KIND_ATTRIBUTE, KIND_CHECK);
    check.setAttribute(DIGEST_ID_ATTRIBUTE, "D" + idCounter);
    idCounter++;
  }

  @Override
  public void remove() {
    self.removeFromParent();
    container.onDigestRemoved(this);
  }

  /** @return an id of this widget, unique within the space of digest widgets. */
  public String getId() {
    return self.getAttribute(DIGEST_ID_ATTRIBUTE);
  }

  public Element getElement() {
    return self;
  }

  /** Restores this object to a post-constructor state. */
  public void reset() {
    avatars.setInnerHTML("");
    title.setInnerText("");
    snippet.setInnerText("");
    time.setInnerText("");
    msgs.setInnerHTML("");
    setSelected(false);
    setChecked(false);
  }

  @Override
  public void setAvatars(Iterable<Profile> profiles) {
    SafeHtmlBuilder html = new SafeHtmlBuilder();
    for (Profile profile : profiles) {
      renderAvatar(html, profile);
    }
    avatars.setInnerHTML(html.toSafeHtml().asString());
  }

  @Override
  public void setText(String titleText, String snippetText) {
    this.titleText = titleText;
    this.title.setInnerText(titleText != null && !titleText.isEmpty() ? titleText : messages.empty());
    this.snippetText = snippetText;
    this.snippet.setInnerText((snippetText != null && !snippetText.isEmpty()) ?
        (MDASH + " " + snippetText) : "");    
    checkTooltip();
  }

  @Override
  public void setTimestamp(String time) {
    this.time.setInnerText(time);
  }

  @Override
  public void setMessageCounts(int unread, int total) {
    if (unread == 0) {
      msgs.setInnerHTML(renderReadMessages(total).asString());
    } else {
      msgs.setInnerHTML(renderUnreadMessages(unread, total).asString());
    }

    this.read = (unread == 0);
    checkView();
  }

  @Override
  public void setSelected(boolean selected) {
    this.selected = selected;
    checkView();
  }

  @Override
  public boolean isSelected() {
    return selected;
  }

  @Override
  public void setChecked(boolean checked) {
    this.checked = checked;
    checkBox.setChecked(checked);
    checkView();
  }

  @Override
  public boolean isChecked() {
    return checked;
  }

  @Override
  public void setTouched(boolean touched) {
    this.touched = touched;
    checkView();
  }

  @Override
  public boolean isTouched() {
    return touched;
  }

  @Override
  public void update() {
    checkView();
  }

  private void checkTooltip() {
    String tooltip = titleText;
    if (snippetText != null && !snippetText.isEmpty()) {
      tooltip += " " + MDASH + " " + snippetText;
    }
    text.setTitle(tooltip);
  }

  private void checkView() {
    self.setClassName(css.digest());
    avatars.setClassName(css.avatars());
    title.setClassName("");
    info.setClassName(css.info());
    time.setClassName("");

    if (selected) {
      self.addClassName(css.selected());
    } else if (touched) {
      self.addClassName(css.touched());
    } else if (checked) {
      self.addClassName(css.checked());
    }

    if (!selected) {
      title.addClassName(css.title());
      time.addClassName(css.time());
    }

    if (!read) {
      title.addClassName(css.unread());
      time.addClassName(css.unread());
    }
  }

  private SafeHtml renderUnreadMessages(int unread, int total) {
    SafeHtmlBuilder html = new SafeHtmlBuilder();
    html.appendHtmlConstant("<span class='" + css.unreadCount() + "'>");
    html.appendHtmlConstant(String.valueOf(unread));
    html.appendHtmlConstant("</span>");
    html.appendHtmlConstant(" " + messages.of(total));
    return html.toSafeHtml();
  }

  private SafeHtml renderReadMessages(int total) {
    SafeHtmlBuilder html = new SafeHtmlBuilder();
    html.appendHtmlConstant(String.valueOf(total));
    html.appendHtmlConstant(" " + messages.msgs());
    return html.toSafeHtml();
  }

  private void renderAvatar(SafeHtmlBuilder html, Profile profile) {
    // URL is trusted to be attribute safe (i.e., no ' or ")
    String name = profile.getFullName();
    html.appendHtmlConstant("<img class='" + css.avatar() + "' src='");
    html.appendHtmlConstant(profile.getImageUrl());
    html.appendHtmlConstant("' alt='");
    html.appendEscaped(name);
    html.appendHtmlConstant("' title='");
    html.appendEscaped(name);
    html.appendHtmlConstant("'>");
  }
}
