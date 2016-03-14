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

package org.waveprotocol.box.server;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.waveprotocol.box.server.persistence.lucene.FSIndexDirectory;
import org.waveprotocol.box.server.persistence.lucene.IndexDirectory;
import org.waveprotocol.box.server.search.LuceneSearchImpl;
import org.waveprotocol.box.server.search.MemorySearchImpl;
import org.waveprotocol.box.server.search.SearchBusSubscriber;
import org.waveprotocol.box.server.search.SearchProvider;
import org.waveprotocol.box.server.search.WaveIndexer;

/**
 * @author yurize@apache.org (Yuri Zelikov)
 */
public class SearchModule extends AbstractModule {

  private final String searchType;

  @Inject
  public SearchModule(@Named(CoreSettings.SEARCH_TYPE) String searchType) {
    this.searchType = searchType;
  }

  @Override
  public void configure() {
    if ("lucene".equals(searchType)) {
      bind(SearchBusSubscriber.class).to(LuceneSearchImpl.class).in(
          Singleton.class);
      bind(WaveIndexer.class).to(LuceneSearchImpl.class).in(
          Singleton.class);
      bind(SearchProvider.class).to(LuceneSearchImpl.class).in(
          Singleton.class);
      bind(IndexDirectory.class).to(FSIndexDirectory.class);
    } else if ("memory".equals(searchType)) {
      bind(SearchBusSubscriber.class).to(MemorySearchImpl.class).in(
          Singleton.class);
      bind(WaveIndexer.class).to(MemorySearchImpl.class).in(
          Singleton.class);
      bind(SearchProvider.class).to(MemorySearchImpl.class).in(
          Singleton.class);
    } else {
      throw new RuntimeException("Unknown search type: " + searchType);
    }
  }
}
