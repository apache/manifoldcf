/* $Id: QueuedDocument.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.system;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;

import java.util.*;

/** This class represents a document that will be placed on the document queue, and will be
* processed by a worker thread.
* The reason that DocumentDescription by itself is not used has to do with the fact that
* a good deal more information about the document must be obtained in order to find the
* last version ingested (which must be done in bulk, for performance reasons).  Since we
* are finding everything anyway, it makes sense to put what we have in a structure so
* that the worker threads don't need to repeat what the stuffer thread did.
*/
public class QueuedDocument
{
  public static final String _rcsid = "@(#)$Id: QueuedDocument.java 988245 2010-08-23 18:39:35Z kwright $";

  /** The document description. */
  protected final DocumentDescription documentDescription;
  /** The last ingested status, null meaning "never ingested". */
  protected final Map<String,DocumentIngestStatusSet> lastIngestedStatus;
  /** The binnames for the document, according to the connector */
  protected final String[] binNames;
  /** This flag indicates whether the document has been processed or not. */
  protected boolean wasProcessed = false;

  /** Constructor.
  *@param documentDescription is the document description.
  *@param lastIngestedStatus is the document's last ingested status.
  *@param binNames are the bins associated with the document.
  */
  public QueuedDocument(DocumentDescription documentDescription, Map<String,DocumentIngestStatusSet> lastIngestedStatus, String[] binNames)
  {
    this.documentDescription = documentDescription;
    this.lastIngestedStatus = lastIngestedStatus;
    this.binNames = binNames;
  }

  /** Get the document description.
  *@return the document description.
  */
  public DocumentDescription getDocumentDescription()
  {
    return documentDescription;
  }

  /** Get the last ingested status.
  *@param outputConnectionName is the name of the output connection.
  *@return the last ingested status for that output, or null if not found.
  */
  public DocumentIngestStatusSet getLastIngestedStatus(String outputConnectionName)
  {
    if (lastIngestedStatus == null)
      return null;
    return lastIngestedStatus.get(outputConnectionName);
  }

  /** Return true if there are *any* last ingested records.
  *@return true if any last ingested records exist.
  */
  public boolean anyLastIngestedRecords()
  {
    if (lastIngestedStatus == null)
      return false;
    return lastIngestedStatus.size() > 0;
  }
  
  /** Get the bin names for this document */
  public String[] getBinNames()
  {
    return binNames;
  }

  /** Check if document has been processed yet.
  *@return true if processed, false if not.
  */
  public boolean wasProcessed()
  {
    return wasProcessed;
  }

  /** Note that the document was processed in some way.
  */
  public void setProcessed()
  {
    wasProcessed = true;
  }

}

