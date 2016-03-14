package org.waveprotocol.box.server.rpc.render.web.text;

import com.google.wave.api.Gadget;

import java.util.List;


public interface GadgetRenderer {

  public abstract void render(Gadget gadget, List<String> contributors, StringBuilder builder);

}
