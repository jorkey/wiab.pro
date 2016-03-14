
package org.waveprotocol.box.server.rpc.render.view;

import org.waveprotocol.wave.model.conversation.Conversation;

/**
 * View of a tag collection.
 *
 */
public interface TagsView extends View, IntrinsicTagsView {

  /**
   * Appends a rendering of a tag. (the id alone is an insufficient
   * representation of the participation, hence a pair is required).
   *
   * @param conversation conversation in which the participant participates
   * @param tag render
   */
  TagView appendTag(Conversation conversation, String tag);
}
