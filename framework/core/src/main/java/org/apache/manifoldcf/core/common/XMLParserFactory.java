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

import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Factory helper for constructing hardened JAXP XML parsers and sources.
 */
public final class XMLParserFactory {

  private static final String FEATURE_DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";
  private static final String FEATURE_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
  private static final String FEATURE_EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
  private static final String FEATURE_LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

  private XMLParserFactory() {
    // utility class
  }

  /**
   * Create a SAXParserFactory that rejects DOCTYPE declarations and external entities.
   */
  public static SAXParserFactory newSecureSAXParserFactory(final boolean namespaceAware)
      throws ParserConfigurationException, SAXException {
    final SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setNamespaceAware(namespaceAware);
    factory.setXIncludeAware(false);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature(FEATURE_DISALLOW_DOCTYPE, true);
    factory.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
    factory.setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
    factory.setFeature(FEATURE_LOAD_EXTERNAL_DTD, false);
    return factory;
  }

  /**
   * Create a SAXParserFactory that disables external entities while allowing harmless DOCTYPE declarations.
   */
  public static SAXParserFactory newSafeSAXParserFactoryAllowingDoctype(final boolean namespaceAware)
      throws ParserConfigurationException, SAXException {
    final SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setNamespaceAware(namespaceAware);
    factory.setXIncludeAware(false);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
    factory.setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
    factory.setFeature(FEATURE_LOAD_EXTERNAL_DTD, false);
    return factory;
  }

  /**
   * Create a DocumentBuilderFactory that rejects DOCTYPE declarations and external entities.
   */
  public static DocumentBuilderFactory newSecureDocumentBuilderFactory(final boolean namespaceAware)
      throws ParserConfigurationException {
    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(namespaceAware);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature(FEATURE_DISALLOW_DOCTYPE, true);
    factory.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
    factory.setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
    factory.setFeature(FEATURE_LOAD_EXTERNAL_DTD, false);
    return factory;
  }

  /**
   * Create a DocumentBuilderFactory that disables external entities while allowing harmless DOCTYPE declarations.
   */
  public static DocumentBuilderFactory newSafeDocumentBuilderFactoryAllowingDoctype(final boolean namespaceAware)
      throws ParserConfigurationException {
    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(namespaceAware);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
    factory.setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
    factory.setFeature(FEATURE_LOAD_EXTERNAL_DTD, false);
    return factory;
  }

  /**
   * Wrap an InputStream in a SAXSource backed by a hardened XMLReader suitable for JAXB unmarshalling.
   */
  public static SAXSource newSecureSAXSource(final InputStream inputStream)
      throws ParserConfigurationException, SAXException {
    final SAXParserFactory factory = newSecureSAXParserFactory(true);
    final SAXParser parser = factory.newSAXParser();
    final XMLReader reader = parser.getXMLReader();
    return new SAXSource(reader, new InputSource(inputStream));
  }
}
