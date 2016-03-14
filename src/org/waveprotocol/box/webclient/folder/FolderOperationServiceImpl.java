
/**
 *
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

package org.waveprotocol.box.webclient.folder;

import com.google.gwt.http.client.*;
import org.waveprotocol.wave.client.debug.logger.DomLogger;
import org.waveprotocol.wave.common.logging.LoggerBundle;

/**
 * Implementation of {@link FolderOperationService}.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class FolderOperationServiceImpl implements FolderOperationService {
  private static final LoggerBundle LOG = new DomLogger("FolderOperation");

  @Override
  public void execute(String url, final Callback callback) {
    LOG.trace().log("Performing a folder operation: ", url);

    RequestBuilder requestBuilder = new RequestBuilder(RequestBuilder.GET, url);

    requestBuilder.setCallback(new RequestCallback() {
      @Override
      public void onResponseReceived(Request request, Response response) {
        LOG.trace().log("Folder operation response received: ", response.getText());
        if (response.getStatusCode() != Response.SC_OK) {
          callback.onFailure("Got back status code " + response.getStatusCode());
        } else {
          callback.onSuccess();
        }
      }

      @Override
      public void onError(Request request, Throwable exception) {
        LOG.error().log("Folder operation error: ", exception);
        callback.onFailure(exception.getMessage());
      }
    });

    try {
      requestBuilder.send();
    } catch (RequestException e) {
      callback.onFailure(e.getMessage());
    }
  }

}
