/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.waveprotocol.wave.common.profiling;

/**
 * Profiler task names.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public enum TaskName {
  SERVER_FETCH_WAVE,
  SERVER_OPEN_WAVELET,
  SERVER_GET_DIFFS,
  SERVER_SUBMIT,
  SERVER_WRITE_DELTA,
  SERVER_READ_DELTA,
  SERVER_APPLY_DELTA,
  SERVER_READ_INITIAL_SNAPSHOT,
  SERVER_READ_NEAREST_SNAPSHOT,

  CLIENT_OPEN_WAVE,
  CLIENT_OPEN_WAVELET,
  CLIENT_FETCH_WAVE,
  CLIENT_RENDER_SNAPSHOT,
}
