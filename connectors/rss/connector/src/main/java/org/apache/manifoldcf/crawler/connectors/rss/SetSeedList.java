/* $Id: SetSeedList.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.rss;

import java.io.*;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** This class is used to set the seed list for a specified RSS job.
*/
public class SetSeedList
{
  public static final String _rcsid = "@(#)$Id: SetSeedList.java 988245 2010-08-23 18:39:35Z kwright $";

  private SetSeedList()
  {
  }

  public static void main(String[] args)
  {
    if (args.length != 1)
    {
      System.err.println("Usage: SetSeedList <job_id>");
      System.err.println("(Reads a set of urls from stdin)");
      System.exit(-1);
    }

    String jobString = args[0];

    try
    {
      IThreadContext tc = ThreadContextFactory.make();
      ManifoldCF.initializeEnvironment(tc);
      IJobManager jobManager = JobManagerFactory.make(tc);
      IJobDescription desc = jobManager.load(new Long(jobString));

      // Edit the job specification
      Specification ds = desc.getSpecification();

      // Delete all url specs first
      int i = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("feed"))
          ds.removeChild(i);
        else
          i++;
      }

      java.io.Reader str = new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8);
      try
      {
        java.io.BufferedReader is = new java.io.BufferedReader(str);
        try
        {
          while (true)
          {
            String nextString = is.readLine();
            if (nextString == null)
              break;
            if (nextString.length() == 0)
              continue;
            SpecificationNode node = new SpecificationNode("feed");
            node.setAttribute("url",nextString);
            ds.addChild(ds.getChildCount(),node);
          }
        }
        finally
        {
          is.close();
        }
      }
      finally
      {
        str.close();
      }

      // Now, save
      jobManager.save(desc);
    }
    catch (Exception e)
    {
      e.printStackTrace();
      System.exit(-2);
    }
  }

}
