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
package org.apache.manifoldcf.agents.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;

/** This object functions as a key describing a unique output, consisting of:
* - a document class
* - a document ID hash
* - an output connection name
*/
public class OutputKey
{
  public static final String _rcsid = "@(#)$Id$";

  protected final String documentClass;
  protected final String documentIDHash;
  protected final String outputConnectionName;
  
  /** Constructor */
  public OutputKey(String documentClass, String documentIDHash, String outputConnectionName)
  {
    // Identifying information
    this.documentClass = documentClass;
    this.documentIDHash = documentIDHash;
    this.outputConnectionName = outputConnectionName;
  }

  /** Get the document class */
  public String getDocumentClass()
  {
    return documentClass;
  }
  
  /** Get the document ID hash */
  public String getDocumentIDHash()
  {
    return documentIDHash;
  }
  
  /** Get the output connection name */
  public String getOutputConnectionName()
  {
    return outputConnectionName;
  }
  
  public int hashCode()
  {
    return documentClass.hashCode() + documentIDHash.hashCode() + outputConnectionName.hashCode();
  }
  
  public boolean equals(Object o)
  {
    if (!(o instanceof OutputKey))
      return false;
    OutputKey dis = (OutputKey)o;
    return dis.documentClass.equals(documentClass) &&
      dis.documentIDHash.equals(documentIDHash) &&
      dis.outputConnectionName.equals(outputConnectionName);
  }
      
}
