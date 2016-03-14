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

package org.waveprotocol.box.webclient.widget.update;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Panel;

/**
 * GWT implementation of the UI for an update indicator.
 */
public final class UpdateIndicatorWidget extends Composite {
  public interface Listener {
    void refresh();
  }

  interface Binder extends UiBinder<HTMLPanel, UpdateIndicatorWidget> {
  }

  interface Style extends CssResource {

  }

  private static final Binder BINDER = GWT.create(Binder.class);

  private static String REFRESH_ATTRIBUTE = "ra";
  private static String REFRESH_ATTRIBUTE_VAL = "rv";

  private static boolean created;

  @UiField
  Style style;

  @UiField
  HTMLPanel self;

  @UiField
  Element refresh;

  private final Listener listener;

  private UpdateIndicatorWidget(final Listener listener) {
    this.listener = listener;
    initWidget(BINDER.createAndBindUi(this));
    refresh.setAttribute(REFRESH_ATTRIBUTE, REFRESH_ATTRIBUTE_VAL);
  }

  public static void create(Panel container, Listener listener) {
    if (!created) {
      UpdateIndicatorWidget ui = new UpdateIndicatorWidget(listener);
      container.add(ui);
      created = true;
    }
  }

  @UiHandler("self")
  void handleClick(ClickEvent e) {
    final Element top = getElement();
    Element target;
    target = e.getNativeEvent().getEventTarget().cast();
    while (!top.equals(target)) {
      if (REFRESH_ATTRIBUTE_VAL.equals(target.getAttribute(REFRESH_ATTRIBUTE))) {
        listener.refresh();
        e.stopPropagation();
        return;
      }
      target = target.getParentElement();
      if (target == null) {
        break;
      }
    }
  }
}
