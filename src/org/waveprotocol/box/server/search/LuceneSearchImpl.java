/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.waveprotocol.box.server.search;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.wave.api.SearchResult;
import com.google.wave.api.SearchResult.Digest;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DefaultSimilarity;
import org.apache.lucene.search.FieldCache.LongParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NRTManager;
import org.apache.lucene.search.NRTManagerReopenThread;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.SearcherWarmer;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.util.Version;

import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.search.query.QueryParser;
import org.waveprotocol.box.search.query.SearchQuery;
import org.waveprotocol.box.server.CoreSettings;
import org.waveprotocol.box.server.executor.ExecutorAnnotations;
import org.waveprotocol.box.server.persistence.lucene.IndexDirectory;
import org.waveprotocol.box.server.shutdown.ShutdownManager;
import org.waveprotocol.box.server.shutdown.ShutdownPriority;
import org.waveprotocol.box.server.shutdown.Shutdownable;
import org.waveprotocol.box.server.util.regexp.RegExpWrapFactoryImpl;
import org.waveprotocol.box.server.waveserver.PerUserWaveViewHandler;
import org.waveprotocol.box.server.waveserver.TextCollator;
import org.waveprotocol.box.server.waveserver.WaveDigester;
import org.waveprotocol.box.server.waveserver.WaveMap;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveletstate.WaveletStateException;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.supplement.WaveDigestSupplement;
import org.waveprotocol.wave.model.supplement.WaveDigestWithSupplements;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.model.wave.WaveDigest;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.waveprotocol.box.server.waveletstate.IndexingInProcessException;
import org.waveprotocol.wave.model.operation.OperationException;

/**
 * Lucene based implementation of {@link PerUserWaveViewHandler}.
 *
 * @author yurize@apache.org (Yuri Zelikov)
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
@Singleton
public class LuceneSearchImpl implements WaveIndexer, SearchProvider, SearchBusSubscriber, Closeable {

  private static final Logger LOG = Logger.getLogger(LuceneSearchImpl.class
      .getName());

  private static class WaveSearchWarmer implements SearcherWarmer {

    WaveSearchWarmer(String waveDomain) {
    }

    @Override
    public void warm(IndexSearcher searcher) throws IOException {
      // TODO (Yuri Z): Run some diverse searches, searching against all
      // fields.
    }
  }

  private LongParser longParser = new LongParser() {

    @Override
    public long parseLong(String value) {
      if (value == null || value.isEmpty()) {
        return 0;
      }
      return Long.parseLong(value);
    }
  };

  /** Current indexing waves **/
  private static ConcurrentHashMap<WaveId, ListenableFutureTask<Void>> indexingWaves =
      new ConcurrentHashMap<WaveId, ListenableFutureTask<Void>>();

  /** Commit task **/
  AtomicReference<ListenableFutureTask<Void>> commitTask = new AtomicReference<ListenableFutureTask<Void>>();

  /** Delay between wave indexing **/
  private static final long WAVE_INDEXING_DELAY_SEC = 10;

  /** Delay between commit **/
  private static final long WAVE_COMMIT_DELAY_SEC = 60;

  private static final Version LUCENE_VERSION = Version.LUCENE_35;

  /** Minimum time until a new reader can be opened. */
  private static final double MIN_STALE_SEC = 0.025;

  /** Maximum time until a new reader must be opened. */
  private static final double MAX_STALE_SEC = 1.0;

  private final QueryParser queryParser = new QueryParser(new RegExpWrapFactoryImpl());

  private final Analyzer analyzer;
  private final TextCollator textCollator;
  private final IndexWriter indexWriter;
  private final ScheduledExecutorService indexExecutor;
  private final Similarity similarity;
  private final NRTManager nrtManager;
  private final NRTManagerReopenThread nrtManagerReopenThread;
  private final WaveMap waveMap;
  private final WaveDigester digester;
  private final String waveDomain;
  private final String sharedDomainParticipant;
  private boolean isClosed = false;

  @Inject
  public LuceneSearchImpl(IndexDirectory directory, WaveMap waveMap,
      WaveDigester digester, TextCollator textCollator,
      @Named(CoreSettings.WAVE_SERVER_DOMAIN) final String waveDomain,
      @ExecutorAnnotations.IndexExecutor ScheduledExecutorService indexExecutor) {
    this.textCollator = textCollator;
    this.waveMap = waveMap;
    this.digester = digester;
    this.waveDomain = waveDomain;
    this.indexExecutor = indexExecutor;
    sharedDomainParticipant = ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(waveDomain).getAddress();
    analyzer = new StandardAnalyzer(LUCENE_VERSION, StandardAnalyzer.STOP_WORDS_SET);
    similarity = new DefaultSimilarity() {
      @Override
      public float computeNorm(String string, FieldInvertState fis) {
        return fis.getBoost();
      }

      @Override
      public float tf(float freq) {
        return freq > 0 ? 1.0f : 0.0f;
      }

      @Override
      public float tf(int freq) {
        return freq > 0 ? 1 : 0;
      }

    };
    try {
      IndexWriterConfig config = new IndexWriterConfig(LUCENE_VERSION, analyzer);
      config.setOpenMode(OpenMode.CREATE_OR_APPEND);
      config.setSimilarity(similarity);
      indexWriter = new IndexWriter(directory.getDirectory(), config);
      nrtManager = new NRTManager(indexWriter, new WaveSearchWarmer(waveDomain));
    } catch (IOException ex) {
      throw new IndexException(ex);
    }

    nrtManagerReopenThread = new NRTManagerReopenThread(nrtManager, MAX_STALE_SEC, MIN_STALE_SEC);
    nrtManagerReopenThread.start();

    ShutdownManager.getInstance().register(new Shutdownable() {

      @Override
      public void shutdown() throws Exception {
        synchronized (LuceneSearchImpl.this) {
          if (!isClosed) {
            close();
          }
        }
      }
    }, LuceneSearchImpl.class.getSimpleName(), ShutdownPriority.Storage);
  }

  /**
   * Closes the handler, releases resources and flushes the recent index changes
   * to persistent storage.
   */
  @Override
  public synchronized void close() {
    if (isClosed) {
      throw new AlreadyClosedException("Already closed");
    }
    isClosed = true;
    try {
      nrtManager.close();
      if (analyzer != null) {
        analyzer.close();
      }
      nrtManagerReopenThread.close();
      indexWriter.close();
    } catch (IOException ex) {
      LOG.log(Level.SEVERE, "Failed to close the Lucene index", ex);
    }
    LOG.info("Successfully closed the Lucene index...");
  }

  /**
   * Ensures that the index is up to date. Exits quickly if no changes were done
   * to the index.
   *
   * @throws IOException if something goes wrong.
   */
  public void forceReopen() throws IOException {
    nrtManager.maybeReopen(true);
  }

  @Override
  public synchronized void updateIndex(WaveId waveId) throws WaveletStateException, WaveServerException {
    Preconditions.checkNotNull(waveId);
    try {
      WaveViewData waveData = waveMap.getWaveViewData(waveId);
      // TODO (Yuri Z): Update documents instead of totally removing and adding.
      LOG.info("Updating index for wave " + waveId.serialise());
      removeIndex(waveId);
      addIndex(waveId, waveData);
      sheduleCommitIndex();
      LOG.info("Index for wave " + waveId.serialise() + " has been updated");
    } catch (CorruptIndexException e) {
      throw new IndexException(waveId.serialise(), e);
    } catch (IOException e) {
      throw new IndexException(waveId.serialise(), e);
    }
  }

  @Override
  public void waveletUpdate(WaveletName waveletName, DeltaSequence deltas) {
    sheduleUpdateIndex(waveletName.waveId);
  }

  @Override
  public void waveletCommitted(WaveletName waveletName, HashedVersion version) {
  }

  private ListenableFutureTask<Void> sheduleUpdateIndex(final WaveId waveId) {
    synchronized (indexingWaves) {
      ListenableFutureTask<Void> task = indexingWaves.get(waveId);
      if (task == null) {
        task = ListenableFutureTask.create(new Callable<Void>() {

          @Override
          public Void call() throws Exception {
            indexingWaves.remove(waveId);
            try {
              updateIndex(waveId);
            } catch (IndexingInProcessException e) {
              sheduleUpdateIndex(waveId);
            } catch (Throwable e) {
              LOG.log(Level.SEVERE, "Failed to update index for " + waveId.serialise(), e);
              throw e;
            }
            return null;
          }
        });
        indexingWaves.put(waveId, task);
        indexExecutor.schedule(task, WAVE_INDEXING_DELAY_SEC, TimeUnit.SECONDS);
      }
      return task;
    }
  }

  private ListenableFutureTask<Void> sheduleCommitIndex() {
    ListenableFutureTask<Void> task = commitTask.get();
    if (task == null) {
      task = ListenableFutureTask.create(new Callable<Void>() {

        @Override
        public Void call() throws Exception {
          commitTask.set(null);
          try {
            LOG.info("Commiting indexes...");
            indexWriter.commit();
            LOG.info("Commiting indexes is complete");
          } catch (IndexException e) {
            LOG.log(Level.SEVERE, "Index commit failed", e);
            throw e;
          }
          return null;
        }
      });
      commitTask.set(task);
      indexExecutor.schedule(task, WAVE_COMMIT_DELAY_SEC, TimeUnit.SECONDS);
    }
    return task;
  }

  private void addIndex(WaveId waveId, WaveViewData waveData) throws CorruptIndexException, IOException, WaveletStateException {
    Document doc = new Document();
    addWaveFieldsToIndex(waveId, waveData, doc);
    nrtManager.addDocument(doc);
  }

  private void addWaveFieldsToIndex(WaveId waveId, WaveViewData waveData, Document doc) throws WaveletStateException {
    WaveDigestWithSupplements digestWithSupplements = digester.generateDigestWithSupplements(waveData);
    WaveDigest digest = digestWithSupplements.getDigest();
    Map<ParticipantId, WaveDigestSupplement> supplements = digestWithSupplements.getSupplements();
    if (digest.getParticipants().size() > 0) {
      addField(doc, IndexCondition.Field.WAVE_ID, digest.getWaveId());
      addField(doc, IndexCondition.Field.CREATOR, digest.getCreator());
      for (String participant : digest.getParticipants()) {
        doc.add(new Field(IndexCondition.Field.PARTICIPANTS.toString(), participant, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
      }
      addField(doc, IndexCondition.Field.TITLE, digest.getTitle());
      addField(doc, IndexCondition.Field.CONTENT, digest.getTitle());
      addField(doc, IndexCondition.Field.SNIPPET, digest.getSnippet());
      addField(doc, IndexCondition.Field.BLIP_COUNT, Integer.toString(digest.getBlipCount()));
      for (ReadableWaveletData wavelet : waveData.getWavelets()) {
        if (IdUtil.isConversationalId(wavelet.getWaveletId())
            || IdUtil.isConversationalId(wavelet.getWaveletId())) {
          for (String tag : wavelet.getTags()) {
            addField(doc, IndexCondition.Field.TAG, tag);
            addField(doc, IndexCondition.Field.CONTENT, tag);
          }
          addField(doc, IndexCondition.Field.CONTENT, textCollator.collateTextForWavelet(wavelet));
        }
      }
      addField(doc, IndexCondition.Field.CREATED, Long.toString(digest.getCreated()));
      addField(doc, IndexCondition.Field.LAST_MODIFIED, Long.toString(digest.getLastModified()));
      for (ParticipantId participantId : supplements.keySet()) {
        WaveDigestSupplement supplement = supplements.get(participantId);
        addField(doc, IndexCondition.Field.IN_, IndexCondition.Field.IN_.toString() + participantId.getAddress(),
            supplement.getFolder());
        addField(doc, IndexCondition.Field.UNREAD_COUNT_, IndexCondition.Field.UNREAD_COUNT_.toString() + participantId.getAddress(),
            Integer.toString(supplement.getUnreadCount()));
      }
      LOG.fine("Write index for wave " + waveId.serialise());
      for (Fieldable field : doc.getFields()) {
        LOG.fine("  " + field.name() + " : " + field.stringValue());
      }
    }
  }

  private void addField(Document doc, IndexCondition.Field field, String value) {
    doc.add(new Field(field.toString(), value, field.isStored()?Field.Store.YES:Field.Store.NO,
        field.isAnalyzed()?Field.Index.ANALYZED_NO_NORMS:Field.Index.NOT_ANALYZED_NO_NORMS));
  }

  private void addField(Document doc, IndexCondition.Field field, String fieldName, String value) {
    doc.add(new Field(fieldName, value, field.isStored()?Field.Store.YES:Field.Store.NO,
        field.isAnalyzed()?Field.Index.ANALYZED_NO_NORMS:Field.Index.NOT_ANALYZED_NO_NORMS));
  }

  private void removeIndex(WaveId waveId)
      throws CorruptIndexException, IOException {
    nrtManager.deleteDocuments(new Term(IndexCondition.Field.WAVE_ID.toString(), waveId.serialise()));
  }

  @Override
  public SearchResult search(String query, int startAt, int numResults, ParticipantId viewer) {
    LOG.fine("Search query '" + query + "' from user: " + viewer + " [" + startAt + ", "
        + (startAt + numResults - 1) + "]");
    SearchResult result = new SearchResult(query);
    SearchQuery queryParams = queryParser.parseQuery(query);
    List<IndexCondition> indexConditions =
        SearchQueryHelper.convertToIndexQuery(queryParams, viewer, waveDomain);
    try {
      BooleanQuery allQuery = new BooleanQuery();
      String fromParticipant = viewer.getAddress();
      Query userQuery = null;
      if (!indexConditions.isEmpty()) {
        try {
          userQuery = makeQuery(indexConditions);
        } catch (ParseException ex) {
          LOG.log(Level.SEVERE, "Invalid query: " + query, ex);
          return result;
        }
      }
      if (userQuery == null || !SearchQueryHelper.withParticipant(indexConditions, sharedDomainParticipant)) {
        TermQuery participantQuery = new TermQuery(new Term(IndexCondition.Field.PARTICIPANTS.toString(), fromParticipant));
        participantQuery.setBoost(0);
        allQuery.add(participantQuery, Occur.MUST);
      }
      if (userQuery != null) {
        userQuery.setBoost(1);
        allQuery.add(userQuery, Occur.MUST);
        for (IndexCondition condition : indexConditions) {
          if (condition.getField() == IndexCondition.Field.CONTENT) {
            IndexCondition titleCondition = new IndexCondition(IndexCondition.Field.TITLE, null,
                condition.getValue(), condition.isPhrase(), condition.isNot());
            Query titleQuery = makeQuery(titleCondition);
            titleQuery.setBoost(2);
            allQuery.add(titleQuery, titleCondition.isNot()?Occur.MUST_NOT:Occur.SHOULD);
            IndexCondition tagCondition = new IndexCondition(IndexCondition.Field.TAG, null,
                condition.getValue(), condition.isPhrase(), condition.isNot());
            Query tagQuery = makeQuery(tagCondition);
            tagQuery.setBoost(3);
            allQuery.add(tagQuery, tagCondition.isNot()?Occur.MUST_NOT:Occur.SHOULD);
          }
        }
      }
      LOG.fine("Search query " + allQuery.toString());
      List<SortField> sortFields = new LinkedList<SortField>();
      sortFields.add(SortField.FIELD_SCORE);
      sortFields.add(new SortField(IndexCondition.Field.LAST_MODIFIED.toString(), longParser, true));
      SearcherManager searcherManager = nrtManager.getSearcherManager(true);
      IndexSearcher indexSearcher = searcherManager.acquire();
      try {
        indexSearcher.setSimilarity(similarity);
        TopDocs hints = indexSearcher.search(allQuery, startAt+numResults,
            new Sort(sortFields.toArray(new SortField[sortFields.size()])));
        for (int i=startAt; i < hints.scoreDocs.length; i++) {
          try {
            ScoreDoc hint = hints.scoreDocs[i];
            Document doc = indexSearcher.doc(hint.doc);
            result.addDigest(parseDigest(doc, viewer));
          } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Get digest from index", ex);
          }
        }
      } finally {
        searcherManager.release(indexSearcher);
      }
    } catch (ParseException ex) {
      LOG.log(Level.SEVERE, "Search failed: " + query, ex);
    } catch (IOException ex) {
      LOG.log(Level.SEVERE, "Search failed: " + query, ex);
    }
    return result;
  }

  @Override
  public Digest findWave(WaveId waveId, ParticipantId viewer) {
    TermQuery query = new TermQuery(new Term(IndexCondition.Field.WAVE_ID.toString(), waveId.serialise()));
    SearcherManager searcherManager = nrtManager.getSearcherManager(true);
    IndexSearcher indexSearcher = searcherManager.acquire();
    try {
      TopDocs hints = indexSearcher.search(query, 1);
      if (hints.totalHits != 0) {
        ScoreDoc hint = hints.scoreDocs[0];
        return parseDigest(indexSearcher.doc(hint.doc), null);
      }
    } catch (IOException ex) {
      LOG.log(Level.SEVERE, "Search wave " + waveId.serialise() + " failed", ex);
    }
    return null;
  }

  private Digest parseDigest(Document doc, ParticipantId viewer) {
    String waveId = doc.get(IndexCondition.Field.WAVE_ID.toString());
    String title = doc.get(IndexCondition.Field.TITLE.toString());
    String snippet = doc.get(IndexCondition.Field.SNIPPET.toString());
    String creator = doc.get(IndexCondition.Field.CREATOR.toString());
    List<String> participants = new ArrayList<String>();
    for (String participant : doc.getValues(IndexCondition.Field.PARTICIPANTS.toString())) {
      participants.add(participant);
    }
    int blipCount = Integer.parseInt(doc.get(IndexCondition.Field.BLIP_COUNT.toString()));
    long created = Long.parseLong(doc.get(IndexCondition.Field.CREATED.toString()));
    long lastModified = Long.parseLong(doc.get(IndexCondition.Field.LAST_MODIFIED.toString()));
    WaveDigest digest = new WaveDigest(waveId, title, snippet, creator, participants, blipCount,
        created, lastModified);
    WaveDigestSupplement supplement = null;
    if (viewer != null) {
      String in = doc.get(IndexCondition.Field.IN_.toString() + viewer.getAddress());
      int unreadCount = blipCount;
      String unreadCountValue = doc.get(IndexCondition.Field.UNREAD_COUNT_.toString() + viewer.getAddress());
      // TODO remove after fix name
      if (unreadCountValue == null) {
        unreadCountValue = doc.get("readCount_" + viewer.getAddress());
      }
      if (unreadCountValue != null) {
        unreadCount = Integer.parseInt(unreadCountValue);
      }
      supplement = new WaveDigestSupplement(in, unreadCount);
    }
    return new Digest(digest, supplement);
  }

  private Query makeQuery(List<IndexCondition> conditions) throws ParseException {
    BooleanQuery query = new BooleanQuery();
    for (IndexCondition condition : conditions) {
      query.add(makeQuery(condition), condition.isNot()?Occur.MUST_NOT:Occur.MUST);
    }
    return query;
  }

  private Query makeQuery(IndexCondition condition) throws ParseException {
    if (condition.getField().isAnalyzed()) {
      if (condition.isPhrase()) {
        Matcher m = Pattern.compile("\\S+").matcher(condition.getValue());
        PhraseQuery phraseQuery = new PhraseQuery();
        while (m.find()) {
          for (String v : splitValue(m.group())) {
            phraseQuery.add(makeTerm(condition, v));
          }
        }
        return phraseQuery;
      } else {
        BooleanQuery booleanQuery = new BooleanQuery();
        for (String v : splitValue(condition.getValue())) {
          TermQuery termQuery = new TermQuery(makeTerm(condition, v));
          booleanQuery.add(termQuery, Occur.MUST);
        }
        return booleanQuery;
      }
    } else {
      return new TermQuery(makeTerm(condition, condition.getValue()));
    }
  }

  private Term makeTerm(IndexCondition condition, String value) {
    String fieldName = condition.getField().toString();
    if (condition.getFieldAddition() != null) {
      fieldName += condition.getFieldAddition();
    }
    return new Term(fieldName.toLowerCase(), value.toLowerCase());
  }

  private List<String> splitValue(String value) {
    List<String> values = Lists.newArrayList();
    StringBuilder v = new StringBuilder();
    for (Character c: value.toCharArray()) {
      if (Character.isLetter(c) || Character.isDigit(c)) {
        v.append(c);
      } else if (v.length() != 0) {
        values.add(v.toString());
        v = new StringBuilder();
      }
    }
    if (v.length() != 0) {
      values.add(v.toString());
    }
    return values;
  }
}
