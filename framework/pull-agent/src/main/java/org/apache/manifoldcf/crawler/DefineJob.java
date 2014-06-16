/* $Id: DefineJob.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler;

import java.io.*;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.*;
import java.util.*;

/** This class is used during testing.
*/
public class DefineJob
{
  public static final String _rcsid = "@(#)$Id: DefineJob.java 988245 2010-08-23 18:39:35Z kwright $";

  private DefineJob()
  {
  }

  public static void main(String[] args)
  {
    if (args.length != 13)
    {
      System.err.println("Usage: DefineJob <description> <connection_name> <type> <start_method> <hopcount_method> <recrawl_interval> <expiration_interval> <reseed_interval> <job_priority> <hop_filters> <filespec_xml>");
      System.err.println("<type> is one of: continuous or specified");
      System.err.println("<start_method> is one of: windowbegin, windowinside, disable");
      System.err.println("<hopcount_method> is one of: accurate, nodelete, neverdelete");
      System.err.println("<recrawl_interval> is the default document recrawl interval in minutes");
      System.err.println("<expiration_interval> is the default document expiration interval in minutes");
      System.err.println("<reseed_interval> is the default document reseed interval in minutes");
      System.err.println("<job_priority> is the job priority (and integer between 0 and 10)");
      System.err.println("<hop_filters> is a comma-separated list of tuples, of the form 'linktype=maxhops'");
      System.err.println("<filespec_xml> is the document specification XML, its form dependent on the connection type");
      System.exit(-1);
    }

    String description = args[0];
    String connectionName = args[1];
    String typeString = args[2];
    String startString = args[3];
    String hopcountString = args[4];
    String recrawlInterval = args[5];
    String expirationInterval = args[6];
    String reseedInterval = args[7];
    String jobPriority = args[8];
    String hopFilters = args[9];
    String filespecXML = args[10];

    try
    {
      IThreadContext tc = ThreadContextFactory.make();
      ManifoldCF.initializeEnvironment(tc);
      IJobManager jobManager = JobManagerFactory.make(tc);
      IJobDescription desc = jobManager.createJob();

      desc.setDescription(description);
      desc.setConnectionName(connectionName);

      if (typeString.equals("continuous"))
        desc.setType(IJobDescription.TYPE_CONTINUOUS);
      else if (typeString.equals("specified"))
        desc.setType(IJobDescription.TYPE_SPECIFIED);
      else
        throw new ManifoldCFException("Unknown type: '"+typeString+"'");
      if (startString.equals("windowbegin"))
        desc.setStartMethod(IJobDescription.START_WINDOWBEGIN);
      else if (startString.equals("windowinside"))
        desc.setStartMethod(IJobDescription.START_WINDOWINSIDE);
      else if (startString.equals("disable"))
        desc.setStartMethod(IJobDescription.START_DISABLE);
      else
        throw new ManifoldCFException("Unknown start method: '"+startString+"'");

      if (hopcountString.equals("accurate"))
        desc.setHopcountMode(IJobDescription.HOPCOUNT_ACCURATE);
      else if (hopcountString.equals("nodelete"))
        desc.setHopcountMode(IJobDescription.HOPCOUNT_NODELETE);
      else if (hopcountString.equals("neverdelete"))
        desc.setHopcountMode(IJobDescription.HOPCOUNT_NEVERDELETE);
      else
        throw new ManifoldCFException("Unknown hopcount mode: '"+hopcountString+"'");
      
      if (recrawlInterval.length() > 0)
        desc.setInterval(new Long(recrawlInterval));
      if (expirationInterval.length() > 0)
        desc.setExpiration(new Long(expirationInterval));
      if (reseedInterval.length() > 0)
        desc.setReseedInterval(new Long(reseedInterval));
      desc.setPriority(Integer.parseInt(jobPriority));
      
      String[] hopFilterSet = hopFilters.split(",");
      int i = 0;
      while (i < hopFilterSet.length)
      {
        String hopFilter = hopFilterSet[i++];
        if (hopFilter != null && hopFilter.length() > 0)
        {
            String[] stuff = hopFilter.trim().split("=");
            if (stuff != null && stuff.length == 2)
          desc.addHopCountFilter(stuff[0],((stuff[1].length()>0)?new Long(stuff[1]):null));
        }
      }
      
      desc.getSpecification().fromXML(filespecXML);
      
      // Now, save
      jobManager.save(desc);

      System.out.print(desc.getID().toString());
    }
    catch (Exception e)
    {
      e.printStackTrace();
      System.exit(-2);
    }
  }


}
