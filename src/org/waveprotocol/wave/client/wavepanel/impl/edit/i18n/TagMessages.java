package org.waveprotocol.wave.client.wavepanel.impl.edit.i18n;

import com.google.gwt.i18n.client.Messages;

/**
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public interface TagMessages extends Messages {
  @DefaultMessage("Add tag(s) (separate with comma ','): ")
  String addTagPrompt();
  
  @DefaultMessage("Do you want to remove tag \"{0}\"?")
  String removeTagPrompt(String tag);
  
  @DefaultMessage("Added by {0} at {1}")
  String added(String authorName, String timestamp);
  
  @DefaultMessage("Removed by {0} at {1}")
  String removed(String authorName, String timestamp);
  
  @DefaultMessage("Add tag")
  String addTagHint();  
}
