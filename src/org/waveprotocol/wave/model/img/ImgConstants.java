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

/**
 * Common external image constants.
 *
 */

public final class ImgConstants {
  private ImgConstants() {} // Non-instantiable.

  /** Image tag */
  public static final String TAGNAME ="img";

  /** Image Alt Attribute. */
  public static final String ALT_ATTRIBUTE = "alt";

  /** Image Height Attribute. */
  public static final String HEIGHT_ATTRIBUTE = "height";

  /** Image Width Attribute. */
  public static final String WIDTH_ATTRIBUTE = "width";

  /** Image Source Attribute. */
  public static final String SRC_ATTRIBUTE = "src";

  /** Source Prefs Attribute. */
  public static final String[] ATTRIBUTE_NAMES =
      {ALT_ATTRIBUTE, HEIGHT_ATTRIBUTE, WIDTH_ATTRIBUTE, SRC_ATTRIBUTE};
}
