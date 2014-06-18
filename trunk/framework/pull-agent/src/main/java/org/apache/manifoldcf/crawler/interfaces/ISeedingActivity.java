/* $Id: ISeedingActivity.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.agents.interfaces.*;

/** This interface abstracts from the activities that a seeding operation can do.
*
* See IProcessActivity for a description of the framework's prerequisite event model.  This interface too has support for that model.
*
*/
public interface ISeedingActivity extends IHistoryActivity, INamingActivity, IAbortActivity
{
  public static final String _rcsid = "@(#)$Id: ISeedingActivity.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Record a "seed" document identifier.
  * Seeds passed to this method will be loaded into the job's queue at the beginning of the
  * job's execution, and for continuous crawling jobs, periodically throughout the crawl.
  *
  * All documents passed to this method are placed on the "pending documents" list, and are marked as being seed
  * documents.  All pending documents will be processed to determine if they have changed or have been deleted.
  * It is not a big problem if the connector chooses to put more documents onto the pending list than are
  * strictly necessary; it is merely a question of overall work required.
  *
  * Note that it is always ok to send MORE documents rather than less to this method.
  *
  *@param documentIdentifier is the identifier of the document to add to the "pending" queue.
  *@param prereqEventNames is the list of prerequisite events required for this document, or null if none.
  */
  public void addSeedDocument(String documentIdentifier, String[] prereqEventNames)
    throws ManifoldCFException;

  /** Record a "seed" document identifier.
  * Seeds passed to this method will be loaded into the job's queue at the beginning of the
  * job's execution, and for continuous crawling jobs, periodically throughout the crawl.
  *
  * All documents passed to this method are placed on the "pending documents" list, and are marked as being seed
  * documents.  All pending documents will be processed to determine if they have changed or have been deleted.
  * It is not a big problem if the connector chooses to put more documents onto the pending list than are
  * strictly necessary; it is merely a question of overall work required.
  *
  * Note that it is always ok to send MORE documents rather than less to this method.
  *
  *@param documentIdentifier is the identifier of the document to add to the "pending" queue.
  */
  public void addSeedDocument(String documentIdentifier)
    throws ManifoldCFException;

  /** This method receives document identifiers that should be considered part of the seeds, but do not need to be
  * queued for processing at this time.  (This method is used to keep the hopcount tables up to date.)  It is
  * allowed to receive more identifiers than it strictly needs to, specifically identifiers that may have also been
  * sent to the getDocumentIdentifiers() method above.  However, the connector must constrain the identifiers
  * it sends by the document specification.
  * This method is only required to be called at all if the connector supports hopcount determination (which it
  * should signal by having more than zero legal relationship types returned by the getRelationshipTypes() method).
  *
  *@param documentIdentifier is the identifier of the document to consider as a seed, but not to put in the
  * "pending" queue.
  */
  public void addUnqueuedSeedDocument(String documentIdentifier)
    throws ManifoldCFException;

}
