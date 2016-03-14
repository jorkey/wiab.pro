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

package org.waveprotocol.wave.model.img;

import static org.waveprotocol.wave.model.img.ImgConstants.TAGNAME;
import static org.waveprotocol.wave.model.img.ImgConstants.SRC_ATTRIBUTE;

import org.waveprotocol.wave.model.document.util.XmlStringBuilder;


/**
 * Static methods to produce Wave Gadget XML elements.
 *
 */
public final class ImgXmlUtil {

  private ImgXmlUtil() {} // Non-instantiable.

  /**
   * Returns initialized XML string builder for the gadget with initial prefs.
   *
   * @param url that points to the external image.
   * @return content XML string for the gadget.
   */
  public static XmlStringBuilder constructXml(String url) {
    final XmlStringBuilder builder = XmlStringBuilder.createEmpty();
    builder.wrap(
        TAGNAME,
        SRC_ATTRIBUTE, url);
    return builder;
  }
}
