package org.waveprotocol.wave.client.wavepanel.view.dom.full.i18n;

import com.google.gwt.i18n.client.Messages;
import com.google.gwt.i18n.client.Messages.DefaultMessage;

/**
 *
 * @author akaplanov (Andrew Kaplanov)
 */
public interface TagMessages extends Messages {
  @DefaultMessage("Tags:")
  String tags();

  @DefaultMessage("less")
  String less();

  @DefaultMessage("more")
  String more();
  
  @DefaultMessage("Add tag")
  String addTagHint();
}
