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

/** This object contains statuses for the primary document and all component documents.
*/
public class DocumentIngestStatusSet
{
  public static final String _rcsid = "@(#)$Id$";

  protected DocumentIngestStatus primary = null;
  protected final Map<String,DocumentIngestStatus> components = new HashMap<String,DocumentIngestStatus>();
  
  /** Constructor */
  public DocumentIngestStatusSet()
  {
  }
  
  /** Add document status.
  *@param componentHash is the component identifier hash, or null.
  *@param status is the document ingest status.
  */
  public void addDocumentStatus(String componentHash, DocumentIngestStatus status)
  {
    if (componentHash == null)
      primary = status;
    else
      components.put(componentHash,status);
  }
  
  /** Get primary status.
  *@return the primary status.
  */
  public DocumentIngestStatus getPrimary()
  {
    return primary;
  }
  
  /** Get component status.
  *@param componentHash is the component identifier hash, or null.
  *@return the component status.
  */
  public DocumentIngestStatus getComponent(String componentHash)
  {
    if (componentHash == null)
      return primary;
    return components.get(componentHash);
  }
  
  /** Iterate over components.
  *@return an iterator over component hashes.
  */
  public Iterator<String> componentIterator()
  {
    return components.keySet().iterator();
  }
}
