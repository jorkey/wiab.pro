/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.box.webclient.search.i18n;

import com.google.gwt.i18n.client.Messages;
import com.google.gwt.i18n.client.Messages.DefaultMessage;

/**
 *
 * @author akaplanov (Andrew Kaplanov)
 */
public interface SearchesEditorMessages extends Messages {
  @DefaultMessage("Searches")
  String searches();

  @DefaultMessage("Add")
  String add();

  @DefaultMessage("Modify")
  String modify();
  
  @DefaultMessage("Remove")
  String remove();

  @DefaultMessage("Up")
  String up();

  @DefaultMessage("Down")
  String down();

  @DefaultMessage("Cancel")
  String cancel();

  @DefaultMessage("Ok")
  String ok();
}
