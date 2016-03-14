package org.waveprotocol.box.server.contact;

/**
 * Call from one participant to another implementation.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class CallImpl implements Call {
  private final boolean directly;
  private final long time;

  public CallImpl(boolean directly, long time) {
    this.directly = directly;
    this.time = time;
  }

  @Override
  public boolean isDirect() {
    return directly;
  }

  @Override
  public long getTime() {
    return time;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final CallImpl other = (CallImpl) obj;
    if (this.directly != other.directly) {
      return false;
    }
    if (this.time != other.time) {
      return false;
    }
    return true;
  }
}
