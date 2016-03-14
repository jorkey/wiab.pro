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

import com.google.common.collect.Lists;

import org.waveprotocol.box.search.query.QueryCondition;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.List;
import org.waveprotocol.box.search.query.SearchQuery;

/**
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class SearchQueryHelper {
  @SuppressWarnings("serial")
  public static class InvalidQueryException extends Exception {

    public InvalidQueryException(String msg) {
      super(msg);
    }
  }

  static List<IndexCondition> convertToIndexQuery(SearchQuery query,
      ParticipantId viewer, String waveDomain) {
    List<IndexCondition> indexConditions = Lists.newArrayList();
    for (QueryCondition queryCondition : query.getConditions()) {
      IndexCondition.Field indexField;
      String indexFieldAddition = null;
      if (queryCondition.getField() == QueryCondition.Field.IN) {
        indexField = IndexCondition.Field.IN_;
        indexFieldAddition = viewer.getAddress();
      } else if (queryCondition.getField() == QueryCondition.Field.WITH) {
        indexField = IndexCondition.Field.PARTICIPANTS;
      } else {
        indexField = IndexCondition.Field.of(queryCondition.getField().toString());
      }
      String indexValue;
      if (queryCondition.getField() == QueryCondition.Field.WITH ||
          queryCondition.getField() == QueryCondition.Field.CREATOR) {
        indexValue = queryCondition.getValue();
        int index = indexValue.indexOf('@');
        if (index == -1) {
          indexValue += ("@" + waveDomain);
        } else if (index == indexValue.length()-1) {
          indexValue += waveDomain;
        }
      } else {
        indexValue = queryCondition.getValue();
      }
      IndexCondition indexCondition = new IndexCondition(
          indexField, indexFieldAddition, indexValue, queryCondition.isPhrase(), queryCondition.isNot());
      indexConditions.add(indexCondition);
    }
    return indexConditions;
  }

  static boolean withParticipant(List<IndexCondition> conditions, String participant) {
    for (IndexCondition condition : conditions) {
      if (IndexCondition.Field.PARTICIPANTS == condition.getField()) {
        if (participant.equals(condition.getValue())) {
          return true;
        }
      }
    }
    return false;
  }
}
