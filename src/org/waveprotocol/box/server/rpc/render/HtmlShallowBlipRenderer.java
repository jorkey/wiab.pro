/**
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.waveprotocol.box.server.rpc.render;


import org.waveprotocol.box.server.rpc.render.account.ProfileManager;
import org.waveprotocol.box.server.rpc.render.common.safehtml.SafeHtml;
import org.waveprotocol.box.server.rpc.render.renderer.ShallowBlipRenderer;
import org.waveprotocol.box.server.rpc.render.view.IntrinsicBlipMetaView;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.supplement.ReadableSupplementedWave;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import org.waveprotocol.wave.model.operation.OperationException;

/**
 * Defines the shallow blip rendering for the server sside.
 *
 *@author yurize@apache.org (Yuri Zelikov)
 */
public final class HtmlShallowBlipRenderer implements ShallowBlipRenderer {
  private static final int MAX_CONTRIBUTORS = 3;
  private final static long SEC_MS = 1000;
  private final static long MIN_MS = 60 * SEC_MS;
  private final static long HOUR_MS = 60 * MIN_MS;

  /** Provides names and avatars of participants. */
  private final ProfileManager manager;

  /** Provides read state of blips. */
  private final ReadableSupplementedWave supplement;
  
  

  /**
   * Defines the rendering function for the contents of a blip.
   */
  public interface DocumentRenderer {
    SafeHtml render(String blipId, Document doc);
  }

  public HtmlShallowBlipRenderer(
      ProfileManager manager, ReadableSupplementedWave supplement) {
    this.manager = manager;
    this.supplement = supplement;
  }

  @Override
  public void render(ConversationBlip blip, IntrinsicBlipMetaView view) {
//    blip.hackGetRaw().get
    renderContributors(blip, view);
    renderTime(blip, view);
    renderRead(blip, view);
  }

  @Override
  public void renderContributors(ConversationBlip blip, IntrinsicBlipMetaView meta) {
    Set<ParticipantId> contributors = blip.getContributorIds();
    if (!contributors.isEmpty()) {
      meta.setAvatar(
          avatarOf(contributors.iterator().next()), 
          blip.getAuthorId().getAddress());
      meta.setMetaline(buildNames(contributors));
    } else {
      // Blips are never meant to have no contributors.  The wave state is broken.
      meta.setAvatar(null, null);
      meta.setMetaline("anon");
    }
  }

  @Override
  public void renderTime(ConversationBlip blip, IntrinsicBlipMetaView meta) {
    meta.setTime(formatPastDate(new Date(blip.getLastModifiedTime()), new Date()));
  }
  
  /**
   * Package-private version, takes a fixed "now" time - used for testing
   */
  public static String formatPastDate(Date date, Date now) {

    // NOTE(zdwang): For now skip it for junit code; also see formatDateTime()
    if (isRecent(date, now) || onSameDay(date, now)) {
      SimpleDateFormat dateFmt = new SimpleDateFormat("hh:mm");
      return   dateFmt.format(date); // AM/PM -> am/pm
    } else {
      SimpleDateFormat monthFmt = new SimpleDateFormat("dd MMM yyyy");
      return monthFmt.format(date);
    }
  }
  
  /**
   * @return true if a duration is less than six hours.
   */
  private static boolean isRecent(Date date, Date now) {
   return (now.getTime() - date.getTime()) < 6 * HOUR_MS;
  }

 /**
  * @return true if a date occurs on the same day as today.
  */
  private static boolean onSameDay(Date date, Date now) {
    return (date.getDate() == now.getDate())
        && (date.getMonth() == now.getMonth())
        && (date.getYear() == now.getYear());
  }

  /**
   * @return true if a date occurs in the same year as this year.
   */
  private boolean isSameYear(Date date, Date now) {
    return date.getYear() == now.getYear();
  }

  @Override
  public void renderRead(ConversationBlip blip, IntrinsicBlipMetaView blipUi) {
    blipUi.setRead(!supplement.isUnread(blip));
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
}
