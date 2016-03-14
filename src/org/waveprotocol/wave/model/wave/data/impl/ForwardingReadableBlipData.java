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

package org.waveprotocol.wave.model.wave.data.impl;

import com.google.common.collect.ImmutableSet;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.DocumentOperationSink;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.operation.OperationException;

/**
 * Forwards to a delegate. Implementations must implement the delegate() method
 * and override any appropriate methods.
 *
 */
public abstract class ForwardingReadableBlipData implements ReadableBlipData {

  protected abstract ReadableBlipData delegate();

  @Override
  public String getId() {
    return delegate().getId();
  }

  @Override
  public ParticipantId getAuthor() {
    return delegate().getAuthor();
  }

  @Override
  public ImmutableSet<ParticipantId> getContributors() {
    return delegate().getContributors();
  }

  @Override
  public long getCreationTime() {
    return delegate().getCreationTime();
  }

  @Override
  public long getCreationVersion() {
    return delegate().getCreationVersion();
  }

  @Override
  public long getLastModifiedTime() {
    return delegate().getLastModifiedTime();
  }

  @Override
  public long getLastModifiedVersion() {
    return delegate().getLastModifiedVersion();
  }

  @Override
  public boolean isConsistent() {
    return delegate().isConsistent();
  }

  @Override
  public boolean hasContent() {
    return delegate().hasContent();
  }

  @Override
  public boolean isContentInitialized() {
    return delegate().isContentInitialized();
  }

  @Override
  public void initalizeSnapshot() {
    delegate().initalizeSnapshot();
  }

  @Override
  public void processDiffs() throws OperationException {
    delegate().processDiffs();
  }

  @Override
  public DocumentOperationSink getContent() {
    return delegate().getContent();
  }
}
