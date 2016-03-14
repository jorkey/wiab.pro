/**
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.waveprotocol.box.server.rpc.render;

import com.google.wave.api.InvalidRequestException;

import org.waveprotocol.box.server.robots.OperationContext;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.supplement.PrimitiveSupplement;
import org.waveprotocol.wave.model.supplement.SupplementedWave;
import org.waveprotocol.wave.model.supplement.SupplementedWaveImpl;
import org.waveprotocol.wave.model.supplement.SupplementedWaveImpl.DefaultFollow;
import org.waveprotocol.wave.model.supplement.WaveletBasedSupplement;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;

/**
 * @author vega113@gmail.com (Yuri Zelikov)
 */
public class ServiceUtil {

  private ServiceUtil() {

  }

  /**
   * Builds the supplement model for a wave.
   *
   * @param operation the operation.
   * @param context the operation context.
   * @param participant the viewer.
   * @return the wave supplement.
   * @throws InvalidRequestException if the wave id provided in the operation is
   *         invalid.
   */
  public static SupplementedWave buildSupplement(WaveId waveId, WaveletId waveletId,
      OperationContext context, ParticipantId participant) throws InvalidRequestException {
    OpBasedWavelet wavelet = context.openWavelet(waveId, waveletId, participant);
    ConversationView conversationView = context.getConversationUtil().buildConversation(wavelet);

    // TODO (Yuri Z.) Find a way to obtain an instance of IdGenerator and use it
    // to create udwId.
    WaveletId udwId = buildUserDataWaveletId(participant, waveId.getDomain());
    OpBasedWavelet udw = context.openWavelet(waveId, udwId, participant);
    PrimitiveSupplement udwState = WaveletBasedSupplement.create(udw);
    SupplementedWave supplement =
        SupplementedWaveImpl.create(udwState, conversationView, participant, DefaultFollow.ALWAYS);
    return supplement;
  }

  /**
   * Builds user data wavelet id.
   */
  public static WaveletId buildUserDataWaveletId(ParticipantId participant, String domain) {
    WaveletId udwId =
        WaveletId.of(domain,
            IdUtil.join(IdConstants.USER_DATA_WAVELET_PREFIX, participant.getAddress()));
    return udwId;
  }
}
