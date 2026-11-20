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
package org.apache.manifoldcf.agents.transformation.tikaservice.tests;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.util.EntityUtils;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IPipelineConnector;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.system.Logging;
import org.apache.manifoldcf.agents.transformation.tikaservice.TikaConfig;
import org.apache.manifoldcf.agents.transformation.tikaservice.TikaExtractor;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.core.interfaces.VersionContext;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class TikaServiceExtractorTest {

  private HttpServer server;

  @BeforeClass
  public static void setUpLoggers() {
    if (Logging.ingest == null) {
      Logging.ingest = org.apache.log4j.Logger.getLogger("org.apache.manifoldcf.ingest");
    }
  }

  @After
  public void tearDown() {
    if (server != null) {
      server.shutdown(1, TimeUnit.SECONDS);
      server = null;
    }
  }

  private TikaExtractor createConnectedExtractor(int port) {
    TikaExtractor extractor = new TikaExtractor();
    ConfigParams params = new ConfigParams();
    params.setParameter(TikaConfig.PARAM_TIKAHOSTNAME, "127.0.0.1");
    params.setParameter(TikaConfig.PARAM_TIKAPORT, Integer.toString(port));
    extractor.connect(params);
    return extractor;
  }

  @Test
  public void testTikaServer4xReservedMetadataAndTikaTextEndpoint() throws Exception {
    final List<String> requestedPaths = new ArrayList<>();
    server = ServerBootstrap.bootstrap()
        .setLocalAddress(InetAddress.getLoopbackAddress())
        .setListenerPort(0)
        .registerHandler("/meta", (req, resp, ctx) -> {
          requestedPaths.add(req.getRequestLine().getUri());
          if (req instanceof HttpEntityEnclosingRequest) {
            EntityUtils.consumeQuietly(((HttpEntityEnclosingRequest) req).getEntity());
          }
          resp.setStatusCode(HttpStatus.SC_OK);
          resp.setEntity(new StringEntity(
              "{\"Content-Type\":\"application/pdf\",\"dc:title\":\"Tika 4 Doc\","
                  + "\"tk:parsed-by\":[\"org.apache.tika.parser.DefaultParser\",\"org.apache.tika.parser.pdf.PDFParser\"],"
                  + "\"tk:parse-time-millis\":\"12\"}",
              ContentType.APPLICATION_JSON));
        })
        .registerHandler("/tika/text", (req, resp, ctx) -> {
          requestedPaths.add(req.getRequestLine().getUri());
          if (req instanceof HttpEntityEnclosingRequest) {
            EntityUtils.consumeQuietly(((HttpEntityEnclosingRequest) req).getEntity());
          }
          resp.setStatusCode(HttpStatus.SC_OK);
          resp.setEntity(new StringEntity("Extracted Tika 4 body text", ContentType.TEXT_PLAIN));
        })
        .create();
    server.start();

    TikaExtractor extractor = createConnectedExtractor(server.getLocalPort());
    try {
      // Verify repeated check() calls do not leak connections
      assertEquals("Connection working", extractor.check());
      assertEquals("Connection working", extractor.check());
      requestedPaths.clear();

      byte[] inputBytes = "dummy pdf bytes".getBytes(StandardCharsets.UTF_8);
      RepositoryDocument doc = new RepositoryDocument();
      doc.setFileName("sample.pdf");
      doc.setMimeType("application/octet-stream");
      doc.setBinary(new ByteArrayInputStream(inputBytes), inputBytes.length);

      Specification spec = new Specification();
      SpecificationNode ignoreNode = new SpecificationNode(TikaConfig.NODE_IGNORETIKAEXCEPTION);
      ignoreNode.setAttribute(TikaConfig.ATTRIBUTE_VALUE, "false");
      spec.addChild(0, ignoreNode);
      VersionContext version = extractor.getPipelineDescription(spec);
      RecordingActivity activity = new RecordingActivity();

      int status = extractor.addOrReplaceDocumentWithException("doc://1", version, doc, null, activity);
      assertEquals(IPipelineConnector.DOCUMENTSTATUS_ACCEPTED, status);
      assertEquals("Extracted Tika 4 body text", activity.capturedText);
      assertEquals("Tika 4 Doc", activity.capturedFields.get("dc:title")[0]);
      assertEquals("application/pdf", activity.capturedFields.get("Content-Type")[0]);
      assertEquals(2, activity.capturedFields.get("tk:parsed-by").length);
      assertTrue(requestedPaths.contains("/meta"));
      assertTrue(requestedPaths.contains("/tika/text"));
    } finally {
      extractor.disconnect();
    }
  }

  @Test
  public void testLegacyTikaServerFallbackAndLegacyMetadataKeys() throws Exception {
    final List<String> requestedPaths = new ArrayList<>();
    server = ServerBootstrap.bootstrap()
        .setLocalAddress(InetAddress.getLoopbackAddress())
        .setListenerPort(0)
        .registerHandler("/meta", (req, resp, ctx) -> {
          requestedPaths.add(req.getRequestLine().getUri());
          if (req instanceof HttpEntityEnclosingRequest) {
            EntityUtils.consumeQuietly(((HttpEntityEnclosingRequest) req).getEntity());
          }
          resp.setStatusCode(HttpStatus.SC_OK);
          resp.setEntity(new StringEntity(
              "{\"Content-Type\":\"text/plain\",\"X-TIKA:Parsed-By\":\"org.apache.tika.parser.txt.TXTParser\",\"title\":\"Legacy Doc\"}",
              ContentType.APPLICATION_JSON));
        })
        .registerHandler("/tika/text", (req, resp, ctx) -> {
          requestedPaths.add(req.getRequestLine().getUri());
          if (req instanceof HttpEntityEnclosingRequest) {
            EntityUtils.consumeQuietly(((HttpEntityEnclosingRequest) req).getEntity());
          }
          resp.setStatusCode(HttpStatus.SC_NOT_FOUND);
          resp.setEntity(new StringEntity("Not Found", ContentType.TEXT_PLAIN));
        })
        .registerHandler("/tika", (req, resp, ctx) -> {
          requestedPaths.add(req.getRequestLine().getUri());
          if (req instanceof HttpEntityEnclosingRequest) {
            EntityUtils.consumeQuietly(((HttpEntityEnclosingRequest) req).getEntity());
          }
          resp.setStatusCode(HttpStatus.SC_OK);
          resp.setEntity(new StringEntity("Legacy Tika 1.x text", ContentType.TEXT_PLAIN));
        })
        .create();
    server.start();

    TikaExtractor extractor = createConnectedExtractor(server.getLocalPort());
    try {
      byte[] inputBytes = "hello legacy".getBytes(StandardCharsets.UTF_8);
      RepositoryDocument doc = new RepositoryDocument();
      doc.setFileName("legacy.txt");
      doc.setBinary(new ByteArrayInputStream(inputBytes), inputBytes.length);

      Specification spec = new Specification();
      SpecificationNode ignoreNode = new SpecificationNode(TikaConfig.NODE_IGNORETIKAEXCEPTION);
      ignoreNode.setAttribute(TikaConfig.ATTRIBUTE_VALUE, "false");
      spec.addChild(0, ignoreNode);
      VersionContext version = extractor.getPipelineDescription(spec);
      RecordingActivity activity = new RecordingActivity();

      int status = extractor.addOrReplaceDocumentWithException("doc://legacy", version, doc, null, activity);
      assertEquals(IPipelineConnector.DOCUMENTSTATUS_ACCEPTED, status);
      assertEquals("Legacy Tika 1.x text", activity.capturedText);
      assertEquals("Legacy Doc", activity.capturedFields.get("title")[0]);
      assertEquals("org.apache.tika.parser.txt.TXTParser", activity.capturedFields.get("X-TIKA:Parsed-By")[0]);
      assertTrue(requestedPaths.contains("/tika/text"));
      assertTrue(requestedPaths.contains("/tika"));
    } finally {
      extractor.disconnect();
    }
  }

  @Test
  public void testHttp429TriggersRetryableServiceInterruption() throws Exception {
    server = ServerBootstrap.bootstrap()
        .setLocalAddress(InetAddress.getLoopbackAddress())
        .setListenerPort(0)
        .registerHandler("/meta", (req, resp, ctx) -> {
          if (req instanceof HttpEntityEnclosingRequest) {
            EntityUtils.consumeQuietly(((HttpEntityEnclosingRequest) req).getEntity());
          }
          resp.setStatusCode(429);
          resp.addHeader("Retry-After", "3");
          resp.setEntity(new StringEntity("CLIENT_UNAVAILABLE_WITHIN_MS", ContentType.TEXT_PLAIN));
        })
        .create();
    server.start();

    TikaExtractor extractor = createConnectedExtractor(server.getLocalPort());
    try {
      byte[] inputBytes = "test".getBytes(StandardCharsets.UTF_8);
      RepositoryDocument doc = new RepositoryDocument();
      doc.setFileName("busy.pdf");
      doc.setBinary(new ByteArrayInputStream(inputBytes), inputBytes.length);

      Specification spec = new Specification();
      SpecificationNode ignoreNode = new SpecificationNode(TikaConfig.NODE_IGNORETIKAEXCEPTION);
      ignoreNode.setAttribute(TikaConfig.ATTRIBUTE_VALUE, "false");
      spec.addChild(0, ignoreNode);
      VersionContext version = extractor.getPipelineDescription(spec);
      RecordingActivity activity = new RecordingActivity();

      long before = System.currentTimeMillis();
      try {
        extractor.addOrReplaceDocumentWithException("doc://429", version, doc, null, activity);
        fail("Expected ServiceInterruption on HTTP 429");
      } catch (ServiceInterruption si) {
        assertTrue("Expected retry time >= 2500ms ahead", si.getRetryTime() >= before + 2500L);
        assertTrue("Expected retry time <= 6000ms ahead", si.getRetryTime() <= before + 6000L);
      }
    } finally {
      extractor.disconnect();
    }
  }

  private static class RecordingActivity implements IOutputAddActivity {
    String capturedText;
    final Map<String, String[]> capturedFields = new HashMap<>();

    @Override
    public int sendDocument(String documentURI, RepositoryDocument document)
        throws ManifoldCFException, ServiceInterruption, IOException {
      capturedText = IOUtils.toString(document.getBinaryStream(), StandardCharsets.UTF_8);
      java.util.Iterator<String> it = document.getFields();
      while (it.hasNext()) {
        String field = it.next();
        capturedFields.put(field, document.getFieldAsStrings(field));
      }
      return IPipelineConnector.DOCUMENTSTATUS_ACCEPTED;
    }

    @Override
    public void noDocument() {}

    @Override
    public String qualifyAccessToken(String authorityNameString, String accessToken) {
      return accessToken;
    }

    @Override
    public void recordActivity(Long startTime, String activityType, Long dataSize, String entityURI,
        String resultCode, String resultDescription) {}

    @Override
    public boolean checkDateIndexable(Date date) { return true; }

    @Override
    public boolean checkMimeTypeIndexable(String mimeType) { return true; }

    @Override
    public boolean checkDocumentIndexable(File localFile) { return true; }

    @Override
    public boolean checkLengthIndexable(long length) { return true; }

    @Override
    public boolean checkURLIndexable(String url) { return true; }
  }

}
