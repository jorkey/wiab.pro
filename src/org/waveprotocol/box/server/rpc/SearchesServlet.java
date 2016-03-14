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
package org.waveprotocol.box.server.rpc;

import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.rpc.ProtoSerializer.SerializationException;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.search.query.QueryCondition;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.searches.SearchesProto;
import org.waveprotocol.box.searches.SearchesProto.Searches;
import org.waveprotocol.box.searches.impl.SearchesItemImpl;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import com.google.gson.JsonParser;
import com.google.inject.Inject;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * Servlet allows user to get and set his search patterns.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
@SuppressWarnings("serial")
@Singleton
public final class SearchesServlet extends HttpServlet {
  static final private List<SearchesItem> DEFAULT_SEARCHES = new ArrayList<SearchesItem>();

  static {
    SearchesItem item = new SearchesItemImpl();
    item.setName("");
    item.setQuery(QueryCondition.INBOX.toString());
    DEFAULT_SEARCHES.add(item);
    item = new SearchesItemImpl();
    item.setName("");
    item.setQuery(QueryCondition.ARCHIVE.toString());
    DEFAULT_SEARCHES.add(item);
    item = new SearchesItemImpl();
    item.setName("");
    item.setQuery(QueryCondition.PUBLIC.toString());
    DEFAULT_SEARCHES.add(item);
  }

  private final SessionManager sessionManager;
  private final AccountStore accountStore;
  private final ProtoSerializer serializer;
  private final static JsonParser parser = new JsonParser();
  private final Log LOG = Log.get(SearchesServlet.class);

  @Inject
  public SearchesServlet(SessionManager sessionManager, AccountStore accountStore, ProtoSerializer serializer) {
    this.sessionManager = sessionManager;
    this.accountStore = accountStore;
    this.serializer = serializer;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    try {
      ParticipantId participant = sessionManager.getLoggedInUser(req.getSession(false));
      if (participant == null) {
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return;
      }
      BufferedReader reader = req.getReader();
      String request = reader.readLine();
      SearchesProto.Searches searches = serializer.fromJson(parser.parse(request), SearchesProto.Searches.class);
      AccountData account = accountStore.getAccount(participant);
      HumanAccountData humanAccount;
      if (account != null) {
        humanAccount = account.asHuman();
      } else {
        humanAccount = new HumanAccountDataImpl(participant);
      }
      humanAccount.setSearches(deserializeSearches(searches));
      accountStore.putAccount(humanAccount);
    } catch (SerializationException ex) {
      throw new IOException(ex);
    } catch (PersistenceException ex) {
      throw new IOException(ex);
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId participant = sessionManager.getLoggedInUser(req.getSession(false));
    if (participant == null) {
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    try {
      AccountData account = accountStore.getAccount(participant);
      List<SearchesItem> searches = null;
      if (account != null) {
        searches = account.asHuman().getSearches();
      }
      if (searches == null) {
        searches = DEFAULT_SEARCHES;
      }
      resp.setStatus(HttpServletResponse.SC_OK);
      resp.setContentType("application/json; charset=utf8");
      resp.setHeader("Cache-Control", "no-store");
      resp.getWriter().append(serializer.toJson(serializeSearches(searches)).toString());
    } catch (SerializationException ex) {
      throw new IOException(ex);
    } catch (PersistenceException ex) {
      throw new IOException(ex);
    }
  }

  private List<SearchesItem> deserializeSearches(Searches protoSearches) {
    List<SearchesItem> searches = CollectionUtils.newArrayList();
    for (SearchesProto.SearchesItem protoSearch : protoSearches.getSearchList()) {
      SearchesItem search = new SearchesItemImpl();
      search.setName(protoSearch.getName());
      search.setQuery(protoSearch.getQuery());
      searches.add(search);
    }
    return searches;
  }

  private Searches serializeSearches(List<SearchesItem> searches) {
    Searches.Builder searchesBuilder = SearchesProto.Searches.newBuilder();
    for (SearchesItem search : searches) {
      SearchesProto.SearchesItem.Builder searchBuilder = SearchesProto.SearchesItem.newBuilder();
      searchBuilder.setName(search.getName());
      searchBuilder.setQuery(search.getQuery());
      searchesBuilder.addSearch(searchBuilder);
    }
    return searchesBuilder.build();
  }
}