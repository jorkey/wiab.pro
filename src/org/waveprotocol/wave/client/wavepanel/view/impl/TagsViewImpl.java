
package org.waveprotocol.wave.client.wavepanel.view.impl;

import org.waveprotocol.wave.client.wavepanel.view.IntrinsicTagsView;
import org.waveprotocol.wave.client.wavepanel.view.TagView;
import org.waveprotocol.wave.client.wavepanel.view.TagsView;
import org.waveprotocol.wave.client.wavepanel.view.View;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;

/**
 * Implements a tags-collection view by delegating primitive state
 * matters to a view object, and structural state matters to a helper. The
 * intent is that the helper is a flyweight handler.
 *
 * @param <I> intrinsic participants implementation
 */
public final class TagsViewImpl<I extends IntrinsicTagsView> // \u2620
    extends AbstractStructuredView<TagsViewImpl.Helper<? super I>, I> // \u2620
    implements TagsView {

  /**
   * Handles structural queries on participants views.
   *
   * @param <I> intrinsic tags implementation
   */
  public interface Helper<I> {
    
    TagView append(
        I impl, Conversation conv, String tag, WaveletOperationContext opContext, boolean showDiff);
    
    TagView remove(
        I impl, Conversation conv, String tag, WaveletOperationContext opContext, boolean showDiff);
    
    void clearDiffs(I impl);
    
    void remove(I impl);
  }

  public TagsViewImpl(Helper<? super I> helper, I impl) {
    super(helper, impl);
  }

  @Override
  public Type getType() {
    return Type.TAGS;
  }

  @Override
  public TagView appendTag(
      Conversation conv, String tag, WaveletOperationContext opContext, boolean showDiff) {
    return helper.append(impl, conv, tag, opContext, showDiff);
  }

  @Override
  public TagView removeTag(
      Conversation conv, String tag, WaveletOperationContext opContext, boolean showDiff) {
    return helper.remove(impl, conv, tag, opContext, showDiff);
  }  
  
  @Override
  public void clearDiffs() {
    helper.clearDiffs(impl);
  }
  
  @Override
  public void remove() {
    helper.remove(impl);
  }

  @Override
  public String getId() {
    return impl.getId();
  }
  
  @Override
  public View getParent() {
    return null;
  }
}
