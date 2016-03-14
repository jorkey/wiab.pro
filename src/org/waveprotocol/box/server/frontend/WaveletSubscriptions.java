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

package org.waveprotocol.box.server.frontend;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.gxp.com.google.common.collect.Maps;

import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.util.List;
import java.util.Map;

/**
 * Collects active wave view subscriptions.

 * @author akaplanov@gmail.com (A. Kaplanov)
 */
final class WaveletSubscriptions {
  private static final Log LOG = Log.get(WaveletSubscriptions.class);

  private final ListMultimap<WaveletName, WaveletSubscription> waveletSubscriptions =
      LinkedListMultimap.create();
  private final Map<String, WaveletSubscription> channelSubscriptions = Maps.newHashMap();
  private final ListMultimap<String, WaveletSubscription> connectionSubscriptions =
      LinkedListMultimap.create();

  public synchronized WaveletSubscription subscribe(WaveletName waveletName, ParticipantId participantId,
      String channelId, String connectionId, ClientFrontend.UpdateChannelListener listener) {
    WaveletSubscription subscription =
        new WaveletSubscription(waveletName, participantId, channelId, connectionId, listener);
    waveletSubscriptions.put(waveletName, subscription);
    channelSubscriptions.put(channelId, subscription);
    connectionSubscriptions.put(connectionId, subscription);
    return subscription;
  }

  public synchronized void unsubscribe(WaveletSubscription subscription) {
    waveletSubscriptions.remove(subscription.getWaveletName(), subscription);
    channelSubscriptions.remove(subscription.getChannelId());
    connectionSubscriptions.remove(subscription.getConnectionId(), subscription);
  }

  public synchronized List<WaveletSubscription> getSubscriptions(WaveletName waveletName) {
    return Lists.newLinkedList(waveletSubscriptions.get(waveletName));
  }

  public synchronized List<WaveletSubscription> getSubscriptions(WaveletName waveletName,
      ParticipantId participantId) {
    List<WaveletSubscription> result = Lists.newArrayList();
    for (WaveletSubscription subscription : waveletSubscriptions.get(waveletName)) {
      if (subscription.getParticipantId().equals(participantId)) {
        result.add(subscription);
      }
    }
    return result;
  }

  public synchronized WaveletSubscription getSubscriptionByChannelId(String channelId) {
    return channelSubscriptions.get(channelId);
  }

  public synchronized List<WaveletSubscription> getSubscriptionsByConnectionId(String connectionId) {
    return Lists.newLinkedList(connectionSubscriptions.get(connectionId));
  }
}
