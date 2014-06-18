/* $Id: XMLStream.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.agents.common;

import org.xml.sax.XMLReader;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.XMLReaderFactory;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;

/** This object allows easier control of an XML parsing stream than does standard SAX.
*/
public class XMLStream
{
  /** The xml reader object */
  protected XMLReader xr = null;
  /** The current context */
  protected XMLContext currentContext = null;
  /** The parse exception we saw, if any */
  protected SAXParseException parseException = null;

  /** Constructor.  This does NOT actually execute the parse yet, because we need the object before that makes any sense.
  */
  public XMLStream(boolean laxChecking)
    throws ManifoldCFException
  {
    try
    {
      xr = XMLReaderFactory.createXMLReader();
      xr.setContentHandler(new MyContentHandler());
      xr.setErrorHandler(new MyErrorHandler());
      xr.setEntityResolver(new MyEntityResolver());
      if (laxChecking)
      {
        // For many kinds of real-world parsing, we want to continue if at all possible.  Hopefully
        // this will include unicode errors of various kinds, etc.

        // This is xerces specific, so we'd better be invoking the xerces parser or we're screwed
        xr.setFeature("http://apache.org/xml/features/continue-after-fatal-error",true);
        xr.setFeature("http://apache.org/xml/features/ignore-badly-encoded-chars",true);
      }
    }
    catch (SAXException e)
    {
      Exception e2 = e.getException();
      if (e2 != null && e2 instanceof ManifoldCFException)
        throw (ManifoldCFException)e2;
      throw new ManifoldCFException("Error setting up parser: "+e.getMessage(),e);
    }
  }

  /** Default constructor */
  public XMLStream()
    throws ManifoldCFException
  {
    this(true);
  }

  public void parse(InputStream xmlInputStream)
    throws ManifoldCFException, ServiceInterruption, IOException
  {
    try
    {
      InputSource is = new InputSource(xmlInputStream);
      xr.parse(is);
      if (parseException != null)
        throw new ManifoldCFException("XML parse error: "+parseException.getMessage(),parseException);
    }
    catch (SAXException e)
    {
      Exception e2 = e.getException();
      if (e2 != null && e2 instanceof ManifoldCFException)
        throw (ManifoldCFException)e2;
      if (e2 != null && e2 instanceof ServiceInterruption)
        throw (ServiceInterruption)e2;
      throw new ManifoldCFException("Error setting up parser: "+e.getMessage(),e);
    }
    catch (RuntimeException e)
    {
      // Xerces is unfortunately not constructed in such a way that it doesn't occasionally completely barf on a malformed file.
      // So, we catch runtime exceptions and treat them as parse errors.
      throw new ManifoldCFException("XML parse error: "+e.getMessage(),e);
    }
  }

  /** Call this method to clean up completely after a parse attempt, whether successful or failure. */
  public void cleanup()
    throws ManifoldCFException
  {
    // This sets currentContext == null as a side effect, unless an error occurs during cleanup!!
    currentContext.cleanup();
  }

  public void setContext(XMLContext context)
  {
    currentContext = context;
  }

  public XMLContext getContext()
  {
    return currentContext;
  }

  protected class MyContentHandler extends DefaultHandler
  {
    public void characters(char[] ch, int start, int length)
      throws SAXException
    {
      super.characters(ch,start,length);
      // Look up the current context, and invoke its methods
      if (currentContext != null)
        currentContext.characters(ch,start,length);
    }

    public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
      throws SAXException
    {
      super.startElement(namespaceURI,localName,qName,atts);
      if (currentContext != null)
        currentContext.startElement(namespaceURI,localName,qName,atts);
    }

    public void endElement(String namespaceURI, String localName, String qName)
      throws SAXException
    {
      super.endElement(namespaceURI,localName,qName);
      if (currentContext != null)
        currentContext.endElement(namespaceURI,localName,qName);
    }

    public void startDocument()
      throws SAXException
    {
      super.startDocument();
      if (currentContext != null)
        currentContext.startDocument();
    }

    public void endDocument()
      throws SAXException
    {
      super.endDocument();
      if (currentContext != null)
        currentContext.endDocument();
    }


  }

  protected class MyErrorHandler extends DefaultHandler
  {
    public void fatalError(SAXParseException exception)
    {
      parseException = exception;
    }
  }

  protected static class MyEntityResolver implements org.xml.sax.EntityResolver
  {
    public org.xml.sax.InputSource resolveEntity(java.lang.String publicId, java.lang.String systemId)
      throws SAXException, java.io.IOException
    {
      // ALL references resolve to blank documents
      return new org.xml.sax.InputSource(new ByteArrayInputStream("<?xml version='1.0' encoding='UTF-8'?>".getBytes(StandardCharsets.UTF_8)));
    }
  }

}
