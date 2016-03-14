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

import org.waveprotocol.box.server.persistence.blocks.FarForwardLink;
import org.waveprotocol.box.server.persistence.blocks.FarForwardLinks;
import org.waveprotocol.wave.model.util.Preconditions;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class FarForwardLinksImpl implements FarForwardLinks {
  private final SortedMap<Long, FarForwardLink> linksMap = new TreeMap();

  public FarForwardLinksImpl() {
  }

  @Override
  public synchronized FarForwardLink getNearestLinkByVersionDistance(long versionDistance) {
    FarForwardLink nearestLink = null;
    for (Map.Entry<Long, FarForwardLink> entry : linksMap.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        if (entry.getKey() > versionDistance) {
          break;
        }
        nearestLink = entry.getValue();
      }
    }
    return nearestLink;
  }

  @Override
  public synchronized FarForwardLink getLinkByVersionDistance(long versionDistance) {
    return linksMap.get(versionDistance);
  }

  @Override
  public synchronized List<FarForwardLink> getList() {
    return ImmutableList.copyOf(linksMap.values());
  }

  @Override
  public synchronized boolean addLink(FarForwardLink link) {
    if (!linksMap.containsKey(link.getDistanceToNextFarVersion())) {
      linksMap.put(link.getDistanceToNextFarVersion(), link);
      return true;
    }
    return false;
  }

  @Override
  public synchronized void replaceLink(FarForwardLink oldLink, FarForwardLink newLink) {
    Preconditions.checkArgument(linksMap.containsKey(oldLink.getDistanceToNextFarVersion()),
      "Far link to version distance " + oldLink.getDistanceToNextFarVersion() + " does not exists");
    linksMap.remove(oldLink.getDistanceToNextFarVersion());
    linksMap.put(newLink.getDistanceToNextFarVersion(), newLink);
  }
}
