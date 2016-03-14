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

package org.waveprotocol.wave.client.wavepanel.render;

import org.waveprotocol.wave.client.account.ProfileManager;
import org.waveprotocol.wave.client.common.util.DateUtils;
import org.waveprotocol.wave.client.doodad.diff.DiffAnnotationHandler;
import org.waveprotocol.wave.client.editor.content.DiffHighlightingFilter;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicBlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicBlipView;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationBlip;
import org.waveprotocol.wave.model.supplement.ReadableSupplementedWave;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.Date;
import java.util.Iterator;
import java.util.Set;

/**
 * Defines the shallow blip rendering for the Undercurrent UI.
 *
 */
public final class UndercurrentShallowBlipRenderer implements ShallowBlipRenderer {

  /** Maximum number of contributors shown in the blip. */
  private static final int MAX_CONTRIBUTORS = 3;

  /** Top margin of the first blip in inline thread. */
  private static final int INLINE_TOP_MARGIN_PX = 20;

  /** Top margin of the first blip in outline thread. */
  private static final int OUTLINE_TOP_MARGIN_PX = 10;
  
  /** Top margin to deparate blip from predecessor's children. */
  private static final int SEPARATE_TOP_MARGIN_PX = 10;
  
  /** Bottom margin to separate the blip from its successors. */
  private static final int INLINE_BOTTOM_MARGIN_PX = 14;  
  
  /** Provides names and avatars of participants. */
  private final ProfileManager manager;
  /** Provides read state of blips. */
  private final ReadableSupplementedWave supplement;
  /** Navigator. */
  private final ConversationNavigator navigator;

  public UndercurrentShallowBlipRenderer(ProfileManager manager, ReadableSupplementedWave supplement,
      ConversationNavigator navigator) {
    this.manager = manager;
    this.supplement = supplement;
    this.navigator = navigator;
  }

  @Override
  public void render(ConversationBlip blip, IntrinsicBlipView blipView,
      IntrinsicBlipMetaView metaView) {
    renderStyle(blip, blipView);
    renderContributors(blip, metaView);
    renderTime(blip, metaView);
    renderRead(blip, metaView);
    renderFrame(blip, metaView);

    QuasiConversationBlip quasiBlip = (QuasiConversationBlip) blip;
    if (quasiBlip.isQuasiDeleted()) {
      String deleteTitle = DiffAnnotationHandler.formatOperationContext(
          DiffHighlightingFilter.DIFF_DELETE_KEY, quasiBlip.getQuasiDeletionContext());
      QuasiConversationBlip quasiRowOwnerBlip =
          (QuasiConversationBlip) navigator.getBlipRowOwnerBlip(blip);
      blipView.setQuasiDeleted(deleteTitle, quasiRowOwnerBlip != null &&
          quasiRowOwnerBlip.isQuasiDeleted());
    }
  }

  @Override
  public void renderContributors(ConversationBlip blip, IntrinsicBlipMetaView meta) {
    Set<ParticipantId> contributors = blip.getContributorIds();
    if (!contributors.isEmpty()) {
      meta.setAvatar(avatarOf(contributors.iterator().next()),
          nameOf(contributors.iterator().next()));
      meta.setMetaline(buildNames(contributors));
    } else {
      // Blips are never meant to have no contributors.  The wave state is broken.
      meta.setAvatar("", "");
      meta.setMetaline("anon");
    }
  }

  @Override
  public void renderTime(ConversationBlip blip, IntrinsicBlipMetaView meta) {
    long lastModifiedTime = blip.getLastModifiedTime();
    meta.setTime(DateUtils.getInstance().shortFormatPastDate(lastModifiedTime == 0
        // Blip sent using c/s protocol, which has no timestamp attached (WAVE-181).
        // Using received time as an estimate of the sent time.
        ? new Date().getTime()
        : lastModifiedTime));
  }

  @Override
  public void renderRead(ConversationBlip blip, IntrinsicBlipMetaView blipUi) {
    blipUi.setRead(!supplement.isUnread(blip));
  }

  @Override
  public void renderFrame(ConversationBlip blip, IntrinsicBlipMetaView metaView) {
    boolean top = false;
    boolean right = false;
    boolean bottom = false;
    boolean left = false;
    boolean isFirstBlip = false;
    if (blip != null) {
      int level = navigator.getBlipLevel(blip);
      ConversationBlip prevBlip = navigator.getPreviousBlipInParentThread(blip);
      isFirstBlip = prevBlip == null || hasOutlineChildBlips(prevBlip);
      // not first blip or the first outline blip of the root blip
      top = !isFirstBlip || (!blip.getThread().isInline() && level == 1);
      bottom = level != 0 && (hasOutlineChildBlips(blip) || navigator.isBlipLastInParentThread(blip));
      left = level != 0;
    }
    metaView.setBorders(top, right, bottom, left, isFirstBlip);
  }

  @Override
  public void renderStyle(ConversationBlip blip, IntrinsicBlipView blipView) {
    blipView.setIndentationLevel(navigator.getBlipIndentationLevel(blip));
    ConversationThread thread = blip.getThread();
    ConversationBlip prevBlip = navigator.getPreviousBlipInParentThread(blip);
    int topMargin = 0;
    if (prevBlip == null && thread.isInline()) {
      // Top inline blip.
      topMargin = INLINE_TOP_MARGIN_PX;
    } else if (prevBlip == null && !thread.isInline() && navigator.getBlipLevel(blip) > 1) {
      // Top outline blip.
      topMargin = OUTLINE_TOP_MARGIN_PX;
    } else if (prevBlip != null && hasOutlineChildBlips(prevBlip)) {
      // Next blip after predecessor blip's children.
      topMargin = SEPARATE_TOP_MARGIN_PX;
    }
    int bottomMargin = 0;
    ConversationThread rowOwnerThread = navigator.getBlipRowOwnerThread(blip);
    if (rowOwnerThread.isInline() && navigator.getNextBlipInRow(blip) == null) {
      // The last blip in the inline row.
      bottomMargin = INLINE_BOTTOM_MARGIN_PX;
    }
    blipView.setMargins(topMargin, bottomMargin);
  }

  /**
   * @return the rich text for the contributors in a blip.
   */
  private String buildNames(Set<ParticipantId> contributors) {
    StringBuilder names = new StringBuilder();
    int i = 0;
    for (ParticipantId contributor : contributors) {
      if (i >= MAX_CONTRIBUTORS) {
        break;
      } else if (manager.shouldIgnore(contributor)) {
        continue;
      }

      if (i > 0) {
        names.append(", ");
      }
      names.append(nameOf(contributor));
      i++;
    }
    return names.toString();
  }

  private String nameOf(ParticipantId contributor) {
    return manager.getProfile(contributor).getFirstName();
  }

  private String avatarOf(ParticipantId contributor) {
    return manager.getProfile(contributor).getImageUrl();
  }
  
  private static boolean hasOutlineChildBlips(ConversationBlip blip) {
    Iterator<? extends ConversationThread> it = blip.getReplyThreads().iterator();
    while (it.hasNext()) {
      ConversationThread thread = it.next();
      if (!thread.isInline() && thread.getFirstBlip() != null) {
        return true;
      }
    }
    return false;
  }  
}
