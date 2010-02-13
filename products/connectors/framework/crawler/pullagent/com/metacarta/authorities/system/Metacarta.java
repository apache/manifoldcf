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
package com.metacarta.authorities.system;

import com.metacarta.core.interfaces.*;
import com.metacarta.authorities.interfaces.*;
import java.io.*;
import java.util.*;

public class Metacarta extends com.metacarta.core.system.Metacarta
{
        protected static boolean needInit = true;

        // Threads
        protected static IdleCleanupThread idleCleanupThread = null;
        protected static AuthCheckThread[] authCheckThreads = null;

        // Number of auth check threads
        protected static int numAuthCheckThreads = 0;

        protected static final String authCheckThreadCountProperty = "com.metacarta.authorityservice.threads";

        // Request queue
        protected static RequestQueue requestQueue = null;

        /** Initialize environment.
        */
        public static synchronized void initializeEnvironment()
        {
                if (needInit == false)
                        return;

                com.metacarta.core.system.Metacarta.initializeEnvironment();
                Logging.initializeLoggers();
                Logging.setLogLevels();
                needInit = false;

        }


        /** Install all the authority manager system tables.
        *@param threadcontext is the thread context.
        */
        public static void installSystemTables(IThreadContext threadcontext)
                throws MetacartaException
        {
                IDBInterface mainDatabase = DBInterfaceFactory.make(threadcontext,
                        Metacarta.getMasterDatabaseName(),
                        Metacarta.getMasterDatabaseUsername(),
                        Metacarta.getMasterDatabasePassword());

                IAuthorityConnectorManager connMgr = AuthorityConnectorManagerFactory.make(threadcontext);
                IAuthorityConnectionManager authConnMgr = AuthorityConnectionManagerFactory.make(threadcontext);

                mainDatabase.beginTransaction();
                try	
                {
                        connMgr.install();
                        authConnMgr.install();
                }
                catch (MetacartaException e)
                {
                        mainDatabase.signalRollback();
                        throw e;
                }
                catch (Error e)
                {
                        mainDatabase.signalRollback();
                        throw e;
                }
                finally
                {
                        mainDatabase.endTransaction();
                }

        }

        /** Uninstall all the authority manager system tables.
        *@param threadcontext is the thread context.
        */
        public static void deinstallSystemTables(IThreadContext threadcontext)
                throws MetacartaException
        {
                IDBInterface mainDatabase = DBInterfaceFactory.make(threadcontext,
                        Metacarta.getMasterDatabaseName(),
                        Metacarta.getMasterDatabaseUsername(),
                        Metacarta.getMasterDatabasePassword());

                MetacartaException se = null;

                IAuthorityConnectorManager connMgr = AuthorityConnectorManagerFactory.make(threadcontext);
                IAuthorityConnectionManager authConnMgr = AuthorityConnectionManagerFactory.make(threadcontext);

                mainDatabase.beginTransaction();
                try
                {
                        authConnMgr.deinstall();
                        connMgr.deinstall();
                }
                catch (MetacartaException e)
                {
                        mainDatabase.signalRollback();
                        throw e;
                }
                catch (Error e)
                {
                        mainDatabase.signalRollback();
                        throw e;
                }
                finally
                {
                        mainDatabase.endTransaction();
                }
                if (se != null)
                        throw se;

        }

        /** Start the authority system.
        */
        public static void startSystem(IThreadContext threadContext)
                throws MetacartaException
        {
                // Read any parameters
                String maxThreads = getProperty(authCheckThreadCountProperty);
                if (maxThreads == null)
                        maxThreads = "10";
                numAuthCheckThreads = new Integer(maxThreads).intValue();
                if (numAuthCheckThreads < 1 || numAuthCheckThreads > 100)
                        throw new MetacartaException("Illegal value for the number of auth check threads");

                // Start up threads
                idleCleanupThread = new IdleCleanupThread();
                idleCleanupThread.start();

                requestQueue = new RequestQueue();
                
                authCheckThreads = new AuthCheckThread[numAuthCheckThreads];
                int i = 0;
                while (i < numAuthCheckThreads)
                {
                        authCheckThreads[i] = new AuthCheckThread(Integer.toString(i),requestQueue);
                        authCheckThreads[i].start();
                        i++;
                }
        }

        /** Shut down the authority system.
        */
        public static void stopSystem(IThreadContext threadContext)
                throws MetacartaException
        {

                while (idleCleanupThread != null || authCheckThreads != null)
                {
                        if (idleCleanupThread != null)
                        {
                                idleCleanupThread.interrupt();
                        }
                        if (authCheckThreads != null)
                        {
                                int i = 0;
                                while (i < authCheckThreads.length)
                                {
                                        Thread authCheckThread = authCheckThreads[i++];
                                        if (authCheckThread != null)
                                                authCheckThread.interrupt();
                                }
                        }

                        if (idleCleanupThread != null)
                        {
                                if (!idleCleanupThread.isAlive())
                                        idleCleanupThread = null;
                        }
                        if (authCheckThreads != null)
                        {
                                int i = 0;
                                boolean isAlive = false;
                                while (i < authCheckThreads.length)
                                {
                                        Thread authCheckThread = authCheckThreads[i];
                                        if (authCheckThread != null)
                                        {
                                                if (!authCheckThread.isAlive())
                                                        authCheckThreads[i] = null;
                                                else
                                                        isAlive = true;
                                        }
                                        i++;
                                }
                                if (!isAlive)
                                        authCheckThreads = null;
                        }

                        try
                        {
                                Metacarta.sleep(1000);
                        }
                        catch (InterruptedException e)
                        {
                        }
                }	

                // Release all authority connectors
                AuthorityConnectorFactory.closeAllConnectors(threadContext);
                numAuthCheckThreads = 0;
                requestQueue = null;
        }

        /** Get the current request queue */
        public static RequestQueue getRequestQueue()
        {
                return requestQueue;
        }
        
}

