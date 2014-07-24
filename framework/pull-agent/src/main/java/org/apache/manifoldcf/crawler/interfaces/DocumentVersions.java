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
package org.apache.manifoldcf.crawler.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;

/** This class represents a set of document versions, organized by document identifier.
* It's part of the IRepositoryConnector API.
*/
public class DocumentVersions
{
  public static final String _rcsid = "@(#)$Id$";

  protected final Map<String,VersionContext> documentVersions = new HashMap<String,VersionContext>();
  protected final Set<String> alwaysRefetch = new HashSet<String>();
  
  /** Constructor */
  public DocumentVersions()
  {
  }
  
  /** Set a non-special document version.
  *@param documentIdentifier is the document identifier.
  *@param documentVersion is the document version.
  */
  public void setDocumentVersion(String documentIdentifier, VersionContext documentVersion)
  {
    documentVersions.put(documentIdentifier,documentVersion);
  }
  
  /** Signal to always refetch document.
  *@param documentIdentifier is the document identifier.
  */
  public void alwaysRefetch(String documentIdentifier)
  {
    alwaysRefetch.add(documentIdentifier);
  }
  
  /** Get the document version, if any.
  *@param documentIdentifier is the document identifier.
  *@return the document version, if any.  Null indicates that no such document was found.
  */
  public VersionContext getDocumentVersion(String documentIdentifier)
  {
    return documentVersions.get(documentIdentifier);
  }
  
  /** Check whether we should always refetch a specified document.
  *@param documentIdentifier is the document identifier.
  *@return true if we are directed to always refetch.  False will be returned by default.
  */
  public boolean isAlwaysRefetch(String documentIdentifier)
  {
    return alwaysRefetch.contains(documentIdentifier);
  }
  
}
