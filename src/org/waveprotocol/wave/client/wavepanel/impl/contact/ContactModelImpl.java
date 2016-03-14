/**
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
package org.waveprotocol.wave.client.wavepanel.impl.contact;

import org.waveprotocol.wave.client.account.Profile;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * A widget for displaying contacts.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
class ContactModelImpl implements ContactModel {
  private final ParticipantId participantId;
  private final Profile profile;
  private String fullName;

  public ContactModelImpl(ParticipantId participantId, Profile profile) {
    this.participantId = participantId;
    this.profile = profile;
  }

  @Override
  public ParticipantId getParticipantId() {
    return participantId;
  }

  @Override
  public String getFullName() {
    //return profile.getFullName();
    return null;
  }

  @Override
  public String getImageUrl() {
    return profile.getImageUrl();
  }

  @Override
  public Pair<Integer, Integer> matchParticipant(String pattern) {
    if (!getParticipantId().getAddress().trim().equals(getParticipantId().getAddress())) {
      return null;
    }
    return match(getParticipantId().getAddress(), pattern);
  }

  @Override
  public Pair<Integer, Integer> matchFullName(String pattern) {
    return match(getFullName(), pattern);
  }

  private static Pair<Integer, Integer> match(String text, String pattern) {
    if (text == null) {
      return null;
    }
    int start, end;
    if (pattern == null || pattern.isEmpty()) {
      start = end = 0;
    } else {
      start = text.toLowerCase().indexOf(pattern.toLowerCase());
      if (start != -1) {
        end = start + pattern.length();
      } else {
        return null;
      }
    }
    return Pair.of(start, end);
  }
}