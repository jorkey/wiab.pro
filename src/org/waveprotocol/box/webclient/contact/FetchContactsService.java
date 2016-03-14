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

package org.waveprotocol.box.webclient.contact;

import org.waveprotocol.box.contact.ContactResponse;

/**
 * Interface that exposes the fetch profile services to the client.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public interface FetchContactsService {

  public interface Callback {
    void onFailure(String message);

    /**
     * Notifies this callback of a successful fetch contacts response.
     *
     * @param contactResponse the response from the server.
     */
    void onSuccess(ContactResponse contactResponse);
  }

  /**
   * Fetches profiles.
   *
   * @param timestamp the timestamp of last response or 0.
   * @param callback the callback.
   */
  void fetch(long timestamp, Callback callback);
}
