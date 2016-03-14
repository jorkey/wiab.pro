package org.waveprotocol.box.webclient.search;

import java.util.List;
import org.waveprotocol.box.searches.SearchesItem;

/**
 * Asynchronous RPC to server /searches servlet {@link org.waveprotocol.box.server.rpc.SearchServlet}
 * 
 * 
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public interface SearchesService {

  interface StoreCallback {

    void onFailure(String message);

    void onSuccess();
  }

  interface GetCallback {

    void onFailure(String message);

    void onSuccess(List<SearchesItem> searches);
  }

  public void storeSearches(List<SearchesItem> searches, final StoreCallback callback);

  public void getSearches(final GetCallback callback);
}
