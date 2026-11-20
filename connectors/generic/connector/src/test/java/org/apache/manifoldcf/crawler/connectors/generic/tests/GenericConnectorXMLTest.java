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
package org.apache.manifoldcf.crawler.connectors.generic.tests;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.UnmarshalException;
import jakarta.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.SAXParser;
import org.apache.manifoldcf.core.common.XMLParserFactory;
import org.apache.manifoldcf.crawler.connectors.generic.api.Auth;
import org.apache.manifoldcf.crawler.connectors.generic.api.Items;
import org.junit.Assume;
import org.junit.Test;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import static org.junit.Assert.*;

public class GenericConnectorXMLTest {

  private static JAXBContext createContextOrSkip(final Class<?> type) {
    try {
      return JAXBContext.newInstance(type);
    } catch (Exception e) {
      Assume.assumeNoException("Jakarta JAXB runtime not available on classpath", e);
      return null;
    }
  }

  @Test
  public void testSaxParsesValidSeedsWithSecureFactory() throws Exception {
    final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<seeds><seed id=\"1\"/></seeds>";
    final SAXParser parser = XMLParserFactory.newSecureSAXParserFactory(true).newSAXParser();
    parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), new DefaultHandler());
  }

  @Test(expected = SAXParseException.class)
  public void testSaxRejectsDoctypeInSeeds() throws Exception {
    final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<!DOCTYPE seeds [<!ENTITY ext \"val\">]>"
        + "<seeds><seed id=\"&ext;\"/></seeds>";
    final SAXParser parser = XMLParserFactory.newSecureSAXParserFactory(true).newSAXParser();
    parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), new DefaultHandler());
  }

  @Test
  public void testJaxbUnmarshalsValidItemsWithSecureSource() throws Exception {
    final JAXBContext ctx = createContextOrSkip(Items.class);
    final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<items><item id=\"1\"><url>http://example.com/1</url><version>v1</version></item></items>";
    final Unmarshaller u = ctx.createUnmarshaller();
    final Items items = (Items) u.unmarshal(
        XMLParserFactory.newSecureSAXSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
    assertNotNull(items);
    assertNotNull(items.items);
    assertEquals(1, items.items.size());
    assertEquals("1", items.items.get(0).id);
  }

  @Test(expected = UnmarshalException.class)
  public void testJaxbRejectsDoctypeInItems() throws Exception {
    final JAXBContext ctx = createContextOrSkip(Items.class);
    final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<!DOCTYPE items [<!ENTITY ext \"val\">]>"
        + "<items><item id=\"1\"><url>&ext;</url><version>v1</version></item></items>";
    final Unmarshaller u = ctx.createUnmarshaller();
    u.unmarshal(XMLParserFactory.newSecureSAXSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
  }

  @Test(expected = UnmarshalException.class)
  public void testJaxbRejectsDoctypeInAuthorityAuth() throws Exception {
    final JAXBContext ctx = createContextOrSkip(Auth.class);
    final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<!DOCTYPE auth [<!ENTITY ext \"val\">]>"
        + "<auth exists=\"true\"><token>&ext;</token></auth>";
    final Unmarshaller u = ctx.createUnmarshaller();
    u.unmarshal(XMLParserFactory.newSecureSAXSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
  }
}
