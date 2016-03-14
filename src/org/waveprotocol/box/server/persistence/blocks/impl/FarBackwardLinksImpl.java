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

package org.waveprotocol.box.server.persistence.blocks.impl;

import org.waveprotocol.box.server.persistence.blocks.FarBackwardLink;
import org.waveprotocol.box.server.persistence.blocks.FarBackwardLinks;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class FarBackwardLinksImpl implements FarBackwardLinks {
  private final SortedMap<Long, FarBackwardLink> linksMap = new TreeMap();

  @Override
  public synchronized FarBackwardLink getNearestLinkByVersionDistance(long versionDistance) {
    FarBackwardLink nearestLink = null;
    for (Map.Entry<Long, FarBackwardLink> entry : linksMap.entrySet()) {
      if (entry.getKey() > versionDistance) {
        break;
      }
      nearestLink = entry.getValue();
    }
    return nearestLink;
  }

  @Override
  public synchronized FarBackwardLink getLinkByVersionDistance(long versionDistance) {
    return linksMap.get(versionDistance);
  }

  @Override
  public synchronized List<FarBackwardLink> getList() {
    return ImmutableList.<FarBackwardLink>builder().addAll(linksMap.values()).build();
  }

  @Override
  public synchronized boolean addLink(FarBackwardLink link) {
    if (!linksMap.containsKey(link.getDistanceToPreviousFarVersion())) {
      linksMap.put(link.getDistanceToPreviousFarVersion(), link);
      return true;
    }
    return false;
  }
}
