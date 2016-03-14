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

package org.waveprotocol.box.webclient.client;

import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.SegmentId;

import java.util.Set;

/**
 * Makes requests for fragments.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public interface FragmentRequester {

  /** Uploading fragments listener. */
  interface Listener {
    void onFragmentsUploaded(Set<SegmentId> segmentIds);
  }
  
  final static int MIN_FETCH_REPLY_SIZE = 20000;
  final static int MAX_FETCH_REPLY_SIZE = 500000;
  final static int MAX_FETCH_BLIPS_COUNT = 50;
  
  /** Cancels deferred requests. */
  void close();

  /** Creates new request. */
  void newRequest(WaveletId waveletId);

  /** Checks that the request is full.  */
  boolean isFull();

  /** Adds new segment to the request.  */
  void addSegmentId(WaveletId waveletId, SegmentId segmentId, long creationVersion);

  /** Schedules request. */
  void scheduleRequest();

  /** Adds uploaded blips listener. */
  void addListener(Listener listener);
}
