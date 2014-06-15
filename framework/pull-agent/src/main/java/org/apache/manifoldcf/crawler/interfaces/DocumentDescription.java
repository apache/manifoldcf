/* $Id: DocumentDescription.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This class describes a document to be fetched and processes, plus the job it's being fetched as part of, and
* the time beyond which a failed fetch is considered to be a hard error.
* It is immutable.
*/
public class DocumentDescription
{
  public static final String _rcsid = "@(#)$Id: DocumentDescription.java 988245 2010-08-23 18:39:35Z kwright $";

  // Member variables
  protected final Long id;
  protected final Long jobID;
  protected final String documentIdentifierHash;
  protected final String documentIdentifier;
  protected final long failTime;
  protected final int failRetryCount;

  /** Constructor.
  *@param id is the record id.
  *@param jobID is the job identifier for a document to be processed.
  *@param documentIdentifierHash is the document identifier hash (primary key).
  *@param documentIdentifier is the document identifier.
  */
  public DocumentDescription(Long id, Long jobID, String documentIdentifierHash, String documentIdentifier)
  {
    this.id = id;
    this.jobID = jobID;
    this.documentIdentifierHash = documentIdentifierHash;
    this.documentIdentifier = documentIdentifier;
    this.failTime = -1L;
    this.failRetryCount = -1;
  }

  /** Constructor.
  *@param id is the record id.
  *@param jobID is the job identifier for a document to be processed.
  *@param documentIdentifierHash is the document identifier hash (primary key).
  *@param documentIdentifier is the document identifier.
  *@param failTime is the time beyond which a failed fetch will be considered a hard error.
  */
  public DocumentDescription(Long id, Long jobID, String documentIdentifierHash, String documentIdentifier, long failTime, int failRetryCount)
  {
    this.id = id;
    this.jobID = jobID;
    this.documentIdentifierHash = documentIdentifierHash;
    this.documentIdentifier = documentIdentifier;
    this.failTime = failTime;
    this.failRetryCount = failRetryCount;
  }

  /** Get the job queue id.
  *@return the id.
  */
  public Long getID()
  {
    return id;
  }

  /** Get the job identifier.
  *@return the job id.
  */
  public Long getJobID()
  {
    return jobID;
  }

  /** Get document identifier hash (primary key).
  */
  public String getDocumentIdentifierHash()
  {
    return documentIdentifierHash;
  }

  /** Get document identifier.
  *@return the identifier.
  */
  public String getDocumentIdentifier()
  {
    return documentIdentifier;
  }

  /** Get the hard fail time.
  *@return the fail time in ms since epoch, or -1L if none.
  */
  public long getFailTime()
  {
    return failTime;
  }

  /** Get the hard fail retry count.
  *@return the fail retry count, or -1 if none.
  */
  public int getFailRetryCount()
  {
    return failRetryCount;
  }

}
