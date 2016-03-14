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

package org.waveprotocol.box.server.persistence;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import org.waveprotocol.box.server.persistence.blocks.BlockStore;
import org.waveprotocol.box.server.persistence.deltas.DeltaStore;
import org.waveprotocol.box.server.persistence.memory.MemoryBlockStore;
import org.waveprotocol.box.server.persistence.memory.MemoryDeltaStore;
import org.waveprotocol.box.server.persistence.memory.MemorySnapshotStore;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.IdGeneratorImpl;

import java.security.SecureRandom;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class TestStoresModule extends AbstractModule {
  private static final String DOMAIN = "domain";
  
  public TestStoresModule() {
  }

  @Override
  public void configure() {
    bind(DeltaStore.class).to(MemoryDeltaStore.class);
    bind(BlockStore.class).to(MemoryBlockStore.class);
    bind(SnapshotStore.class).to(MemorySnapshotStore.class);
  }
  
  @Provides
  @Singleton
  public IdGeneratorImpl.Seed provideSeed(final SecureRandom random) {
    return new IdGeneratorImpl.Seed() {
      @Override
      public String get() {
        return Long.toString(Math.abs(random.nextLong()), 36);
      }
    };
  }

  @Provides
  @Singleton
  public IdGenerator provideIdGenerator(IdGeneratorImpl.Seed seed) {
    return new IdGeneratorImpl(DOMAIN, seed);
  }
}
