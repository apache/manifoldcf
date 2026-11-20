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
package org.apache.manifoldcf.agents.transformation.tika.tests;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.manifoldcf.agents.transformation.tika.TikaConfig;
import org.apache.manifoldcf.agents.transformation.tika.TikaExtractor;
import org.apache.manifoldcf.agents.transformation.tika.TikaParser;
import org.apache.manifoldcf.connectorcommon.extmimemap.ExtensionMimeMap;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.boilerpipe.BoilerpipeContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import de.l3s.boilerpipe.extractors.ArticleExtractor;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class TikaParserTest {

  private static List<String> docs = new ArrayList<>();
  static {
    docs.add("/test-documents/testEXCEL.xlsx");
    docs.add("/test-documents/testHTML.html");
    docs.add("/test-documents/testPDF.pdf");
  }

  @Test
  public void testSimple() throws IOException, SAXException, TikaException, ManifoldCFException {
    TikaParser tikaParser = new TikaParser(null);
    for (String doc : docs) {
      try (InputStream stream = getClass().getResourceAsStream(doc)) {
        Metadata metadata = new Metadata();
        metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, new File(getClass().getResource(doc).getFile()).getName());
        ContentHandler unlimitedHandler
          = TikaParser.newWriteOutBodyContentHandler(new StringWriter(), -1);
        tikaParser.parse(stream, metadata, unlimitedHandler);

        assertThat(unlimitedHandler.toString().length(), not(0));
        assertThat(metadata.get("Content-Type"), notNullValue());
        assertThat(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY), notNullValue());
      }
    }
  }

  @Test
  public void testExtractWithWriteLimit() throws IOException, SAXException, TikaException, ManifoldCFException {
    TikaParser tikaParser = new TikaParser(null);
    for (String doc : docs) {
      try (InputStream stream = getClass().getResourceAsStream(doc)) {
        Metadata metadata = new Metadata();
        metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, new File(getClass().getResource(doc).getFile()).getName());
        ContentHandler limitedHandler
          = TikaParser.newWriteOutBodyContentHandler(new StringWriter(), 100 * 1000);
        tikaParser.parse(stream, metadata, limitedHandler);

        assertThat(limitedHandler.toString().length(), not(0));
        assertThat(metadata.get("Content-Type"), notNullValue());
        assertThat(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY), notNullValue());
      }
    }
  }

  @Test
  public void testExtractWithTooShortWriteLimit() throws IOException, ManifoldCFException {
    TikaParser tikaParser = new TikaParser(null);
    for (String doc : docs) {
      try (InputStream stream = getClass().getResourceAsStream(doc)) {
        Metadata metadata = new Metadata();
        metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, new File(getClass().getResource(doc).getFile()).getName());
        ContentHandler limitedHandler
          = TikaParser.newWriteOutBodyContentHandler(new StringWriter(), 10);
        try {
          tikaParser.parse(stream, metadata, limitedHandler);
          fail("Expected WriteLimitReachedException for " + doc);
        } catch (Exception e) {
          assertTrue("Expected SAXException, got " + e.getClass().getName(), e instanceof SAXException);
          assertTrue("Expected WriteLimitReachedException, got " + e, WriteLimitReachedException.isWriteLimitReached(e));
        }
      }
    }
  }

  @Test
  public void testBoilerpipeExtraction() throws IOException, SAXException, TikaException, ManifoldCFException {
    TikaParser tikaParser = new TikaParser(null);
    try (InputStream stream = getClass().getResourceAsStream("/test-documents/testHTML.html")) {
      Metadata metadata = new Metadata();
      metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, "testHTML.html");
      StringWriter writer = new StringWriter();
      ContentHandler handler = new BoilerpipeContentHandler(
        TikaParser.newWriteOutBodyContentHandler(writer, -1),
        ArticleExtractor.INSTANCE);
      tikaParser.parse(stream, metadata, handler);

      assertThat(writer.toString().trim().length(), not(0));
    }
  }

  @Test
  public void testCustomJsonAndBlankConfigs() throws IOException, SAXException, TikaException, ManifoldCFException {
    for (String config : new String[]{"", "   \n  ", "{}", "{\"parse-context\": {}}"}) {
      TikaParser tikaParser = new TikaParser(config);
      try (InputStream stream = getClass().getResourceAsStream("/test-documents/testHTML.html")) {
        Metadata metadata = new Metadata();
        metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, "testHTML.html");
        StringWriter writer = new StringWriter();
        tikaParser.parse(stream, metadata, TikaParser.newWriteOutBodyContentHandler(writer, -1));
        assertThat(writer.toString().length(), not(0));
      }
    }
  }

  @Test
  public void testLegacyXmlConfigRejected() {
    try {
      new TikaParser("<properties><parsers/></properties>");
      fail("Expected ManifoldCFException for legacy XML Tika config");
    } catch (ManifoldCFException e) {
      assertTrue("Expected JSON migration hint, got: " + e.getMessage(),
        e.getMessage().contains("JSON"));
    }
  }

  @Test
  public void testCheckValidatesConfig() throws ManifoldCFException {
    TikaExtractor extractor = new TikaExtractor();
    try {
      ConfigParams validParams = new ConfigParams();
      validParams.setParameter(TikaConfig.PARAM_TIKACONFIG, "{}");
      extractor.connect(validParams);
      assertEquals("Connection working", extractor.check());
      extractor.disconnect();

      ConfigParams invalidParams = new ConfigParams();
      invalidParams.setParameter(TikaConfig.PARAM_TIKACONFIG, "<properties/>");
      extractor.connect(invalidParams);
      String status = extractor.check();
      assertTrue("Expected invalid config status, got: " + status,
        status.startsWith("Invalid Tika configuration:") && status.contains("JSON"));
    } finally {
      extractor.disconnect();
    }
  }

  @Test
  public void testExtensionMimeMap() {
    assertEquals("application/pdf", ExtensionMimeMap.mapToMimeType("pdf"));
    assertEquals("text/html", ExtensionMimeMap.mapToMimeType("html"));
    assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      ExtensionMimeMap.mapToMimeType("xlsx"));
  }

}
