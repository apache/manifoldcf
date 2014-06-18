/* $Id: XMLFileContext.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

import org.apache.manifoldcf.core.interfaces.*;

/** An instance of this class represents a parsing context within a node.  Data is written to the supplied file in utf-8 format.
*/
public class XMLFileContext extends XMLOutputStreamContext
{
  /** The output file */
  protected File outputFile;

  /** Full constructor.  Used for individual tags. */
  public XMLFileContext(XMLStream theStream, String namespaceURI, String localname, String qname, Attributes theseAttributes, File f)
    throws FileNotFoundException
  {
    // Construct an appropriate writer
    super(theStream,namespaceURI,localname,qname,theseAttributes,new FileOutputStream(f));
    // Save the file
    outputFile = f;
  }

  /** Get file object, flushing it, closing it, and clearing it.  (This prevents the file from being deleted during cleanup of this context.) */
  public File getCompletedFile()
    throws ManifoldCFException
  {
    flush();
    close();
    File rval = outputFile;
    outputFile = null;
    return rval;
  }

  /** Cleanup whatever is left over */
  public void tagCleanup()
    throws ManifoldCFException
  {
    if (outputFile != null)
    {
      close();
      outputFile.delete();
      outputFile = null;
    }
  }
}
