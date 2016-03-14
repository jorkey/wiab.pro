package com.google.wave.api.data;

import java.io.IOException;
import org.waveprotocol.wave.model.id.InvalidIdException;

/**
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public interface ApiAttachment {
  public interface Provider {
    public ApiAttachment getAttachment(String attachmentId) throws IOException, InvalidIdException;
  }

  public String getMimeType();
  public String getFileName();
}
