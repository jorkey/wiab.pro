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
public interface Contact {

  /**
   * Gets participant id of interlocutor.
   */
  ParticipantId getParticipantId();

  /**
   * Gets last contact time.
   */
  long getLastContactTime();

  /**
   * Sets last contact time.
   */
  void setLastContactTime(long time);

  /**
   * Gets bonus to score of contact.
   */
  long getScoreBonus();

  /**
   * Sets bonus to score of contact.
   */
  void setScoreBonus(long bonus);
}
