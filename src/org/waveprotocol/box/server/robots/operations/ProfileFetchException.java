package org.waveprotocol.box.server.robots.operations;

/**
 *
 * @author Andrew Kaplanov
 */
class ProfileFetchException extends Exception {

  public ProfileFetchException(Throwable cause) {
    super(cause);
  }

  public ProfileFetchException(String message, Throwable cause) {
    super(message, cause);
  }

  public ProfileFetchException(String message) {
    super(message);
  }

  public ProfileFetchException() {
  }

}
