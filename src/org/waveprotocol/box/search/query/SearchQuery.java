package org.waveprotocol.box.search.query;

import java.util.Collections;
import java.util.List;

/**
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 *
 * TODO(user) parse query to determine type
 */
public class SearchQuery {

  private final List<QueryCondition> conditions;

  public SearchQuery() {
    this.conditions = Collections.emptyList();
  }

  public SearchQuery(List<QueryCondition> conditions) {
    this.conditions = conditions;
  }

  public List<QueryCondition> getConditions() {
    return conditions;
  }

  public boolean isInbox() {
    for (QueryCondition condition : conditions) {
      if (condition.isInbox()) {
        return true;
      }
    }
    return false;
  }

  public boolean isArchive() {
    for (QueryCondition condition : conditions) {
      if (condition.isArchive()) {
        return true;
      }
    }
    return false;
  }

  public boolean isPublic() {
    for (QueryCondition condition : conditions) {
      if (condition.isPublic()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (QueryCondition condition : conditions) {
      if (sb.length() != 0) {
        sb.append(" ");
      }
      sb.append(condition.toString());
    }
    return sb.toString();
  }
}
