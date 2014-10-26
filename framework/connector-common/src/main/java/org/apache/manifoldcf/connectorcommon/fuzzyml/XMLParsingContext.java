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
package org.apache.manifoldcf.connectorcommon.fuzzyml;

import org.apache.manifoldcf.core.interfaces.*;
import java.io.*;
import java.util.*;

/** An instance of this class represents a parsing context within a node.  Parsing functionality is implemented
* by extending this class to do the right thing for the context in which it is deployed.  The base functionality
* "does nothing"; extended functionality is needed to interpret nodes and act upon them.
*/
public class XMLParsingContext
{
  /** The stream we belong to */
  protected final XMLFuzzyHierarchicalParseState theStream;
  /** The previous context */
  protected final XMLParsingContext previousContext;
  /** The attributes belonging to the node associated with this context */
  protected final Map<String,String> theseAttributes;
  /** The namespace associated with the context */
  protected final String namespace;
  /** The localname associated with the context */
  protected final String localname;
  /** The qname associated with the context */
  protected final String qname;

  /** Root constructor.  Used for outer document level. */
  public XMLParsingContext(XMLFuzzyHierarchicalParseState theStream)
  {
    this(theStream,null,null,null,null);
  }

  /** Full constructor.  Used for individual tags. */
  public XMLParsingContext(XMLFuzzyHierarchicalParseState theStream, String namespace, String localname, String qname, Map<String,String> theseAttributes)
  {
    this.theStream = theStream;
    this.previousContext = theStream.getContext();
    this.namespace = namespace;
    this.localname = localname;
    this.qname = qname;
    this.theseAttributes = theseAttributes;
  }

  /** Get an attribute's value, if any */
  public String getAttribute(String attributeName)
  {
    if (theseAttributes != null)
      return theseAttributes.get(attributeName);
    else
      return null;
  }

  /** Get the namespace name of this node */
  public String getNamespace()
  {
    return namespace;
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

  /** Handle the start of a tag */
  public final void startElement(String namespace, String localName, String qName, Map<String,String> atts)
    throws ManifoldCFException
  {
    // For every child tag, we must create a new context.  We call a stub method to do that here; the stub method is meant
    // to be overridden to provide the proper non-default context, where desired.
    XMLParsingContext newContext = beginTag(namespace, localName, qName, atts);
    if (newContext == null)
      newContext = new XMLParsingContext(theStream,namespace,localName,qName,atts);
    // We need to establish the new context in the stack of the owning XMLStream object
    theStream.setContext(newContext);
  }

  /** Handle the end of a tag */
  public final void endElement(String namespace, String localName, String qName)
    throws ManifoldCFException
  {
    // When a child tag ends, pop back to the previous context.  That will allow the current one to go away.  But first, call
    // a stub method that can be overridden to perform activities.
    // Signal the end of the tag.  This goes last, because we have to do things in the reverse order from the
    // way the context got pushed to make sense.
    if (previousContext != null)
      previousContext.endTag();
    // Before we leave the child context, clean up the child tag itself, but not the whole chain
    theStream.getContext().tagCleanup();
    // Go back to the parent context
    theStream.setContext(previousContext);
  }

  /** Handle content of a tag */
  public final void characters(String contents)
    throws ManifoldCFException
  {
    // Call the overridden method with the right context object
    tagContents(contents);
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
  protected XMLParsingContext beginTag(String namespace, String localName, String qName, Map<String,String> atts)
    throws ManifoldCFException
  {
    // The default action is to establish a new default context.
    return null;
  }

  /** This method is meant to be extended by classes that extend this class */
  protected void endTag()
    throws ManifoldCFException
  {
  }

  /** This method is meant to be extended by classes that extend this class */
  protected void tagContents(String contents)
    throws ManifoldCFException
  {
  }

  /** Override this method to be called during cleanup */
  protected void tagCleanup()
    throws ManifoldCFException
  {
  }
}
