package org.waveprotocol.wave.model.supplement;

/**
 *
 * @author Andrew Kaplanov
 */
public class WaveDigestSupplement {
  public static final String FOLDER_INBOX="inbox";
  public static final String FOLDER_ARCHIVE="archive";
  public static final String FOLDER_TRASH="trach";

  private final String folder;
  private final int unreadCount;

  public WaveDigestSupplement() {
    folder = "";
    unreadCount = 0;
  }

  public WaveDigestSupplement(String folder, int unreadCount) {
    this.folder = folder;
    this.unreadCount = unreadCount;
  }

  public String getFolder() {
    return folder;
  }

  public int getUnreadCount() {
    return unreadCount;
  }

}
