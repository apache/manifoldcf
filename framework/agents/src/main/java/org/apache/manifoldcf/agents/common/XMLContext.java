/* $Id: XMLContext.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;

/** An instance of this class represents a parsing context within a node.  Parsing functionality is implemented
* by extending this class to do the right thing for the context in which it is deployed.  The base functionality
* "does nothing"; extended functionality is needed to interpret nodes and act upon them.
*/
public class XMLContext
{
  /** The stream we belong to */
  protected XMLStream theStream;
  /** The previous context */
  protected XMLContext previousContext;
  /** The attributes belonging to the node associated with this context */
  protected Attributes theseAttributes = null;
  /** The namespace URI associated with the context */
  protected String namespaceURI = null;
  /** The localname associated with the context */
  protected String localname = null;
  /** The qname associated with the context */
  protected String qname = null;

  /** Root constructor.  Used for outer document level. */
  public XMLContext(XMLStream theStream)
  {
    this(theStream,null,null,null,null);
  }

  /** Full constructor.  Used for individual tags. */
  public XMLContext(XMLStream theStream, String namespaceURI, String localname, String qname, Attributes theseAttributes)
  {
    this.theStream = theStream;
    this.previousContext = theStream.getContext();
    this.namespaceURI = namespaceURI;
    this.localname = localname;
    this.qname = qname;
    this.theseAttributes = theseAttributes;
  }

  /** Get an attribute's value, if any */
  public String getAttribute(String attributeName)
  {
    if (theseAttributes != null)
      return theseAttributes.getValue(attributeName);
    else
      return null;
  }

  /** Get the namespace URI of this node */
  public String getNamespaceURI()
  {
    return namespaceURI;
  }

  /** Get the localname of this node */
  public String getLocalname()
  {
    return localname;
  }

  /** Get the qname of this node */
  public String getQname()
  {
    return qname;
  }

  /** Handle the start of the document */
  public final void startDocument()
    throws SAXException
  {
  }

  /** Handle the end of the document */
  public final void endDocument()
    throws SAXException
  {
  }

  /** Handle the start of a tag */
  public final void startElement(String namespaceURI, String localName, String qName, Attributes atts)
    throws SAXException
  {
    // For every child tag, we must create a new context.  We call a stub method to do that here; the stub method is meant
    // to be overridden to provide the proper non-default context, where desired.
    try
    {
      XMLContext newContext = beginTag(namespaceURI, localName, qName, atts);
      if (newContext == null)
        newContext = new XMLContext(theStream,namespaceURI,localName,qName,atts);
      // We need to establish the new context in the stack of the owning XMLStream object
      theStream.setContext(newContext);
    }
    catch (ServiceInterruption e)
    {
      throw new SAXException(e);
    }
    catch (ManifoldCFException e)
    {
      throw new SAXException(e);
    }
  }

  /** Handle the end of a tag */
  public final void endElement(String namespaceURI, String localName, String qName)
    throws SAXException
  {
    // When a child tag ends, pop back to the previous context.  That will allow the current one to go away.  But first, call
    // a stub method that can be overridden to perform activities.
    try
    {
      // Signal the end of the tag.  This goes last, because we have to do things in the reverse order from the
      // way the context got pushed to make sense.
      if (previousContext != null)
        previousContext.endTag();
      // Before we leave the child context, clean up the child tag itself, but not the whole chain
      theStream.getContext().tagCleanup();
      // Go back to the parent context
      theStream.setContext(previousContext);
    }
    catch (ServiceInterruption e)
    {
      throw new SAXException(e);
    }
    catch (ManifoldCFException e)
    {
      throw new SAXException(e);
    }
  }

  /** Handle content of a tag */
  public final void characters(char[] ch, int start, int length)
    throws SAXException
  {
    try
    {
      // Call the overridden method with the right context object
      tagContents(ch,start,length);
    }
    catch (ServiceInterruption e)
    {
      throw new SAXException(e);
    }
    catch (ManifoldCFException e)
    {
      throw new SAXException(e);
    }
  }

  /** Cleanup this context object, and then recurse up the chain.
  * This method is called without fail at the end of any parse, whether it errored out or not, so that proper cleanup always happens for any tags left on the stack.
  */
  public final void cleanup()
    throws ManifoldCFException
  {
    tagCleanup();
    theStream.setContext(previousContext);
  }

  /** This method is meant to be extended by classes that extend this class.  The form of this method is meant to enable creation of a
  * context object derived from XMLContext that understands how to actually handle tags and content within the current context. */
  protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
    throws ManifoldCFException, ServiceInterruption
  {
    // The default action is to establish a new default context.
    return null;
  }

  /** This method is meant to be extended by classes that extend this class */
  protected void endTag()
    throws ManifoldCFException, ServiceInterruption
  {
  }

  /** This method is meant to be extended by classes that extend this class */
  protected void tagContents(char[] ch, int start, int length)
    throws ManifoldCFException, ServiceInterruption
  {
  }

  /** Override this method to be called during cleanup */
  protected void tagCleanup()
    throws ManifoldCFException
  {
  }
}
