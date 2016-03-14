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

package org.waveprotocol.box.server.waveletstate;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

/**
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class IndexingState {
  
  public static final IndexingState NO_INDEXING = new IndexingState(0);

  private final long targetVersion;

  private long currentVersion;

  private final SettableFuture future = SettableFuture.create();

  public IndexingState() {
    this.targetVersion = 0;
    future.set(null);
  }
  
  public IndexingState(long targetVersion) {
    this.targetVersion = targetVersion;
    if (targetVersion == 0) {
      future.set(null);
    }
  }
  
  public synchronized void setCurrentVersion(long currentVersion) {
    this.currentVersion = currentVersion;
    if (currentVersion >= targetVersion) {
      future.set(null);
    }
  }

  public synchronized void setException(WaveletStateException exception) {
    future.setException(exception);
  }

  public synchronized long getTargetVersion() {
    return targetVersion;
  }

  public synchronized long getCurrentVersion() {
    return currentVersion;
  }

  public synchronized boolean isComplete() {
    return currentVersion == targetVersion;
  }

  public ListenableFuture<Void> getFuture() {
    return future;
  }
}
