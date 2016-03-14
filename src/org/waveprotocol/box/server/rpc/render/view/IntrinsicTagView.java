package org.waveprotocol.box.server.rpc.render.view;

/**
 * View of a tag.
 *
 */
public interface IntrinsicTagView {
  String getId();

  String getName();
  void setName(String name);
}
