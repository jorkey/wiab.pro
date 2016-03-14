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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Named;
import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.persistence.blocks.BlockStore;
import org.waveprotocol.box.server.persistence.deltas.DeltaStore;
import org.waveprotocol.box.server.persistence.file.*;
import org.waveprotocol.box.server.persistence.memory.MemoryBlockStore;
import org.waveprotocol.box.server.persistence.memory.MemoryDeltaStore;
import org.waveprotocol.box.server.persistence.memory.MemorySnapshotStore;
import org.waveprotocol.box.server.persistence.memory.MemoryStore;
import org.waveprotocol.box.server.persistence.mongodb.MongoDbProvider;
import org.waveprotocol.box.server.waveletstate.block.BlockWaveletState;
import org.waveprotocol.box.server.waveletstate.block.BlockWaveletStateImpl;
import org.waveprotocol.box.server.waveletstate.delta.DeltaWaveletState;
import org.waveprotocol.box.server.waveletstate.delta.DeltaWaveletStateImpl;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletState;
import org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletStateImpl;
import org.waveprotocol.box.server.waveserver.DeltaWaveletStateMap;
import org.waveprotocol.wave.crypto.CertPathStore;

/**
 * Module for setting up the different persistence stores.
 *
 *<p>
 * The valid names for the cert store are 'memory', 'file' and 'mongodb'
 *
 *<p>
 *The valid names for the attachment store are 'disk' and 'mongodb'
 *
 *<p>
 *The valid names for the account store are 'memory', 'file' and 'mongodb'.
 *
 * @author ljvderijk@google.com (Lennard de Rijk)
 */
public class PersistenceModule extends AbstractModule {

  private final String signerInfoStoreType;

  private final String attachmentStoreType;

  private final String accountStoreType;

  private final String contactStoreType;

  private final String deltaStoreType;

  private final String blockStoreType;

  private MongoDbProvider mongoDbProvider;

  @Inject
  public PersistenceModule(@Named(CoreSettings.SIGNER_INFO_STORE_TYPE) String signerInfoStoreType,
      @Named(CoreSettings.ATTACHMENT_STORE_TYPE) String attachmentStoreType,
      @Named(CoreSettings.ACCOUNT_STORE_TYPE) String accountStoreType,
      @Named(CoreSettings.CONTACT_STORE_TYPE) String contactStoreType,
      @Named(CoreSettings.DELTA_STORE_TYPE) String deltaStoreType,
      @Named(CoreSettings.BLOCK_STORE_TYPE) String blockStoreType) {
    this.signerInfoStoreType = signerInfoStoreType;
    this.attachmentStoreType = attachmentStoreType;
    this.accountStoreType = accountStoreType;
    this.contactStoreType = contactStoreType;
    this.deltaStoreType = deltaStoreType;
    this.blockStoreType = blockStoreType;
  }

  /**
   * Returns a {@link MongoDbProvider} instance.
   */
  public MongoDbProvider getMongoDbProvider() {
    if (mongoDbProvider == null) {
      mongoDbProvider = new MongoDbProvider();
    }
    return mongoDbProvider;
  }

  @Override
  protected void configure() {
    bindCertPathStore();
    bindAttachmentStore();
    bindAccountStore();
    bindContactStore();
    bindDeltaStore();
    bindBlockStore();
  }

  /**
   * Binds the CertPathStore implementation to the store specified in the
   * properties.
   */
  private void bindCertPathStore() {
    switch (signerInfoStoreType.toLowerCase()) {
      case CoreSettings.STORE_TYPE_MEMORY:
        bind(CertPathStore.class).to(MemoryStore.class).in(Singleton.class);
        break;

      case CoreSettings.STORE_TYPE_FILE:
        bind(CertPathStore.class).to(FileSignerInfoStore.class).in(Singleton.class);
        break;

      case CoreSettings.STORE_TYPE_MONGODB:
        MongoDbProvider mongoDbProvider = getMongoDbProvider();
        bind(CertPathStore.class).toInstance(mongoDbProvider.provideMongoDbStore());
        break;

      default:
        throw new RuntimeException("Invalid certificate path store type: '" + signerInfoStoreType + "'");
    }
  }

  private void bindAttachmentStore() {
    switch (attachmentStoreType.toLowerCase()) {
      case CoreSettings.STORE_TYPE_DISK:
        bind(AttachmentStore.class).to(FileAttachmentStore.class).in(Singleton.class);
        break;

      case CoreSettings.STORE_TYPE_MONGODB:
        MongoDbProvider mongoDbProvider = getMongoDbProvider();
        bind(AttachmentStore.class).toInstance(mongoDbProvider.provideMongoDbStore());
        break;

      default:
        throw new RuntimeException("Invalid attachment store type: '" + attachmentStoreType + "'");
    }
  }

  private void bindAccountStore() {
    switch (accountStoreType.toLowerCase()) {
      case CoreSettings.STORE_TYPE_MEMORY:
        bind(AccountStore.class).to(MemoryStore.class).in(Singleton.class);
        break;

      case CoreSettings.STORE_TYPE_FILE:
        bind(AccountStore.class).to(FileAccountStore.class).in(Singleton.class);
        break;

      case CoreSettings.STORE_TYPE_FAKE:
        bind(AccountStore.class).to(FakePermissiveAccountStore.class).in(Singleton.class);
        break;

      case CoreSettings.STORE_TYPE_MONGODB:
        MongoDbProvider mongoDbProvider = getMongoDbProvider();
        bind(AccountStore.class).toInstance(mongoDbProvider.provideMongoDbStore());
        break;

      default:
        throw new RuntimeException("Invalid account store type: '" + accountStoreType + "'");
    }
  }

  private void bindContactStore() {
    switch (contactStoreType.toLowerCase()) {
      case CoreSettings.STORE_TYPE_MEMORY:
        bind(ContactStore.class).to(MemoryStore.class).in(Singleton.class);
        break;

      case CoreSettings.STORE_TYPE_FILE:
        bind(ContactStore.class).to(FileContactStore.class).in(Singleton.class);
        break;

      default:
        throw new RuntimeException("Invalid contact store type: '" + contactStoreType + "'");
    }
  }

  private void bindDeltaStore() {
    switch (deltaStoreType.toLowerCase()) {
      case CoreSettings.STORE_TYPE_MEMORY:
        bind(DeltaStore.class).to(MemoryDeltaStore.class).in(Singleton.class);
        bind(SnapshotStore.class).to(MemorySnapshotStore.class).in(Singleton.class);
        break;

      case CoreSettings.STORE_TYPE_FILE:
        bind(DeltaStore.class).to(FileDeltaStore.class).in(Singleton.class);
        bind(SnapshotStore.class).to(FileSnapshotStore.class).in(Singleton.class);
        break;

      default:
        throw new RuntimeException("Invalid delta store type: '" + deltaStoreType + "'");
    }
    bind(DeltaWaveletStateMap.class).in(Singleton.class);
    install(new FactoryModuleBuilder().implement(DeltaWaveletState.class, DeltaWaveletStateImpl.class).build(
      DeltaWaveletState.GuiceFactory.class));
  }

  private void bindBlockStore() {
    if (blockStoreType.equalsIgnoreCase("memory")) {
      bind(BlockStore.class).to(MemoryBlockStore.class).in(Singleton.class);
    } else if (blockStoreType.equalsIgnoreCase("file")) {
      bind(BlockStore.class).to(FileBlockStore.class).in(Singleton.class);
    } else {
      throw new RuntimeException("Invalid block store type: '" + blockStoreType + "'");
    }
    install(new FactoryModuleBuilder().implement(BlockWaveletState.class, BlockWaveletStateImpl.class).build(
      BlockWaveletState.GuiceFactory.class));
    install(new FactoryModuleBuilder().implement(SegmentWaveletState.class, SegmentWaveletStateImpl.class).build(
      SegmentWaveletState.GuiceFactory.class));
  }
}
