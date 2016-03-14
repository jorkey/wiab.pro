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

package org.waveprotocol.wave.model.image;

import static org.waveprotocol.wave.model.image.ImageConstants.CAPTION_TAGNAME;
import static org.waveprotocol.wave.model.image.ImageConstants.TAGNAME;
import static org.waveprotocol.wave.model.image.ImageConstants.ATTACHMENT_ATTRIBUTE;
import static org.waveprotocol.wave.model.image.ImageConstants.STYLE_ATTRIBUTE;
import static org.waveprotocol.wave.model.image.ImageConstants.STYLE_FULL_VALUE;

import org.waveprotocol.wave.model.document.util.XmlStringBuilder;

/**
 * Static methods to produce Wave Gadget XML elements.
 *
 */
public final class ImageXmlUtil {

  private ImageXmlUtil() {} // Non-instantiable.

  /**
   * @param attachmentId
   * @param xmlCaption
   * @return A content xml string containing an image thumbnail
   */
  public static XmlStringBuilder constructXml(String attachmentId, String xmlCaption) {
    return XmlStringBuilder.createText(xmlCaption).wrap(CAPTION_TAGNAME).wrap(
        TAGNAME, ATTACHMENT_ATTRIBUTE, attachmentId);
  }

  /**
   * @param attachmentId
   * @param full whether it's full size
   * @param xmlCaption
   */
  public static XmlStringBuilder constructXml(String attachmentId, boolean full, String xmlCaption) {
    if (!full) {
      return constructXml(attachmentId, xmlCaption);
    }
    return XmlStringBuilder.createText(xmlCaption).wrap(CAPTION_TAGNAME).wrap(
        TAGNAME, ATTACHMENT_ATTRIBUTE, attachmentId, STYLE_ATTRIBUTE, STYLE_FULL_VALUE);
  }
}
