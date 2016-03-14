/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.wave.model.conversation;

import junit.framework.TestCase;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationBlip;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationBlipImpl;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationImpl;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationThread;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationThreadImpl;
import org.waveprotocol.wave.model.conversation.quasi.QuasiConversationViewImpl;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.supplement.SupplementedWave;
import org.waveprotocol.wave.model.testing.FakeIdGenerator;
import org.waveprotocol.wave.model.wave.opbased.ObservableWaveView;

/**
 * Quasi-deletion test.
 *
 * @author dyukon@gmail.com (D. Konovalchik)
 */
public class QuasiDeletionTest extends TestCase {

  private QuasiConversationImpl quasi_c;
  private WaveletBasedConversation base_c;
  private QuasiConversationThreadImpl quasi_rt, quasi_ot;
  private ObservableConversationThread base_rt, base_ot;
  private QuasiConversationBlipImpl quasi_b, quasi_b1, quasi_b2;
  private ObservableConversationBlip base_b, base_b1, base_b2;
  private WaveletOperationContext opContext;
  private SupplementedWave supplement;

  @Override
  protected void setUp() throws Exception {
    IdGenerator idGenerator = FakeIdGenerator.create();
    ObservableWaveView waveView = ConversationTestUtils.createWaveView(idGenerator);
    WaveBasedConversationView conversationView = WaveBasedConversationView.create(waveView,
        idGenerator);
    conversationView.createRoot();
    
    QuasiConversationViewImpl quasiConversationView = QuasiConversationViewImpl.create(null);
    supplement = mock(SupplementedWave.class);
    when(supplement.isBlipLooked(any(ConversationBlip.class))).thenReturn(Boolean.TRUE);
    when(supplement.isUnread(any(ConversationBlip.class))).thenReturn(Boolean.TRUE);
    quasiConversationView.initialize(conversationView, supplement);
        
    quasi_c = (QuasiConversationImpl) quasiConversationView.getRoot();
    base_c = quasi_c.getBaseConversation();
    quasi_rt = (QuasiConversationThreadImpl) quasi_c.getRootThread();
    base_rt = quasi_rt.getBaseThread();
    quasi_b = (QuasiConversationBlipImpl) quasi_rt.appendBlip();
    base_b = quasi_b.getBaseBlip();
    quasi_ot = (QuasiConversationThreadImpl) quasi_b.addReplyThread();
    base_ot = quasi_ot.getBaseThread();
    quasi_b1 = (QuasiConversationBlipImpl) quasi_ot.appendBlip();
    base_b1 = quasi_b1.getBaseBlip();
    quasi_b2 = (QuasiConversationBlipImpl) quasi_ot.appendBlip();
    base_b2 = quasi_b2.getBaseBlip();
    opContext = new WaveletOperationContext(null, -1, 0, false);
  }

  public void testSingleBlipDeletion() {
    localDelete(quasi_b1);
    assertTrue(conversationContainsBlips(base_c, base_b, base_b2));
    assertTrue(conversationDoesntContainBlips(base_c, base_b1));
    assertTrue(conversationContainsThreads(base_c, base_ot));
    assertTrue(conversationContainsBlips(quasi_c, quasi_b, quasi_b2));
    assertTrue(conversationDoesntContainBlips(quasi_c, quasi_b1));
    assertTrue(conversationContainsThreads(quasi_c, quasi_ot));
    assertTrue(blipsAreNotDeleted(quasi_b, quasi_b2));

    localDelete(quasi_b2);
    assertTrue(conversationContainsBlips(base_c, base_b));
    assertTrue(conversationDoesntContainBlips(base_c, base_b1, base_b2));
    assertTrue(conversationDoesntContainThreads(base_c, base_ot));
    assertTrue(conversationContainsBlips(quasi_c, quasi_b));
    assertTrue(conversationDoesntContainBlips(quasi_c, quasi_b1, quasi_b2));
    assertTrue(conversationDoesntContainThreads(quasi_c, quasi_ot));
    assertTrue(blipsAreNotDeleted(quasi_b));
  }

  public void testSingleBlipQuasiDeletionAndReading() {
    remoteDelete(quasi_b1);
    assertTrue(conversationContainsBlips(base_c, base_b, base_b2));
    assertTrue(conversationDoesntContainBlips(base_c, base_b1));
    assertTrue(conversationContainsThreads(base_c, base_ot));
    assertTrue(conversationContainsBlips(quasi_c, quasi_b, quasi_b1, quasi_b2));
    assertTrue(conversationContainsThreads(quasi_c, quasi_ot));
    assertTrue(blipsAreDeleted(quasi_b1));
    assertTrue(blipsAreNotDeleted(quasi_b, quasi_b2));

    remoteDelete(quasi_b2);
    assertTrue(conversationContainsBlips(base_c, base_b));
    assertTrue(conversationDoesntContainBlips(base_c, base_b1, base_b2));
    assertTrue(conversationContainsThreads(base_c, base_ot));
    assertTrue(conversationContainsBlips(quasi_c, quasi_b, quasi_b1, quasi_b2));
    assertTrue(conversationContainsThreads(quasi_c, quasi_ot));
    assertTrue(blipsAreDeleted(quasi_b1, quasi_b2));
    assertTrue(blipsAreNotDeleted(quasi_b));

    localDelete(quasi_b1);
    assertTrue(conversationContainsBlips(base_c, base_b));
    assertTrue(conversationDoesntContainBlips(base_c, base_b1, base_b2));
    assertTrue(conversationContainsThreads(base_c, base_ot));
    assertTrue(conversationContainsBlips(quasi_c, quasi_b, quasi_b2));
    assertTrue(conversationDoesntContainBlips(quasi_c, quasi_b1));
    assertTrue(conversationContainsThreads(quasi_c, quasi_ot));
    assertTrue(blipsAreDeleted(quasi_b2));
    assertTrue(blipsAreNotDeleted(quasi_b));

    localDelete(quasi_b2);
    localDelete(quasi_ot);    
    assertTrue(conversationContainsBlips(base_c, base_b));
    assertTrue(conversationDoesntContainBlips(base_c, base_b1, base_b2));
    assertTrue(conversationDoesntContainThreads(base_c, base_ot));
    assertTrue(conversationContainsBlips(quasi_c, quasi_b));
    assertTrue(conversationDoesntContainBlips(quasi_c, quasi_b1, quasi_b2));
    assertTrue(conversationDoesntContainThreads(quasi_c, quasi_ot));
    assertTrue(blipsAreNotDeleted(quasi_b));
  }

  public void testThreadDeletion() {
    localDelete(quasi_ot);
    assertTrue(conversationContainsBlips(base_c, base_b));
    assertTrue(conversationDoesntContainBlips(base_c, base_b1, base_b2));
    assertTrue(conversationDoesntContainThreads(base_c, base_ot));
    assertTrue(conversationContainsBlips(quasi_c, quasi_b));
    assertTrue(conversationDoesntContainBlips(quasi_c, quasi_b1, quasi_b2));
    assertTrue(conversationDoesntContainThreads(quasi_c, quasi_ot));
    assertTrue(blipsAreNotDeleted(quasi_b));
  }

  public void testThreadQuasiDeletion() {
    remoteDelete(quasi_ot);
    assertTrue(conversationContainsBlips(base_c, base_b));
    assertTrue(conversationDoesntContainBlips(base_c, base_b1, base_b2));
    assertTrue(conversationDoesntContainThreads(base_c, base_ot));
    assertTrue(conversationContainsBlips(quasi_c, quasi_b, quasi_b1, quasi_b2));
    assertTrue(conversationContainsThreads(quasi_c, quasi_ot));
    assertTrue(blipsAreNotDeleted(quasi_b));
    assertTrue(blipsAreDeleted(quasi_b1, quasi_b2));

    localDelete(quasi_ot);
    assertTrue(conversationContainsBlips(base_c, base_b));
    assertTrue(conversationDoesntContainBlips(base_c, base_b1, base_b2));
    assertTrue(conversationDoesntContainThreads(base_c, base_ot));
    assertTrue(conversationContainsBlips(quasi_c, quasi_b));
    assertTrue(conversationDoesntContainBlips(quasi_c, quasi_b1, quasi_b2));
    assertTrue(conversationDoesntContainThreads(quasi_c, quasi_ot));
    assertTrue(blipsAreNotDeleted(quasi_b));
  }

  public void testHierarchicalBlipDeletion() {
    localDelete(quasi_b);
    assertTrue(conversationDoesntContainBlips(base_c, base_b, base_b1, base_b2));
    assertTrue(conversationDoesntContainThreads(base_c, base_ot));
    assertTrue(conversationDoesntContainBlips(quasi_c, quasi_b, quasi_b1, quasi_b2));
    assertTrue(conversationDoesntContainThreads(quasi_c, quasi_ot));
  }

  public void testHierarchicalBlipQuasiDeletionAndReading() {
    remoteDelete(quasi_b);
    assertTrue(conversationDoesntContainBlips(base_c, base_b, base_b1, base_b2));
    assertTrue(conversationDoesntContainThreads(base_c, base_ot));
    assertTrue(conversationContainsBlips(quasi_c, quasi_b, quasi_b1, quasi_b2));
    assertTrue(conversationContainsThreads(quasi_c, quasi_ot));
    assertTrue(blipsAreDeleted(quasi_b, quasi_b1, quasi_b2));

    localDelete(quasi_b);
    assertTrue(conversationDoesntContainBlips(base_c, base_b, base_b1, base_b2));
    assertTrue(conversationDoesntContainThreads(base_c, base_ot));
    assertTrue(conversationDoesntContainBlips(quasi_c, quasi_b, quasi_b1, quasi_b2));
    assertTrue(conversationDoesntContainThreads(quasi_c, quasi_ot));
  }

  public void testRepeatedBlipQuasiDeletion() {
    remoteDelete(quasi_b1);
    remoteDelete(quasi_b);
    assertTrue(conversationDoesntContainBlips(base_c, base_b, base_b1, base_b2));
    assertTrue(conversationDoesntContainThreads(base_c, base_ot));
    assertTrue(conversationContainsBlips(quasi_c, quasi_b, quasi_b1, quasi_b2));
    assertTrue(conversationContainsThreads(quasi_c, quasi_ot));
    assertTrue(blipsAreDeleted(quasi_b, quasi_b1, quasi_b2));
  }

  // Private methods

  private void localDelete(ConversationBlip blip) {
    blip.delete();
  }

  private void localDelete(ConversationThread thread) {
    thread.delete();
  }

  private void remoteDelete(ConversationBlip blip) {
    for (ConversationThread thread : blip.getReplyThreads()) {
      remoteDelete(thread);
    }
    
    ObservableManifestBlip manifestBlip = getManifestBlipByQuasiBlip(blip);
    DocumentBasedManifestThread manifestThread = getManifestThreadByQuasiThread(blip.getThread());
    manifestThread.triggerOnBeforeManifestBlipRemoved(manifestBlip, opContext);
    manifestThread.triggerOnManifestBlipRemoved(manifestBlip, opContext);
  }

  private void remoteDelete(ConversationThread thread) {
    for (ConversationBlip blip : thread.getBlips()) {
      remoteDelete(blip);
    }
    
    ObservableManifestThread manifestThread = getManifestThreadByQuasiThread(thread);    
    DocumentBasedManifestBlip manifestBlip = getManifestBlipByQuasiBlip(thread.getParentBlip());
    manifestBlip.triggerOnBeforeManifestThreadRemoved(manifestThread, opContext);
    manifestBlip.triggerOnManifestThreadRemoved(manifestThread, opContext);
  }

  private boolean blipsAreDeleted(ConversationBlip... blips) {
    for (ConversationBlip blip : blips) {
      if (!((QuasiConversationBlip) blip).isQuasiDeleted()) {
        return false;
      }
    }
    return true;
  }

  private boolean blipsAreNotDeleted(ConversationBlip... blips) {
    for (ConversationBlip blip : blips) {
      if (((QuasiConversationBlip) blip).isQuasiDeleted()) {
        return false;
      }
    }
    return true;
  }

  private boolean conversationContainsBlips(Conversation conversation, ConversationBlip... blips) {
    return conversationContainsBlips(conversation, true, blips);
  }

  private boolean conversationDoesntContainBlips(Conversation conversation,
      ConversationBlip... blips) {
    return conversationContainsBlips(conversation, false, blips);
  }

  private boolean conversationContainsBlips(Conversation conversation, boolean mustContain,
      ConversationBlip... blips) {
    for (ConversationBlip blip : blips) {
      if (conversationContainsBlip(conversation, blip) != mustContain) {
        return false;
      }
    }
    return true;
  }

  private boolean conversationContainsBlip(Conversation conversation, ConversationBlip blip) {
    return conversation.getBlip(blip.getId()) == blip;
  }

  private boolean conversationContainsThreads(Conversation conversation,
      ConversationThread... threads) {
    return conversationContainsThreads(conversation, true, threads);
  }

  private boolean conversationDoesntContainThreads(Conversation conversation,
      ConversationThread... threads) {
    return conversationContainsThreads(conversation, false, threads);
  }

  private boolean conversationContainsThreads(Conversation conversation, boolean mustContain,
      ConversationThread... threads) {
    for (ConversationThread thread : threads) {
      if (conversationContainsThread(conversation, thread) != mustContain) {
        return false;
      }
    }
    return true;
  }

  private boolean conversationContainsThread(Conversation conversation, ConversationThread thread) {
    return conversation.getThread(thread.getId()) == thread;
  }

  private DocumentBasedManifestThread getManifestThreadByQuasiThread(ConversationThread quasiThread) {
    WaveletBasedConversationThread waveletThread =
        (WaveletBasedConversationThread) ((QuasiConversationThread) quasiThread).getBaseThread();
    return (DocumentBasedManifestThread) waveletThread.getManifestThread();
  }
  
  private DocumentBasedManifestBlip getManifestBlipByQuasiBlip(ConversationBlip quasiBlip) {
    WaveletBasedConversationBlip waveletBlip =
        (WaveletBasedConversationBlip) ((QuasiConversationBlip) quasiBlip).getBaseBlip();
    return (DocumentBasedManifestBlip) waveletBlip.getManifestBlip();
  }
}
