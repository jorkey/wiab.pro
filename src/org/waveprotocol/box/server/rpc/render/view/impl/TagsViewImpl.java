
package org.waveprotocol.box.server.rpc.render.view.impl;

import org.waveprotocol.box.server.rpc.render.view.IntrinsicTagsView;
import org.waveprotocol.box.server.rpc.render.view.TagView;
import org.waveprotocol.box.server.rpc.render.view.TagsView;
import org.waveprotocol.wave.model.conversation.Conversation;

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

    TagView append(I impl, Conversation conv, String tag);
  }

  public TagsViewImpl(Helper<? super I> helper, I impl) {
    super(helper, impl);
  }

  @Override
  public TagView appendTag(Conversation conv, String tag) {
    return helper.append(impl, conv, tag);
  }

  @Override
  public String getId() {
    return impl.getId();
  }
}
