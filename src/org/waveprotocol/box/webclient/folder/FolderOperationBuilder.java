/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.waveprotocol.box.webclient.folder;

/**
 * Interface for a folder operation builder.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public interface FolderOperationBuilder {

  public static final String PARAM_OPERATION = "operation";
  public static final String PARAM_FOLDER = "folder";
  public static final String PARAM_WAVE_ID = "waveId";

  public static final String OPERATION_MOVE = "move";

  public static final String FOLDER_INBOX = "inbox";
  public static final String FOLDER_ARCHIVE = "archive";

  public static final String FOLDER_OPERATION_URL_BASE = "/folder";

  /**
   * @param name of parameter,
   * @param value of parameter.
   */
  FolderOperationBuilder addParameter(String name, String value);

  /**
   *
   * @return URL with operation to send
   */
  String getUrl();
}
