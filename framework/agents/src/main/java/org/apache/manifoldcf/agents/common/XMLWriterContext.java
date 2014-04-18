/* $Id: XMLWriterContext.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import java.io.Writer;
import java.io.IOException;
import java.io.InterruptedIOException;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;

/** An instance of this class represents a parsing context within a node.  Data is written to the supplied writer.
*/
public class XMLWriterContext extends XMLContext
{
  /** The writer */
  protected Writer theWriter;

  /** Full constructor.  Used for individual tags. */
  public XMLWriterContext(XMLStream theStream, String namespaceURI, String localname, String qname, Attributes theseAttributes, Writer writer)
  {
    super(theStream,namespaceURI,localname,qname,theseAttributes);
    theWriter = writer;
  }

  /** Flush the data to the underlying output stream */
  public void flush()
    throws ManifoldCFException
  {
    try
    {
      // Flush the data to the underlying output stream
      theWriter.flush();
    }
    catch (java.net.SocketTimeoutException e)
    {
      throw new ManifoldCFException("Socket timeout exception: "+e.getMessage(),e);
    }
    catch (InterruptedIOException e)
    {
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO exception: "+e.getMessage(),e);
    }
  }

  /** This method is meant to be extended by classes that extend this class */
  protected void tagContents(char[] ch, int start, int length)
    throws ManifoldCFException
  {
    try
    {
      escapeCharData(ch,start,length,theWriter);
    }
    catch (java.net.SocketTimeoutException e)
    {
      throw new ManifoldCFException("Socket timeout exception: "+e.getMessage(),e);
    }
    catch (InterruptedIOException e)
    {
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO exception: "+e.getMessage(),e);
    }
  }

  /** Start a child tag.
  * The XMLWriterContext accepts all subtags in their text form.
  */
  protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
    throws ManifoldCFException, ServiceInterruption
  {
    // First, write out the tag text.  We strip off the namespace.
    try
    {
      theWriter.write("<"+localName);
      int length = atts.getLength();
      int i = 0;
      while (i < length)
      {
        theWriter.write(" ");
        theWriter.write(atts.getLocalName(i));
        theWriter.write("=\"");
        theWriter.write(escapeAttribute(atts.getValue(i)));
        theWriter.write("\"");
        i++;
      }
      theWriter.write(">");
    }
    catch (java.net.SocketTimeoutException e)
    {
      throw new ManifoldCFException("Socket timeout exception: "+e.getMessage(),e);
    }
    catch (InterruptedIOException e)
    {
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO exception: "+e.getMessage(),e);
    }
    // Now, start a new context which is also a writer context.
    super.beginTag(namespaceURI,localName,qName,atts);
    return new XMLWriterContext(theStream,namespaceURI,localName,qName,atts,theWriter);
  }

  /** End a child tag.
  * The XMLWriterContext accepts all subtags in their text form.
  */
  protected void endTag()
    throws ManifoldCFException, ServiceInterruption
  {
    // First, write out the tag text.  We strip off the namespace.
    try
    {
      XMLContext context = theStream.getContext();
      String tagName = context.getLocalname();

      theWriter.write("</"+tagName+">");
    }
    catch (java.net.SocketTimeoutException e)
    {
      throw new ManifoldCFException("Socket timeout exception: "+e.getMessage(),e);
    }
    catch (InterruptedIOException e)
    {
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO exception: "+e.getMessage(),e);
    }
    super.endTag();
  }

  /** Convert a string to a value that's safe to include inside an attribute value */
  protected static String escapeAttribute(String value)
  {
    StringBuilder rval = new StringBuilder();
    int i = 0;
    while (i < value.length())
    {
      char x = value.charAt(i++);
      if (x == '\'' || x == '"' || x == '<' || x == '>' || x == '&'|| (x < ' ' && x >= 0))
      {
        rval.append("&#").append(Integer.toString((int)x)).append(";");
      }
      else
        rval.append(x);
    }
    return rval.toString();
  }
  
  /** Escapes sequence of characters to output writer */
  protected static void escapeCharData(char [] chars, int start, int length, Writer out) throws IOException
  {
    for (int i=start; i<start+length; i++) {
      char x = chars[i];
      if (x == '<' || x == '>' || x == '&'|| (x < ' ' && x >= 0))
      {
        StringBuilder rval = new StringBuilder();
        rval.append("&#").append(Integer.toString((int)x)).append(";");
        out.write(rval.toString());
        continue;
      }
      out.write(x);
    }
  }
}
