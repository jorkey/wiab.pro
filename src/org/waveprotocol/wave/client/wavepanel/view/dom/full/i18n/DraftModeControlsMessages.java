/*
 * Copyright 2014 fwnd80@gmail.com (Nikolay Liber).
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
 */
package org.waveprotocol.wave.client.wavepanel.view.dom.full.i18n;

import com.google.gwt.i18n.client.Messages;

/**
 *
 * @author fwnd80@gmail.com (Nikolay Liber)
 */
public interface DraftModeControlsMessages extends Messages {    
  @DefaultMessage("Draft")
  String draft();
  
  @DefaultMessage("Done")
  String doneTitle();
  
  @DefaultMessage("Cancel")
  String cancelTitle();
  
  @DefaultMessage("Done with editing. Save draft")
  String doneHint();
  
  @DefaultMessage("Finish editing. Discard draft")
  String cancelHint();
}
