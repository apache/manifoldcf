/* $Id$ */

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
package org.apache.manifoldcf.core.common;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.SAXParser;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import static org.junit.Assert.*;

public class XMLParserFactoryTest {

  @Test
  public void testValidXmlParsesWithSecureFactories() throws Exception {
    final byte[] xml = "<root><item>ok</item></root>".getBytes(StandardCharsets.UTF_8);

    final SAXParser saxParser = XMLParserFactory.newSecureSAXParserFactory(true).newSAXParser();
    saxParser.parse(new ByteArrayInputStream(xml), new DefaultHandler());

    final DocumentBuilder db = XMLParserFactory.newSecureDocumentBuilderFactory(true).newDocumentBuilder();
    final Document doc = db.parse(new ByteArrayInputStream(xml));
    assertEquals("ok", doc.getElementsByTagName("item").item(0).getTextContent());
  }

  @Test(expected = SAXParseException.class)
  public void testSecureSAXParserRejectsDoctype() throws Exception {
    final String xml = "<?xml version=\"1.0\"?><!DOCTYPE root [<!ENTITY test \"val\">]><root>&test;</root>";
    final SAXParser saxParser = XMLParserFactory.newSecureSAXParserFactory(true).newSAXParser();
    saxParser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), new DefaultHandler());
  }

  @Test(expected = SAXParseException.class)
  public void testSecureDocumentBuilderRejectsDoctype() throws Exception {
    final String xml = "<?xml version=\"1.0\"?><!DOCTYPE root [<!ENTITY test \"val\">]><root>&test;</root>";
    final DocumentBuilder db = XMLParserFactory.newSecureDocumentBuilderFactory(true).newDocumentBuilder();
    db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }
}
