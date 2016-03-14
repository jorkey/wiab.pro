
package org.waveprotocol.box.server.rpc.render.view.impl;

import org.waveprotocol.box.server.rpc.render.view.IntrinsicTagView;
import org.waveprotocol.box.server.rpc.render.view.TagView;

/**
 * Implements a tag view by delegating primitive state matters to a view
 * object, and structural state matters to a helper. The intent is that the
 * helper is a flyweight handler.
 *
 * @param <I> intrinsic participant implementation
 */
public final class TagViewImpl<I extends IntrinsicTagView> // \u2620
    extends AbstractStructuredView<TagViewImpl.Helper<? super I>, I> // \u2620
    implements TagView {

  /**
   * Handles structural queries on tag views.
   *
   * @param <I> intrinsic tag implementation
   */
  public interface Helper<I> {
  }

  public TagViewImpl(Helper<? super I> helper, I impl) {
    super(helper, impl);
  }

  @Override
  public String getId() {
    return impl.getId();
  }

  @Override
  public String getName() {
    return impl.getName();
  }

  @Override
  public void setName(String name) {
    impl.setName(name);
  }
}
