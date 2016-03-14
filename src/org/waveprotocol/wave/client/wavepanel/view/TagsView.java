
package org.waveprotocol.wave.client.wavepanel.view;

import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;

/**
 * View of a tag collection.
 *
 */
public interface TagsView extends View, IntrinsicTagsView {

  /**
   * Appends a rendering of a tag with showing diffs, if necessary.
   *
   * @param conversation conversation in which the tag is added
   * @param tag tag name
   * @param opContext wavelet operation context
   * @param showDiff - if true, the diffs should be shown
   */
  TagView appendTag(
      Conversation conversation, String tag, WaveletOperationContext opContext, boolean showDiff);
  
  /**
   * Removes a rendering of a tag with showing diffs, if necessary.
   * 
   * @param conversation conversation in which the tag is removed
   * @param tag tag name
   * @param opContext wavelet operation context
   * @param showDiff - if true, the diffs should be shown
   */
  TagView removeTag(
      Conversation conversation, String tag, WaveletOperationContext opContext, boolean showDiff);
  
  /**
   * Clears all diffs shown before.
   */
  void clearDiffs();
}
