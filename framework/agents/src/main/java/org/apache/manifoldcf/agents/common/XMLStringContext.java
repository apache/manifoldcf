/* $Id: XMLStringContext.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** An instance of this class represents a parsing context within a node, where the data value is to be recorded as an in-memory string.  The data string is
* available as a local StringBuilder object, which will be accessible to any class that extends this one.
*/
public class XMLStringContext extends XMLContext
{
  /** The string buffer */
  protected StringBuilder value = new StringBuilder();

  /** Full constructor.  Used for individual tags. */
  public XMLStringContext(XMLStream theStream, String namespaceURI, String localname, String qname, Attributes theseAttributes)
  {
    super(theStream,namespaceURI,localname,qname,theseAttributes);
  }

  /** Get the string value */
  public String getValue()
  {
    return value.toString();
  }

  /** This method is meant to be extended by classes that extend this class */
  protected void tagContents(char[] ch, int start, int length)
    throws ManifoldCFException
  {
    // Append the characters to the buffer
    value.append(ch,start,length);
  }

}
