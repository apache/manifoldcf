/* $Id: XMLOutputStreamContext.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.manifoldcf.core.interfaces.*;

/** An instance of this class represents a parsing context within a node.  Data is written to the supplied output stream in utf-8 format.
*/
public class XMLOutputStreamContext extends XMLWriterContext
{
  /** The writer */
  protected OutputStream outputStream;

  /** Full constructor.  Used for individual tags. */
  public XMLOutputStreamContext(XMLStream theStream, String namespaceURI, String localname, String qname, Attributes theseAttributes, OutputStream os)
  {
    // Construct an appropriate writer
    super(theStream,namespaceURI,localname,qname,theseAttributes,new OutputStreamWriter(os, StandardCharsets.UTF_8));
    // Save the stream
    outputStream = os;
  }

  /** Flush the data to the underlying output stream */
  public void flush()
    throws ManifoldCFException
  {
    try
    {
      if (outputStream != null)
      {
        // Do the base class first - this flushes the Writer
        super.flush();
        outputStream.flush();
      }
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

  /** Close the underlying stream. */
  public void close()
    throws ManifoldCFException
  {
    // Now, close.
    try
    {
      if (outputStream != null)
      {
        outputStream.close();
        outputStream = null;
      }
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

}
