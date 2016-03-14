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

package org.waveprotocol.wave.client.wavepanel.view.dom;

import com.google.common.base.Preconditions;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Display;

import org.waveprotocol.wave.client.common.util.DateUtils;
import org.waveprotocol.wave.client.common.util.UserAgent;
import org.waveprotocol.wave.client.scroll.DomScrollPanel;
import org.waveprotocol.wave.client.scroll.Extent;
import org.waveprotocol.wave.client.scroll.ScrollPanel;
import org.waveprotocol.wave.client.wavepanel.impl.edit.i18n.ParticipantMessages;
import org.waveprotocol.wave.client.wavepanel.impl.edit.i18n.TagMessages;
import org.waveprotocol.wave.client.wavepanel.view.AnchorView;
import org.waveprotocol.wave.client.wavepanel.view.BlipLinkPopupView;
import org.waveprotocol.wave.client.wavepanel.view.BlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.ContinuationIndicatorView;
import org.waveprotocol.wave.client.wavepanel.view.ConversationView;
import org.waveprotocol.wave.client.wavepanel.view.FocusFrameView;
import org.waveprotocol.wave.client.wavepanel.view.InlineThreadView;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicParticipantView.ParticipantState;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicTagView.TagState;
import org.waveprotocol.wave.client.wavepanel.view.ParticipantView;
import org.waveprotocol.wave.client.wavepanel.view.ParticipantsView;
import org.waveprotocol.wave.client.wavepanel.view.PlaceholderView;
import org.waveprotocol.wave.client.wavepanel.view.ReplyBoxView;
import org.waveprotocol.wave.client.wavepanel.view.RootThreadView;
import org.waveprotocol.wave.client.wavepanel.view.TagView;
import org.waveprotocol.wave.client.wavepanel.view.TagsView;
import org.waveprotocol.wave.client.wavepanel.view.ThreadView;
import org.waveprotocol.wave.client.wavepanel.view.View;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import static org.waveprotocol.wave.client.wavepanel.view.dom.DomViewHelper.getAfter;
import static org.waveprotocol.wave.client.wavepanel.view.dom.DomViewHelper.getBefore;
import static org.waveprotocol.wave.client.wavepanel.view.dom.DomViewHelper.insert;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipLinkPopupWidget;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.DomRenderer;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.FocusFrame;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.WavePanelResourceLoader;
import org.waveprotocol.wave.client.wavepanel.view.impl.AbstractConversationViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.impl.AbstractStructuredView;
import org.waveprotocol.wave.client.wavepanel.view.impl.AnchorViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.impl.BlipMetaViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.impl.BlipViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.impl.ContinuationIndicatorViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.impl.DraftModeControlsWidget;
import org.waveprotocol.wave.client.wavepanel.view.impl.InlineConversationViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.impl.InlineThreadViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.impl.OutlineThreadViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.impl.ParticipantViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.impl.ParticipantsViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.impl.PlaceholderViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.impl.ReplyBoxViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.impl.RootThreadViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.impl.TagViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.impl.TagsViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.impl.TopConversationViewImpl;
import org.waveprotocol.wave.client.widget.popup.AlignedPopupPositioner;
import org.waveprotocol.wave.client.widget.profile.ProfilePopupView;
import org.waveprotocol.wave.client.widget.profile.ProfilePopupWidget;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.util.ValueUtils;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implements structural accessors and mutators for wave-panel views.
 *
 */
public class FullStructure implements UpgradeableDomAsViewProvider {

  private final static ParticipantMessages participantMessages =
      WavePanelResourceLoader.getParticipantMessages();

  private final static TagMessages tagMessages = WavePanelResourceLoader.getTagMessages();

  private final BlipMetaViewImpl.Helper<BlipMetaDomImpl> metaHelper =
      new BlipMetaViewImpl.Helper<BlipMetaDomImpl>() {

        private DraftModeControlsWidget draftModeControls;

        @Override
        public void setChrome(BlipMetaDomImpl impl, FocusFrameView frame, boolean on) {
          if (on) {
            impl.getElement().appendChild(frameElement(frame));
          } else {
            frameElement(frame).removeFromParent();
          }
        }

        @Override
        public boolean hasChrome(BlipMetaDomImpl impl) {
          return DomUtil.findFirstChildElement(impl.getElement(), Type.BLIP_FOCUS_FRAME) != null;
        }

        @Override
        public boolean hasEditedChrome(BlipMetaDomImpl impl) {
          Element focusFrame = DomUtil.findFirstChildElement(impl.getElement(),
              Type.BLIP_FOCUS_FRAME);
          return DomUtil.getElementBooleanAttribute(focusFrame, FocusFrame.EDITED_ATTRIBUTE);
        }

        @Override
        public AnchorView getInlineAnchorAfter(BlipMetaDomImpl impl, AnchorView ref) {
          return asAnchor(impl.getInlineAnchorAfter(elementOf(ref)));
        }

        @Override
        public AnchorView getInlineAnchorBefore(BlipMetaDomImpl impl, AnchorView ref) {
          return asAnchor(impl.getInlineAnchorBefore(elementOf(ref)));
        }

        @Override
        public void insertInlineAnchorBefore(BlipMetaDomImpl impl, AnchorView ref, AnchorView x) {
          @SuppressWarnings("unchecked")
          AnchorDomImpl anchor = ((AnchorViewImpl<AnchorDomImpl>) x).getIntrinsic();
          impl.insertInlineLocatorBefore(elementOf(ref), anchor.getElement());
          anchor.setParentId(impl.getId());
        }

        @Override
        public BlipView getBlip(BlipMetaDomImpl impl) {
          return asBlip(parentOf(impl, Type.BLIP));
        }

        @Override
        public void remove(BlipMetaDomImpl impl) {
          impl.remove();
        }

        private Element frameElement(FocusFrameView frame) {
          return ((FocusFrame) frame).getElement();
        }

        @Override
        public BlipMetaView.DraftModeControls attachDraftModeControlsWidget(BlipMetaDomImpl impl) {
          Preconditions.checkArgument(draftModeControls == null, "Draft mode controls widget is already attached");
          draftModeControls = new DraftModeControlsWidget(impl.getDraftModeControls());
          return draftModeControls;
        }

        @Override
        public void detachDraftModeControlsWidget(BlipMetaDomImpl impl) {
          Preconditions.checkNotNull(draftModeControls, "Editor mode controls widget is not attached");
          draftModeControls = null;
          impl.getDraftModeControls().removeAllChildren();
        }

        @Override
        public void showDraftModeControls(BlipMetaDomImpl impl) {
          impl.getDraftModeControls().getStyle().setDisplay(Display.BLOCK);
        }

        @Override
        public void hideDraftModeControls(BlipMetaDomImpl impl) {
          impl.getDraftModeControls().getStyle().setDisplay(Display.NONE);
        }

        @Override
        public boolean areDraftModeControlsVisible(BlipMetaDomImpl impl) {
          String display = impl.getDraftModeControls().getStyle().getDisplay();
          return  display == "none" || display == "" || display == null;
        }
      };

  private final BlipViewImpl.Helper<BlipViewDomImpl> blipHelper =
      new BlipViewImpl.Helper<BlipViewDomImpl>() {

        @Override
        public ThreadView getBlipParent(BlipViewDomImpl impl) {
          List<Type> types = Arrays.asList(Type.ROOT_THREAD, Type.INLINE_THREAD, Type.OUTLINE_THREAD);
          return asThread(parentOf(impl, types));
        }

        @Override
        public ConversationView getBlipConversation(BlipViewDomImpl impl) {
          Element blip = impl.getElement();
          Element conversation = DomUtil.findParentElement(blip, Type.ROOT_CONVERSATION);
          return asConversation(conversation);
        }

        @Override
        public BlipMetaViewImpl<BlipMetaDomImpl> getMeta(BlipViewDomImpl impl) {
          return asBlipMeta(impl.getMeta());
        }

        @Override
        public AnchorView getDefaultAnchorBefore(BlipViewDomImpl impl, AnchorView ref) {
          return asAnchor(getBefore(impl.getReplies(), elementOf(ref)));
        }

        @Override
        public AnchorView getDefaultAnchorAfter(BlipViewDomImpl impl, AnchorView ref) {
          return asAnchor(getAfter(impl.getReplies(), elementOf(ref)));
        }

        @Override
        public AnchorView insertDefaultAnchor(BlipViewDomImpl impl, ConversationThread thread,
            AnchorView neighbor, boolean forward) {
          Element anchorElement = getRenderer().render(thread);
          Element neighborElement = elementOf(neighbor);
          insert(impl.getReplies(), anchorElement, neighborElement, forward);
          return asAnchorUnchecked(anchorElement);
        }

        @Override
        public void insertOutlineThread(BlipViewDomImpl impl, ConversationThread thread) {
          Element prevBlipElement = impl.getElement();
          Element threadElement = prevBlipElement.getParentElement();
          for (ConversationBlip blip : thread.getBlips()) {
            Element blipElement = getRenderer().render(blip);
            insert(threadElement, blipElement, prevBlipElement, false);
            prevBlipElement = blipElement;
          }
        }

        @Override
        public void removeBlip(BlipViewDomImpl impl) {
          impl.remove();
        }

        @Override
        public BlipLinkPopupView createLinkPopup(BlipViewDomImpl impl) {
          return new BlipLinkPopupWidget(impl.getElement());
        }
      };

  private final PlaceholderViewImpl.Helper<PlaceholderViewDomImpl> placeholderHelper =
      new PlaceholderViewImpl.Helper<PlaceholderViewDomImpl>() {

        @Override
        public void removePlaceholder(PlaceholderViewDomImpl impl) {
          impl.remove();
        }

        @Override
        public ThreadView getPlaceholderParent(PlaceholderViewDomImpl impl) {
          List<Type> types = Arrays.asList(Type.ROOT_THREAD, Type.INLINE_THREAD, Type.OUTLINE_THREAD);
          return asThread(parentOf(impl, types));
        }
      };

  private final RootThreadViewImpl.Helper<RootThreadDomImpl> rootThreadHelper =
      new RootThreadViewImpl.Helper<RootThreadDomImpl>() {

        @Override
        public ConversationView getThreadParent(RootThreadDomImpl thread) {
          List<Type> types = Arrays.asList(Type.ROOT_CONVERSATION, Type.INLINE_CONVERSATION);
          return asConversation(parentOf(thread, types));
        }

        @Override
        public BlipView insertBlip(RootThreadDomImpl thread, ConversationBlip blip, View neighbor,
            boolean beforeNeighbor) {
          return FullStructure.this.insertBlip(thread.getBlipContainer(), blip, neighbor,
              beforeNeighbor);
        }

        @Override
        public void removeThread(RootThreadDomImpl thread) {
          thread.remove();
        }

        @Override
        public ReplyBoxView getIndicator(RootThreadDomImpl impl) {
          return asReplyBox(impl.getIndicator());
        }

        @Override
        public PlaceholderView insertPlaceholder(RootThreadDomImpl thread, View neighbor,
            boolean beforeNeighbor) {
          return FullStructure.this.insertPlaceholder(thread.getBlipContainer(), neighbor,
              beforeNeighbor);
        }
      };

  private final InlineThreadViewImpl.Helper<InlineThreadDomImpl> inlineThreadHelper =
      new InlineThreadViewImpl.Helper<InlineThreadDomImpl>() {

        @Override
        public AnchorView getThreadParent(InlineThreadDomImpl thread) {
          return asAnchor(parentOf(thread, Type.ANCHOR));
        }

        @Override
        public BlipView insertBlip(InlineThreadDomImpl thread, ConversationBlip blip, View neighbor,
            boolean beforeNeighbor) {
          return FullStructure.this.insertBlip(thread.getBlipContainer(), blip, neighbor,
              beforeNeighbor);
        }

        @Override
        public void removeThread(InlineThreadDomImpl thread) {
          thread.remove();
        }

        @Override
        public PlaceholderView insertPlaceholder(InlineThreadDomImpl thread, View neighbor,
            boolean beforeNeighbor) {
          return FullStructure.this.insertPlaceholder(thread.getBlipContainer(), neighbor,
              beforeNeighbor);
        }
      };

  private final OutlineThreadViewImpl.Helper<OutlineThreadDomImpl> outlineThreadHelper =
      new OutlineThreadViewImpl.Helper<OutlineThreadDomImpl>() {

        @Override
        public BlipView getThreadParent(OutlineThreadDomImpl thread) {
          return asBlip(parentOf(thread, Type.ANCHOR));
        }

        @Override
        public BlipView insertBlip(OutlineThreadDomImpl thread, ConversationBlip blip, View neighbor,
            boolean beforeNeighbor) {
          return FullStructure.this.insertBlip(thread.getBlipContainer(), blip, neighbor,
              beforeNeighbor);
        }

        @Override
        public void removeThread(OutlineThreadDomImpl thread) {
          // do nothing here
        }

        @Override
        public PlaceholderView insertPlaceholder(OutlineThreadDomImpl thread, View neighbor,
            boolean beforeNeighbor) {
          return FullStructure.this.insertPlaceholder(thread.getBlipContainer(), neighbor,
              beforeNeighbor);
        }
      };

  private final ReplyBoxViewImpl.Helper<ReplyBoxDomImpl> rootThreadIndicatorHelper =
      new ReplyBoxViewImpl.Helper<ReplyBoxDomImpl>() {

        @Override
        public RootThreadView getParent(ReplyBoxDomImpl impl) {
          return asRootThread(parentOf(impl, Type.ROOT_THREAD));
        }

        @Override
        public void remove(ReplyBoxDomImpl impl) {
          impl.remove();
        }
      };

  private final ContinuationIndicatorViewImpl.Helper<ContinuationIndicatorDomImpl>
      continuationIndicatorHelper =
      new ContinuationIndicatorViewImpl.Helper<ContinuationIndicatorDomImpl>() {

        @Override
        public BlipView getParent(ContinuationIndicatorDomImpl impl) {
          return asBlip(parentOf(impl, Type.BLIP));
        }

        @Override
        public void remove(ContinuationIndicatorDomImpl impl) {
          impl.remove();
        }
      };

  private final AnchorViewImpl.Helper<AnchorDomImpl> anchorHelper =
      new AnchorViewImpl.Helper<AnchorDomImpl>() {

        @Override
        public void attach(AnchorDomImpl anchor, InlineThreadView thread) {
          anchor.setChild(elementOf(thread));
        }

        @Override
        public void detach(AnchorDomImpl anchor, InlineThreadView thread) {
          anchor.removeChild(elementOf(thread));
        }

        @Override
        public InlineThreadView getThread(AnchorDomImpl anchor) {
          Element child = anchor.getChild();
          return asInlineThread(child);
        }

        @Override
        public void remove(AnchorDomImpl impl) {
          View parent = getParent(impl);
          if (parent.getType() == Type.BLIP) {
            BlipViewDomImpl parentImpl = ((BlipViewDomImpl) narrow(parent).getIntrinsic());
            DomViewHelper.detach(parentImpl.getReplies(), impl.getElement());
          } else if (parent.getType() == Type.META) {
            BlipMetaDomImpl parentImpl = ((BlipMetaDomImpl) narrow(parent).getIntrinsic());
            parentImpl.removeInlineLocator(impl.getElement());
            // Do not detach here - editor rendering controls that.
            impl.setParentId(null);
          }
        }

        @Override
        public View getParent(AnchorDomImpl impl) {
          String pid = impl.getParentId();
          return pid != null ? viewOf(Document.get().getElementById(pid)) : parentOf(impl);
        }
      };

  private final TopConversationViewImpl.Helper<TopConversationDomImpl> convHelper =
      new TopConversationViewImpl.Helper<TopConversationDomImpl>() {

        @Override
        public ScrollPanel<? super View> getScroller(TopConversationDomImpl impl) {
          final ScrollPanel<Element> domScroller = createDomScroller(impl);
          return
              new ScrollPanel<View>() {

                @Override
                public Extent extentOf(View ui) {
                  return domScroller.extentOf(elementOf(ui));
                }

                @Override
                public Extent getContent() {
                  return domScroller.getContent();
                }

                @Override
                public Extent getViewport() {
                  return domScroller.getViewport();
                }

                @Override
                public void moveTo(double location) {
                  domScroller.moveTo(location);
                }
              };
        }

        @Override
        public void setToolbar(TopConversationDomImpl impl, Element e) {
          impl.setToolbar(e);
        }

        @Override
        public ParticipantsView getParticipants(TopConversationDomImpl impl) {
          return asParticipants(impl.getParticipants());
        }

        @Override
        public RootThreadView getRootThread(TopConversationDomImpl impl) {
          return asRootThread(impl.getThread());
        }

        @Override
        public void remove(TopConversationDomImpl impl) {
          impl.remove();
        }

        private ScrollPanel<Element> createDomScroller(TopConversationDomImpl impl) {
          return DomScrollPanel.create(impl.getThreadContainer());
        }
      };

  private final InlineConversationViewImpl.Helper<InlineConversationDomImpl> inlineConvHelper =
      new InlineConversationViewImpl.Helper<InlineConversationDomImpl>() {

        @Override
        public ParticipantsView getParticipants(InlineConversationDomImpl impl) {
          return asParticipants(impl.getParticipants());
        }

        @Override
        public RootThreadView getRootThread(InlineConversationDomImpl impl) {
          return asRootThread(impl.getRootThread());
        }

        @Override
        public void remove(InlineConversationDomImpl impl) {
          impl.remove();
        }

        @Override
        public BlipView getParent(InlineConversationDomImpl impl) {
          return asBlip(parentOf(impl));
        }
      };

  private final ParticipantViewImpl.Helper<ParticipantAvatarDomImpl> participantHelper =
      new ParticipantViewImpl.Helper<ParticipantAvatarDomImpl>() {

        @Override
        public void remove(ParticipantAvatarDomImpl impl) {
          Element container = impl.getElement().getParentElement();
          impl.remove();
          kickWebkit(container);
        }

        @Override
        public ProfilePopupView showParticipation(ParticipantAvatarDomImpl impl) {
          return new ProfilePopupWidget(impl.getElement(), AlignedPopupPositioner.BELOW_RIGHT);
        }
      };

  private final ParticipantsViewImpl.Helper<ParticipantsDomImpl> participantsHelper =
      new ParticipantsViewImpl.Helper<ParticipantsDomImpl>() {

        @Override
        public ParticipantView append(ParticipantsDomImpl impl, Conversation conv,
            ParticipantId participant, WaveletOperationContext opContext, boolean showDiff) {
          Element t = null;
          if (!showDiff) {
            clearDiffs(impl);
          }
          ParticipantView participantView = getView(impl, participant);
          if (showDiff) {
            //non-existed, removed => added
            if (participantView == null ||
                ParticipantState.REMOVED.equals(participantView.getState())) {
              doRemove(participantView);
              t = doAdd(impl, conv, participant, ParticipantState.ADDED,
                  getHint(opContext, true), true);
            }
          } else {
            if (participantView == null) {
              t = doAdd(impl, conv, participant, ParticipantState.NORMAL, null, false);
            }
          }
          return asParticipant(t);
        }

        @Override
        public ParticipantView remove(ParticipantsDomImpl impl, Conversation conv,
            ParticipantId participant, WaveletOperationContext opContext, boolean showDiff) {
          Element t = null;
          if (!showDiff) {
            clearDiffs(impl);
          }
          ParticipantView participantView = getView(impl, participant);
          if (showDiff) {
            //normal, added => removed
            if (participantView != null) {
              doRemove(participantView);
              t = doAdd(impl, conv, participant, ParticipantState.REMOVED,
                  getHint(opContext, false), true);
            }
          } else {
            doRemove(participantView);
          }
          return asParticipant(t);
        }

        @Override
        public void clearDiffs(ParticipantsDomImpl impl) {
          List<ParticipantView> views = getViews(impl);
          for (ParticipantView view : views) {
            ParticipantState state = view.getState();
            if (ParticipantState.ADDED.equals(state)) {
              view.setState(ParticipantState.NORMAL);
            } else if (ParticipantState.REMOVED.equals(state)) {
              view.remove();
            }
          }
        }

        @Override
        public void remove(ParticipantsDomImpl impl) {
          impl.remove();
        }

        private List<ParticipantView> getViews(ParticipantsDomImpl impl) {
          List<ParticipantView> participants = new ArrayList<>();
          Element container = impl.getParticipantContainer();
          NodeList nodes = container.getChildNodes();
          for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.getItem(i);
            if (node instanceof Element) {
              Element t = (Element)node;
              if (DomUtil.doesElementHaveType(t, Type.PARTICIPANT)) {
                participants.add(asParticipant(t));
              }
            }
          }
          return participants;
        }

        private ParticipantView getView(
            ParticipantsDomImpl impl, ParticipantId participant) {
          String address = participant.getAddress();
          List<ParticipantView> views = getViews(impl);
          for (ParticipantView view : views) {
            if (view.getParticipantId().equals(address)) {
              return view;
            }
          }
          return null;
        }

        private String getHint(WaveletOperationContext opContext, boolean add) {
          String hint = null;
          if (opContext != null) {
            String authorName = ValueUtils.toCapitalCase(opContext.getCreator().getName());
            if (authorName != null) {
              hint = (add ? participantMessages.added(authorName,
                  DateUtils.getInstance().formatPastDate(opContext.getTimestamp()) )
                  : participantMessages.removed(authorName,
                  DateUtils.getInstance().formatPastDate(opContext.getTimestamp()) ));
            }
          }
          return hint;
        }

        private Element doAdd( ParticipantsDomImpl impl, Conversation conv,
            ParticipantId participant, ParticipantState state, String hint, boolean forward) {
          Element participantElement = getRenderer().render(conv, participant, state, hint);
          Element neighborElement = forward ? impl.getFirstChild() : impl.getSimpleMenu();
          insert(impl.getParticipantContainer(), participantElement, neighborElement, true);
          kickWebkit(impl.getElement());
          return participantElement;
        }

        private void doRemove(ParticipantView participantView) {
          if (participantView != null) {
            participantView.remove();
          }
        }
      };

  private final TagViewImpl.Helper<TagDomImpl> tagHelper =
      new TagViewImpl.Helper<TagDomImpl>() {

        @Override
        public void remove(TagDomImpl impl) {
          Element container = impl.getElement().getParentElement();
          impl.remove();
          kickWebkit(container);
        }
      };

  private final TagsViewImpl.Helper<TagsDomImpl> tagsHelper =
      new TagsViewImpl.Helper<TagsDomImpl>() {

        @Override
        public TagView append(TagsDomImpl impl, Conversation conv, String tag,
            WaveletOperationContext opContext, boolean showDiff) {
          Element t = null;
          if (!showDiff) {
            clearDiffs(impl);
          }
          TagView tagView = getView(impl, tag);
          if (showDiff) {
            //non-existed, removed => added
            if (tagView == null || TagState.REMOVED.equals(tagView.getState())) {
              doRemove(tagView);
              t = doAdd(impl, conv, tag, TagState.ADDED, getHint(opContext, true), true);
            }
          } else {
            if (tagView == null) {
              t = doAdd(impl, conv, tag, TagState.NORMAL, null, false);
            }
          }
          return asTag(t);
        }

        @Override
        public TagView remove(TagsDomImpl impl, Conversation conv, String tag,
            WaveletOperationContext opContext, boolean showDiff) {
          Element t = null;
          if (!showDiff) {
            clearDiffs(impl);
          }
          TagView tagView = getView(impl, tag);
          if (showDiff) {
            //normal, added => removed
            if (tagView != null) {
              doRemove(tagView);
              t = doAdd(impl, conv, tag, TagState.REMOVED, getHint(opContext, false), true);
            }
          } else {
            doRemove(tagView);
          }
          return asTag(t);
        }

        @Override
        public void clearDiffs(TagsDomImpl impl) {
          List<TagView> views = getViews(impl);
          for (TagView view : views) {
            TagState state = view.getState();
            if (TagState.ADDED.equals(state)) {
              view.setState(TagState.NORMAL);
            } else if (TagState.REMOVED.equals(state)) {
              view.remove();
            }
          }
        }

        @Override
        public void remove(TagsDomImpl impl) {
          impl.remove();
        }

        private List<TagView> getViews(TagsDomImpl impl) {
          List<TagView> tags = new ArrayList<>();
          Element container = impl.getTagContainer();
          NodeList nodes = container.getChildNodes();
          for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.getItem(i);
            if (node instanceof Element) {
              Element t = (Element)node;
              if (DomUtil.doesElementHaveType(t, Type.TAG)) {
                tags.add(asTag(t));
              }
            }
          }
          return tags;
        }

        private TagView getView(TagsDomImpl impl, String tag) {
          List<TagView> views = getViews(impl);
          for (TagView view : views) {
            if (view.getName().equals(tag)) {
              return view;
            }
          }
          return null;
        }

        private String getHint(WaveletOperationContext opContext, boolean add) {
          String hint = null;
          if (opContext != null) {
            String authorName = ValueUtils.toCapitalCase(opContext.getCreator().getName());
            if (authorName != null) {
              hint = (add ? tagMessages.added(authorName,
                  DateUtils.getInstance().formatPastDate(opContext.getTimestamp()))
                  : tagMessages.removed(authorName,
                  DateUtils.getInstance().formatPastDate(opContext.getTimestamp())));
            }
          }
          return hint;
        }

        private Element doAdd(TagsDomImpl impl, Conversation conv, String tag, TagState state,
            String hint, boolean forward) {
          Element tagElement = getRenderer().render(conv, tag, state, hint);
          Element neighborElement = forward ? impl.getTagsCaption() : impl.getSimpleMenu();
          insert(impl.getTagContainer(), tagElement, neighborElement, !forward);
          kickWebkit(impl.getElement());
          return tagElement;
        }

        private void doRemove(TagView tagView) {
          if (tagView != null) {
            tagView.remove();
          }
        }
      };
  
  /**
   * Creates a view provider/manager/handler/oracle.
   */
  public static FullStructure create(CssProvider cssProvider) {
    return new FullStructure(cssProvider);
  }

  /** Injected CSS resources providing access to style names to apply. */
  private final CssProvider cssProvider;

  /**
   * Renderer for creating new parts of the DOM. Initially unset, then set once
   * in {@link #setRenderer(DomRenderer)}.
   */
  private DomRenderer domRenderer;

  private FullStructure(CssProvider cssProvider) {
    this.cssProvider = cssProvider;
  }

  @Override
  public void setRenderer(DomRenderer renderer) {
    Preconditions.checkArgument(renderer != null);
    Preconditions.checkState(this.domRenderer == null);
    this.domRenderer = renderer;
  }

  @Override
  public RootThreadViewImpl<RootThreadDomImpl> asRootThread(Element e) {
    Preconditions.checkArgument(isOfType(e, Type.ROOT_THREAD));
    return asRootThreadUnchecked(e);
  }

  @Override
  public InlineThreadViewImpl<InlineThreadDomImpl> asInlineThread(Element e) {
    Preconditions.checkArgument(isOfType(e, Type.INLINE_THREAD));
    return asInlineThreadUnchecked(e);
  }

  @Override
  public OutlineThreadViewImpl<OutlineThreadDomImpl> asOutlineThread(Element e) {
    return asOutlineThreadUnchecked(e);
  }

  @Override
  public ReplyBoxViewImpl<ReplyBoxDomImpl> asReplyBox(Element e) {
    Preconditions.checkArgument(isOfType(e, Type.REPLY_BOX));
    return asRootThreadIndicatorUnchecked(e);
  }

  @Override
  public ContinuationIndicatorView asContinuationIndicator(Element e) {
    Preconditions.checkArgument(isOfType(e, Type.CONTINUATION_BAR));
    return asContinuationIndicatorUnchecked(e);
  }

  @Override
  public BlipViewImpl<BlipViewDomImpl> asBlip(Element e) {
    Preconditions.checkArgument(isOfType(e, Type.BLIP));
    return asBlipUnchecked(e);
  }

  @Override
  public PlaceholderViewImpl<PlaceholderViewDomImpl> asPlaceholder(Element e) {
    Preconditions.checkArgument(isOfType(e, Type.PLACEHOLDER));
    return asPlaceholderUnchecked(e);
  }

  @Override
  public ParticipantView asParticipant(Element e) {
    Preconditions.checkArgument(isOfType(e, Type.PARTICIPANT));
    return asParticipantUnchecked(e);
  }

  @Override
  public ParticipantsView asParticipants(Element e) {
    Preconditions.checkArgument(isOfType(e, Type.PARTICIPANTS));
    return asParticipantsUnchecked(e);
  }

  @Override
  public TagView asTag(Element e) {
    Preconditions.checkArgument(isOfType(e, Type.TAG));
    return asTagUnchecked(e);
  }

  @Override
  public TagsView asTags(Element e) {
    Preconditions.checkArgument(isOfType(e, Type.TAGS));
    return asTagsUnchecked(e);
  }

  @Override
  public BlipMetaViewImpl<BlipMetaDomImpl> asBlipMeta(Element e) {
    Preconditions.checkArgument(isOfType(e, Type.META));
    return asBlipMetaUnchecked(e);
  }

  @Override
  public AnchorViewImpl<AnchorDomImpl> asAnchor(Element e) {
    Preconditions.checkArgument(isOfType(e, Type.ANCHOR));
    return asAnchorUnchecked(e);
  }

  @Override
  public TopConversationViewImpl<TopConversationDomImpl> asTopConversation(Element e) {
    Preconditions.checkArgument(isOfType(e, Type.ROOT_CONVERSATION));
    return asTopConversationUnchecked(e);
  }

  @Override
  public InlineConversationViewImpl<InlineConversationDomImpl> asInlineConversation(Element e) {
    Preconditions.checkArgument(isOfType(e, Type.INLINE_CONVERSATION));
    return asInlineConversationUnchecked(e);
  }

  @Override
  public AbstractConversationViewImpl<?, ?> asConversation(Element e) {
    if (e == null) {
      return null;
    }
    View.Type type = DomUtil.getElementType(e);
    switch (type) {
      case ROOT_CONVERSATION:
        return asTopConversationUnchecked(e);
      case INLINE_CONVERSATION:
        return asInlineConversationUnchecked(e);
      default:
        throw new IllegalArgumentException(
            "Element has a non-conversation type: " + type);
    }
  }

  @Override
  public InlineThreadView fromToggle(Element e) {
    Preconditions.checkArgument(isOfType(e, Type.TOGGLE));
    return e == null ? null : new InlineThreadViewImpl<>(inlineThreadHelper,
        InlineThreadDomImpl.ofToggle(e, cssProvider.getCollapsibleCss()) );
  }

  @Override
  public ParticipantsView participantsFromAddButton(Element e) {
    Preconditions.checkArgument(isOfType(e, Type.ADD_PARTICIPANT));
    e = DomUtil.findParentElement(e, Type.PARTICIPANTS);
    // Assume that the nearest kinded ancestor of the add button is the
    // participants view (an exception is thrown if not).
    return asParticipants(e);
  }

  @Override
  public TagsView tagsFromAddButton(Element e) {
    Preconditions.checkArgument(isOfType(e, Type.ADD_TAG));
    e = DomUtil.findParentElement(e, Type.TAGS);
    // Assume that the nearest kinded ancestor of the add button is the
    // tags view (an exception is thrown if not).
    return asTags(e);
  }

  /** @return the domRenderer for creating new sections of the DOM. */
  private DomRenderer getRenderer() {
    Preconditions.checkState(domRenderer != null, "Renderer not ready");
    return domRenderer;
  }

  /**
   * Narrows a view to the most specific common supertype of all views in this
   * package.
   *
   * @return {@code} v as a {@link DomView}.
   */
  @SuppressWarnings("unchecked")
  private AbstractStructuredView<?, ? extends DomView> narrow(View v) {
    // Note:
    // Since the view APIs only exposes closed-universe synthesis of view
    // objects, this cast is safe. This could alternatively be implemented by
    // saving all synthesized views in a map of View -> DomView, and
    // implementing this via a lookup. The existence of a non-cast
    // implementation shows that narrowing is not the same as down-casting. The
    // use of down-casting here is just an optimization, and is not logically
    // unsound like regular down-casting is.
    return (AbstractStructuredView<?, ? extends DomView>) v;
  }

  /** @return the DOM element of a view. */
  private Element elementOf(View v) {
    return v == null ? null : narrow(v).getIntrinsic().getElement();
  }

  //
  // Adapters.
  //

  /**
   * Adapts a DOM element to the view implementation from this package. The view
   * implementation is chosen based on the element's kind attribute.
   */
  private View viewOf(Element e) {
    if (e == null) {
      return null;
    }
    switch (DomUtil.getElementType(e)) {
      case ANCHOR:
        return asAnchorUnchecked(e);
      case BLIP:
        return asBlipUnchecked(e);
      case META:
        return asBlipMetaUnchecked(e);
      case ROOT_THREAD:
        return asRootThreadUnchecked(e);
      case INLINE_THREAD:
        return asInlineThreadUnchecked(e);
      case OUTLINE_THREAD:
        return asOutlineThreadUnchecked(e);
      case REPLY_BOX:
        return asRootThreadIndicatorUnchecked(e);
      case CONTINUATION_BAR:
        return asContinuationIndicatorUnchecked(e);
      case ROOT_CONVERSATION:
        return asTopConversationUnchecked(e);
      case INLINE_CONVERSATION:
        return asInlineConversationUnchecked(e);
      case PARTICIPANT:
        return asParticipant(e);
      case PARTICIPANTS:
        return asParticipants(e);
      case TAG:
        return asTag(e);
      case TAGS:
        return asTags(e);
      default:
        throw new AssertionError();
    }
  }

  private RootThreadViewImpl<RootThreadDomImpl> asRootThreadUnchecked(Element e) {
    return e == null ? null : new RootThreadViewImpl<>(rootThreadHelper,
        RootThreadDomImpl.of(e));
  }

  private ReplyBoxViewImpl<ReplyBoxDomImpl> asRootThreadIndicatorUnchecked(Element e) {
    return e == null ? null : new ReplyBoxViewImpl<>(rootThreadIndicatorHelper,
        ReplyBoxDomImpl.of(e));
  }

  private InlineThreadViewImpl<InlineThreadDomImpl> asInlineThreadUnchecked(Element e) {
    return e == null ? null : new InlineThreadViewImpl<>(
        inlineThreadHelper, InlineThreadDomImpl.of(e, cssProvider.getCollapsibleCss()));
  }

  private OutlineThreadViewImpl<OutlineThreadDomImpl> asOutlineThreadUnchecked(Element e) {
    return e == null ? null : new OutlineThreadViewImpl<>(outlineThreadHelper,
        OutlineThreadDomImpl.of(e));
  }

  private ContinuationIndicatorViewImpl<ContinuationIndicatorDomImpl>
      asContinuationIndicatorUnchecked(Element e) {
    return e == null ? null : new ContinuationIndicatorViewImpl<>(
        continuationIndicatorHelper, ContinuationIndicatorDomImpl.of(e));
  }

  private AnchorViewImpl<AnchorDomImpl> asAnchorUnchecked(Element e) {
    return e == null ? null : new AnchorViewImpl<>(anchorHelper, AnchorDomImpl.of(e));
  }

  private BlipViewImpl<BlipViewDomImpl> asBlipUnchecked(Element e) {
    return e == null ? null : new BlipViewImpl<>(blipHelper,
        BlipViewDomImpl.of(e, cssProvider.getBlipCss()) );
  }

  private PlaceholderViewImpl<PlaceholderViewDomImpl> asPlaceholderUnchecked(Element e) {
    return e == null ? null : new PlaceholderViewImpl<>(placeholderHelper,
        PlaceholderViewDomImpl.of(e, cssProvider.getConversationCss()) );
  }

  private ParticipantViewImpl<ParticipantAvatarDomImpl> asParticipantUnchecked(Element e) {
    return e == null ? null : new ParticipantViewImpl<>(participantHelper,
        ParticipantAvatarDomImpl.of(e));
  }

  private ParticipantsViewImpl<ParticipantsDomImpl> asParticipantsUnchecked(Element e) {
    return e == null ? null : new ParticipantsViewImpl<>(participantsHelper,
        ParticipantsDomImpl.of(e));
  }

  private TagViewImpl<TagDomImpl> asTagUnchecked(Element e) {
    return e == null ? null : new TagViewImpl<>(tagHelper, TagDomImpl.of(e));
  }

  private TagsViewImpl<TagsDomImpl> asTagsUnchecked(Element e) {
    return e == null ? null : new TagsViewImpl<>(tagsHelper, TagsDomImpl.of(e));
  }

  private BlipMetaViewImpl<BlipMetaDomImpl> asBlipMetaUnchecked(Element e) {
    return e == null ? null : new BlipMetaViewImpl<>(metaHelper,
        BlipMetaDomImpl.of(e, cssProvider.getBlipCss()));
  }

  private TopConversationViewImpl<TopConversationDomImpl> asTopConversationUnchecked(Element e) {
    return e == null ? null : new TopConversationViewImpl<>(convHelper,
        TopConversationDomImpl.of(e));
  }

  private InlineConversationViewImpl<InlineConversationDomImpl> asInlineConversationUnchecked(
      Element e) {
    return e == null ? null : new InlineConversationViewImpl<>(inlineConvHelper,
        InlineConversationDomImpl.of(e, cssProvider.getCollapsibleCss()));
  }
  
  private AnchorView asAnchor(View v) {
    Preconditions.checkArgument(v == null || v.getType() == Type.ANCHOR);
    return (AnchorView) v;
  }

  private BlipView asBlip(View v) {
    Preconditions.checkArgument(v == null || v.getType() == Type.BLIP);
    return (BlipView) v;
  }

  private ThreadView asThread(View v) {
    if (v != null) {
      Type type = v.getType();
      Preconditions.checkArgument(type == Type.ROOT_THREAD || type == Type.INLINE_THREAD ||
          type == Type.OUTLINE_THREAD);
    }
    return (ThreadView) v;
  }

  private RootThreadView asRootThread(View v) {
    Preconditions.checkArgument(v == null || v.getType() == Type.ROOT_THREAD);
    return (RootThreadView) v;
  }

  private ConversationView asConversation(View v) {
    Preconditions.checkArgument(v == null || v.getType() == Type.ROOT_CONVERSATION
        || v.getType() == Type.INLINE_CONVERSATION);
    return (ConversationView) v;
  }
  
  //
  // Shared structure.
  //

  private boolean isOfType(Element e, Type type) {
    return e == null || DomUtil.doesElementHaveType(e, type);
  }

  private View parentOf(DomView view) {
    Element e = view.getElement().getParentElement();
    while (e != null && DomUtil.getElementType(e) == null) {
      e = e.getParentElement();
    }
    return viewOf(e);
  }

  private View parentOf(DomView view, Type parentType) {
    Element e = view.getElement().getParentElement();
    while (e != null && !DomUtil.doesElementHaveType(e, parentType)) {
      e = e.getParentElement();
    }
    return viewOf(e);
  }

  private View parentOf(DomView view, List<Type> parentTypes) {
    Element e = view.getElement().getParentElement();
    while (e != null && !parentTypes.contains(DomUtil.getElementType(e)) ) {
      e = e.getParentElement();
    }
    return viewOf(e);
  }

  // Kick Webkit, because of its incremental layout bugs.
  private static void kickWebkit(Element element) {
    if (UserAgent.isWebkit()) {

      Style style = element.getStyle();
      String oldDisplay = style.getDisplay();

      // Erase layout. Querying getOffsetParent() forces layout.
      style.setDisplay(Display.NONE);
      element.getOffsetParent();

      // Restore layout.
      style.setProperty("display", oldDisplay);
    }
  }

  public BlipView insertBlip(Element blipContainer, ConversationBlip blip, View neighbor,
      boolean beforeNeighbor) {
    Element blipElement = domRenderer.render(blip);
    Element neighborElement = elementOf(neighbor);
    insert(blipContainer, blipElement, neighborElement, beforeNeighbor);
    return asBlip(blipElement);
  }

  public PlaceholderView insertPlaceholder(Element blipContainer, View neighbor,
      boolean beforeNeighbor) {
    Element placeholderElement = domRenderer.renderPlaceholder();
    Element neighborElement = elementOf(neighbor);
    insert(blipContainer, placeholderElement, neighborElement, beforeNeighbor);
    return asPlaceholder(placeholderElement);
  }
}
