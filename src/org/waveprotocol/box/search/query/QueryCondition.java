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

package org.waveprotocol.box.search.query;

import java.util.HashMap;
import java.util.Map;

/**
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class QueryCondition {

  public enum Field {
    CONTENT("content"),
    IN("in"),
    WITH("with"),
    CREATOR("creator"),
    TITLE("title"),
    TAG("tag");

    private final String name;

    Field(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
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

  private static final String VALUE_INBOX = "inbox";
  private static final String VALUE_ARCHIVE = "archive";
  private static final String VALUE_PUBLIC = "@";

  public static final QueryCondition INBOX = new QueryCondition(Field.IN, VALUE_INBOX, false, false);
  public static final QueryCondition ARCHIVE = new QueryCondition(Field.IN, VALUE_ARCHIVE, false, false);
  public static final QueryCondition PUBLIC = new QueryCondition(Field.WITH, VALUE_PUBLIC, false, false);

  private final Field field;
  private final String value;
  private boolean phrase;
  private boolean not;

  public QueryCondition(Field field, String value, boolean phrase, boolean not) {
    this.field = field;
    this.value = value;
    this.phrase = phrase;
    this.not = not;
  }

  public String getValue() {
    return value;
  }

  public Field getField() {
    return field;
  }

  public boolean isPhrase() {
    return phrase;
  }

  public boolean isNot() {
    return not;
  }

  public boolean isInbox() {
    return Field.IN.equals(field) && value.equals(VALUE_INBOX);
  }

  public boolean isArchive() {
    return Field.IN.equals(field) && value.equals(VALUE_ARCHIVE);
  }

  public boolean isPublic() {
    return Field.WITH.equals(field) && value.equals(VALUE_PUBLIC);
  }

  @Override
  public String toString() {
    if (phrase) {
      return field.toString() + ":\"" + value + "\"";
    }
    return field.toString() + ":" + value;
  }
}
