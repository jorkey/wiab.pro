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

package org.waveprotocol.box.server.executor;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;
import org.waveprotocol.box.server.CoreSettings;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class TestExecutorsModule extends AbstractModule {

  public TestExecutorsModule() {
  }

  @Override
  public void configure() {
    install(new ExecutorsModule());
    bind(Key.get(Integer.class, Names.named(CoreSettings.STORAGE_INDEXING_EXECUTOR_THREAD_COUNT))).toInstance(0);
    bind(Key.get(Integer.class, Names.named(CoreSettings.STORAGE_CONTINUATION_EXECUTOR_THREAD_COUNT))).toInstance(0);
    bind(Key.get(Integer.class, Names.named(CoreSettings.LOOKUP_EXECUTOR_THREAD_COUNT))).toInstance(0);
    bind(Key.get(Integer.class, Names.named(CoreSettings.LISTENER_EXECUTOR_THREAD_COUNT))).toInstance(0);
    bind(Key.get(Integer.class, Names.named(CoreSettings.WAVELET_LOAD_EXECUTOR_THREAD_COUNT))).toInstance(0);
    bind(Key.get(Integer.class, Names.named(CoreSettings.DELTA_PERSIST_EXECUTOR_THREAD_COUNT))).toInstance(0);
    bind(Key.get(Integer.class, Names.named(CoreSettings.SNAPSHOT_PERSIST_EXECUTOR_THREAD_COUNT))).toInstance(0);
    bind(Key.get(Integer.class, Names.named(CoreSettings.BLOCK_PERSIST_EXECUTOR_THREAD_COUNT))).toInstance(0);
    bind(Key.get(Integer.class, Names.named(CoreSettings.ROBOT_CONNECTION_THREAD_COUNT))).toInstance(0);
    bind(Key.get(Integer.class, Names.named(CoreSettings.ROBOT_GATEWAY_THREAD_COUNT))).toInstance(0);
  }
}
