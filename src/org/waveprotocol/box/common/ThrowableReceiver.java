package org.waveprotocol.box.common;

/**
 * Callback interface to sequential reception objects.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public interface ThrowableReceiver<T, E extends Throwable> {
  /**
   * Receives objects.
   *
   * @param the object.
   * @return true to continue, false to cancel transmission.
   */
  public boolean put(T obj) throws E;
}
