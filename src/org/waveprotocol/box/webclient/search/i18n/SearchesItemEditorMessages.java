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
public interface SearchesItemEditorMessages extends Messages {
  @DefaultMessage("Search")
  String search();

  @DefaultMessage("Name")
  String name();

  @DefaultMessage("Query")
  String query();

  @DefaultMessage("Add")
  String add();

  @DefaultMessage("Modify")
  String modify();

  @DefaultMessage("Cancel")
  String cancel();
}
