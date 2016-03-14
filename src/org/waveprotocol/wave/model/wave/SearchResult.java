/* Copyright (c) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.waveprotocol.wave.model.wave;

import java.util.ArrayList;
import java.util.List;
import org.waveprotocol.wave.model.supplement.WaveDigestSupplement;

/**
 * SearchResult is returned from a search request.
 *
 */
public class SearchResult {

  /**
   * Digest contains the digest information for one 'hit' in the result.
   */
  public static class Digest {
    private final WaveDigest digest;
    private final WaveDigestSupplement supplement;

    public Digest(WaveDigest digest, WaveDigestSupplement supplement) {
      this.digest = digest;
      this.supplement = supplement;
    }

    public String getTitle() {
      return digest.getTitle();
    }

    public String getSnippet() {
      return digest.getSnippet();
    }

    public String getWaveId() {
      return digest.getWaveId();
    }

    public String getCreator() {
      return digest.getCreator();
    }

    public List<String> getParticipants() {
      return digest.getParticipants();
    }

    public long getLastModified() {
      return digest.getLastModified();
    }

    public long getCreated() {
      return digest.getCreated();
    }

    public int getBlipCount() {
      return digest.getBlipCount();
    }

    public int getUnreadCount() {
      if (supplement != null) {
        return supplement.getUnreadCount();
      }
      return 0;
    }

  }

  private final String query;
  private int numResults;
  private final List<Digest> digests = new ArrayList<Digest>(10);

  public SearchResult(String query) {
    this.query = query;
    this.numResults = 0;
  }

  /**
   * Add a result to the set
   * @param digest to add
   */
  public void addDigest(Digest digest) {
    numResults += 1;
    digests.add(digest);
  }

  /**
   * @returns the query associated with this result
   */
  public String getQuery() {
    return query;
  }

  /**
   * @returns the number of results
   */
  public int getNumResults() {
    return numResults;
  }

  /**
   * @returns the digests for the result
   */
  public List<Digest> getDigests() {
    return digests;
  }
}
