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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import org.waveprotocol.wave.common.regexp.MatchResultWrap;
import org.waveprotocol.wave.common.regexp.RegExpWrap;
import org.waveprotocol.wave.common.regexp.RegExpWrapFactory;

import java.util.List;

/**
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class QueryParser {
  private static final String REGEX = "(!)?(([A-Za-z]+):)?(\"([^\"]*)\"|\\S+)";
  private static final int GROUP_NOT = 1;
  private static final int GROUP_FIELD = 3;
  private static final int GROUP_TERM = 4;
  private static final int GROUP_PHRASE = 5;

  private final RegExpWrap regexp;

  public QueryParser(RegExpWrapFactory regexpFactory) {
    this.regexp = regexpFactory.create(REGEX);
  }

  public SearchQuery parseQuery(String query) {
    Preconditions.checkArgument(query != null);
    if (query.isEmpty()) {
      return new SearchQuery();
    }
    List<QueryCondition> conditions = Lists.newArrayList();
    List<MatchResultWrap> results = regexp.exec(query);
    for (MatchResultWrap result : results) {
      QueryCondition.Field field;
      String value;
      boolean phrase;
      boolean not = result.getGroup(GROUP_NOT) != null;
      if (result.getGroup(GROUP_PHRASE) != null) {
        phrase = true;
        value = result.getGroup(GROUP_PHRASE);
      } else {
        phrase = false;
        value = result.getGroup(GROUP_TERM);
      }
      String fieldName = result.getGroup(GROUP_FIELD);
      if (fieldName != null) {
        if (QueryCondition.Field.hasField(fieldName)) {
          field = QueryCondition.Field.of(result.getGroup(GROUP_FIELD));
        } else {
          field = QueryCondition.Field.CONTENT;
          value = result.getInput();
          phrase = true;
        }
      } else {
        field = QueryCondition.Field.CONTENT;
      }
      conditions.add(new QueryCondition(field, value, phrase, not));
    }
    return new SearchQuery(conditions);
  }
}
