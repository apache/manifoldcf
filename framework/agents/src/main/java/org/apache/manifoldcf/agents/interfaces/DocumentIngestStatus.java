/* $Id: DocumentIngestStatus.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This object contains the complete status for a document, specifically:
* - version string
* - output version string
* - authority name
* - parameter version string
*/
public class DocumentIngestStatus
{
  public static final String _rcsid = "@(#)$Id: DocumentIngestStatus.java 988245 2010-08-23 18:39:35Z kwright $";

  protected final String documentVersionString;
  protected final String transformationVersionString;
  protected final String outputVersionString;
  protected final String documentAuthorityNameString;

  /** Constructor */
  public DocumentIngestStatus(
    String documentVersionString,
    String transformationVersionString, String outputVersionString,
    String documentAuthorityNameString)
  {
    // Looked-up information
    this.documentVersionString = documentVersionString;
    this.transformationVersionString = transformationVersionString;
    this.outputVersionString = outputVersionString;
    this.documentAuthorityNameString = documentAuthorityNameString;
  }

  /** Get the document version */
  public String getDocumentVersion()
  {
    return documentVersionString;
  }

  /** Get the transformation version strings */
  public String getTransformationVersion()
  {
    return transformationVersionString;
  }
  
  /** Get the output version */
  public String getOutputVersion()
  {
    return outputVersionString;
  }

  /** Get the document authority name string */
  public String getDocumentAuthorityNameString()
  {
    return documentAuthorityNameString;
  }
  
}
