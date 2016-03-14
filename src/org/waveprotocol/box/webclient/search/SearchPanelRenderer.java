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

import org.waveprotocol.wave.client.account.Profile;
import org.waveprotocol.wave.client.account.ProfileListener;
import org.waveprotocol.wave.client.account.ProfileManager;
import org.waveprotocol.wave.client.common.util.DateUtils;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Renders a digest model into a digest view.
 *
 * @author hearnden@google.com (David Hearnden)
 */
public final class SearchPanelRenderer implements ProfileListener, Search.Listener {
  
  private final static int MAX_AVATARS = 2;

  /** Profile provider, for avatars. */
  private final ProfileManager profileManager;

  private final Map<Profile, Set<Digest>> profileDigestsMap = new HashMap<>();
  private final Map<Digest, DigestView> digestViewMap = new HashMap<>();

  public SearchPanelRenderer(ProfileManager profileManager) {
    this.profileManager = profileManager;
    profileManager.addListener(this);
  }

  /**
   * Renders a digest model into a digest view.
   */
  public void render(Digest digest, DigestView digestUi) {
    Collection<Profile> profiles = getAvatars(digest);

    for (Profile profile : profiles) {
      Set<Digest> profileDigests = profileDigestsMap.get(profile);
      if (profileDigests == null) {
        profileDigests = new HashSet<>();
        profileDigestsMap.put(profile, profileDigests);
      }
      profileDigests.add(digest);
    }

    digestUi.setAvatars(profiles);
    digestUi.setText(digest.getTitle(), digest.getSnippet());
    digestUi.setMessageCounts(digest.getUnreadCount(), digest.getBlipCount());
    digestUi.setTimestamp(DateUtils.getInstance().shortFormatPastDate(
        (long) digest.getLastModifiedTime()));

    digestViewMap.put(digest, digestUi);    
  }

  @Override
  public void onProfileUpdated(Profile profile) {
    Set<Digest> digests = profileDigestsMap.get(profile);
    if (digests != null) {
      for (Digest digest : digests) {
        DigestView digestView = digestViewMap.get(digest);
        if (digestView != null) {
          digestView.setAvatars(getAvatars(digest));
        }
      }
    }
  }

  private Collection<Profile> getAvatars(Digest digest) {
    Collection<Profile> avatars = CollectionUtils.createQueue();
    if (digest.getAuthor() != null) {
      avatars.add(profileManager.getProfile(digest.getAuthor()));
    }
    for (ParticipantId participant : digest.getParticipantsSnippet()) {
      if (avatars.size() == MAX_AVATARS) {
        break;
      }
      if (!participant.equals(digest.getAuthor())) {
        avatars.add(profileManager.getProfile(participant));
      }
    }
    return  avatars;
  }

  @Override
  public void onStateChanged() {
  }

  @Override
  public void onDigestReady(int index, Digest digest) {
  }

  @Override
  public void onDigestAdded(int index, Digest digest) {
  }

  @Override
  public void onDigestRemoved(int index, Digest digest) {
    Collection<Profile> profiles = getAvatars(digest);
    for (Profile profile : profiles) {
      Set<Digest> digests = profileDigestsMap.get(profile);
      if (digests != null) {
        digests.remove(digest);
      }
    }
    digestViewMap.remove(digest);
  }

  @Override
  public void onTotalChanged(int total) {
  }
}