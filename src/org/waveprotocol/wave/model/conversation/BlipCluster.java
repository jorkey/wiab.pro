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

package org.waveprotocol.wave.model.conversation;

import org.waveprotocol.wave.model.conversation.navigator.ConversationNavigator;

import java.util.Iterator;

/**
 * Cluster of successive blips of same row starting at first blip and finishing at last blip.
 *
 * @author dyukon@gmail.com (Denis Konovalchik)
 */
public class BlipCluster {

  /** Maximum number of blips tried one-by-one as a middle blip of the placeholder. */
  private final static int MAX_MIDDLE_BLIP_SEARCH_TRIES = 100;

  private ConversationBlip firstBlip;
  private ConversationBlip lastBlip;

  //
  // Constructors
  //

  /**
   * Empty blip cluster.
   */
  public BlipCluster() {
    this.firstBlip = null;
    this.lastBlip = null;
  }

  /**
   * Blip cluster consisting only of the given blip.
   * 
   * @param blip the given blip
   */
  public BlipCluster(ConversationBlip blip) {
    this(blip, blip);
  }

  /**
   * Blip cluster restricted by given the first and the last blips.
   * 
   * @param firstBlip the first blip
   * @param lastBlip the last blip
   */
  public BlipCluster(ConversationBlip firstBlip, ConversationBlip lastBlip) {
    this.firstBlip = firstBlip;
    this.lastBlip = lastBlip;
  }

  /**
   * Copy of the given blip cluster.
   * 
   * @param cluster the given blip cluster
   */
  public BlipCluster(BlipCluster cluster) {
    this(cluster.getFirstBlip(), cluster.getLastBlip());
  }

  //
  // Getters
  //

  /**
   * @return the first blip of the cluster.
   */
  public ConversationBlip getFirstBlip() {
    return firstBlip;
  }

  /**
   * @return the last blip of the cluster.
   */
  public ConversationBlip getLastBlip() {
    return lastBlip;
  }

  /**
   * @return the blip in the same row before the cluster.
   * 
   * @param navigator conversation navigator
   */
  public ConversationBlip getBlipBefore(ConversationNavigator navigator) {
    return navigator.getPreviousBlipInRow(firstBlip);
  }

  /**
   * @return the blip in the same row after the cluster.
   * 
   * @param navigator conversation navigator
   */
  public ConversationBlip getBlipAfter(ConversationNavigator navigator) {
    return navigator.getNextBlipInRow(lastBlip);
  }

  /**
   * @return middle blip of the cluster found for MAX_MIDDLE_BLIP_SEARCH_TRIES iterations.
   *
   * @param preferFrontBlip true, if the blip from beginning must be chosen
   * @param navigator conversation navigator
   */
  public ConversationBlip getMiddleBlip(boolean preferFrontBlip, ConversationNavigator navigator) {
    ConversationBlip frontBlip = firstBlip;
    ConversationBlip backBlip = lastBlip;
    for (int tryCount = 1; tryCount <= MAX_MIDDLE_BLIP_SEARCH_TRIES; tryCount++) {
      if (frontBlip == backBlip) {
        return frontBlip;
      }
      if (tryCount % 2 == 1) { // odd try number - move the front blip to the end
        frontBlip = navigator.getNextBlipInRow(frontBlip);
      } else { // even try number - move the back blip to the begin
        backBlip = navigator.getPreviousBlipInRow(backBlip);
      }
    }
    return preferFrontBlip ? frontBlip : backBlip;
  }

  /**
   * @return true, if the blip cluster is empty.
   */
  public boolean isEmpty() {
    return firstBlip == null || lastBlip == null;
  }

  /**
   * @return true, if the blip cluster contains only one blip.
   */
  public boolean isSingle() {
    return !isEmpty() && firstBlip == lastBlip;
  }

  /**
   * @return true, if the cluster contains only given blip.
   * 
   * @param blip the given blip
   */
  public boolean consistsOfBlip(ConversationBlip blip) {
    return isSingle() && isBorderBlip(blip);
  }

  public boolean isFirstBlip(ConversationBlip blip) {
    return blip == firstBlip;
  }

  public boolean isLastBlip(ConversationBlip blip) {
    return blip == lastBlip;
  }

  public boolean isBorderBlip(ConversationBlip blip) {
    return isFirstBlip(blip) || isLastBlip(blip);
  }

  /**
   * Makes this blip cluster consist of the given blip.
   * 
   * @param blip given blip
   */
  public void setTo(ConversationBlip blip) {
    firstBlip = blip;
    lastBlip = blip;
  }
  
  /**
   * Includes the given blip into the cluster, if it is neighbor (or does nothing).
   * 
   * @param blip the given blip
   * @param navigator conversation navigator
   */
  public void includeNeighborBlip(ConversationBlip blip, ConversationNavigator navigator) {
    if (isEmpty()) {
      return;
    }
    if (blip == getBlipBefore(navigator)) {
      firstBlip = blip;
    } else if (blip == getBlipAfter(navigator)) {
      lastBlip = blip;
    }
  }

  /**
   * @return true, if the given blip is a neighbor of the cluster.
   * 
   * @param blip the given blip
   * @param navigator conversation navigator
   */
  public boolean isNeighborBlip(ConversationBlip blip, ConversationNavigator navigator) {
    return blip == getBlipBefore(navigator) || blip == getBlipAfter(navigator);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof BlipCluster)) {
      return false;
    }
    BlipCluster other = (BlipCluster) obj;
    return firstBlip == other.getFirstBlip() && lastBlip == other.getLastBlip();
  }

  @Override
  public int hashCode() {
    return (firstBlip != null ? firstBlip.hashCode() : 0) * 1000 +
        (lastBlip != null ? lastBlip.hashCode() : 0);
  }
  
  @Override
  public String toString() {
    return "[" + (firstBlip != null ? firstBlip.getId() : "null") + ", " +
        (lastBlip != null ? lastBlip.getId() : "null") + "]";
  }

  /**
   * @return result of combination this cluster with another adjacent cluster.
   * 
   * @param cluster another adjacent cluster
   * @param navigator conversation navigator
   */
  public BlipCluster combine(BlipCluster cluster, ConversationNavigator navigator) {
    return combine(this, cluster, navigator);
  }

  /**
   * @return result of subtracting a cluster from this cluster.
   * 
   * @param cluster the subtracted cluster
   * @param navigator conversation navigator
   */
  public BlipCluster[] subtract(BlipCluster cluster, ConversationNavigator navigator) {
    return subtract(this, cluster, navigator);
  }

  /**
   * @return result of subtracting a blip from this cluster.
   * 
   * @param blip the subtracted blip
   * @param navigator conversation navigator
   */
  public BlipCluster[] subtract(ConversationBlip blip, ConversationNavigator navigator) {
    return subtract(this, blip, navigator);
  }

  //
  // Iterator
  //

  public Iterator<ConversationBlip> getIterator(final ConversationNavigator navigator) {
    return new Iterator<ConversationBlip>() {

      private ConversationBlip currentBlip = firstBlip;

      @Override
      public boolean hasNext() {
        return currentBlip != null;
      }

      @Override
      public ConversationBlip next() {
        ConversationBlip oldCurrentBlip = currentBlip;
        currentBlip = oldCurrentBlip == lastBlip ? null : navigator.getNextBlipInRow(oldCurrentBlip);
        return oldCurrentBlip;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException("Removing isn't supported!");
      }
    };
  }

  //
  // Private static methods
  //

  /**
   * @return the combination of the adjacent blip clusters, or null, if combination is impossible.
   */
  private static BlipCluster combine(BlipCluster cluster1, BlipCluster cluster2,
      ConversationNavigator navigator) {
    if (!cluster1.isEmpty() && !cluster2.isEmpty()) {
      if (cluster1.getBlipAfter(navigator) == cluster2.getFirstBlip()) {
        return new BlipCluster(cluster1.getFirstBlip(), cluster2.getLastBlip());
      }
      if (cluster2.getBlipAfter(navigator) == cluster1.getFirstBlip()) {
        return new BlipCluster(cluster2.getFirstBlip(), cluster1.getLastBlip());
      }
    }
    return null;
  }

  /**
   * Excludes a blip from the cluster and returns array of resulting clusters.
   */
  private static BlipCluster[] subtract(BlipCluster cluster, ConversationBlip blip,
      ConversationNavigator navigator) {
    return subtract(cluster, new BlipCluster(blip), navigator);
  }

  /**
   * Returns the difference between two clusters.
   * If the clusters have the same first and last blips, then the result is { EMPTY, null }.
   * If the clusters have the same first or last blips, then the result is { not empty, null }.
   * If the clusters have different first and last blips, the the result is { not empty, not empty }.
   */
  private static BlipCluster[] subtract(BlipCluster c1, BlipCluster c2,
      ConversationNavigator navigator) {
    BlipCluster diff1 = EMPTY;
    BlipCluster diff2 = null;
    if (!c1.equals(c2)) {
      if (c1.getFirstBlip() == c2.getFirstBlip()) {
        diff1 = new BlipCluster(c2.getBlipAfter(navigator), c1.getLastBlip());
      } else if (c1.getLastBlip() == c2.getLastBlip()) {
        diff1 = new BlipCluster(c1.getFirstBlip(), c2.getBlipBefore(navigator));
      } else {
        diff1 = new BlipCluster(c1.getFirstBlip(), c2.getBlipBefore(navigator));
        diff2 = new BlipCluster(c2.getBlipAfter(navigator), c1.getLastBlip());
      }
    }  
    return new BlipCluster[] { diff1, diff2 };
  }

  /**
   * Empty blip cluster containing nothing.
   */
  public static final BlipCluster EMPTY = new BlipCluster() {

    @Override
    public ConversationBlip getMiddleBlip(boolean preferFrontBlip, ConversationNavigator navigator) {
      return null;
    }

    @Override
    public String toString() {
      return "<empty>";
    }
  };
}
