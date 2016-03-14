package org.waveprotocol.wave.model.wave;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Andrew Kaplanov
 */
public class WaveDigest {

  private final String waveId;
  private final String title;
  private final String snippet;
  private final String creator;
  private final List<String> participants;
  private final int blipCount;
  private final long created;
  private final long lastModified;

  public WaveDigest() {
    this.waveId = "";
    this.title = "";
    this.snippet = "";
    this.creator = "";
    this.participants = new ArrayList<String>();
    this.blipCount = 0;
    this.lastModified = -1;
    this.created = -1;
  }

  public WaveDigest(String waveId, String title, String snippet,
      String creator, List<String> participants,
      int blipCount, long created, long lastModified) {
    this.waveId = waveId;
    this.title = title;
    this.snippet = snippet;
    this.creator = creator;
    this.participants = participants;
    this.blipCount = blipCount;
    this.lastModified = lastModified;
    this.created = created;
  }

  public String getTitle() {
    return title;
  }

  public String getSnippet() {
    return snippet;
  }

  public String getWaveId() {
    return waveId;
  }

  public String getCreator() {
    return creator;
  }

  public List<String> getParticipants() {
    return participants;
  }

  public long getLastModified() {
    return lastModified;
  }

  public long getCreated() {
    return created;
  }

  public int getBlipCount() {
    return blipCount;
  }

}
