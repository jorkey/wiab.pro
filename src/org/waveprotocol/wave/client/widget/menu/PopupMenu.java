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

package org.waveprotocol.wave.client.widget.menu;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.CssResource.NotStrict;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import org.waveprotocol.wave.client.common.webdriver.DebugClassHelper;
import org.waveprotocol.wave.client.widget.popup.AlignedPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupEventListener;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.RelativePopupPositioner;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;
import org.waveprotocol.wave.model.util.Pair;

/**
 *
 * Desktop version for rendering a vertical sub-menu.
 * I don't like it any more than you do.
 */
public class PopupMenu implements Menu {
  interface Resources extends ClientBundle {
    /** CSS class names used by DesktopSubMenu. These are used in Menu.css */
    interface Css extends CssResource {
      String item();
      String disabled();
      String divider();
      String hotkey();
    }

    @Source("Menu.css")
    @NotStrict  // TODO(user): make Strict by including/inheriting "verticalSeparator"
    Css css();
  }

  /**
   * An implementation of {@code MenuItem} for a {@code PopupMenu}.
   *
   */
  private class PopupMenuItem extends HTML implements MenuItem {
    
    /**
     * Whether the default state of the item is enabled or not.
     */
    private final boolean defaultEnabled;

    /**
     * Whether the item is enabled or not.
     */
    private boolean enabled;

    /**
     * The command to execute when clicked.
     */
    private Command command;

    /** Should clicking this menu item hide the popup? */
    private final boolean hide;

    /**
     * Constructs a {@PopupMenuItem}
     *
     * @param text The text label for the item.
     * @param cmd The command to run when the item is clicked.
     * @param isEnabled True if this menu item is enabled.
     * @param hide True if clicking this menu item should hide the popup.
     */
    public PopupMenuItem(String text, Command command, boolean isEnabled, boolean hide) {
      super(text, false);

      this.command = command;      
      this.defaultEnabled = isEnabled;
      this.hide = hide;      
      
      setStyleName(CSS.item());
      
      if (isPreClicked) {
        // If this menu is pre-clicked it doesn't require a full click to select
        // an item, just a mouseup over the item.  If the user then does click the
        // item then that will also give a mouseup so this handler will deal with
        // that case as well.
        addMouseUpHandler(new MouseUpHandler() {
          @Override
          public void onMouseUp(MouseUpEvent event) {
            onClicked();
          }
        });
      } else {
        addClickHandler(new ClickHandler() {
          
          @Override
          public void onClick(ClickEvent e) {
            onClicked();
          }
        });
      }
      // Ensure that clicking this menu item doesn't affect the current selection.
      addMouseDownHandler(PREVENT_DEFAULT_HANDLER);
    }

    public void init() {
      setEnabled(defaultEnabled);      
    }
    
    private void onClicked() {
      if (enabled) {
        if (command != null) {
          command.execute();
        }
        if (hide) {
          popup.hide();
        }
      }
    }

    @Override
    public void setEnabled(boolean enabled) {
      if (!enabled) {
        addStyleName(CSS.disabled());
      } else {
        removeStyleName(CSS.disabled());
      }
      this.enabled = enabled;
    }

    @Override
    public void resetEnabled() {
      setEnabled(defaultEnabled);
    }

    @Override
    public final void setDebugClassTODORename(String debugClass) {
      DebugClassHelper.addDebugClass(this, debugClass);
    }

    /** Set the command to me executed when this menu item is clicked */
    public void setCommand(Command cmd) {
      command = cmd;
    }
  }

  /**
   * Mouse down handler that prevents the event's default action thereby, the
   * way it's used here, preventing mouse downs from affecting selection.
   */
  private static final MouseDownHandler PREVENT_DEFAULT_HANDLER = new MouseDownHandler() {
    @Override
    public void onMouseDown(MouseDownEvent event) {
      event.preventDefault();
    }
  };

  /** The singleton instance of our resources. */
  private static final Resources RESOURCES = GWT.create(Resources.class);
  public static final Resources.Css CSS = RESOURCES.css();

  /**
   * Inject the CSS once.
   */
  static {
    StyleInjector.inject(CSS.getText());
  }

  /** The popup to use for this submenu */
  private final UniversalPopup popup;

  /**
   * True if the user has already clicked a mouse button and so just releasing the
   * mouse over an item should count as a click.
   */
  private final boolean isPreClicked;

  /**
   * Create a new PopupMenu
   *
   * @param relative The reference element for the positioner.
   * @param positioner The positioner to position the menu popup.
   */
  public PopupMenu(Element relative, RelativePopupPositioner positioner,
      boolean isPreClicked) {
    popup = PopupFactory.createPopup(relative, positioner,
        PopupChromeFactory.createPopupChrome(), true);
    this.isPreClicked = isPreClicked;
  }

  /**
   * Create a new PopupMenu
   *
   * @param relative The reference element for the positioner.
   * @param positioner The positioner to position the menu popup.
   */
  public PopupMenu(Element relative, RelativePopupPositioner positioner) {
    this(relative, positioner, false);
  }

  @Override
  public void addDivider() {
    Widget divider = new Label();
    divider.setStyleName(CSS.divider());
    popup.add(divider);
  }

  @Override
  public Pair<Menu, MenuItem> createSubMenu(String title, boolean enabled) {
    PopupMenuItem item = addItem(title, null, enabled, false);
    final PopupMenu menu = new PopupMenu(item.getElement(), AlignedPopupPositioner.BELOW_RIGHT);
    item.setCommand(new Command() {
      
      @Override
      public void execute() {
        menu.show();
      }
    });
    return new Pair<Menu, MenuItem>(menu, item);
  }

  @Override
  public MenuItem addItem(String label, final Command cmd, boolean enabled) {
    return addItem(label, cmd, enabled, true);
  }

  @Override
  public MenuItem addItem(String title, String hotkey, final Command cmd, boolean enabled) {
    String fullTitle = title;
    if (hotkey != null) {
      fullTitle += "<div class='" + CSS.hotkey() + "'>" + hotkey + "</div>";      
    }
    return addItem(fullTitle, cmd, enabled, true);
  }
  
  private PopupMenuItem addItem(String label, final Command cmd, boolean enabled,
                                boolean hideOnClick) {
    PopupMenuItem item = new PopupMenuItem(label, cmd, enabled, hideOnClick);
    item.init();    
    popup.add(item);
    return item;
  }

  /**
   * Removes an item previously added to the menu.
   *
   * @param item The item to remove.
   */
  public boolean removeItem(MenuItem item) {
    if (item instanceof PopupMenuItem) {
      PopupMenuItem popup = (PopupMenuItem) item;
      return this.popup.remove(popup);
    }
    return false;
  }

  /**
   * Hide the menu.
   */
  @Override
  public void hide() {
    popup.hide();
  }

  /**
   * Show the menu.
   */
  @Override
  public void show() {
    popup.show();
  }

  /**
   * Set the debugClassName (for Webdriver) on the popup window.
   * @param dcName debugClassName to set.
   */
  public void setDebugClass(String dcName) {
    popup.setDebugClass(dcName);
  }

  @Override
  public void clearItems() {
    popup.clear();
  }

  /**
   * Add a PopupEventListener to this menu.
   * @param listener The listener to add.
   */
  public void addPopupEventListener(PopupEventListener listener) {
    popup.addPopupEventListener(listener);
  }
  /**
   * Remove a PopupEventListener to this menu.
   * @param listener The listener to remove.
   */
  public void removePopupEventListener(PopupEventListener listener) {
    popup.removePopupEventListener(listener);
  }

  /**
   * Indicates that clicks inside the given widget should not be considered
   * 'outside' of the popup menu, for purposes of auto-hiding.
   *
   * @param w A widget.
   */
  public void associateWidget(Widget w) {
    popup.associateWidget(w);
  }
}
