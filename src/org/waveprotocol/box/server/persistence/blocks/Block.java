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

package org.waveprotocol.box.server.persistence.blocks;

/**
 * Block container.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public interface Block extends ReadableBlock, WritableBlock {
  /** Version of block file format. */
  public final static int BLOCK_FORMAT_VERSION = 5;

  /** Maximum block size to open new fragments. */
  public final static int LOW_WATER = 500 * 1000;

  /** Maximum block size to writing existing fragments. */
  public final static int HIGH_WATER = 1000 * 1000;
}
