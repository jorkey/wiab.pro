/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.waveprotocol.box.server.search;

import java.util.HashMap;
import java.util.Map;

/**
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
class SearchToken {

  enum IndexField {
    WAVE_ID("waveId", false, true),
    CREATOR("creator", false, true),
    PARTICIPANTS("participants", false, true),
    TITLE("title", true, true),
    SNIPPET("snippet", false, true),
    BLIP_COUNT("blipCount", false, true),
    CONTENT("content", true, false),
    CREATED("created", false, true),
    LAST_MODIFIED("lastModified", false, true),
    TAG("tag", true, false),
    IN_("in_", false, true),
    UNREAD_COUNT_("readCount_", false, true);

    private final String name;
    private final boolean analyzed;
    private final boolean stored;

    IndexField(String name, boolean analyzed, boolean stored) {
      this.name = name;
      this.analyzed = analyzed;
      this.stored = stored;
    }

    @Override
    public String toString() {
      return name;
    }

    public boolean isAnalyzed() {
      return analyzed;
    }

    public boolean isStored() {
      return stored;
    }

    private static final Map<String, IndexField> reverseLookupMap =
        new HashMap<String, IndexField>();

    static {
      for (IndexField type : IndexField.values()) {
        reverseLookupMap.put(type.toString(), type);
      }
    }

    public static IndexField of(String name) {
      IndexField queryToken = reverseLookupMap.get(name);
      if (queryToken == null) {
        throw new IllegalArgumentException("Illegal query param: " + name);
      }
      return queryToken;
    }

    public static boolean hasField(String name) {
      return reverseLookupMap.keySet().contains(name);
    }
  }

  enum QueryField {
    CONTENT("content"),
    IN("in"),
    WITH("with"),
    CREATOR("creator"),
    TITLE("title"),
    TAG("tag");

    private final String name;

    QueryField(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }

    private static final Map<String, QueryField> reverseLookupMap =
        new HashMap<String, QueryField>();

    static {
      for (QueryField type : QueryField.values()) {
        reverseLookupMap.put(type.toString(), type);
      }
    }

    public static QueryField of(String name) {
      QueryField queryToken = reverseLookupMap.get(name);
      if (queryToken == null) {
        throw new IllegalArgumentException("Illegal query param: " + name);
      }
      return queryToken;
    }

    public static boolean hasField(String name) {
      return reverseLookupMap.keySet().contains(name);
    }
  }
}
