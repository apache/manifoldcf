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

import org.apache.manifoldcf.agents.transformation.tika.TikaParser;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.junit.Test;
import org.junit.Ignore;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

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
    for (String doc : docs) {
      String path = doc;
      InputStream stream = getClass().getResourceAsStream(path);
      Metadata metadata = new Metadata();
      metadata.add(TikaMetadataKeys.RESOURCE_NAME_KEY, new File(getClass().getResource(path).getFile()).getName());
      TikaParser tikaParser = new TikaParser(null);
      ContentHandler unlimitedHandler
        = tikaParser.newWriteOutBodyContentHandler(new StringWriter(), -1);
      tikaParser.parse(stream, metadata, unlimitedHandler);
 
      assertThat(unlimitedHandler.toString().length(), not(0));
      assertThat(metadata.get("Content-Type"), notNullValue());
      assertThat(metadata.get("resourceName"), notNullValue());
    }
  }

  @Test
  public void testExtractWithWriteLimit() throws IOException, SAXException, TikaException, ManifoldCFException {
    for (String doc : docs) {
      String path = doc;
      InputStream stream = getClass().getResourceAsStream(path);
      Metadata metadata = new Metadata();
      metadata.add(TikaMetadataKeys.RESOURCE_NAME_KEY, new File(getClass().getResource(path).getFile()).getName());
      TikaParser tikaParser = new TikaParser(null);
      ContentHandler limitedHandler
        = tikaParser.newWriteOutBodyContentHandler(new StringWriter(), 100 * 1000);
      tikaParser.parse(stream, metadata, limitedHandler);

      assertThat(limitedHandler.toString().length(), not(0));
      assertThat(metadata.get("Content-Type"), notNullValue());
      assertThat(metadata.get("resourceName"), notNullValue());
    }
  }

  @Test
  @Ignore
  public void testExtractWithTooShortWriteLimit() throws ManifoldCFException {
    for (String doc : docs) {
      String path = doc;
      InputStream stream = getClass().getResourceAsStream(path);
      Metadata metadata = new Metadata();
      metadata.add(TikaMetadataKeys.RESOURCE_NAME_KEY, new File(getClass().getResource(path).getFile()).getName());
      TikaParser tikaParser = new TikaParser(null);
      ContentHandler limitedHandler
        = tikaParser.newWriteOutBodyContentHandler(new StringWriter(), 10);
      try {
        tikaParser.parse(stream, metadata, limitedHandler);
        fail("Should not get here");
      } catch (Exception e) {
        assert e instanceof SAXException;
        assertThat(e.toString().indexOf("org.apache.tika.sax.WriteOutContentHandler$WriteLimitReachedException"), not(-1));
      }
    }
  }

}
