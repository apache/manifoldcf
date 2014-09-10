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
package org.apache.manifoldcf.crawler.tests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.ManifoldCF;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Connector class to be used by general integration tests that need documents */
public class TestingRepositoryConnector extends org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector
{
  public TestingRepositoryConnector()
  {
  }

  @Override
  public String addSeedDocuments(ISeedingActivity activities, Specification spec,
    String lastSeedVersion, long seedTime, int jobMode)
    throws ManifoldCFException, ServiceInterruption
  {
    String docCount = "3";
    for (int i = 0; i < spec.getChildCount(); i++)
    {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals("documentcount"))
        docCount = sn.getAttributeValue("count");
    }
    int count = Integer.parseInt(docCount);
    
    for (int i = 0; i < count; i++)
    {
      String doc = "test"+i+".txt";
      activities.addSeedDocument(doc,null);
    }
    return "";
  }
  
  /** Process a set of documents.
  * This is the method that should cause each document to be fetched, processed, and the results either added
  * to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
  * The document specification allows this class to filter what is done based on the job.
  * The connector will be connected before this method can be called.
  *@param documentIdentifiers is the set of document identifiers to process.
  *@param statuses are the currently-stored document versions for each document in the set of document identifiers
  * passed in above.
  *@param activities is the interface this method should use to queue up new document references
  * and ingest documents.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@param usesDefaultAuthority will be true only if the authority in use for these documents is the default one.
  */
  @Override
  public void processDocuments(String[] documentIdentifiers, IExistingVersions statuses, Specification spec,
    IProcessActivity activities, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption
  {
    for (int i = 0; i < documentIdentifiers.length; i++)
    {
      String documentIdentifier = documentIdentifiers[i];
      RepositoryDocument rd = new RepositoryDocument();
      byte[] bytes = documentIdentifier.getBytes(StandardCharsets.UTF_8);
      rd.setBinary(new ByteArrayInputStream(bytes),bytes.length);
      try
      {
        activities.ingestDocumentWithException(documentIdentifier,"","http://"+documentIdentifier,rd);
      }
      catch (IOException e)
      {
        throw new RuntimeException("Shouldn't be seeing IOException from binary array input stream: "+e.getMessage(),e);
      }
    }
  }

}
