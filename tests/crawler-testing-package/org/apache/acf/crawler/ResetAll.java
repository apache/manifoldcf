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
package org.apache.acf.crawler;

import java.io.*;
import org.apache.acf.core.interfaces.*;
import org.apache.acf.agents.interfaces.*;
import org.apache.acf.crawler.interfaces.*;
import org.apache.acf.crawler.system.*;
import java.util.*;

/** This class is used during testing.
*/
public class ResetAll
{
        public static final String _rcsid = "@(#)$Id$";

        private ResetAll()
        {
        }

        public static void main(String[] args)
        {
                if (args.length != 0)
                {
                        System.err.println("Usage: ResetAll");
                        System.exit(1);
                }

                try
                {
                        ACF.initializeEnvironment();
                        IThreadContext tc = ThreadContextFactory.make();
                        IJobManager jobManager = JobManagerFactory.make(tc);
                        IRepositoryConnectionManager connMgr = RepositoryConnectionManagerFactory.make(tc);
                        IOutputConnectionManager outputMgr = OutputConnectionManagerFactory.make(tc);

                        // Get a list of the current active jobs
                        IJobDescription[] jobs = jobManager.getAllJobs();
                        int i = 0;
                        while (i < jobs.length)
                        {
                                IJobDescription desc = jobs[i++];
                                // Abort this job, if it is running
                                try
                                {
                                        jobManager.manualAbort(desc.getID());
                                }
                                catch (ACFException e)
                                {
                                        // This generally means that the job was not running
                                }
                        }
                        i = 0;
                        while (i < jobs.length)
                        {
                                IJobDescription desc = jobs[i++];
                                // Wait for this job to stop
                                while (true)
                                {
                                        JobStatus status = jobManager.getStatus(desc.getID());
                                        if (status != null)
                                        {
                                                int statusValue = status.getStatus();
                                                switch (statusValue)
                                                {
                                                case JobStatus.JOBSTATUS_NOTYETRUN:
                                                case JobStatus.JOBSTATUS_COMPLETED:
                                                case JobStatus.JOBSTATUS_ERROR:
                                                        break;
                                                default:
                                                        ACF.sleep(10000);
                                                        continue;
                                                }
                                        }
                                        break;
                                }
                        }

                        // Now, delete them all
                        i = 0;
                        while (i < jobs.length)
                        {
                                IJobDescription desc = jobs[i++];
                                try
                                {
                                        jobManager.deleteJob(desc.getID());
                                }
                                catch (ACFException e)
                                {
                                        // This usually means that the job is already being deleted
                                }
                        }

                        i = 0;
                        while (i < jobs.length)
                        {
                                IJobDescription desc = jobs[i++];
                                // Wait for this job to disappear
                                while (true)
                                {
                                        JobStatus status = jobManager.getStatus(desc.getID());
                                        if (status != null)
                                        {
                                                ACF.sleep(10000);
                                                continue;
                                        }
                                        break;
                                }
                        }

                        // Now, get a list of the repository connections
                        IRepositoryConnection[] connections = connMgr.getAllConnections();
                        i = 0;
                        while (i < connections.length)
                        {
                                connMgr.delete(connections[i++].getName());
                        }

                        // Finally, get rid of output connections
                        IOutputConnection[] outputs = outputMgr.getAllConnections();
                        i = 0;
                        while (i < outputs.length)
                        {
                                outputMgr.delete(outputs[i++].getName());
                        }

                        System.out.println("Reset complete");
                }
                catch (Exception e)
                {
                        e.printStackTrace();
                        System.exit(2);
                }
        }
                
}
