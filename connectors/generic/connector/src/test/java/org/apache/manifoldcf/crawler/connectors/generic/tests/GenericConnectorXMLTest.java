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
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.manifoldcf.core.common.XMLParserFactory;
import org.apache.manifoldcf.crawler.connectors.generic.api.Auth;
import org.apache.manifoldcf.crawler.connectors.generic.api.Items;
import org.junit.Test;
import static org.junit.Assert.*;

public class GenericConnectorXMLTest {

  @Test
  public void testJaxbUnmarshalsValidItemsWithSecureSource() throws Exception {
    final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<items><item id=\"1\"><url>http://example.com/1</url><version>v1</version></item></items>";
    final JAXBContext ctx = JAXBContext.newInstance(Items.class);
    final Unmarshaller u = ctx.createUnmarshaller();
    final Items items = (Items) u.unmarshal(
        XMLParserFactory.newSecureSAXSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
    assertNotNull(items);
    assertNotNull(items.items);
    assertEquals(1, items.items.size());
    assertEquals("1", items.items.get(0).id);
  }

  @Test(expected = JAXBException.class)
  public void testJaxbRejectsDoctypeInItems() throws Exception {
    final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<!DOCTYPE items [<!ENTITY ext \"injected\">]>"
        + "<items><item id=\"1\"><url>&ext;</url><version>v1</version></item></items>";
    final JAXBContext ctx = JAXBContext.newInstance(Items.class);
    final Unmarshaller u = ctx.createUnmarshaller();
    u.unmarshal(XMLParserFactory.newSecureSAXSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
  }

  @Test(expected = JAXBException.class)
  public void testJaxbRejectsDoctypeInAuthorityAuth() throws Exception {
    final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<!DOCTYPE auth [<!ENTITY ext \"injected\">]>"
        + "<auth exists=\"true\"><token>&ext;</token></auth>";
    final JAXBContext ctx = JAXBContext.newInstance(Auth.class);
    final Unmarshaller u = ctx.createUnmarshaller();
    u.unmarshal(XMLParserFactory.newSecureSAXSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
  }
}
