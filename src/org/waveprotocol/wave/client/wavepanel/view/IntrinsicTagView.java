
package org.waveprotocol.wave.client.wavepanel.view;

/**
 * View of a tag.
 *
 */
public interface IntrinsicTagView {
  
  public enum TagState {
    NORMAL,
    ADDED,
    REMOVED
  }
  
  String getId();

  String getName();
  
  void setName(String name);
  
  TagState getState();
  
  void setState(TagState state);
  
  String getHint();
  
  void setHint(String hint);
}
