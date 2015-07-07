/**
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.manifoldcf.agents.output.lucene;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NRTCachingDirectory;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class LuceneClient implements Closeable {

  private final Path path;
  private final Map<String,Map<String,Object>> charfiltersInfo;
  private final Map<String,Map<String,Object>> tokenizersInfo;
  private final Map<String,Map<String,Object>> filtersInfo;
  private final Map<String,Map<String,Object>> analyzersInfo;
  private final Map<String,Map<String,Object>> fieldsInfo;
  private final String idField;
  private final String contentField;
  private final Long maximumDocumentLength;

  private final String versionString;

  private final IndexWriter writer;
  private final CommitService commitService;
  private static final long COMMIT_DELAY_MS = 15000L;

  private DirectoryReader realtimeReader;
  private final CommitService refreshService;
  private static final long REFRESH_DELAY_MS = 1000L;

  private final StandardQueryParser queryParser;

  private static final Gson GSON = new Gson();
  private static final Type TYPE = new TypeToken<Map<String,Map<String,Object>>>(){}.getType();

  /** analyzer attribute */
  private static final String ATTR_CHARFILTER = "charfilter";
  private static final String ATTR_TOKENIZER = "tokenizer";
  private static final String ATTR_FILTER = "filter";

  private static final String ATTR_TYPE = "type";
  private static final String ATTR_PARAMS = "params";

  /** field attribute */
  public static final String ATTR_FIELDTYPE = "type";
  public static final String ATTR_STORE = "store";
  public static final String ATTR_INDEX_ANALYZER = "index_analyzer";
  public static final String ATTR_QUERY_ANALYZER = "query_analyzer";
  public static final String ATTR_COPY_TO = "copy_to";

  public static final String FIELDTYPE_STRING = "string";
  public static final String FIELDTYPE_TEXT = "text";

  public LuceneClient(Path path) throws IOException {
    this(path,
         LuceneClient.defaultCharfilters(), LuceneClient.defaultTokenizers(), LuceneClient.defaultFilters(),
         LuceneClient.defaultAnalyzers(), LuceneClient.defaultFields(),
         LuceneClient.defaultIdField(), LuceneClient.defaultContentField(),
         LuceneClient.defaultMaximumDocumentLength());
  }

  public LuceneClient(Path path,
                      String charfilters, String tokenizers, String filters,
                      String analyzers, String fields,
                      String idField, String contentField,
                      Long maximumDocumentLength) throws IOException {
    this.path = Preconditions.checkNotNull(path);
    this.charfiltersInfo = parseAsMap(Preconditions.checkNotNull(charfilters));
    this.tokenizersInfo = parseAsMap(Preconditions.checkNotNull(tokenizers));
    this.filtersInfo = parseAsMap(Preconditions.checkNotNull(filters));
    this.analyzersInfo = parseAsMap(Preconditions.checkNotNull(analyzers));
    this.fieldsInfo = parseAsMap(Preconditions.checkNotNull(fields));
    this.idField = Preconditions.checkNotNull(idField);
    this.contentField = Preconditions.checkNotNull(contentField);
    this.maximumDocumentLength = Preconditions.checkNotNull(maximumDocumentLength);

    this.versionString = createVersionString(path, charfiltersInfo, tokenizersInfo, filtersInfo, analyzersInfo, fieldsInfo, idField, contentField, maximumDocumentLength);

    Map<String,Analyzer> analyzersMap = createAnalyzersMap();
    Map<String,Analyzer> fieldIndexAnalyzers = createFieldAnalyzers(analyzersMap, ATTR_INDEX_ANALYZER);
    Map<String,Analyzer> fieldQueryAnalyzers = createFieldAnalyzers(analyzersMap, ATTR_QUERY_ANALYZER);

    Analyzer indexAnalyzer = new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), fieldIndexAnalyzers);
    Analyzer queryAnalyzer = new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), fieldQueryAnalyzers);

    IndexWriterConfig config = new IndexWriterConfig(indexAnalyzer)
      .setOpenMode(OpenMode.CREATE_OR_APPEND)
      .setUseCompoundFile(false)
      .setCommitOnClose(IndexWriterConfig.DEFAULT_COMMIT_ON_CLOSE)
      .setRAMBufferSizeMB(IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB * 6);

    Directory fsDir = FSDirectory.open(path);
    NRTCachingDirectory cachedDir = new NRTCachingDirectory(fsDir, 4, 48);

    this.writer = new IndexWriter(cachedDir, config);

    initIndex();

    this.commitService = new CommitService(this, false, COMMIT_DELAY_MS);
    commitService.startAsync().awaitRunning();

    this.refreshService = new CommitService(this, true, REFRESH_DELAY_MS);
    refreshService.startAsync().awaitRunning();

    this.queryParser = new StandardQueryParser(queryAnalyzer);
  }

  public static Map<String,Map<String,Object>> parseAsMap(String json) {
    return GSON.fromJson(json, TYPE);
  }

  private Map<String,Analyzer> createAnalyzersMap() throws IOException {
    Map<String,Analyzer> analyzersMap = Maps.newHashMap();
    for (Map.Entry<String,Map<String,Object>> analyzerInfo : analyzersInfo.entrySet()) {
      String name = analyzerInfo.getKey();

      Map<String,Object> info = analyzerInfo.getValue();
      @SuppressWarnings("unchecked")
      final List<String> charfilterList = (List<String>)Objects.firstNonNull(info.get(ATTR_CHARFILTER), new ArrayList<String>());
      final String tokenizer = info.get(ATTR_TOKENIZER).toString();
      @SuppressWarnings("unchecked")
      final List<String> filterList = (List<String>)Objects.firstNonNull(info.get(ATTR_FILTER), new ArrayList<String>());

      CustomAnalyzer.Builder builder = CustomAnalyzer.builder();

      Map<String,Map<String,Object>> charfilterInfo = Maps.filterKeys(charfiltersInfo, new Predicate<String>() {
        @Override public boolean apply(String key) {
          return charfilterList.contains(key);
        }
      });
      for (Map.Entry<String,Map<String,Object>> e : charfilterInfo.entrySet()) {
        String type = e.getValue().get(ATTR_TYPE).toString();
        @SuppressWarnings("unchecked")
        Map<String,String> params = (Map<String,String>)Objects.firstNonNull(e.getValue().get(ATTR_PARAMS),new HashMap<String,String>());
        builder = builder.addCharFilter(type, newMap(params));
      }

      Map<String,Map<String,Object>> tokenizerInfo = Maps.filterKeys(tokenizersInfo, new Predicate<String>() {
        @Override public boolean apply(String key) {
          return tokenizer.equals(key);
        }
      });
      assert tokenizerInfo.size() == 1;
      for (Map.Entry<String,Map<String,Object>> e : tokenizerInfo.entrySet()) {
        String type = e.getValue().get(ATTR_TYPE).toString();
        @SuppressWarnings("unchecked")
        Map<String,String> params = (Map<String,String>)Objects.firstNonNull(e.getValue().get(ATTR_PARAMS),new HashMap<String,String>());
        builder = builder.withTokenizer(type, newMap(params));
      }

      Map<String,Map<String,Object>> filterInfo = Maps.filterKeys(filtersInfo, new Predicate<String>() {
        @Override public boolean apply(String key) {
          return filterList.contains(key);
        }
      });
      for (Map.Entry<String,Map<String,Object>> e : filterInfo.entrySet()) {
        String type = e.getValue().get(ATTR_TYPE).toString();
        @SuppressWarnings("unchecked")
        Map<String,String> params = (Map<String,String>)Objects.firstNonNull(e.getValue().get(ATTR_PARAMS),new HashMap<String,String>());
        builder = builder.addTokenFilter(type, newMap(params));
      }

      builder = builder.withPositionIncrementGap(100);

      analyzersMap.put(name, builder.build());
    }
    return analyzersMap;
  }

  private Map<String,Analyzer> createFieldAnalyzers(Map<String,Analyzer> analyzersMap, String target) {
    Map<String,Analyzer> fieldAnalyzers = Maps.newHashMap();
    for (Map.Entry<String,Map<String,Object>> e : fieldsInfo.entrySet()) {
      if (e.getValue().get(ATTR_FIELDTYPE).toString().equals(FIELDTYPE_TEXT)) {
        String field = e.getKey();
        String analyzer = e.getValue().get(target).toString();
        fieldAnalyzers.put(field, analyzersMap.get(analyzer));
      }
    }
    return fieldAnalyzers;
  }

  private Map<String,String> newMap(Map<String,String> map) {
    Map<String,String> copy = Maps.newHashMap();
    for (Map.Entry<String,String> e : map.entrySet()) {
      copy.put(e.getKey(), e.getValue());
    }
    return copy;
  }

  private void initIndex() throws IOException {
    File dirFile = path.toFile();
    boolean indexExists = dirFile.canRead() && dirFile.list().length > 1;
    if (!indexExists) writer.commit();
  }

  public Map<String,Map<String,Object>> fieldsInfo() {
    return fieldsInfo;
  }

  public String idField() {
    return idField;
  }

  public String contentField() {
    return contentField;
  }

  public Long maximumDocumentLength() {
    return maximumDocumentLength;
  }

  public String versionString() {
    return versionString;
  }

  public static String createVersionString(
    Path path,
    Map<String,Map<String,Object>> charfiltersInfo,
    Map<String,Map<String,Object>> tokenizersInfo,
    Map<String,Map<String,Object>> filtersInfo,
    Map<String,Map<String,Object>> analyzersInfo,
    Map<String,Map<String,Object>> fieldsInfo,
    String idField,String contentField,
    Long maximumDocumentLength) {
    return LuceneConfig.PARAM_PATH + ":" + path.toString() + "+"
         + LuceneConfig.PARAM_CHARFILTERS + ":" + Joiner.on(",").withKeyValueSeparator("=").join(charfiltersInfo) + "+"
         + LuceneConfig.PARAM_TOKENIZERS + ":" + Joiner.on(",").withKeyValueSeparator("=").join(tokenizersInfo) + "+"
         + LuceneConfig.PARAM_FILTERS + ":" + Joiner.on(",").withKeyValueSeparator("=").join(filtersInfo) + "+"
         + LuceneConfig.PARAM_ANALYZERS + ":" + Joiner.on(",").withKeyValueSeparator("=").join(analyzersInfo) + "+"
         + LuceneConfig.PARAM_FIELDS + ":" + Joiner.on(",").withKeyValueSeparator("=").join(fieldsInfo) + "+"
         + LuceneConfig.PARAM_IDFIELD + ":" + idField + "+"
         + LuceneConfig.PARAM_CONTENTFIELD + ":" + contentField + "+"
         + LuceneConfig.PARAM_MAXIMUMDOCUMENTLENGTH + ":" + maximumDocumentLength.toString();
  }

  public void refresh() throws IOException {
    if (realtimeReader == null) {
      realtimeReader = DirectoryReader.open(writer.getDirectory());
    }
    DirectoryReader newReader = DirectoryReader.openIfChanged(realtimeReader, writer, true);
    if (newReader != null) {
      realtimeReader.close();
      realtimeReader = newReader;
    }
  }

  public void commit() throws IOException {
    if (writer.hasUncommittedChanges()) {
      writer.commit();
    }
  }

  public void optimize() throws IOException {
    writer.forceMerge(1);
    commit();
  }

  @Override
  public void close() throws IOException {
    refreshService.stopAsync().awaitTerminated();
    commitService.stopAsync().awaitTerminated();
    writer.close();
    if (realtimeReader != null) realtimeReader.close();
    writer.getDirectory().close();
  }

  public boolean isOpen() {
    return writer.isOpen();
  }

  public void addOrReplace(String id, LuceneDocument document) throws IOException {
    Term uniqueKey = new Term(idField, id);
    writer.updateDocument(uniqueKey, document.toDocument());
  }

  public void remove(String id) throws IOException {
    Term uniqueKey = new Term(idField, id);
    writer.deleteDocuments(uniqueKey);
  }

  private class CommitService extends AbstractScheduledService {
    private final LuceneClient client;
    private final boolean refresh;
    private final long delay;

    public CommitService(LuceneClient client, boolean refresh, long ms) {
      this.client = client;
      this.refresh = refresh;
      this.delay = ms;
    }

    @Override
    protected void runOneIteration() throws Exception {
      if (client.isOpen()) {
        if (!refresh) {
          client.commit();
        } else {
          client.refresh();
        }
      }
    }

    @Override
    protected Scheduler scheduler() {
     return Scheduler.newFixedDelaySchedule(delay, delay, TimeUnit.MILLISECONDS);
    }
  }

  public LeafReader reader() throws IOException {
    return SlowCompositeReaderWrapper.wrap(DirectoryReader.open(writer.getDirectory()));
  }

  public IndexSearcher newSearcher() throws IOException {
    return new IndexSearcher(DirectoryReader.open(writer.getDirectory()));
  }

  public IndexSearcher newRealtimeSearcher() throws IOException {
    if (realtimeReader == null) refresh();
    return new IndexSearcher(realtimeReader);
  }

  public Query newQuery(String queryString) {
    String qstr = Objects.firstNonNull(queryString, "*:*");
    Query query;
    try {
      query = queryParser.parse(qstr, contentField);
    } catch (QueryNodeException e) {
      query = new MatchNoDocsQuery();
    }
    return query;
  }

  public static String defaultPath() {
    String sep = StandardSystemProperty.FILE_SEPARATOR.value();
    String userDir = StandardSystemProperty.USER_DIR.value();
    return userDir+sep+"lucene"+sep+"collection1"+sep+"data"+sep+"index";
  }

  public static String defaultCharfilters() {
    String charfilters =
        "{" + "\n"
        + "  \"my_htmlstrip\":{\""+ATTR_TYPE+"\":\"htmlstrip\"}" + "\n"
      + "}";
    return charfilters;
  }

  public static String defaultTokenizers() {
    String tokenizers =
        "{" + "\n"
          + "  \"my_standard\":{\""+ATTR_TYPE+"\":\"standard\"}," + "\n"
          + "  \"my_whitespace\":{\""+ATTR_TYPE+"\":\"whitespace\"}," + "\n"
          + "  \"my_ngram\":{\""+ATTR_TYPE+"\":\"ngram\",\""+ATTR_PARAMS+"\":{\"minGramSize\":\"2\", \"maxGramSize\":\"2\"}}" + "\n"
      + "}";
    return tokenizers;
  }

  public static String defaultFilters() {
    String filters =
        "{" + "\n"
          + "  \"my_stop\":{\""+ATTR_TYPE+"\":\"stop\",\""+ATTR_PARAMS+"\":{\"ignoreCase\":\"true\"}}," + "\n"
          + "  \"my_lowercase\":{\""+ATTR_TYPE+"\":\"lowercase\"}" + "\n"
      + "}";
    return filters;
  }

  public static String defaultAnalyzers() {
    String analyzers =
        "{" + "\n"
          + "  \"text_general\":{\""+ATTR_CHARFILTER+"\":[\"my_htmlstrip\"], \""+ATTR_TOKENIZER+"\":\"my_standard\",\""+ATTR_FILTER+"\":[\"my_stop\",\"my_lowercase\"]},"+ "\n"
          + "  \"text_ws\":{\""+ATTR_TOKENIZER+"\":\"my_whitespace\"},"+ "\n"
          + "  \"text_ngram\":{\""+ATTR_TOKENIZER+"\":\"my_ngram\",\""+ATTR_FILTER+"\":[\"my_lowercase\"]}"+ "\n"
      + "}";
    return analyzers;
  }

  public static String defaultFields() {
    String fields =
        "{" + "\n"
          + "  \"id\":{\""+ATTR_FIELDTYPE+"\":\""+FIELDTYPE_STRING+"\", \""+ATTR_STORE+"\":true},"+ "\n"
          + "  \"cat\":{\""+ATTR_FIELDTYPE+"\":\""+FIELDTYPE_STRING+"\", \""+ATTR_STORE+"\":true},"+ "\n"
          + "  \"author\":{\""+ATTR_FIELDTYPE+"\":\""+FIELDTYPE_STRING+"\", \""+ATTR_STORE+"\":true},"+ "\n"
          + "  \"content\":{\""+ATTR_FIELDTYPE+"\":\""+FIELDTYPE_TEXT+"\", \""+ATTR_STORE+"\":true,\""+ATTR_INDEX_ANALYZER+"\":\"text_general\",\""+ATTR_QUERY_ANALYZER+"\":\"text_general\",\""+ATTR_COPY_TO+"\":[\"content_ws\", \"content_ngram\"]}," + "\n"
          + "  \"content_ws\":{\""+ATTR_FIELDTYPE+"\":\""+FIELDTYPE_TEXT+"\", \""+ATTR_STORE+"\":false,\""+ATTR_INDEX_ANALYZER+"\":\"text_ws\",\""+ATTR_QUERY_ANALYZER+"\":\"text_ws\"}," + "\n"
          + "  \"content_ngram\":{\""+ATTR_FIELDTYPE+"\":\""+FIELDTYPE_TEXT+"\", \""+ATTR_STORE+"\":false,\""+ATTR_INDEX_ANALYZER+"\":\"text_ngram\",\""+ATTR_QUERY_ANALYZER+"\":\"text_ngram\"}" + "\n"
      + "}";
    return fields;
  }

  public static String defaultIdField() {
    return "id";
  }

  public static String defaultContentField() {
    return "content";
  }

  public static Long defaultMaximumDocumentLength() {
    return new Long(700000000L);
  }

}
