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

package org.waveprotocol.box.server.persistence.blocks.impl;

import org.waveprotocol.box.server.persistence.blocks.TopMarker;
import org.waveprotocol.wave.model.id.SegmentId;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import static junit.framework.Assert.assertFalse;

import junit.framework.TestCase;
import org.waveprotocol.box.server.persistence.blocks.Block;
import org.waveprotocol.box.server.persistence.blocks.FarBackwardMarker;
import org.waveprotocol.box.server.persistence.blocks.FarForwardMarker;
import org.waveprotocol.box.server.persistence.blocks.Marker;
import org.waveprotocol.box.server.persistence.blocks.SegmentOperation;
import org.waveprotocol.box.server.persistence.blocks.SnapshotMarker;
import org.waveprotocol.box.server.persistence.blocks.VersionInfo;

import org.waveprotocol.box.server.persistence.protos.ProtoBlockStore;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.impl.DocInitializationBuilder;
import org.waveprotocol.wave.model.document.operation.impl.DocOpBuilder;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.IdGeneratorImpl;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.operation.OpComparators;
import org.waveprotocol.wave.model.operation.wave.AddParticipant;
import org.waveprotocol.wave.model.operation.wave.AddSegment;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.EndModifyingSegment;
import org.waveprotocol.wave.model.operation.wave.NoOp;
import org.waveprotocol.wave.model.operation.wave.RemoveParticipant;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Serialization/deserialization tests.
 *
 * @author akaplanov@gmail.com (A. Kaplanov)
 */
public class SerializationTest extends TestCase {

  private static final String DOMAIN = "example.com";

  private static final WaveletId WAVELET_ID = WaveletId.of(DOMAIN, "456");

  private static final ParticipantId USER1 = ParticipantId.ofUnsafe("user1", DOMAIN);
  private static final ParticipantId USER2 = ParticipantId.ofUnsafe("user2", DOMAIN);
  private static final ParticipantId USER3 = ParticipantId.ofUnsafe("user3", DOMAIN);

  private static final long VERSION1 = 1L;
  private static final HashedVersion HASHED_VERSION1 = HashedVersion.unsigned(1);
  private static final long VERSION2 = 2L;
  private static final HashedVersion HASHED_VERSION2 = HashedVersion.unsigned(2);
  private static final long VERSION3 = 3L;
  private static final HashedVersion HASHED_VERSION3 = HashedVersion.unsigned(3);

  private static final long CREATION_TIME = 123L;
  private static final long LAST_MODIFY_TIME = 456L;

  private static final WaveletOperationContext CONTEXT1 = new WaveletOperationContext(USER1, LAST_MODIFY_TIME, VERSION1);
  private static final WaveletOperationContext CONTEXT2 = new WaveletOperationContext(USER2, LAST_MODIFY_TIME, VERSION2);
  private static final WaveletOperationContext CONTEXT3 = new WaveletOperationContext(USER3, LAST_MODIFY_TIME, VERSION3);

  private static final WaveletOperation OP1 = new AddParticipant(CONTEXT1, USER1);
  private static final WaveletOperation OP2 = new RemoveParticipant(CONTEXT1, USER2);
  private static final WaveletOperation OP3 = new NoOp(CONTEXT1);

  private static final SegmentOperation SOP = new SegmentOperationImpl(OP1, OP2, OP3);

  private static final IdGenerator ID_GENERATOR = new IdGeneratorImpl(DOMAIN, new IdGeneratorImpl.Seed() {

    @Override
    public String get() {
      return "qwe";
    }
  });

  private static final String BLOCK_ID = ID_GENERATOR.newBlockId();

  private static final String BLIP1_ID = ID_GENERATOR.newBlipId();
  private static final String BLIP2_ID = ID_GENERATOR.newBlipId();

  private static final SegmentId SEGMENT1_ID = SegmentId.ofBlipId(BLIP1_ID);
  private static final SegmentId SEGMENT2_ID = SegmentId.ofBlipId(BLIP2_ID);

  @Override
  protected void setUp() throws Exception {
  }

  public void testTopMarkerSerialization() throws Exception {
    TopMarker m = new TopMarkerImpl(6, 2);

    byte[] serialized = m.serialize();
    TopMarker m1 = TopMarkerImpl.deserialize(new ByteArrayInputStream(serialized));

    assertEquals(2, m1.getFromPreviousVersionOperationOffset());
    assertEquals(6, m1.getVersionInfoOffset());
  }

  public void testFarBackwardMarkerSerialization() throws Exception {
    FarBackwardMarker m = new FarBackwardMarkerImpl(10, 24, 12);

    byte[] serialized = m.serialize();
    FarBackwardMarker m1 = FarBackwardMarkerImpl.deserialize(new ByteArrayInputStream(serialized));

    assertEquals(10, m1.getDistanceToPreviousFarVersion());
    assertEquals(24, m1.getPreviousFarMarkerOffset());
    assertEquals(12, m1.getFromPreviousFarVersionOperationOffset());
  }

  public void testFarForwardMarkerSerialization() throws Exception {
    FarForwardMarker m = new FarForwardMarkerImpl(10, 4);

    byte[] serialized = m.serialize();
    FarForwardMarker m1 = FarForwardMarkerImpl.deserialize(new ByteArrayInputStream(serialized));

    assertEquals(10, m1.getDistanceToNextFarVersion());
    assertEquals(4, m1.getNextFarMarkerOffset());
  }

  public void testSnapshotMarkerSerialization() throws Exception {
    SnapshotMarker m = new SnapshotMarkerImpl(5);

    byte[] serialized = m.serialize();
    SnapshotMarker m1 = SnapshotMarkerImpl.deserialize(new ByteArrayInputStream(serialized));

    assertEquals(5, m1.getSnapshotOffset());
  }

  public void testSegmentOperationSerialization() throws Exception {
    SegmentOperation o = new SegmentOperationImpl(OP1, OP2, OP3);

    Blob serialized = o.serialize();
    SegmentOperation o1 = SegmentOperationImpl.deserialize(serialized.getData(), SEGMENT1_ID, CONTEXT1);

    assertEquals(3, o1.getOperations().size());
    assertEquals(OP1, o1.getOperations().get(0));
    assertEquals(OP2, o1.getOperations().get(1));
    assertEquals(OP3, o1.getOperations().get(2));
  }

  public void testIndexSnapshotSerialization() throws Exception {
    IndexSnapshot s = new IndexSnapshot();
    s.applyAndReturnReverse(new AddSegment(CONTEXT1, SEGMENT1_ID));
    s.applyAndReturnReverse(new AddSegment(CONTEXT1, SEGMENT2_ID));
    s.applyAndReturnReverse(new EndModifyingSegment(CONTEXT1, SEGMENT2_ID));

    ProtoBlockStore.SegmentSnapshotRecord serialized = s.serialize();
    IndexSnapshot s1 = (IndexSnapshot)IndexSnapshot.deserialize(serialized, SegmentId.INDEX_ID);

    assertEquals(2, s1.getExistingSegmentIds().size());
    assertEquals(1, s1.getBeingModifiedSegmentIds().size());
    assertEquals(SEGMENT1_ID, s1.getBeingModifiedSegmentIds().iterator().next());
    assertEquals(SEGMENT2_ID, s1.getLastModifiedSegmentId());

    s1.applyAndReturnReverse(new EndModifyingSegment(CONTEXT2, SEGMENT1_ID));

    serialized = s1.serialize();
    IndexSnapshot s2 = (IndexSnapshot)IndexSnapshot.deserialize(serialized, SegmentId.INDEX_ID);

    assertEquals(0, s2.getBeingModifiedSegmentIds().size());
    assertEquals(SEGMENT1_ID, s2.getLastModifiedSegmentId());
  }

  public void testParticipantSnapshotSerialization() throws Exception {
    ParticipantsSnapshot s = new ParticipantsSnapshot();
    s.applyAndReturnReverse(new AddParticipant(CONTEXT1, USER1));
    s.applyAndReturnReverse(new AddParticipant(CONTEXT2, USER2));
    s.applyAndReturnReverse(new AddParticipant(CONTEXT3, USER3));

    ProtoBlockStore.SegmentSnapshotRecord serialized = s.serialize();
    ParticipantsSnapshot s1 = (ParticipantsSnapshot)ParticipantsSnapshot.deserialize(serialized,
      SegmentId.PARTICIPANTS_ID);

    assertEquals(USER1, s1.getCreator());
    assertEquals(3, s1.getParticipants().size());
    assertTrue(s1.getParticipants().contains(USER1));
    assertTrue(s1.getParticipants().contains(USER2));
    assertTrue(s1.getParticipants().contains(USER3));
  }

  public void testBlipSnapshotSerialization() throws Exception {
    BlipSnapshot s = new BlipSnapshot(BLIP1_ID);
    DocInitialization content = new DocInitializationBuilder().characters("Hello there.").build();
    s.applyAndReturnReverse(new WaveletBlipOperation(BLIP1_ID, new BlipContentOperation(CONTEXT1, content)));
    DocOp docOp1 = new DocOpBuilder().characters("World.").retain(12).build();
    s.applyAndReturnReverse(new WaveletBlipOperation(BLIP1_ID, new BlipContentOperation(CONTEXT2, docOp1)));
    DocOp docOp2 = new DocOpBuilder().characters("World.").retain(18).build();
    s.applyAndReturnReverse(new WaveletBlipOperation(BLIP1_ID, new BlipContentOperation(CONTEXT3, docOp2)));

    ProtoBlockStore.SegmentSnapshotRecord serialized = s.serialize();
    BlipSnapshot s1 = (BlipSnapshot)SegmentSnapshotImpl.deserialize(serialized, SEGMENT1_ID);

    assertEquals(USER1, s1.getAuthor());
    assertEquals(3, s1.getContributors().size());
    assertTrue(s1.getContributors().contains(USER1));
    assertTrue(s1.getContributors().contains(USER2));
    assertTrue(s1.getContributors().contains(USER3));
    assertEquals(VERSION3, s1.getLastModifiedVersion());
    assertEquals(LAST_MODIFY_TIME, s1.getLastModifiedTime());
    assertTrue(OpComparators.SYNTACTIC_IDENTITY.equal(s.getContent(), s1.getContent()));

    BlipSnapshot s2 = (BlipSnapshot)s1.duplicate();

    assertEquals(USER1, s2.getAuthor());
    assertEquals(3, s2.getContributors().size());
    assertTrue(s2.getContributors().contains(USER1));
    assertTrue(s2.getContributors().contains(USER2));
    assertTrue(s2.getContributors().contains(USER3));
    assertEquals(VERSION3, s2.getLastModifiedVersion());
    assertEquals(LAST_MODIFY_TIME, s2.getLastModifiedTime());
    assertTrue(OpComparators.SYNTACTIC_IDENTITY.equal(s1.getContent(), s2.getContent()));
  }

  public void testVersionInfoSerialization() {
    VersionInfo i = new VersionInfoImpl(VERSION1, USER1, LAST_MODIFY_TIME);

    ProtoBlockStore.VersionInfoRecord serialized = i.serialize(CollectionUtils.newLinkedList(USER1));
    VersionInfo i1 = VersionInfoImpl.deserialize(serialized, CollectionUtils.newLinkedList(USER1, USER2));

    assertEquals(USER1, i1.getAuthor());
    assertEquals(VERSION1, i1.getVersion());
    assertEquals(LAST_MODIFY_TIME, i1.getTimestamp());
  }

  public void testFragmentIndexSerialization() throws Exception {
    FragmentIndex fi = new FragmentIndex(true);
    fi = modifyAndCheckSerialization(fi, false);
    modifyAndCheckSerialization(fi, true);
  }

  private FragmentIndex modifyAndCheckSerialization(FragmentIndex fragmentIndex, boolean finish) throws InvalidProtocolBufferException {
    int initialLastMarkerOffset = fragmentIndex.getLastMarkerOffset();

    // Modify
    TopMarker marker = new TopMarkerImpl();
    int markerOffset = fragmentIndex.addMarker(marker);
    assertEquals(markerOffset, fragmentIndex.getLastMarkerOffset());

    TopMarker marker1 = new TopMarkerImpl();
    int marker1Offset = fragmentIndex.addMarker(marker1);
    assertEquals(marker1Offset, fragmentIndex.getLastMarkerOffset());

    int snapshotIndex = fragmentIndex.addSnapshot(10, 12);
    int snapshotIndex1 = fragmentIndex.addSnapshot(11, 21);

    if (finish) {
      fragmentIndex.finish();
    }

    // Serialize initial index
    ByteString serializedMarkers = fragmentIndex.serializeMarkers();
    ByteString serializedSnapshotsIndex = fragmentIndex.serializeSnapshotsIndex();
    ByteString serializedLastSnapshot = fragmentIndex.serializeLastSnapshot();

    // Deserialize initial index
    FragmentIndex deserializedFragmentIndex
      = new FragmentIndex(serializedMarkers.toByteArray(), serializedSnapshotsIndex.toByteArray(),
          serializedLastSnapshot.toByteArray(), fragmentIndex.isFirst(), fragmentIndex.isLast());

    // Check deserialized index
    assertEquals(marker1Offset, deserializedFragmentIndex.getLastMarkerOffset());
    Marker deserializedMarker1 = deserializedFragmentIndex.deserializeTopMarker(deserializedFragmentIndex.getLastMarkerOffset());
    assertEquals(markerOffset, deserializedMarker1.getPreviousMarkerOffset());
    Marker deserializedMarker = deserializedFragmentIndex.deserializeTopMarker(deserializedMarker1.getPreviousMarkerOffset());
    assertEquals(initialLastMarkerOffset, deserializedMarker.getPreviousMarkerOffset());
    assertEquals(marker1Offset, deserializedMarker.getNextMarkerOffset());

    assertEquals(10, deserializedFragmentIndex.getSnapshotVersion(snapshotIndex));
    assertEquals(12, deserializedFragmentIndex.getSnapshotMarkerOffset(snapshotIndex));
    assertEquals(11, deserializedFragmentIndex.getSnapshotVersion(snapshotIndex1));
    assertEquals(21, deserializedFragmentIndex.getSnapshotMarkerOffset(snapshotIndex1));

    assertEquals(finish, !deserializedFragmentIndex.isLast());

    return deserializedFragmentIndex;
  }

  public void testBlockHeaderSerialization() {
    BlockHeader h = new BlockHeader(BLOCK_ID);

    ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    FragmentIndex si1 = h.createFragmentIndex(SEGMENT1_ID, true);
    si1.addMarker(new TopMarkerImpl());

    FragmentIndex si2 = h.createFragmentIndex(SEGMENT2_ID, true);
    si2.addMarker(new TopMarkerImpl());
    si2.addMarker(new TopMarkerImpl());
    si2.finish();

    ProtoBlockStore.BlockHeaderRecord serialized = h.serialize();
    BlockHeader h1 = BlockHeader.deserialize(serialized);

    assertEquals(BLOCK_ID, h1.getBlockId());
    assertTrue(h1.hasSegment(SEGMENT1_ID));
    assertTrue(h1.hasSegment(SEGMENT2_ID));

    FragmentIndex dsi1 = h1.getFragmentIndex(SEGMENT1_ID);
    assertNotNull(dsi1);
    assertEquals(si1.serializeMarkers(), dsi1.serializeMarkers());
    assertEquals(si1.serializeSnapshotsIndex(), dsi1.serializeSnapshotsIndex());
    assertTrue(dsi1.isLast());

    FragmentIndex dsi2 = h1.getFragmentIndex(SEGMENT2_ID);
    assertNotNull(dsi2);
    assertEquals(si2.serializeMarkers(), dsi2.serializeMarkers());
    assertEquals(si2.serializeSnapshotsIndex(), dsi2.serializeSnapshotsIndex());
    assertFalse(dsi2.isLast());
  }

  public void testBlockSerialization() throws Exception {
    Block b = BlockImpl.create(BLOCK_ID);

    VersionInfo versionInfo = new VersionInfoImpl(VERSION1, USER1, LAST_MODIFY_TIME);
    int offset1 = b.writeVersionInfo(versionInfo);
    int offset2 = b.writeSegmentOperation(SOP);
    ParticipantsSnapshot snapshot = new ParticipantsSnapshot();
    snapshot.applyAndReturnReverse(new AddParticipant(CONTEXT1, USER1));
    int offset3 = b.writeSegmentSnapshot(snapshot);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    b.serialize(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    Block b1 = BlockImpl.deserialize(in);

    b1.deserializeVersionInfo(offset1);
    b1.deserializeSegmentOperation(offset2, SEGMENT1_ID, CONTEXT1);
    b1.deserializeSegmentSnapshot(offset3, SEGMENT1_ID);

    int offset4 = b1.writeVersionInfo(versionInfo);
    int offset5 = b1.writeSegmentOperation(SOP);
    int offset6 = b1.writeSegmentSnapshot(snapshot);

    out = new ByteArrayOutputStream();
    b1.serialize(out);
    in = new ByteArrayInputStream(out.toByteArray());
    Block b2 = BlockImpl.deserialize(in);

    b2.deserializeVersionInfo(offset4);
    b2.deserializeSegmentOperation(offset5, SEGMENT1_ID, CONTEXT1);
    b2.deserializeSegmentSnapshot(offset6, SEGMENT1_ID);
  }

}
