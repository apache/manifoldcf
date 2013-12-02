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
  public void addSeedDocuments(ISeedingActivity activities, DocumentSpecification spec,
    long startTime, long endTime, int jobMode)
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
  }
  
  @Override
  public String[] getDocumentVersions(String[] documentIdentifiers, String[] oldVersions, IVersionActivity activities,
    DocumentSpecification spec, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption
  {
    String[] rval = new String[documentIdentifiers.length];
    for (int i = 0; i < rval.length; i++)
    {
      rval[i] = "";
    }
    return rval;
  }

  @Override
  public void processDocuments(String[] documentIdentifiers, String[] versions, IProcessActivity activities,
    DocumentSpecification spec, boolean[] scanOnly, int jobMode)
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
        if (!scanOnly[i])
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

}
