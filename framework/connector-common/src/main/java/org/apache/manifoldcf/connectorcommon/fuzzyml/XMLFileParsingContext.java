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

/** An instance of this class represents a parsing context within a node.  Data is written to the supplied file in utf-8 format.
*/
public class XMLFileParsingContext extends XMLOutputStreamParsingContext
{
  /** The output file */
  protected File outputFile;

  /** Full constructor.  Used for individual tags. */
  public XMLFileParsingContext(XMLFuzzyHierarchicalParseState theStream, String namespace, String localname, String qname, Map<String,String> theseAttributes, File f)
    throws ManifoldCFException, FileNotFoundException
  {
    // Construct an appropriate writer
    super(theStream,namespace,localname,qname,theseAttributes,new FileOutputStream(f));
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
