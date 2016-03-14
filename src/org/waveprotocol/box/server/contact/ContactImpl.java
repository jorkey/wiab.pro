/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.waveprotocol.box.server.contact;

import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 *
 * @author akaplanov@gmail (Andrew Kaplanov)
 */
public class ContactImpl implements Contact {
  private final ParticipantId participantId;
  private long lastContactTime;
  private long scoreBonus;

  public ContactImpl(ParticipantId participantId, long lastContactTime, long bonus) {
    this.participantId = participantId;
    this.lastContactTime = lastContactTime;
    this.scoreBonus = bonus;
  }

  @Override
  public ParticipantId getParticipantId() {
    return participantId;
  }

  @Override
  public long getLastContactTime() {
    return lastContactTime;
  }

  @Override
  public void setLastContactTime(long lastContactTime) {
    this.lastContactTime = lastContactTime;
  }

  @Override
  public long getScoreBonus() {
    return scoreBonus;
  }

  @Override
  public void setScoreBonus(long scoreBonus) {
    this.scoreBonus = scoreBonus;
  }
}
