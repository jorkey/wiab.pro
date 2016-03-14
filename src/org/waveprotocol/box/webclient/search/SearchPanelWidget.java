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

import com.google.common.base.Preconditions;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.resources.client.ImageResource.ImageOptions;
import com.google.gwt.resources.client.ImageResource.RepeatStyle;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.RequiresResize;

import org.waveprotocol.box.webclient.widget.frame.FramedPanel;
import org.waveprotocol.wave.client.common.util.LinkedSequence;
import org.waveprotocol.wave.client.uibuilder.BuilderHelper;
import org.waveprotocol.wave.client.widget.common.ImplPanel;
import org.waveprotocol.wave.client.widget.toolbar.ToplevelToolbarWidget;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.StringMap;

/**
 * View interface for the search panel.
 *
 * @author hearnden@google.com (David Hearnden)
 */
public class SearchPanelWidget
    extends Composite
    implements SearchPanelView, RequiresResize {

  /** Resources used by this widget. */
  interface Resources
      extends ClientBundle {

    @Source("images/toolbar_empty.png")
    @ImageOptions(repeatStyle = RepeatStyle.Horizontal)
    ImageResource emptyToolbar();

    /** CSS */
    @Source("SearchPanel.css")
    Css css();
  }

  interface Css
      extends CssResource {
    
    String self();
    String search();
    String toolbars();
    String leftToolbar();
    String rightToolbar();
    String list();
    String showMore();
  }

  /**
   * Positioning constants for components of this panel.
   */
  public static class CssConstants {

    private static final int SEARCH_HEIGHT_PX = 51; // To match wave panel.
    private static final int TOOLBAR_HEIGHT_PX =
        SearchPanelResourceLoader.getPanel().emptyToolbar().getHeight();
    private static final int TOOLBAR_TOP_PX = 0 + SEARCH_HEIGHT_PX;
    private static final int LIST_TOP_PX = TOOLBAR_TOP_PX + TOOLBAR_HEIGHT_PX;
    // CSS constants exported to .css files
    static final String SEARCH_HEIGHT = SEARCH_HEIGHT_PX + "px";
    static final String TOOLBAR_TOP = TOOLBAR_TOP_PX + "px";
    static final String LIST_TOP = LIST_TOP_PX + "px";
    public static final String SEARCH_BACKGROUND = "#c9e2fc";
  }
  
  @UiField(provided = true)  
  static Css css = SearchPanelResourceLoader.getPanel().css();

  interface Binder
      extends UiBinder<FramedPanel, SearchPanelWidget> {
  }
  
  private final static Binder BINDER = GWT.create(Binder.class);
  
  private final FramedPanel frame;
  private final LinkedSequence<DigestDomImpl> digests = LinkedSequence.create();
  private final StringMap<DigestDomImpl> byId = CollectionUtils.createStringMap();
  private final SearchPanelRenderer renderer;
  private final Pool<DigestDomImpl> digestPool = ToppingUpPool.create(
      new ToppingUpPool.Factory<DigestDomImpl>() {

        @Override
        public DigestDomImpl create() {
          return new DigestDomImpl(SearchPanelWidget.this);
        }
      }, 20);
  private Listener listener;

  // Structure.
  
  @UiField
  SearchView search;
  
  @UiField
  ToplevelToolbarWidget leftToolbar;
  
  @UiField
  ToplevelToolbarWidget rightToolbar;
  
  @UiField
  Element list;
  
  @UiField
  Element showMore;
  
  @UiField
  ImplPanel self;  
  
  public SearchPanelWidget(SearchPanelRenderer renderer) {
    initWidget(frame = BINDER.createAndBindUi(this));
    this.renderer = renderer;
  }

  @Override
  public void init(Listener listener) {
    Preconditions.checkState(this.listener == null);
    Preconditions.checkArgument(listener != null);
    this.listener = listener;
  }

  @Override
  public void reset() {
    Preconditions.checkState(listener != null);
    listener = null;
  }

  void onDigestRemoved(DigestDomImpl digestUi) {
    digests.remove(digestUi);
    byId.remove(digestUi.getId());

    // Restore blank state and recycle.
    digestUi.reset();
    digestPool.recycle(digestUi);
  }

  @Override
  public void setTitleText(String text) {
    frame.setTitleText(text);
  }

  @Override
  public SearchView getSearch() {
    return search;
  }

  @Override
  public ToplevelToolbarWidget getLeftToolbar() {
    return leftToolbar;
  }

  @Override
  public ToplevelToolbarWidget getRightToolbar() {
    return rightToolbar;
  }

  @Override
  public DigestDomImpl getFirst() {
    return digests.getFirst();
  }

  @Override
  public DigestDomImpl getLast() {
    return digests.getLast();
  }

  @Override
  public DigestDomImpl getNext(DigestView ref) {
    return digests.getNext(narrow(ref));
  }

  @Override
  public DigestDomImpl getPrevious(DigestView ref) {
    return digests.getPrevious(narrow(ref));
  }

  @Override
  public DigestDomImpl insertBefore(DigestView ref, Digest digest) {
    DigestDomImpl digestUi = digestPool.get();
    renderer.render(digest, digestUi);

    DigestDomImpl refDomImpl = narrow(ref);
    Element refElement = refDomImpl != null ? refDomImpl.getElement() : showMore;
    byId.put(digestUi.getId(), digestUi);
    digests.insertBefore(refDomImpl, digestUi);
    list.insertBefore(digestUi.getElement(), refElement);

    return digestUi;
  }

  @Override
  public void clearDigest(DigestView ref) {
    DigestDomImpl digest = narrow(ref);
    if (digests.contains(digest)) {
      digest.remove();
    }
  }

  @Override
  public DigestDomImpl insertAfter(DigestView ref, Digest digest) {
    DigestDomImpl digestUi = digestPool.get();
    renderer.render(digest, digestUi);

    DigestDomImpl refDomImpl = narrow(ref);
    Element refElement = refDomImpl != null ? refDomImpl.getElement() : showMore;
    byId.put(digestUi.getId(), digestUi);
    if (refElement != showMore) {
      digests.insertAfter(refDomImpl, digestUi);
      list.insertAfter(digestUi.getElement(), refElement);
    } else {
      digests.insertBefore(refDomImpl, digestUi);
      list.insertBefore(digestUi.getElement(), refElement);
    }
    return digestUi;
  }

  @Override
  public void clearDigests() {
    while (!digests.isEmpty()) {
      digests.getFirst().remove(); // onDigestRemoved removes it from digests.
    }
    assert digests.isEmpty();
  }

  @Override
  public void setShowMoreVisible(boolean visible) {
    // In order to keep the padding effect, the button always need to be present
    // in order to affect layout.  Just make it invisible and non-clickable.
    if (visible) {
      showMore.getStyle().clearVisibility();
    } else {
      showMore.getStyle().setVisibility(Visibility.HIDDEN);
    }
  }

  @Override
  public void onResize() {
    leftToolbar.onResizeDone();
    
    if (listener != null) {
      listener.onResized();
    }    
  }  
  
  @UiHandler("self")
  void handleClick(ClickEvent e) {
    final Element top = self.getElement();    
    Element target;
    target = e.getNativeEvent().getEventTarget().cast();
    while (!top.equals(target)) {
      if (DigestDomImpl.KIND_DIGEST.equals(
          target.getAttribute(BuilderHelper.KIND_ATTRIBUTE))) {
        handleSelect(
            byId.get(target.getAttribute(DigestDomImpl.DIGEST_ID_ATTRIBUTE)),
            e.isControlKeyDown(),
            e.isAltKeyDown());
        e.stopPropagation();
        return;
      } else if (DigestDomImpl.KIND_CHECK.equals(
          target.getAttribute(BuilderHelper.KIND_ATTRIBUTE))) {
        handleCheck(byId.get(
            target.getAttribute(DigestDomImpl.DIGEST_ID_ATTRIBUTE)));
        e.stopPropagation();
        return;
      } else if (showMore.equals(target)) {
        handleShowMoreClicked();
      }
      target = target.getParentElement();
      if (target == null) {
        break;
      }
    }
  }

  @UiHandler("self")
  void handleMouseMove(MouseMoveEvent e) {
    Element top = self.getElement();    
    Element target;
    target = e.getNativeEvent().getEventTarget().cast();
    while (!top.equals(target)) {
      if (DigestDomImpl.KIND_DIGEST.equals(target.getAttribute(BuilderHelper.KIND_ATTRIBUTE)) ) {
        handleTouch(byId.get(target.getAttribute(DigestDomImpl.DIGEST_ID_ATTRIBUTE)) );
        return;
      }
      target = target.getParentElement();
      if (target == null) {
        break;
      }
    }    
    handleTouch(null);    
  }
  
  private void handleSelect(DigestDomImpl digestUi, boolean ctrlDown, boolean altDown) {
    if (digestUi == null) {
      // Error - there's an element in the DOM that looks like a digest, but
      // it's not in the digest map.
      // TODO(hearnden): log.
    } else {
      if (listener != null) {
        listener.onSelected(digestUi, ctrlDown, altDown);
      }
    }
  }

  private void handleCheck(DigestDomImpl digestUi) {
    if (digestUi == null) {
      // Error - there's an element in the DOM that looks like a digest, but
      // it's not in the digest map.
      // TODO(hearnden): log.
    } else {
      if (listener != null) {
        listener.onChecked(digestUi, !digestUi.isChecked());
      }
    }
  }

  private void handleShowMoreClicked() {
    if (listener != null) {
      listener.onShowMoreClicked();
    }
  }

  private void handleTouch(DigestDomImpl digestUi) {
    if (listener != null) {
      listener.onTouched(digestUi);
    }
  }
  
  private static DigestDomImpl narrow(DigestView digestUi) {
    return (DigestDomImpl) digestUi;
  }
}
