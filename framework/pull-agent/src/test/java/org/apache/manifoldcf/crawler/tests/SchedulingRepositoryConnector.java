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
import java.util.*;

/** Connector class to be used by scheduling tests */
public class SchedulingRepositoryConnector extends org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector
{
  // Throttling: the next time a fetch is allowed, per bin.
  protected static final Map<String,Long> nextFetchTime = new HashMap<String,Long>();

  public SchedulingRepositoryConnector()
  {
  }

  @Override
  public String[] getBinNames(String documentIdentifier)
  {
    int index = documentIdentifier.indexOf("/");
    return new String[]{documentIdentifier.substring(0,index)};
  }

  @Override
  public String addSeedDocuments(ISeedingActivity activities, Specification spec,
    String lastSeedVersion, long seedTime, int jobMode)
    throws ManifoldCFException, ServiceInterruption
  {
    // A seed per domain
    String numberDomainsString = params.getParameter("numberDomains");
    if (numberDomainsString == null)
      numberDomainsString = "10";
    int numberDomains = Integer.parseInt(numberDomainsString);
    for (int i = 0; i < numberDomains; i++)
    {
      activities.addSeedDocument(Integer.toString(i)+"/",null);
    }
    System.out.println("Seeding completed at "+System.currentTimeMillis());
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
    String documentsPerSeedString = params.getParameter("documentsperseed");
    if (documentsPerSeedString == null)
      documentsPerSeedString = "200";
    int documentsPerSeed = Integer.parseInt(documentsPerSeedString);
    String timePerDocumentString = params.getParameter("timeperdocument");
    if (timePerDocumentString == null)
      timePerDocumentString = "500";
    int timePerDocument = Integer.parseInt(timePerDocumentString);

    // Seeds process instantly; other documents have a throttle based on the bin.
    for (int i = 0; i < documentIdentifiers.length; i++)
    {
      String documentIdentifier = documentIdentifiers[i];
      if (documentIdentifier.endsWith("/"))
      {
        System.out.println("Evaluating seed for "+documentIdentifier+" at "+System.currentTimeMillis());
        // Seed document.  Add the document ID's
        for (int j = 0; j < documentsPerSeed; j++)
        {
          activities.addDocumentReference(documentIdentifier + Integer.toString(j),documentIdentifier,null,
            null,null,null);
        }
        System.out.println("Done evaluating seed for "+documentIdentifier+" at "+System.currentTimeMillis());
      }
      else
      {
        System.out.println("Fetching "+documentIdentifier);
        // Find the bin
        String bin = documentIdentifier.substring(0,documentIdentifier.indexOf("/"));
        // For now they are all the same
        long binTimePerDocument = timePerDocument;
        long now = System.currentTimeMillis();
        long whenFetch;
        synchronized (nextFetchTime)
        {
          Long time = nextFetchTime.get(bin);
          if (time == null)
            whenFetch = now;
          else
            whenFetch = time.longValue();
          nextFetchTime.put(bin,new Long(whenFetch + binTimePerDocument));
        }
        if (whenFetch > now)
        {
          System.out.println("Waiting "+(whenFetch-now)+" to fetch "+documentIdentifier);
          try
          {
            ManifoldCF.sleep(whenFetch-now);
          }
          catch (InterruptedException e)
          {
            throw new ManifoldCFException(e.getMessage(),ManifoldCFException.INTERRUPTED);
          }
          System.out.println("Wait complete for "+documentIdentifier);
        }
      }
    }
  }

}
