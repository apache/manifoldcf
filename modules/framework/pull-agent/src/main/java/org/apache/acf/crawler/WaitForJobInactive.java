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
import org.apache.acf.crawler.interfaces.*;
import org.apache.acf.crawler.system.*;
import java.util.*;

/** This class is used during testing.
*/
public class WaitForJobInactive
{
        public static final String _rcsid = "@(#)$Id$";

        private WaitForJobInactive()
        {
        }

        // Add: throttle, priority, recrawl interval

        public static void main(String[] args)
        {
                if (args.length != 1)
                {
                        System.err.println("Usage: WaitForJobInactive <jobid>");
                        System.exit(1);
                }

                String jobID = args[0];


                try
                {
                        ACF.initializeEnvironment();
                        IThreadContext tc = ThreadContextFactory.make();
                        IJobManager jobManager = JobManagerFactory.make(tc);

                        while (true)
                        {
                                JobStatus status = jobManager.getStatus(new Long(jobID));
                                if (status == null)
                                        throw new ACFException("No such job: '"+jobID+"'");
                                int statusValue = status.getStatus();
                                switch (statusValue)
                                {
                                case JobStatus.JOBSTATUS_NOTYETRUN:
                                        System.out.println("Never run");
                                        break;
                                case JobStatus.JOBSTATUS_COMPLETED:
                                        System.out.println("OK");
                                        break;
                                case JobStatus.JOBSTATUS_ERROR:
                                        System.out.println("Error: "+status.getErrorText());
                                        break;
                                default:
                                        ACF.sleep(10000);
                                        continue;
                                }
                                break;
                        }

                }
                catch (Exception e)
                {
                        e.printStackTrace();
                        System.exit(2);
                }
        }
                
}
