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
package org.apache.manifoldcf.agents.transformation.tikaservice.rmeta.tests;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
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
import org.apache.manifoldcf.agents.transformation.tikaservice.rmeta.TikaConfig;
import org.apache.manifoldcf.agents.transformation.tikaservice.rmeta.TikaExtractor;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.core.interfaces.VersionContext;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class TikaServiceRmetaExtractorTest {

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
    params.setParameter(TikaConfig.PARAM_CONNECTIONTIMEOUT, TikaConfig.CONNECTIONTIMEOUT_DEFAULT);
    params.setParameter(TikaConfig.PARAM_SOCKETTIMEOUT, TikaConfig.SOCKETTIMEOUT_DEFAULT);
    params.setParameter(TikaConfig.PARAM_RETRYINTERVAL, "5000");
    params.setParameter(TikaConfig.PARAM_RETRYINTERVALTIKADOWN, "10000");
    params.setParameter(TikaConfig.PARAM_RETRYNUMBER, "1");
    extractor.connect(params);
    return extractor;
  }

  private Specification createSpecWithArchives(boolean extractArchives) {
    Specification spec = new Specification();
    SpecificationNode node = new SpecificationNode(TikaConfig.NODE_EXTRACTARCHIVES);
    node.setAttribute(TikaConfig.ATTRIBUTE_VALUE, Boolean.toString(extractArchives));
    spec.addChild(0, node);
    return spec;
  }

  @Test
  public void testTikaServer4xRmetaNamespaceAndEmbeddedResources() throws Exception {
    server = ServerBootstrap.bootstrap()
        .setLocalAddress(InetAddress.getLoopbackAddress())
        .setListenerPort(0)
        .registerHandler("/rmeta/body", (req, resp, ctx) -> {
          if (req instanceof HttpEntityEnclosingRequest) {
            EntityUtils.consumeQuietly(((HttpEntityEnclosingRequest) req).getEntity());
          }
          resp.setStatusCode(HttpStatus.SC_OK);
          resp.setEntity(new StringEntity(
              "["
                  + "{\"Content-Type\":\"application/pdf\",\"dc:title\":\"Tika 4 Report\","
                  + "\"tk:parsed-by\":[\"org.apache.tika.parser.DefaultParser\"],"
                  + "\"tk:warn:truncated-metadata\":\"true\","
                  + "\"tk:content\":\"Main body content. \"},"
                  + "{\"tk:resource-name\":\"attachment.txt\",\"tk:content\":\"Embedded attachment text.\"}"
                  + "]",
              ContentType.APPLICATION_JSON));
        })
        .create();
    server.start();

    TikaExtractor extractor = createConnectedExtractor(server.getLocalPort());
    try {
      byte[] inputBytes = "dummy bytes".getBytes(StandardCharsets.UTF_8);
      RepositoryDocument doc = new RepositoryDocument();
      doc.setFileName("report.pdf");
      doc.setMimeType("application/pdf");
      doc.setBinary(new ByteArrayInputStream(inputBytes), inputBytes.length);

      Specification spec = createSpecWithArchives(true);
      VersionContext version = extractor.getPipelineDescription(spec);
      RecordingActivity activity = new RecordingActivity();

      int status = extractor.addOrReplaceDocumentWithException("doc://rmeta4", version, doc, null, activity);
      assertEquals(IPipelineConnector.DOCUMENTSTATUS_ACCEPTED, status);
      assertEquals("Main body content. Embedded attachment text.", activity.capturedText);
      assertEquals("Tika 4 Report", activity.capturedFields.get("dc:title")[0]);
      assertEquals(Arrays.asList("attachment.txt"), Arrays.asList(activity.capturedFields.get("embeddedResourcesNames")));
      assertNull("Internal tk:parsed-by should not leak into metadata", activity.capturedFields.get("tk:parsed-by"));
      assertNull("Internal tk:content should not leak into metadata", activity.capturedFields.get("tk:content"));
      assertEquals("false", activity.capturedFields.get("truncated")[0]);
      assertEquals("false", activity.capturedFields.get("resources_limit")[0]);
      assertTrue("Expected truncated metadata warning in description",
          activity.lastDescription.contains("truncated by Tika"));
    } finally {
      extractor.disconnect();
    }
  }

  @Test
  public void testTikaServer4xLimitExceptions() throws Exception {
    server = ServerBootstrap.bootstrap()
        .setLocalAddress(InetAddress.getLoopbackAddress())
        .setListenerPort(0)
        .registerHandler("/rmeta/body", (req, resp, ctx) -> {
          if (req instanceof HttpEntityEnclosingRequest) {
            EntityUtils.consumeQuietly(((HttpEntityEnclosingRequest) req).getEntity());
          }
          resp.setStatusCode(HttpStatus.SC_OK);
          resp.setEntity(new StringEntity(
              "[{\"Content-Type\":\"text/plain\","
                  + "\"tk:exception:write-limit-reached\":\"true\","
                  + "\"tk:exception:embedded-resource-limit-reached\":\"true\","
                  + "\"tk:content\":\"Partial\"}]",
              ContentType.APPLICATION_JSON));
        })
        .create();
    server.start();

    TikaExtractor extractor = createConnectedExtractor(server.getLocalPort());
    try {
      byte[] inputBytes = "dummy bytes".getBytes(StandardCharsets.UTF_8);
      RepositoryDocument doc = new RepositoryDocument();
      doc.setFileName("big.txt");
      doc.setBinary(new ByteArrayInputStream(inputBytes), inputBytes.length);

      VersionContext version = extractor.getPipelineDescription(new Specification());
      RecordingActivity activity = new RecordingActivity();

      int status = extractor.addOrReplaceDocumentWithException("doc://limits4", version, doc, null, activity);
      assertEquals(IPipelineConnector.DOCUMENTSTATUS_ACCEPTED, status);
      assertEquals("Partial", activity.capturedText);
      assertEquals("TRUNCATEDOK", activity.lastResultCode);
      assertEquals("true", activity.capturedFields.get("truncated")[0]);
      assertEquals("true", activity.capturedFields.get("resources_limit")[0]);
    } finally {
      extractor.disconnect();
    }
  }

  @Test
  public void testLegacyTikaServer3xRmetaCompatibility() throws Exception {
    server = ServerBootstrap.bootstrap()
        .setLocalAddress(InetAddress.getLoopbackAddress())
        .setListenerPort(0)
        .registerHandler("/rmeta/body", (req, resp, ctx) -> {
          if (req instanceof HttpEntityEnclosingRequest) {
            EntityUtils.consumeQuietly(((HttpEntityEnclosingRequest) req).getEntity());
          }
          resp.setStatusCode(HttpStatus.SC_OK);
          resp.setEntity(new StringEntity(
              "["
                  + "{\"Content-Type\":\"application/pdf\",\"dc:title\":\"Legacy Report\","
                  + "\"X-TIKA:Parsed-By\":[\"org.apache.tika.parser.DefaultParser\"],"
                  + "\"X-TIKA:EXCEPTION:write_limit_reached\":\"true\","
                  + "\"X-TIKA:content\":\"Legacy main. \"},"
                  + "{\"resourceName\":\"legacy.txt\",\"X-TIKA:content\":\"Legacy embedded.\"}"
                  + "]",
              ContentType.APPLICATION_JSON));
        })
        .create();
    server.start();

    TikaExtractor extractor = createConnectedExtractor(server.getLocalPort());
    try {
      byte[] inputBytes = "dummy".getBytes(StandardCharsets.UTF_8);
      RepositoryDocument doc = new RepositoryDocument();
      doc.setFileName("legacy.pdf");
      doc.setBinary(new ByteArrayInputStream(inputBytes), inputBytes.length);

      Specification spec = createSpecWithArchives(true);
      VersionContext version = extractor.getPipelineDescription(spec);
      RecordingActivity activity = new RecordingActivity();

      int status = extractor.addOrReplaceDocumentWithException("doc://legacy3", version, doc, null, activity);
      assertEquals(IPipelineConnector.DOCUMENTSTATUS_ACCEPTED, status);
      assertEquals("Legacy main. Legacy embedded.", activity.capturedText);
      assertEquals("Legacy Report", activity.capturedFields.get("dc:title")[0]);
      assertEquals(Arrays.asList("legacy.txt"), Arrays.asList(activity.capturedFields.get("embeddedResourcesNames")));
      assertEquals("TRUNCATEDOK", activity.lastResultCode);
      assertEquals("true", activity.capturedFields.get("truncated")[0]);
      assertNull("Internal X-TIKA:Parsed-By should not leak", activity.capturedFields.get("X-TIKA:Parsed-By"));
    } finally {
      extractor.disconnect();
    }
  }

  @Test
  public void testHttp429TriggersRetryableServiceInterruption() throws Exception {
    server = ServerBootstrap.bootstrap()
        .setLocalAddress(InetAddress.getLoopbackAddress())
        .setListenerPort(0)
        .registerHandler("/rmeta/body", (req, resp, ctx) -> {
          if (req instanceof HttpEntityEnclosingRequest) {
            EntityUtils.consumeQuietly(((HttpEntityEnclosingRequest) req).getEntity());
          }
          resp.setStatusCode(429);
          resp.addHeader("Retry-After", "2");
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

      VersionContext version = extractor.getPipelineDescription(new Specification());
      RecordingActivity activity = new RecordingActivity();

      long before = System.currentTimeMillis();
      try {
        extractor.addOrReplaceDocumentWithException("doc://rmeta429", version, doc, null, activity);
        fail("Expected ServiceInterruption on HTTP 429");
      } catch (ServiceInterruption si) {
        assertEquals("TIKASERVERBUSY", activity.lastResultCode);
        assertTrue("Expected retry time >= 1500ms ahead", si.getRetryTime() >= before + 1500L);
        assertTrue("Expected retry time <= 5000ms ahead", si.getRetryTime() <= before + 5000L);
      }
    } finally {
      extractor.disconnect();
    }
  }

  private static class RecordingActivity implements IOutputAddActivity {
    String capturedText;
    String lastResultCode;
    String lastDescription;
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
        String resultCode, String resultDescription) {
      this.lastResultCode = resultCode;
      this.lastDescription = resultDescription;
    }

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
