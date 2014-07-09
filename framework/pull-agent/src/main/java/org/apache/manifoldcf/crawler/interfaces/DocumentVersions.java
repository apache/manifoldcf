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
* This structure does two things: (1) describe the version string for child documents of a given primary document, and (2) list the
* current set of child documents.
* For each parent document, this API class can specify:
* - that the parent document should be refetched, regardless
* - the default version string for all child documents of a parent document
* - the actual version string for each child document of a parent document
* - the list of child documents for a given parent document
* 
*/
public class DocumentVersions
{
  public static final String _rcsid = "@(#)$Id$";

  protected final Map<String,VersionContext> documentVersions = new HashMap<String,VersionContext>();
  protected final Map<String,Map<String,VersionContext>> childDocumentVersions = new HashMap<String,Map<String,VersionContext>>();
  protected final Map<String,Set<String>> childDocumentIDs = new HashMap<String,Set<String>>();
  protected final Set<String> alwaysRefetch = new HashSet<String>();
  
  /** Constructor */
  public DocumentVersions()
  {
  }
  
  /** Set a default document version.
  *@param documentIdentifier is the parent document identifier.
  *@param documentVersion is the document version.
  */
  public void setDocumentVersion(String documentIdentifier, VersionContext documentVersion)
  {
    documentVersions.put(documentIdentifier,documentVersion);
  }

  /** Set a child document version.
  *@param documentIdentifier is the parent document identifier.
  *@param childIdentifier is the child document identifier.  Use empty string for the primary document.
  *@param documentVersion is the document version.
  */
  public void setChildDocumentVersion(String documentIdentifier, String childIdentifier, VersionContext documentVersion)
  {
    Map<String,VersionContext> map = childDocumentVersions.get(documentIdentifier);
    if (map == null)
    {
      map = new HashMap<String,VersionContext>();
      childDocumentVersions.put(documentIdentifier,map);
    }
    map.put(childIdentifier,documentVersion);
    noteChildDocument(documentIdentifier,childIdentifier);
  }
  
  /** Note the existence of a child document.
  *@param documentIdentifier is the parent document identifier.
  *@param childIdentifier is the child document identifier.
  */
  public void noteChildDocument(String documentIdentifier, String childIdentifier)
  {
    Set<String> set = childDocumentIDs.get(documentIdentifier);
    if (set == null)
    {
      set = new HashSet<String>();
      childDocumentIDs.put(documentIdentifier,set);
    }
    set.add(childIdentifier);
  }
  
  /** Signal to always refetch document.
  *@param documentIdentifier is the parent document identifier.
  */
  public void alwaysRefetch(String documentIdentifier)
  {
    alwaysRefetch.add(documentIdentifier);
  }
  
  /** Get the parent document version, if any.
  *@param documentIdentifier is the document identifier.
  *@return the document version, if any.  Null indicates that no such document was found.
  */
  public VersionContext getDocumentVersion(String documentIdentifier)
  {
    return getChildDocumentVersion(documentIdentifier,"");
  }
  
  /** Get the child document version, if any.
  *@param documentIdentifier is the document identifier.
  *@param childIdentifier is the child document identifier.  Use empty string for the primary document.
  *@return the document version, if any.  Null indicates that no such document was found.
  */
  public VersionContext getChildDocumentVersion(String documentIdentifier, String childIdentifier)
  {
    Map<String,VersionContext> map = childDocumentVersions.get(documentIdentifier);
    if (map != null)
    {
      VersionContext rval = map.get(childIdentifier);
      if (rval != null)
        return rval;
    }
    return documentVersions.get(documentIdentifier);
  }
  
  /** Get a count of child document identifiers.
  *@param documentIdentifier is the document identifier.
  *@return the count of children.
  */
  public int countChildren(String documentIdentifier)
  {
    Set<String> children = childDocumentIDs.get(documentIdentifier);
    if (children == null)
      return 0;
    return children.size();
  }
  
  /** List the child documents for a document identifier.
  *@param documentIdentifier is the document identifier.
  *@return an iterator over the set of child ID's, or null if the document identifier doesn't have any children.
  */
  public Iterator<String> childIterator(String documentIdentifier)
  {
    Set<String> children = childDocumentIDs.get(documentIdentifier);
    if (children == null)
      return null;
    return children.iterator();
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
