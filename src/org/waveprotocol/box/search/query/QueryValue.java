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

/**
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class QueryValue {

  private final String value;
  private boolean phrase;

  QueryValue(String value, boolean phrase) {
    this.value = value;
    this.phrase = phrase;
  }

  public String getValue() {
    return value;
  }

  public boolean isPhrase() {
    return phrase;
  }

  public void setPhrase(boolean phrase) {
    this.phrase = phrase;
  }
}
