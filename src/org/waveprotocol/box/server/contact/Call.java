package org.waveprotocol.box.server.contact;

/**
 * Call from one participant to another.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public interface Call {
  /**
   * @return true if call is directly.
   */
  boolean isDirect();

  /**
   * @return the time of call.
   */
  long getTime();
}
