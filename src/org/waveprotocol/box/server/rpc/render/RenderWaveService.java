/**
 * Copyright 2011 Google Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.waveprotocol.box.server.rpc.render;

import com.google.wave.api.Blip;
import com.google.wave.api.BlipData;
import com.google.wave.api.BlipThread;
import com.google.wave.api.InvalidRequestException;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.ParticipantProfile;
import com.google.wave.api.data.converter.ContextResolver;
import com.google.wave.api.data.converter.EventDataConverter;
import com.google.wave.api.impl.EventMessageBundle;
import com.google.wave.api.impl.WaveletData;

import org.waveprotocol.box.server.robots.OperationContext;
import org.waveprotocol.box.server.robots.operations.FetchProfilesService.ProfilesFetcher;
import org.waveprotocol.box.server.robots.operations.OperationService;
import org.waveprotocol.box.server.rpc.render.FullHtmlWaveRenderer.DocRefRenderer;
import org.waveprotocol.box.server.rpc.render.account.impl.ProfileImpl;
import org.waveprotocol.box.server.rpc.render.account.impl.ProfileManagerImpl;
import org.waveprotocol.box.server.rpc.render.common.safehtml.EscapeUtils;
import org.waveprotocol.box.server.rpc.render.uibuilder.UiBuilder;
import org.waveprotocol.box.server.rpc.render.view.ModelIdMapperImpl;
import org.waveprotocol.box.server.rpc.render.view.ViewFactory;
import org.waveprotocol.box.server.rpc.render.view.ViewIdMapper;
import org.waveprotocol.box.server.rpc.render.view.builder.BlipViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.CollapsibleBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.ContinuationIndicatorViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.FlowConversationViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.InlineConversationViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.ParticipantsViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.ReplyBoxViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.RootThreadViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.TagsViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.TopConversationViewBuilder;
import org.waveprotocol.box.server.rpc.render.view.builder.WavePanelResources;
import org.waveprotocol.box.server.rpc.render.web.text.ContentRenderer;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.supplement.ReadableSupplementedWave;
import org.waveprotocol.wave.model.supplement.ScreenPosition;
import org.waveprotocol.wave.model.supplement.SimpleWantedEvaluationSet;
import org.waveprotocol.wave.model.supplement.ThreadState;
import org.waveprotocol.wave.model.supplement.WantedEvaluation;
import org.waveprotocol.wave.model.supplement.WantedEvaluationSet;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.IdentityMap;
import org.waveprotocol.wave.model.util.ReadableStringMap;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.Wavelet;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.model.waveref.WaveRef;
import org.waveprotocol.wave.util.escapers.jvm.JavaWaverefEncoder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@link OperationService} for the "fetchWave" operation.
 *
 * @author yurize@apache.org (Yuri Zelikov)
 */
public class RenderWaveService implements OperationService {


  private static final WavePanelResources RESOURCES = new WavePanelResources() {

    @Override
    public RootThreadViewBuilder.Resources getRootThread() {
      return new RootThreadViewBuilder.Resources() {

        @Override
        public RootThreadViewBuilder.Css css() {
          return makeCssProxy(RootThreadViewBuilder.Css.class);
        }
      };
    }

    @Override
    public ReplyBoxViewBuilder.Resources getReplyBox() {
      return new ReplyBoxViewBuilder.Resources() {

        @Override
        public ReplyBoxViewBuilder.Css css() {
          return makeCssProxy(ReplyBoxViewBuilder.Css.class);
        }
      };
    }

    @Override
    public ParticipantsViewBuilder.Resources getParticipants() {
      return new ParticipantsViewBuilder.Resources() {

        @Override
        public WaveImageResource expandButton() {
          return null;
        }

        @Override
        public org.waveprotocol.box.server.rpc.render.view.builder.ParticipantsViewBuilder.Css css() {
          return makeCssProxy(ParticipantsViewBuilder.Css.class);
        }

        @Override
        public WaveImageResource collapseButton() {
          return null;
        }

        @Override
        public WaveImageResource addButton() {
          return null;
        }
      };
    }

    @Override
    public TagsViewBuilder.Resources getTags() {
      return new TagsViewBuilder.Resources() {

        @Override
        public TagsViewBuilder.Css css() {
          return makeCssProxy(TagsViewBuilder.Css.class);
        }

        @Override
        public WaveImageResource expandButton() {
          return null;
        }

        @Override
        public WaveImageResource collapseButton() {
          return null;
        }

        @Override
        public WaveImageResource addButton() {
          return null;
        }

        @Override
        public WaveImageResource deleteButton() {
          return null;
        }
      };
    }

    @Override
    public TopConversationViewBuilder.Resources getConversation() {
      return new TopConversationViewBuilder.Resources() {

        @Override
        public WaveImageResource emptyToolbar() {
          return null;
        }

        @Override
        public TopConversationViewBuilder.Css css() {
          return makeCssProxy(TopConversationViewBuilder.Css.class);
        }
      };
    }

    @Override
    public ContinuationIndicatorViewBuilder.Resources getContinuationIndicator() {
      return new ContinuationIndicatorViewBuilder.Resources() {

        @Override
        public ContinuationIndicatorViewBuilder.Css css() {
          return makeCssProxy(ContinuationIndicatorViewBuilder.Css.class);
        }

        @Override
        public WaveImageResource continuationIcon() {
          return null;
        }
      };
    }

    @Override
    public CollapsibleBuilder.Resources getCollapsible() {
      return new CollapsibleBuilder.Resources() {

        @Override
        public WaveImageResource expandedUnread() {
          return null;
        }

        @Override
        public WaveImageResource expandedRead() {
          return null;
        }

        @Override
        public CollapsibleBuilder.Css css() {
          return makeCssProxy(CollapsibleBuilder.Css.class);
        }

        @Override
        public WaveImageResource collapsedUnread() {
          return null;
        }

        @Override
        public WaveImageResource collapsedRead() {
          return null;
        }

        @Override
        public WaveImageResource callout() {
          return null;
        }
      };
    }

    @Override
    public BlipViewBuilder.Resources getBlip() {
      return new BlipViewBuilder.Resources() {

        @Override
        public BlipViewBuilder.Css css() {
          return makeCssProxy(BlipViewBuilder.Css.class);
        }

        @Override
        public WaveImageResource menuButton() {
          return null;
        }
      };
    }
  };

  public static ReadableSupplementedWave EMPTY_SUPPLEMENTED_WAVE =
      new ReadableSupplementedWave() {

    @Override
    public boolean isUnread(ConversationBlip blip) {
      return false;
    }

    @Override
    public boolean wasBlipEverRead(ConversationBlip blip) {
      return false;
    }

    @Override
    public boolean isTrashed() {
      return false;
    }

    @Override
    public boolean isParticipantsUnread(WaveletId waveletId) {
      return false;
    }

    @Override
    public boolean isTagsUnread(WaveletId waveletId) {
      return false;
    }

    @Override
    public boolean wasTagsEverRead(WaveletId waveletId) {
      return false;
    }

    @Override
    public boolean isMute() {
      return false;
    }

    @Override
    public boolean isInbox() {
      return false;
    }

    @Override
    public boolean isFollowed() {
      return false;
    }

    @Override
    public boolean isArchived() {
      return false;
    }

    @Override
    public boolean haveParticipantsEverBeenRead(WaveletId waveletId) {
      return false;
    }

    @Override
    public boolean hasPendingNotification() {
      return false;
    }

    @Override
    public boolean hasBeenSeen() {
      return false;
    }

    @Override
    public WantedEvaluationSet getWantedEvaluationSet(WaveletId waveletId) {
      return new SimpleWantedEvaluationSet(waveletId, (Collection<WantedEvaluation>) null);
    }

    @Override
    public ThreadState getThreadState(ConversationThread thread) {
      return ThreadState.EXPANDED;
    }

    @Override
    public HashedVersion getSeenVersion(WaveletId id) {
      return HashedVersion.unsigned(0);
    }

    @Override
    public String getGadgetStateValue(String gadgetId, String key) {
      return "";
    }

    @Override
    public ReadableStringMap<String> getGadgetState(String gadgetId) {
      return CollectionUtils.emptyMap();
    }

    @Override
    public Set<Integer> getFolders() {
      return Collections.emptySet();
    }

    @Override
    public String getFocusedBlipId(WaveletId waveletId) {
      return null;
    }

    @Override
    public ScreenPosition getScreenPosition(WaveletId waveletId) {
      return null;
    }

    @Override
    public boolean isWaveletLooked(WaveletId waveletId) {
      return false;
    }

    @Override
    public long getLastReadWaveletVersion(WaveletId waveletId) {
      return -1;
    }

    @Override
    public boolean isBlipLooked(ConversationBlip blip) {
      return false;
    }

    @Override
    public boolean isBlipLooked(WaveletId waveletId, String blipId, long version) {
      return false;
    }
  };

  /**
   * A ViewFactory that creates views suitable for embedding in a fixed-height
   * context.
   */
  public static final ViewFactory FIXED = new ViewFactory() {

    @Override
    public TopConversationViewBuilder createTopConversationView(
        String id, UiBuilder threadUi, UiBuilder participantsUi,
        UiBuilder tagsUi) {
      return FlowConversationViewBuilder.createRoot(
          RESOURCES, id, threadUi, participantsUi, tagsUi);
    }

    @Override
    public final InlineConversationViewBuilder createInlineConversationView(
        String id, UiBuilder threadUi, UiBuilder participantsUi) {
      return InlineConversationViewBuilder.create(
          RESOURCES, id, participantsUi, threadUi);
    }
  };

  public static final DocRefRenderer HTML_DOC_RENDERER = new DocRefRenderer() {

    @Override
    public UiBuilder render(
        ConversationBlip blip,
        IdentityMap<ConversationThread, UiBuilder> replies) {
      return UiBuilder.Constant.of(
          EscapeUtils.fromSafeConstant("[" + blip.getId() + "]"));
    }
  };

  @SuppressWarnings("unchecked")
  private static <T> T makeCssProxy(Class<T> clazz) {
    return (T) Proxy.newProxyInstance(clazz.getClassLoader(),
        new Class<?>[] { clazz },
        new InvocationHandler() {

      @Override
      public Object invoke(Object o, Method method, Object[] objects)
          throws Throwable {
        return method.getName();
      }
    });
  }


  private RenderWaveService() {
  }

  public String exec(
      WaveId waveId, WaveletId waveletId, String blipId,
      ParticipantId participant, OperationContext context)
      throws InvalidRequestException {

    final OpBasedWavelet opBasedWavelet = context.openWavelet(waveId, waveletId, participant);
    final ObservableConversationView conversationView =
        context.getConversationUtil().buildConversation(opBasedWavelet);
    final ProfileManagerImpl profileManagerImpl = new ProfileManagerImpl() {

      ProfilesFetcher profileFetcher = ProfilesFetcher.SIMPLE_PROFILES_FETCHER;

      @Override
      public ProfileImpl getProfile(ParticipantId participantId) {
        ParticipantProfile participantProfile =
            profileFetcher.fetchProfile(participantId.getAddress());
        ProfileImpl profile = new ProfileImpl(null, participantId);
        profile.update(participantProfile.getName(),
            participantProfile.getName(), participantProfile.getImageUrl());
        return profile;
      }
    };
    HtmlThreadReadStateMonitorImpl readStateMonitor = HtmlThreadReadStateMonitorImpl.create(
            ServiceUtil.buildSupplement(waveId, waveletId, context, participant), conversationView);
    String path = JavaWaverefEncoder.encodeToUriPathSegment(WaveRef.of(
        waveId, waveletId));
    HtmlRenderer renderer = FullHtmlWaveRendererImpl.create(
            conversationView, profileManagerImpl,
            new HtmlShallowBlipRenderer(profileManagerImpl, EMPTY_SUPPLEMENTED_WAVE),
            new ViewIdMapper(ModelIdMapperImpl.create(conversationView, "UC")), readStateMonitor,
            FIXED, HTML_DOC_RENDERER, RESOURCES, "/#" + path);

    String html;
    ObservableConversation conversation = conversationView.getRoot();
    if (blipId == null) {
      html = renderer.renderWave(conversationView);
    } else {
      ConversationBlip blip = conversation.getBlip(blipId);
      html = renderer.renderBlip(blip);
    }

    EventMessageBundle messages =
      mapWaveletToMessageBundle(context.getConverter(), participant, opBasedWavelet, conversation);

    Map<String, Blip> blips = new HashMap<>();
    Map<String, BlipThread> threads = new HashMap<>();
    WaveletData waveletData = context.getConverter().toWaveletData(opBasedWavelet, conversation,
        messages);
    com.google.wave.api.Wavelet wavelet = com.google.wave.api.Wavelet.deserialize(null, blips,
        threads,  waveletData);

    threads.putAll(messages.getThreads());
    for (Map.Entry<String, BlipData> entry : messages.getBlipData().entrySet()) {
      BlipData blipData = context.getConverter().toBlipData(conversation.getBlip(entry.getKey()),
          opBasedWavelet, messages);
      Blip tempBlip = Blip.deserialize(null, wavelet, blipData);
      blips.put(tempBlip.getBlipId(), tempBlip);
    }

    ContentRenderer contentRenderer = new ContentRenderer();
    for (Map.Entry<java.lang.String,com.google.wave.api.Blip> entry : wavelet.getBlips().entrySet()) {
      com.google.wave.api.Blip blip = entry.getValue();
      String blipHtml = contentRenderer.renderHtml(blip.getContent(), blip.getAnnotations(),
          blip.getElements(), blip.getContributors());
      // TODO (Yuri Z.) Make it more efficient and safe.
      String htmlId = "[" + blip.getBlipId() + "]";
      html = html.replace(htmlId, blipHtml);
    }
    return html;
  }

  @Override
  public void execute(OperationRequest operation, OperationContext context,
      ParticipantId participant) throws InvalidRequestException {
//    OpBasedWavelet wavelet = null;
//    try {
//      wavelet = context.openWavelet(operation, participant);
//    } catch (InvalidRequestException e) {
//      context.constructErrorResponse(operation, e.getMessage());
//      return;
//    }
//    String blipId = OperationUtil.getOptionalParameter(operation, ParamsProperty.BLIP_ID);
//    ObservableConversationView conversationView =
//        context.getConversationUtil().buildConversation(wavelet);
//    ProfileManagerImpl profileManagerImpl = new ProfileManagerImpl();
//    HtmlThreadReadStateMonitorImpl readStateMonitor =
//      HtmlThreadReadStateMonitorImpl.create(ServiceUtil.buildSupplement(operation, context, participant), conversationView);
//    HtmlRenderer renderer =
//        FullHtmlWaveRendererImpl.create(conversationView, profileManagerImpl,
//            new HtmlShallowBlipRenderer(profileManagerImpl, EMPTY_SUPPLEMENTED_WAVE),
//            new ViewIdMapper(ModelIdMapperImpl.create(conversationView, "UC")), null,
//            readStateMonitor, FIXED, HTML_DOC_RENDERER, RESOURCES);
//
//    String html = null;
//    if (blipId == null) {
//      html = renderer.render(conversationView);
//    } else {
//      ObservableConversation conversation =
//          context.openConversation(operation, participant).getRoot();
//      ConversationBlip blip = conversation.getBlip(blipId);
//      html = renderer.render(blip);
//    }
//
//    Map<ParamsProperty, Object> data =
//        ImmutableMap.<ParamsProperty, Object> of(ParamsProperty.RENDER_RESULT, html);
//    context.constructResponse(operation, data);

  }

  /**
   * Maps a wavelet and its conversation to a new {@link EventMessageBundle}.
   *
   * @param converter to convert to API objects.
   * @param participant the participant who the bundle is for.
   * @param wavelet the wavelet to put in the bundle.
   * @param conversation the conversation to put in the bundle.
   */
  private EventMessageBundle mapWaveletToMessageBundle(EventDataConverter converter,
      ParticipantId participant, Wavelet wavelet, Conversation conversation) {
    EventMessageBundle messages = new EventMessageBundle(participant.getAddress(), "");
    WaveletData waveletData = converter.toWaveletData(wavelet, conversation, messages);
    messages.setWaveletData(waveletData);
    ContextResolver.addAllBlipsToEventMessages(messages, conversation, wavelet, converter);
    return messages;
  }

  public static RenderWaveService create() {
    return new RenderWaveService();
  }
}