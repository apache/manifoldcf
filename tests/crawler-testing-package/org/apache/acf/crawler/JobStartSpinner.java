/* $Id: JobStartSpinner.java 921329 2010-03-10 12:44:20Z kwright $ */

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
public class JobStartSpinner
{
        public static final String _rcsid = "@(#)$Id: JobStartSpinner.java 921329 2010-03-10 12:44:20Z kwright $";

        private JobStartSpinner()
        {
        }

        // Add: throttle, priority, recrawl interval

        public static void main(String[] args)
        {
                if (args.length != 1)
                {
                        System.err.println("Usage: JobStartSpinner <configuration_filename>");
                        System.exit(1);
                }

                String importFilename = args[0];
                
                try
                {
                        LCF.initializeEnvironment();
                        // Create the import thread
                        Thread importThread = new ImportThread(1000,importFilename);
                        // Create the job start thread
                        Thread jobstartThread = new JobStartThread(1000);
                        // Start both threads
                        importThread.start();
                        jobstartThread.start();
                        // Wait for both to finish
                        while (true)
                        {
                                if (importThread.isAlive())
                                        Thread.sleep(10000L);
                                else
                                        break;
                        }
                        while (true)
                        {
                                if (jobstartThread.isAlive())
                                        Thread.sleep(10000L);
                                else
                                        break;
                        }
                }
                catch (Exception e)
                {
                        e.printStackTrace();
                        System.exit(2);
                }
        }
            
        protected static class ImportThread extends Thread
        {
                protected int repeatCount;
                protected String fileName;
            
                public ImportThread(int repeatCount, String fileName)
                {
                        super();
                        setName("import thread");
                        setDaemon(true);
                        this.repeatCount = repeatCount;
                        this.fileName = fileName;
                }
                
                public void run()
                {
                        try
                        {
                                IThreadContext tc = ThreadContextFactory.make();
                                // Repeat in a hard loop, hoping for a hang
                                int i = 0;
                                while (i < repeatCount)
                                {
                                        LCF.importConfiguration(tc,fileName);
                                        if ((i % 100) == 0)
                                                System.out.println("Configuration import #"+Integer.toString(i)+" succeeded.");
                                        i++;
                                }
                        }
                        catch (Exception e)
                        {
                                e.printStackTrace();
                                System.exit(-1);
                        }
                }

        }
        
        protected static class JobStartThread extends Thread
        {
                protected int repeatCount;
            
                public JobStartThread(int repeatCount)
                {
                        super();
                        setName("job start thread");
                        setDaemon(true);
                        this.repeatCount = repeatCount;
                }
                
                public void run()
                {
                        try
                        {
                                IThreadContext tc = ThreadContextFactory.make();
                                IJobManager jobManager = JobManagerFactory.make(tc);
                            
                                // Repeat in a hard loop, hoping for a hang
                                int i = 0;
                                while (i < repeatCount)
                                {
                                        ArrayList list = new ArrayList();
                                        long currentTime = System.currentTimeMillis();
                                        jobManager.startJobs(currentTime,list);
                                        list.clear();
                                        jobManager.waitJobs(currentTime,list);
                                        if ((i % 100) == 0)
                                                System.out.println("Job start #"+Integer.toString(i)+" succeeded.");
                                        i++;
                                }
                        }
                        catch (Exception e)
                        {
                                e.printStackTrace();
                                System.exit(-2);
                        }
                }

        }

}
