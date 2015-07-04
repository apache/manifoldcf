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
package org.apache.manifoldcf.agents.output.lucene.tests;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.output.lucene.LuceneClient;
import org.apache.manifoldcf.agents.output.lucene.LuceneClientManager;
import org.apache.manifoldcf.agents.output.lucene.LuceneDocument;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.system.ManifoldCF;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.StandardSystemProperty;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class LuceneClientTest {

  private static final String sep = StandardSystemProperty.FILE_SEPARATOR.value();
  private File testDir;

  private static final String ID = LuceneClient.defaultIdField();
  private static final String CONTENT = LuceneClient.defaultContentField();

  @Before
  public void setUp() {
    String root = getClass().getResource("/").getFile();
    testDir = new File(root, "testDir");
    testDir.mkdirs();
  }

  @After
  public void tearDown() throws Exception {
    removeDirectory(testDir);
  }

  private void removeDirectory(File f) throws Exception {
    File[] files = f.listFiles();
    if (files != null) {
      int i = 0;
      while (i < files.length) {
        File subfile = files[i++];
        if (subfile.isDirectory())
          removeDirectory(subfile);
        else
          subfile.delete();
      }
    }
    f.delete();
  }

  @Test
  public void testOpenClose() throws IOException {
    String path = testDir.getAbsolutePath()+sep+"tmp"+sep+"openclose-index";
    File f = new File(path);
    assertThat(f.exists(), is(false));
    LuceneClient client = new LuceneClient(f.toPath());
    assertThat(f.exists(), is(true));
    assertThat(client.isOpen(), is(true));
    client.close();
    assertThat(client.isOpen(), is(false));
  }

  @Test
  public void testInitIndexDir() throws IOException {
    String path = testDir.getAbsolutePath()+sep+"tmp"+sep+"initindexdir-index";
    LuceneClient client = new LuceneClient(new File(path).toPath());
    List<String> indexDirList = Arrays.asList(new File(path).list());
    assertThat(indexDirList.size(), is(2));
    assertThat(indexDirList.contains("write.lock"), is(true));
    assertThat(indexDirList.contains("segments_1"), is(true));

    IndexSearcher searcher = client.newSearcher();
    assertThat(searcher.count(new MatchAllDocsQuery()), is(0));
    IndexSearcher realtimeSearcher = client.newRealtimeSearcher();
    assertThat(realtimeSearcher.count(new MatchAllDocsQuery()), is(0));
    client.close();
  }

  @Test
  public void testGetClientFromManager() throws Exception {
    String path = testDir.getAbsolutePath()+sep+"tmp"+sep+"getclientfrommager-index";

    LuceneClient client1 =
      LuceneClientManager.getClient(path, LuceneClient.defaultCharfilters(), LuceneClient.defaultTokenizers(), LuceneClient.defaultFilters(), LuceneClient.defaultAnalyzers(), LuceneClient.defaultFields(),
        LuceneClient.defaultIdField(), LuceneClient.defaultContentField());
    assertThat(client1.isOpen(), is(true));

    LuceneClient client2 =
      LuceneClientManager.getClient(path, LuceneClient.defaultCharfilters(), LuceneClient.defaultTokenizers(), LuceneClient.defaultFilters(), LuceneClient.defaultAnalyzers(), LuceneClient.defaultFields(),
        "id", "content");
    assertThat(client2.isOpen(), is(true));

    assertThat(client1, is(client2));

    LuceneClient client3;
    try {
      client3 =
        LuceneClientManager.getClient(path, LuceneClient.defaultCharfilters(), LuceneClient.defaultTokenizers(), LuceneClient.defaultFilters(), LuceneClient.defaultAnalyzers(), LuceneClient.defaultFields(),
          "dummy_id", "dummy_content");
      fail("Should not get here");
    } catch (Exception e) {
      assert e instanceof IllegalStateException;
    }

    client1.close();
    assertThat(client1.isOpen(), is(false));
    assertThat(client2.isOpen(), is(false));

    client3 =
      LuceneClientManager.getClient(path, LuceneClient.defaultCharfilters(), LuceneClient.defaultTokenizers(), LuceneClient.defaultFilters(), LuceneClient.defaultAnalyzers(), LuceneClient.defaultFields(),
        "dummy_id", "dummy_content");
    assertThat(client3.isOpen(), is(true));

    assertThat(client3, not(client1));
    assertThat(client3, not(client2));
  }

  @Test
  public void testAddOrReplace() throws IOException {
    String path = testDir.getAbsolutePath()+sep+"tmp"+sep+"addorreplace-index";
    try (LuceneClient client = new LuceneClient(new File(path).toPath())) {
      // add
      LuceneDocument doc1 = new LuceneDocument()
        .addStringField(ID, "/repo/001", true)
        .addTextField(CONTENT, "green", true);
      client.addOrReplace("/repo/001", doc1);

      LuceneDocument doc2 = new LuceneDocument()
        .addStringField(ID, "/repo/002", true)
        .addTextField(CONTENT, "yellow", true);
      client.addOrReplace("/repo/002", doc2);

     client.optimize();
     IndexSearcher searcher = client.newSearcher();
     assertThat(searcher.count(new TermQuery(new Term(CONTENT, "green"))), is(1));
     assertThat(searcher.count(new TermQuery(new Term(CONTENT, "yellow"))), is(1));

     // update
     LuceneDocument updateDoc = new LuceneDocument()
       .addStringField(ID, "/repo/001", true)
       .addTextField(CONTENT, "yellow", true);
     client.addOrReplace("/repo/001", updateDoc);

     client.optimize();
     searcher = client.newSearcher();
     assertThat(searcher.count(new TermQuery(new Term(CONTENT, "green"))), is(0));
     assertThat(searcher.count(new TermQuery(new Term(CONTENT, "yellow"))), is(2));

     // add
     LuceneDocument addDoc = new LuceneDocument()
       .addStringField(ID, "/repo/100", true)
       .addTextField(CONTENT, "red", true);
     client.addOrReplace("/repo/100", addDoc);

     client.optimize();
     searcher = client.newSearcher();
     assertThat(searcher.count(new TermQuery(new Term(CONTENT, "green"))), is(0));
     assertThat(searcher.count(new TermQuery(new Term(CONTENT, "yellow"))), is(2));
     assertThat(searcher.count(new TermQuery(new Term(CONTENT, "red"))), is(1));
    }
  }

  @Test
  public void testRemove() throws IOException {
    String path = testDir.getAbsolutePath()+sep+"tmp"+sep+"remove-index";
    try (LuceneClient client = new LuceneClient(new File(path).toPath())) {

      LuceneDocument doc1 = new LuceneDocument()
        .addStringField(ID, "/repo/001", true)
        .addTextField(CONTENT, "Apache", true);
      client.addOrReplace("/repo/001", doc1);

      LuceneDocument doc2 = new LuceneDocument()
        .addStringField(ID, "/repo/002", true)
        .addTextField(CONTENT, "Apache", true);
      client.addOrReplace("/repo/002", doc2);

      client.optimize();
      IndexSearcher searcher = client.newSearcher();
      assertThat(searcher.count(new TermQuery(new Term(CONTENT, "apache"))), is(2));

      client.remove("/repo/001");

      client.optimize();
      searcher = client.newSearcher();
      assertThat(searcher.count(new TermQuery(new Term(CONTENT, "apache"))), is(1));
    }
  }

  @Test
  public void testDefaultSettings() throws IOException, InterruptedException {
    String path = testDir.getAbsolutePath()+sep+"tmp"+sep+"defaultsettings-index";
    try (LuceneClient client = new LuceneClient(new File(path).toPath())) {

      String content1 = "Apache ManifoldCF, Apache Lucene";
      LuceneDocument doc1 = new LuceneDocument()
        .addStringField(ID, "/repo/001", true)
        .addTextField(CONTENT, content1, true)
        .addTextField("content_ws", content1, false)
        .addTextField("content_ngram", content1, false);
      client.addOrReplace("/repo/001", doc1);

      LuceneDocument doc2 = new LuceneDocument()
        .addStringField(ID, "/repo/002", true)
        .addTextField(CONTENT, "This is stop word. apache software.", true);
      client.addOrReplace("/repo/002", doc2);

      LuceneDocument doc3 = new LuceneDocument()
        .addStringField(ID, "/repo/003", true)
        .addTextField(CONTENT, "Apache Solr", true);
      client.addOrReplace("/repo/003", doc3);

      client.optimize();
      IndexSearcher searcher = client.newSearcher();
      assertThat(searcher.count(client.newQuery("*:*")), is(3));
      assertThat(searcher.count(client.newQuery("id:\\/repo\\/001")), is(1));
      assertThat(searcher.count(client.newQuery("content:lu")), is(0));
      assertThat(searcher.count(client.newQuery("content:lucene")), is(1));
      assertThat(searcher.count(client.newQuery("content_ws:lucene")), is(0));
      assertThat(searcher.count(client.newQuery("content_ws:Lucene")), is(1));
      assertThat(searcher.count(client.newQuery("content_ngram:l")), is(0));
      assertThat(searcher.count(client.newQuery("content_ngram:lu")), is(1));
      assertThat(searcher.count(client.newQuery("content:this")), is(0));
      assertThat(searcher.count(client.newQuery("content:is")), is(0));
      assertThat(searcher.count(client.newQuery("content:stop")), is(1));

      TopDocs hits = searcher.search(client.newQuery("id:\\/repo\\/001"), 1);
      int docID = hits.scoreDocs[0].doc;
      Terms terms = client.reader().getTermVector(docID, CONTENT);
      TermsEnum te = terms.iterator();
      BytesRef br;
      while ((br = te.next()) != null) {
        if (te.seekExact(new BytesRef("apache"))) {
          assertThat(br.utf8ToString(), is("apache"));
          assertThat(te.totalTermFreq(), is(2L));
          break;
        }
      }
      assertThat(client.reader().docFreq(new Term(CONTENT, br)), is(3));

      hits = searcher.search(client.newQuery(ID+":\\/repo\\/003"), 1);
      Document storedDocument = searcher.doc(hits.scoreDocs[0].doc);
      assertThat(storedDocument.getField(CONTENT).stringValue(), is("Apache Solr"));

      String rt = "realtime";
      LuceneDocument doc4 = new LuceneDocument()
        .addStringField(ID, rt, true);
      client.addOrReplace(rt, doc4);
      ManifoldCF.sleep(2000L);
      assertThat(searcher.count(client.newQuery(ID+":"+rt)), is(0));
      assertThat(client.newSearcher().count(client.newQuery(ID+":"+rt)), is(0));
      assertThat(client.newRealtimeSearcher().count(client.newQuery(ID+":"+rt)), is(1));
    }
  }

  @Test
  public void testIndexRepositoryDocument() throws IOException, ManifoldCFException {
    String documentURI = "file://dummy/rd";
    RepositoryDocument rd = new RepositoryDocument();
    rd.addField("cat", "foo");
    rd.addField("author", new String[]{ "abe", "obama" });
    rd.addField(CONTENT, "Classification, categorization, and tagging using Lucene");

    String path = testDir.getAbsolutePath()+sep+"tmp"+sep+"rd-index";
    try (LuceneClient client = new LuceneClient(new File(path).toPath())) {

      Map<String,Map<String,Object>> fieldsInfo = client.fieldsInfo();

      LuceneDocument doc = new LuceneDocument();
      doc = LuceneDocument.addField(doc, client.idField(), documentURI, fieldsInfo);

      Iterator<String> it = rd.getFields();
      while (it.hasNext()) {
        String rdField = it.next();
        if (fieldsInfo.containsKey(rdField)) {
          String[] values = rd.getFieldAsStrings(rdField);
          for (String value : values) {
            doc = LuceneDocument.addField(doc, rdField, value, fieldsInfo);
          }
        }
      }

      client.addOrReplace(documentURI, doc);

      client.optimize();
      IndexSearcher searcher = client.newSearcher();
      assertThat(searcher.count(client.newQuery("id:file\\:\\/\\/dummy\\/rd")), is(1));
      assertThat(searcher.count(client.newQuery("cat:foo")), is(1));
      assertThat(searcher.count(client.newQuery("author:abe")), is(1));
      assertThat(searcher.count(client.newQuery("author:obama")), is(1));
      assertThat(searcher.count(client.newQuery("content:categorization")), is(1));
      assertThat(searcher.count(client.newQuery("content:tagging")), is(1));
      assertThat(searcher.count(client.newQuery("content:(classification AND lucene)")), is(1));
    }
  }

}
