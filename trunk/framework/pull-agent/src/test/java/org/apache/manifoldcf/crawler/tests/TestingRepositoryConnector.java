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
  public void addSeedDocuments(ISeedingActivity activities, DocumentSpecification spec,
    long startTime, long endTime, int jobMode)
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
    for (int i = 0; i < documentIdentifiers.length; i++)
    {
      String documentIdentifier = documentIdentifiers[i];
      String version = versions[i];
      if (!scanOnly[i])
      {
        RepositoryDocument rd = new RepositoryDocument();
        byte[] bytes = documentIdentifier.getBytes(StandardCharsets.UTF_8);
        rd.setBinary(new ByteArrayInputStream(bytes),bytes.length);
        activities.ingestDocument(documentIdentifier,version,"http://"+documentIdentifier,rd);
      }
    }
  }

}
