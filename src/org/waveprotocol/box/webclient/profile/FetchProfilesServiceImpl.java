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

package org.waveprotocol.box.webclient.profile;

import org.waveprotocol.wave.client.scheduler.Scheduler;
import org.waveprotocol.wave.client.scheduler.SchedulerInstance;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.Set;

/**
 * Implementation of {@link FetchProfilesService}.
 *
 * @author yurize@apache.org (Yuri Zelikov)
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class FetchProfilesServiceImpl implements FetchProfilesService {

  private static final FetchProfilesBuilder DEFAULT_BUILDER = FetchProfilesBuilder.create();
  private final FetchProfilesBuilder builder;
  private final Callback callback;
  private final Set<String> requestAddresses = new HashSet<>();

  private final Scheduler.Task task = new Scheduler.Task() {

    @Override
    public void execute() {
      builder.newFetchProfilesRequest().setAddresses(new ArrayList<String>(requestAddresses)).fetchProfiles(callback);
      requestAddresses.clear();
    }
  };

  FetchProfilesServiceImpl(FetchProfilesBuilder builder, Callback callback) {
    this.builder = builder;
    this.callback = callback;
  }

  public static FetchProfilesServiceImpl create(Callback callback) {
    return new FetchProfilesServiceImpl(DEFAULT_BUILDER, callback);
  }

  @Override
  public void fetch(String... addresses) {
    for (String address :addresses) {
      requestAddresses.add(address);
    }
    if (!SchedulerInstance.getLowPriorityTimer().isScheduled(task)) {
      SchedulerInstance.getLowPriorityTimer().schedule(task);
    }
  }
}
