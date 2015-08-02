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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.BytesRef;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.output.lucene.LuceneClient;
import org.apache.manifoldcf.agents.output.lucene.LuceneClientManager;
import org.apache.manifoldcf.agents.output.lucene.LuceneDocument;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.system.ManifoldCF;

import com.google.common.base.StandardSystemProperty;
import com.google.common.io.ByteSource;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class LuceneClientTest {

  private static final String sep = StandardSystemProperty.FILE_SEPARATOR.value();
  private File testDir;

  static {
    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
  }
  private static MiniDFSCluster hdfsCluster;
  private static String hdfsPath;

  private static final String ID = LuceneClient.defaultIdField();
  private static final String CONTENT = LuceneClient.defaultContentField();

  @BeforeClass
  public static void beforeClass() throws IOException {
    Configuration conf = new Configuration();
    conf.setBoolean("fs.hdfs.impl.disable.cache", true);
    MiniDFSCluster.Builder builder = new MiniDFSCluster.Builder(conf);
    hdfsCluster = builder.build();
    hdfsPath = "hdfs://localhost:" + hdfsCluster.getNameNodePort() + "/HdfsTest";
  }

  @AfterClass
  public static void afterClass() {
    hdfsCluster.shutdown();
  }

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
    LuceneClient client = new LuceneClient(path);
    assertThat(f.exists(), is(true));
    assertThat(client.isOpen(), is(true));
    client.close();
    assertThat(client.isOpen(), is(false));
  }

  @Test
  public void testInitIndexDir() throws IOException {
    String path = testDir.getAbsolutePath()+sep+"tmp"+sep+"initindexdir-index";
    LuceneClient client = new LuceneClient(path);
    List<String> indexDirList = Arrays.asList(new File(path).list());
    assertThat(indexDirList.size(), is(2));
    assertThat(indexDirList.contains("write.lock"), is(true));
    assertThat(indexDirList.contains("segments_1"), is(true));

    IndexSearcher searcher = client.newSearcher();
    assertThat(searcher.count(new MatchAllDocsQuery()), is(0));
    searcher.getIndexReader().close();

    assertThat(client.newRealtimeSearcher().count(new MatchAllDocsQuery()), is(0));
    client.close();
  }

  @Test
  public void testGetClientFromManager() throws Exception {
    String path = testDir.getAbsolutePath()+sep+"tmp"+sep+"getclientfrommanager-index";

    LuceneClient client1 =
      LuceneClientManager.getClient(path, ManifoldCF.getProcessID(), LuceneClient.defaultCharfilters(), LuceneClient.defaultTokenizers(), LuceneClient.defaultFilters(), LuceneClient.defaultAnalyzers(), LuceneClient.defaultFields(),
        LuceneClient.defaultIdField(), LuceneClient.defaultContentField(), LuceneClient.defaultMaxDocumentLength());
    assertThat(client1.isOpen(), is(true));

    LuceneClient client2 =
      LuceneClientManager.getClient(path, ManifoldCF.getProcessID(), LuceneClient.defaultCharfilters(), LuceneClient.defaultTokenizers(), LuceneClient.defaultFilters(), LuceneClient.defaultAnalyzers(), LuceneClient.defaultFields(),
        "id", "content", LuceneClient.defaultMaxDocumentLength());
    assertThat(client2.isOpen(), is(true));

    assertThat(client1, is(client2));

    LuceneClient client3;
    try {
      client3 =
        LuceneClientManager.getClient(path, ManifoldCF.getProcessID(), LuceneClient.defaultCharfilters(), LuceneClient.defaultTokenizers(), LuceneClient.defaultFilters(), LuceneClient.defaultAnalyzers(), LuceneClient.defaultFields(),
          "dummy_id", "dummy_content", LuceneClient.defaultMaxDocumentLength());
      fail("Should not get here");
    } catch (Exception e) {
      assert e instanceof IllegalStateException;
    }

    client1.close();
    assertThat(client1.isOpen(), is(false));
    assertThat(client2.isOpen(), is(false));
    assertThat(client1, is(client2));

    client3 =
      LuceneClientManager.getClient(path, ManifoldCF.getProcessID(), LuceneClient.defaultCharfilters(), LuceneClient.defaultTokenizers(), LuceneClient.defaultFilters(), LuceneClient.defaultAnalyzers(), LuceneClient.defaultFields(),
        "dummy_id", "dummy_content", LuceneClient.defaultMaxDocumentLength());
    assertThat(client3.isOpen(), is(true));

    assertThat(client3, not(client1));
    assertThat(client3, not(client2));
  }

  @Test
  public void testGetClientFromManagerHdfs() throws Exception {
    String path = hdfsPath+"/getclientfrommanager";
    String processID_A = "A";
    String processID_B = "B";

    LuceneClient client1 =
      LuceneClientManager.getClient(path, processID_A, LuceneClient.defaultCharfilters(), LuceneClient.defaultTokenizers(), LuceneClient.defaultFilters(), LuceneClient.defaultAnalyzers(), LuceneClient.defaultFields(),
        LuceneClient.defaultIdField(), LuceneClient.defaultContentField(), LuceneClient.defaultMaxDocumentLength());
    assertThat(client1.isOpen(), is(true));

    LuceneClient client2 =
      LuceneClientManager.getClient(path, processID_A, LuceneClient.defaultCharfilters(), LuceneClient.defaultTokenizers(), LuceneClient.defaultFilters(), LuceneClient.defaultAnalyzers(), LuceneClient.defaultFields(),
        "id", "content", LuceneClient.defaultMaxDocumentLength());
    assertThat(client2.isOpen(), is(true));

    assertThat(client1, is(client2));

    LuceneClient clientB =
      LuceneClientManager.getClient(path, processID_B, LuceneClient.defaultCharfilters(), LuceneClient.defaultTokenizers(), LuceneClient.defaultFilters(), LuceneClient.defaultAnalyzers(), LuceneClient.defaultFields(),
      LuceneClient.defaultIdField(), LuceneClient.defaultContentField(), LuceneClient.defaultMaxDocumentLength());
    assertThat(clientB.isOpen(), is(true));

    assertThat(clientB, not(client2));

    LuceneClient client3;
    try {
      client3 =
        LuceneClientManager.getClient(path, processID_A, LuceneClient.defaultCharfilters(), LuceneClient.defaultTokenizers(), LuceneClient.defaultFilters(), LuceneClient.defaultAnalyzers(), LuceneClient.defaultFields(),
          "dummy_id", "dummy_content", LuceneClient.defaultMaxDocumentLength());
      fail("Should not get here");
    } catch (Exception e) {
      assert e instanceof IllegalStateException;
    }

    client1.close();
    assertThat(client1.isOpen(), is(false));
    assertThat(client2.isOpen(), is(false));
    assertThat(client1, is(client2));

    client3 =
      LuceneClientManager.getClient(path, processID_A, LuceneClient.defaultCharfilters(), LuceneClient.defaultTokenizers(), LuceneClient.defaultFilters(), LuceneClient.defaultAnalyzers(), LuceneClient.defaultFields(),
        "dummy_id", "dummy_content", LuceneClient.defaultMaxDocumentLength());
    assertThat(client3.isOpen(), is(true));

    assertThat(client3, not(client1));
    assertThat(client3, not(client2));
  }

  @Test
  public void testAddOrReplace() throws IOException {
    String path = testDir.getAbsolutePath()+sep+"tmp"+sep+"addorreplace-index";
    try (LuceneClient client = new LuceneClient(path)) {
      // add
      LuceneDocument doc1 = new LuceneDocument();
      doc1 = LuceneDocument.addField(doc1, ID, "/repo/001", client.fieldsInfo());
      doc1 = LuceneDocument.addField(doc1, CONTENT, ByteSource.wrap("green".getBytes(StandardCharsets.UTF_8)).openBufferedStream(), client.fieldsInfo());
      client.addOrReplace("/repo/001", doc1);

      LuceneDocument doc2 = new LuceneDocument();
      doc2 = LuceneDocument.addField(doc2, ID, "/repo/002", client.fieldsInfo());
      doc2 = LuceneDocument.addField(doc2, CONTENT, ByteSource.wrap("yellow".getBytes(StandardCharsets.UTF_8)).openBufferedStream(), client.fieldsInfo());
      client.addOrReplace("/repo/002", doc2);

      client.optimize();
      IndexSearcher searcher = client.newSearcher();
      assertThat(searcher.count(new TermQuery(new Term(CONTENT, "green"))), is(1));
      assertThat(searcher.count(new TermQuery(new Term(CONTENT, "yellow"))), is(1));
      searcher.getIndexReader().close();

      // update
      LuceneDocument updateDoc = new LuceneDocument();
      updateDoc = LuceneDocument.addField(updateDoc, ID, "/repo/001", client.fieldsInfo());
      updateDoc = LuceneDocument.addField(updateDoc, CONTENT, ByteSource.wrap("yellow".getBytes(StandardCharsets.UTF_8)).openBufferedStream(), client.fieldsInfo());
      client.addOrReplace("/repo/001", updateDoc);

      client.optimize();
      searcher = client.newSearcher();
      assertThat(searcher.count(new TermQuery(new Term(CONTENT, "green"))), is(0));
      assertThat(searcher.count(new TermQuery(new Term(CONTENT, "yellow"))), is(2));
      searcher.getIndexReader().close();

      // add
      LuceneDocument addDoc = new LuceneDocument();
      addDoc = LuceneDocument.addField(addDoc, ID, "/repo/100", client.fieldsInfo());
      addDoc = LuceneDocument.addField(addDoc, CONTENT, ByteSource.wrap("red".getBytes(StandardCharsets.UTF_8)).openBufferedStream(), client.fieldsInfo());
      client.addOrReplace("/repo/100", addDoc);

      client.optimize();
      searcher = client.newSearcher();
      assertThat(searcher.count(new TermQuery(new Term(CONTENT, "green"))), is(0));
      assertThat(searcher.count(new TermQuery(new Term(CONTENT, "yellow"))), is(2));
      assertThat(searcher.count(new TermQuery(new Term(CONTENT, "red"))), is(1));
      searcher.getIndexReader().close();
    }
  }

  @Test
  public void testRemove() throws IOException {
    String path = testDir.getAbsolutePath()+sep+"tmp"+sep+"remove-index";
    try (LuceneClient client = new LuceneClient(path)) {

      LuceneDocument doc1 = new LuceneDocument();
      doc1 = LuceneDocument.addField(doc1, ID, "/repo/001", client.fieldsInfo());
      doc1 = LuceneDocument.addField(doc1, CONTENT, ByteSource.wrap("Apache".getBytes(StandardCharsets.UTF_8)).openBufferedStream(), client.fieldsInfo());
      client.addOrReplace("/repo/001", doc1);

      LuceneDocument doc2 = new LuceneDocument();
      doc2 = LuceneDocument.addField(doc2, ID, "/repo/002", client.fieldsInfo());
      doc2 = LuceneDocument.addField(doc2, CONTENT, ByteSource.wrap("Apache".getBytes(StandardCharsets.UTF_8)).openBufferedStream(), client.fieldsInfo());
      client.addOrReplace("/repo/002", doc2);

      client.optimize();
      IndexSearcher searcher = client.newSearcher();
      assertThat(searcher.count(new TermQuery(new Term(CONTENT, "apache"))), is(2));
      searcher.getIndexReader().close();

      client.remove("/repo/001");

      client.optimize();
      searcher = client.newSearcher();
      assertThat(searcher.count(new TermQuery(new Term(CONTENT, "apache"))), is(1));
      searcher.getIndexReader().close();
    }
  }

  @Test
  public void testDefaultSettings() throws IOException, InterruptedException {
    String path = testDir.getAbsolutePath()+sep+"tmp"+sep+"defaultsettings-index";
    try (LuceneClient client = new LuceneClient(path)) {

      String content1 = "Apache ManifoldCF, Apache Lucene";
      LuceneDocument doc1 = new LuceneDocument();
      doc1 = LuceneDocument.addField(doc1, ID, "/repo/001", client.fieldsInfo());
      doc1 = LuceneDocument.addField(doc1, CONTENT, ByteSource.wrap(content1.getBytes(StandardCharsets.UTF_8)).openBufferedStream(), client.fieldsInfo());
      client.addOrReplace("/repo/001", doc1);

      String content2 = "This is stop word. apache software.";
      LuceneDocument doc2 = new LuceneDocument();
      doc2 = LuceneDocument.addField(doc2, ID, "/repo/002", client.fieldsInfo());
      doc2 = LuceneDocument.addField(doc2, CONTENT, ByteSource.wrap(content2.getBytes(StandardCharsets.UTF_8)).openBufferedStream(), client.fieldsInfo());
      client.addOrReplace("/repo/002", doc2);

      String content3 = "Apache Solr";
      LuceneDocument doc3 = new LuceneDocument();
      doc3 = LuceneDocument.addField(doc3, ID, "/repo/003", client.fieldsInfo());
      doc3 = LuceneDocument.addField(doc3, CONTENT, ByteSource.wrap(content3.getBytes(StandardCharsets.UTF_8)).openBufferedStream(), client.fieldsInfo());
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
      try (LeafReader reader = client.reader()) {
        Terms terms = reader.getTermVector(docID, CONTENT);
        TermsEnum te = terms.iterator();
        BytesRef br;
        while ((br = te.next()) != null) {
          if (te.seekExact(new BytesRef("apache"))) {
            assertThat(br.utf8ToString(), is("apache"));
            assertThat(te.totalTermFreq(), is(2L));
            break;
          }
        }
        assertThat(reader.docFreq(new Term(CONTENT, br)), is(3));

        assertThat(reader.getTermVector(docID, "content_ws"), is(nullValue()));
        assertThat(reader.getTermVector(docID, "content_ngram"), is(nullValue()));
      }

      hits = searcher.search(client.newQuery("id:\\/repo\\/003"), 1);
      Document storedDocument = searcher.doc(hits.scoreDocs[0].doc);
      assertThat(storedDocument.getField(CONTENT).binaryValue().utf8ToString(), is("Apache Solr"));
      assertThat(storedDocument.getField(CONTENT).stringValue(), is(nullValue()));

      String nrt = "near-real-time";
      LuceneDocument doc4 = new LuceneDocument();
      doc4 = LuceneDocument.addField(doc4, ID, nrt, client.fieldsInfo());
      doc4 = LuceneDocument.addField(doc4, CONTENT, ByteSource.wrap(nrt.getBytes(StandardCharsets.UTF_8)).openBufferedStream(), client.fieldsInfo());
      client.addOrReplace(nrt, doc4);
      ManifoldCF.sleep(1500L);
      assertThat(searcher.count(client.newQuery(ID+":"+nrt)), is(0));
      IndexSearcher searcher2 = client.newSearcher();
      assertThat(searcher2.count(client.newQuery(ID+":"+nrt)), is(0));
      assertThat(client.newRealtimeSearcher().count(client.newQuery(ID+":"+nrt)), is(1));
 
      searcher.getIndexReader().close();
      searcher2.getIndexReader().close();
    }
  }

  @Test
  public void testIndexRepositoryDocument() throws IOException, ManifoldCFException {
    String documentURI = "file://dummy/rd";
    String content = "Classification, categorization, and tagging using Lucene";

    RepositoryDocument rd = new RepositoryDocument();
    rd.addField("cat", "foo");
    rd.addField("author", new String[]{ "abe", "obama" });
    byte[] b = content.getBytes(StandardCharsets.UTF_8);
    InputStream in = ByteSource.wrap(b).openBufferedStream();
    rd.setBinary(in, b.length);

    String path = testDir.getAbsolutePath()+sep+"tmp"+sep+"rd-index";
    try (LuceneClient client = new LuceneClient(path)) {
      LuceneDocument doc = new LuceneDocument();

      doc = LuceneDocument.addField(doc, client.idField(), documentURI, client.fieldsInfo());

      doc = LuceneDocument.addField(doc, client.contentField(), rd.getBinaryStream(), client.fieldsInfo());

      Iterator<String> it = rd.getFields();
      while (it.hasNext()) {
        String rdField = it.next();
        if (client.fieldsInfo().containsKey(rdField)) {
          String[] values = rd.getFieldAsStrings(rdField);
          for (String value : values) {
            doc = LuceneDocument.addField(doc, rdField, value, client.fieldsInfo());
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
      searcher.getIndexReader().close();
    }
  }

  @Test
  public void testHdfsSimple1() throws IOException {
    try (LuceneClient client = new LuceneClient(hdfsPath+"/Simple1")) {
      for (int i = 0; i < 10; i++) {
        LuceneDocument doc = new LuceneDocument();
        doc = LuceneDocument.addField(doc, ID, String.valueOf(i), client.fieldsInfo());
        doc = LuceneDocument.addField(doc, CONTENT, ByteSource.wrap("hdfs directory?".getBytes(StandardCharsets.UTF_8)).openBufferedStream(), client.fieldsInfo());
        client.addOrReplace(String.valueOf(i), doc);
      }
      client.refresh();
      assertThat(client.newRealtimeSearcher().count(client.newQuery("content:hdfs")), is(10));

      client.remove(String.valueOf(0));
      client.refresh();
      assertThat(client.newRealtimeSearcher().count(client.newQuery("content:hdfs")), is(9));
    }
  }

  @Test
  public void testHdfsSimple2() throws IOException {
    try (LuceneClient client = new LuceneClient(hdfsPath+"/Simple2")) {
      for (int i = 0; i < 10; i++) {
        LuceneDocument doc = new LuceneDocument();
        doc = LuceneDocument.addField(doc, ID, String.valueOf(i), client.fieldsInfo());
        doc = LuceneDocument.addField(doc, CONTENT, ByteSource.wrap("hdfs directory.".getBytes(StandardCharsets.UTF_8)).openBufferedStream(), client.fieldsInfo());
        client.addOrReplace(String.valueOf(i), doc);
      }
      client.optimize();
      IndexSearcher searcher = client.newSearcher();
      assertThat(searcher.count(client.newQuery("content:hdfs")), is(10));
      searcher.getIndexReader().close();

      client.remove(String.valueOf(0));
      client.optimize();
      IndexSearcher searcher2 = client.newSearcher();
      assertThat(searcher2.count(client.newQuery("content:hdfs")), is(9));
      searcher2.getIndexReader().close();
    }
  }

  @Test
  public void testHdfsLock() throws IOException {
    String samePath = testDir.getAbsolutePath()+sep+"tmp"+sep+"lock";
    try {
      try (LuceneClient client1 = new LuceneClient(samePath);
           LuceneClient client2 = new LuceneClient(samePath)) {
        fail("Should not get here");
      }
    } catch (Exception e) {
      assert e instanceof LockObtainFailedException;
    }

    samePath = hdfsPath+"/lock";
    String processID_A = "/A";
    String processID_B = "/B";
    try {
      try (LuceneClient client1 = new LuceneClient(samePath+processID_A);
           LuceneClient client2 = new LuceneClient(samePath+processID_B)) {
        for (int i = 0; i < 2; i++) {
          LuceneDocument doc1 = new LuceneDocument();
          doc1 = LuceneDocument.addField(doc1, ID, "A"+String.valueOf(i), client1.fieldsInfo());
          client1.addOrReplace("A"+String.valueOf(i), doc1);
        }
        client1.commit();
        for (int i = 0; i < 3; i++) {
          LuceneDocument doc2 = new LuceneDocument();
          doc2 = LuceneDocument.addField(doc2, ID, "B"+String.valueOf(i), client2.fieldsInfo());
          client2.addOrReplace("B"+String.valueOf(i), doc2);
        }
        client2.commit();

        IndexSearcher searcher1 = client1.newSearcher();
        assertThat(searcher1.count(client1.newQuery("*:*")), is(2));
        searcher1.getIndexReader().close();

        IndexSearcher searcher2 = client2.newSearcher();
        assertThat(searcher2.count(client2.newQuery("*:*")), is(3));
        searcher2.getIndexReader().close();
      }
    } catch (Exception e) {
      System.out.println(e.getMessage());
      fail("Should not get here");
    }
  }

}
