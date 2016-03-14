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
public class IndexCondition {

  public enum Field {
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
    UNREAD_COUNT_("unreadCount_", false, true);

    private final String name;
    private final boolean analyzed;
    private final boolean stored;

    Field(String name, boolean analyzed, boolean stored) {
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

    private static final Map<String, Field> reverseLookupMap =
        new HashMap<String, Field>();

    static {
      for (Field type : Field.values()) {
        reverseLookupMap.put(type.toString(), type);
      }
    }

    public static Field of(String name) {
      Field queryToken = reverseLookupMap.get(name);
      if (queryToken == null) {
        throw new IllegalArgumentException("Illegal query param: " + name);
      }
      return queryToken;
    }

    public static boolean hasField(String name) {
      return reverseLookupMap.keySet().contains(name);
    }
  }

  private final Field field;
  private final String fieldAddition;
  private final String value;
  private final boolean phrase;
  private final boolean not;

  public IndexCondition(Field field, String fieldAddition, String value, boolean phrase, boolean not) {
    this.field = field;
    this.fieldAddition = fieldAddition;
    this.value = value;
    this.phrase = phrase;
    this.not = not;
  }

  public Field getField() {
    return field;
  }

  public String getFieldAddition() {
    return fieldAddition;
  }

  public String getValue() {
    return value;
  }

  public boolean isPhrase() {
    return phrase;
  }

  public boolean isNot() {
    return not;
  }
}
