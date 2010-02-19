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
package org.apache.lcf.crawler.interfaces;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.system.Logging;

import java.util.*;
import java.lang.reflect.*;

/** This is the factory class for IRepositoryConnector objects.
*/
public class RepositoryConnectorFactory
{
        public static final String _rcsid = "@(#)$Id$";

        // Pool hash table.
        // Keyed by PoolKey; value is Pool
        protected static Map poolHash = new HashMap();

        // private static HashMap checkedOutConnectors = new HashMap();

        private RepositoryConnectorFactory()
        {
        }

        /** Install connector.
        *@param className is the class name.
        */
        public static void install(IThreadContext threadContext, String className)
                throws LCFException
        {
                IRepositoryConnector connector = getConnectorNoCheck(className);
                connector.install(threadContext);
        }

        /** Uninstall connector.
        *@param className is the class name.
        */
        public static void deinstall(IThreadContext threadContext, String className)
                throws LCFException
        {
                IRepositoryConnector connector = getConnectorNoCheck(className);
                connector.deinstall(threadContext);
        }

        /** Get the activities supported by this connector.
        *@param className is the class name.
        *@return the list of activities.
        */
        public static String[] getActivitiesList(IThreadContext threadContext, String className)
                throws LCFException
        {
                IRepositoryConnector connector = getConnector(threadContext, className);
                if (connector == null)
                        return null;
                String[] values = connector.getActivitiesList();
                java.util.Arrays.sort(values);
                return values;
        }

        /** Get the link types logged by this connector.
        *@param className is the class name.
        *@return the list of link types, in sorted order.
        */
        public static String[] getRelationshipTypes(IThreadContext threadContext, String className)
                throws LCFException
        {
                IRepositoryConnector connector = getConnector(threadContext, className);
                if (connector == null)
                        return null;
                String[] values = connector.getRelationshipTypes();
                java.util.Arrays.sort(values);
                return values;
        }
        
        /** Get the JSP folder for a connector.
        *@param className is the class name.
        *@return the folder string.
        */
        public static String getJSPFolder(IThreadContext threadContext, String className)
                throws LCFException
        {
                IRepositoryConnector connector = getConnector(threadContext, className);
                if (connector == null)
                        return null;
                return connector.getJSPFolder();
        }

        /** Get the operating mode for a connector.
        *@param className is the class name.
        *@return the connector operating model, as specified in IRepositoryConnector.
        */
        public static int getConnectorModel(IThreadContext threadContext, String className)
                throws LCFException
        {
                IRepositoryConnector connector = getConnector(threadContext, className);
                if (connector == null)
                        return -1;
                return connector.getConnectorModel();
        }

        /** Get a repository connector instance, without checking for installed connector.
        *@param className is the class name.
        *@return the instance.
        */
        public static IRepositoryConnector getConnectorNoCheck(String className)
                throws LCFException
        {
                try
                {
                        Class theClass = Class.forName(className);
                        Class[] argumentClasses = new Class[0];
                        // Look for a constructor
                        Constructor c = theClass.getConstructor(argumentClasses);
                        Object[] arguments = new Object[0];
                        Object o = c.newInstance(arguments);
                        if (!(o instanceof IRepositoryConnector))
                                throw new LCFException("Class '"+className+"' does not implement IRepositoryConnector.");
                        return (IRepositoryConnector)o;
                }
                catch (InvocationTargetException e)
                {
                        Throwable z = e.getTargetException();
                        if (z instanceof Error)
                                throw (Error)z;
                        else
                                throw (LCFException)z;
                }
                catch (ClassNotFoundException e)
                {
                        throw new LCFException("No class implementing IRepositoryConnector called '"+
                                className+"'.",
                                e);
                }
                catch (NoSuchMethodException e)
                {
                        throw new LCFException("No appropriate constructor for IRepositoryConnector implementation '"+
                                className+"'.  Need xxx(ConfigParams).",
                                e);
                }
                catch (SecurityException e)
                {
                        throw new LCFException("Protected constructor for IRepositoryConnector implementation '"+className+"'",
                                e);
                }
                catch (IllegalAccessException e)
                {
                        throw new LCFException("Unavailable constructor for IRepositoryConnector implementation '"+className+"'",
                                e);
                }
                catch (IllegalArgumentException e)
                {
                        throw new LCFException("Shouldn't happen!!!",e);
                }
                catch (InstantiationException e)
                {
                        throw new LCFException("InstantiationException for IRepositoryConnector implementation '"+className+"'",
                                e);
                }
                catch (ExceptionInInitializerError e)
                {
                        throw new LCFException("ExceptionInInitializerError for IRepositoryConnector implementation '"+className+"'",
                                e);
                }

        }

        /** Get a repository connector instance.
        *@param className is the class name.
        *@return the instance.
        */
        protected static IRepositoryConnector getConnector(IThreadContext threadContext, String className)
                throws LCFException
        {
                IConnectorManager connMgr = ConnectorManagerFactory.make(threadContext);
                if (connMgr.isInstalled(className) == false)
                        return null;

                try
                {
                        Class theClass = Class.forName(className);
                        Class[] argumentClasses = new Class[0];
                        // Look for a constructor
                        Constructor c = theClass.getConstructor(argumentClasses);
                        Object[] arguments = new Object[0];
                        Object o = c.newInstance(arguments);
                        if (!(o instanceof IRepositoryConnector))
                                throw new LCFException("Class '"+className+"' does not implement IRepositoryConnector.");
                        return (IRepositoryConnector)o;
                }
                catch (InvocationTargetException e)
                {
                        Throwable z = e.getTargetException();
                        if (z instanceof Error)
                                throw (Error)z;
                        else
                                throw (LCFException)z;
                }
                catch (ClassNotFoundException e)
                {
                        // This MAY mean that an existing connector has been uninstalled; check out this possibility!
                        // We return null because that is the signal that we cannot get a connector instance for that reason.
                        if (connMgr.isInstalled(className) == false)
                                return null;

                        throw new LCFException("No class implementing IRepositoryConnector called '"+
                                className+"'.",
                                e);
                }
                catch (NoSuchMethodException e)
                {
                        throw new LCFException("No appropriate constructor for IRepositoryConnector implementation '"+
                                className+"'.  Need xxx(ConfigParams).",
                                e);
                }
                catch (SecurityException e)
                {
                        throw new LCFException("Protected constructor for IRepositoryConnector implementation '"+className+"'",
                                e);
                }
                catch (IllegalAccessException e)
                {
                        throw new LCFException("Unavailable constructor for IRepositoryConnector implementation '"+className+"'",
                                e);
                }
                catch (IllegalArgumentException e)
                {
                        throw new LCFException("Shouldn't happen!!!",e);
                }
                catch (InstantiationException e)
                {
                        throw new LCFException("InstantiationException for IRepositoryConnector implementation '"+className+"'",
                                e);
                }
                catch (ExceptionInInitializerError e)
                {
                        throw new LCFException("ExceptionInInitializerError for IRepositoryConnector implementation '"+className+"'",
                                e);
                }

        }

        /** Get multiple repository connectors, all at once.  Do this in a particular order
        * so that any connector exhaustion will not cause a deadlock.
        */
        public static IRepositoryConnector[] grabMultiple(IThreadContext threadContext,
                String[] orderingKeys, String[] classNames, ConfigParams[] configInfos, int[] maxPoolSizes)
                throws LCFException
        {
                IRepositoryConnector[] rval = new IRepositoryConnector[classNames.length];
                HashMap orderMap = new HashMap();
                int i = 0;
                while (i < orderingKeys.length)
                {
                        if (orderMap.get(orderingKeys[i]) != null)
                            throw new LCFException("Found duplicate order key");
                        orderMap.put(orderingKeys[i],new Integer(i));
                        i++;
                }
                java.util.Arrays.sort(orderingKeys);
                i = 0;
                while (i < orderingKeys.length)
                {
                        String orderingKey = orderingKeys[i];
                        int index = ((Integer)orderMap.get(orderingKey)).intValue();
                        String className = classNames[index];
                        ConfigParams cp = configInfos[index];
                        int maxPoolSize = maxPoolSizes[index];
                        try
                        {
                                IRepositoryConnector connector = grab(threadContext,className,cp,maxPoolSize);
                                rval[index] = connector;
                        }
                        catch (Throwable e)
                        {
                                while (i > 0)
                                {
                                        i--;
                                        orderingKey = orderingKeys[i];
                                        index = ((Integer)orderMap.get(orderingKey)).intValue();
                                        try
                                        {
                                                release(rval[index]);
                                        }
                                        catch (LCFException e2)
                                        {
                                        }
                                }
                                if (e instanceof LCFException)
                                {
                                        throw (LCFException)e;
                                }
                                throw (Error)e;
                        }
                        i++;
                }
                return rval;
        }
        
        /** Get a repository connector.
        * The connector is specified by its class and its parameters.
        *@param threadContext is the current thread context.
        *@param className is the name of the class to get a connector for.
        *@param configInfo are the name/value pairs constituting configuration info
        * for this class.
        */
        public static IRepositoryConnector grab(IThreadContext threadContext,
                String className, ConfigParams configInfo, int maxPoolSize)
                throws LCFException
        {
                // We want to get handles off the pool and use them.  But the
                // handles we fetch have to have the right config information.

                // Use the classname and config info to build a pool key.  This
                // key will be discarded if we actually have to save a key persistently,
                // since we avoid copying the configInfo unnecessarily.
                PoolKey pk = new PoolKey(className,configInfo);
                Pool p;
                synchronized (poolHash)
                {
                        p = (Pool)poolHash.get(pk);
                        if (p == null)
                        {
                                pk = new PoolKey(className,configInfo.duplicate());
                                p = new Pool(pk,maxPoolSize);
                                poolHash.put(pk,p);
                        }
                }

                IRepositoryConnector rval = p.getConnector(threadContext);
                
                // Enter it in the pool so we can figure out whether it closed
                // synchronized (checkedOutConnectors)
                // {
                //      checkedOutConnectors.put(rval.toString(),new ConnectorTracker(rval));
                // }
                 
                return rval;

        }

        /** Release multiple repository connectors.
        */
        public static void releaseMultiple(IRepositoryConnector[] connectors)
                throws LCFException
        {
                int i = 0;
                LCFException currentException = null;
                while (i < connectors.length)
                {
                        IRepositoryConnector c = connectors[i++];
                        try
                        {
                                release(c);
                        }
                        catch (LCFException e)
                        {
                                if (currentException == null)
                                        currentException = e;
                        }
                }
                if (currentException != null)
                        throw currentException;
        }
        
        /** Release a repository connector.
        *@param connector is the connector to release.
        */
        public static void release(IRepositoryConnector connector)
                throws LCFException
        {
                // If the connector is null, skip the release, because we never really got the connector in the first place.
                if (connector == null)
                        return;
                
                // Figure out which pool this goes on, and put it there
                PoolKey pk = new PoolKey(connector.getClass().getName(),connector.getConfiguration());
                Pool p;
                synchronized (poolHash)
                {
                        p = (Pool)poolHash.get(pk);
                }

                p.releaseConnector(connector);
                
                // synchronized (checkedOutConnectors)
                // {
                //      checkedOutConnectors.remove(connector.toString());
                // }

        }

        /** Idle notification for inactive repository connector handles.
        * This method polls all inactive handles.
        */
        public static void pollAllConnectors(IThreadContext threadContext)
                throws LCFException
        {
                // System.out.println("Pool stats:");
                
                // Go through the whole pool and notify everyone
                synchronized (poolHash)
                {
                        Iterator iter = poolHash.values().iterator();
                        while (iter.hasNext())
                        {
                                Pool p = (Pool)iter.next();
                                p.pollAll(threadContext);
                                //p.printStats();
                        }
                }

                // System.out.println("About to check if any repository connector instances have been abandoned...");
                // checkConnectors(System.currentTimeMillis());
        }

        /** Clean up all open repository connector handles.
        * This method is called when the connector pool needs to be flushed,
        * to free resources.
        *@param threadContext is the local thread context.
        */
        public static void closeAllConnectors(IThreadContext threadContext)
                throws LCFException
        {
                // Go through the whole pool and clean it out
                synchronized (poolHash)
                {
                        Iterator iter = poolHash.values().iterator();
                        while (iter.hasNext())
                        {
                                Pool p = (Pool)iter.next();
                                p.releaseAll(threadContext);
                        }
                }
        }

        /** Track connection allocation */
        // public static void checkConnectors(long currentTime)
        // {
        //      synchronized (checkedOutConnectors)
        //      {
        //              Iterator iter = checkedOutConnectors.keySet().iterator();
        //              while (iter.hasNext())
        //              {
        //                      Object key = iter.next();
        //                      ConnectorTracker ct = (ConnectorTracker)checkedOutConnectors.get(key);
        //                      if (ct.hasExpired(currentTime))
        //                              ct.printDetails();
        //              }
        //      }
        // }

        /** This is an immutable pool key class, which describes a pool in terms of two independent keys.
        */
        public static class PoolKey
        {
                protected String className;
                protected ConfigParams configInfo;

                /** Constructor.
                */
                public PoolKey(String className, Map configInfo)
                {
                        this.className = className;
                        this.configInfo = new ConfigParams(configInfo);
                }

                public PoolKey(String className, ConfigParams configInfo)
                {
                        this.className = className;
                        this.configInfo = configInfo;
                }

                /** Get the class name.
                *@return the class name.
                */
                public String getClassName()
                {
                        return className;
                }

                /** Get the config info.
                *@return the params
                */
                public ConfigParams getParams()
                {
                        return configInfo;
                }

                /** Hash code.
                */
                public int hashCode()
                {
                        return className.hashCode() + configInfo.hashCode();
                }

                /** Equals operator.
                */
                public boolean equals(Object o)
                {
                        if (!(o instanceof PoolKey))
                                return false;

                        PoolKey pk = (PoolKey)o;
                        return pk.className.equals(className) && pk.configInfo.equals(configInfo);
                }

        }

        /** This class represents a value in the pool hash, which corresponds to a given key.
        */
        public static class Pool
        {
                protected ArrayList stack = new ArrayList();
                protected PoolKey key;
                protected int numFree;

                /** Constructor
                */
                public Pool(PoolKey pk, int maxCount)
                {
                        key = pk;
                        numFree = maxCount;
                }

                /** Grab a repository connector.
                * If none exists, construct it using the information in the pool key.
                *@return the connector, or null if no connector could be connected.
                */
                public synchronized IRepositoryConnector getConnector(IThreadContext threadContext)
                        throws LCFException
                {
                        while (numFree == 0)
                        {
                                try
                                {
                                        wait();
                                }
                                catch (InterruptedException e)
                                {
                                        throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
                                }
                        }

                        IRepositoryConnector rc;
                        if (stack.size() == 0)
                        {
                                String className = key.getClassName();
                                ConfigParams configParams = key.getParams();
                                
                                IConnectorManager connMgr = ConnectorManagerFactory.make(threadContext);
                                if (connMgr.isInstalled(className) == false)
                                        return null;

                                try
                                {
                                        Class theClass = Class.forName(className);
                                        Class[] argumentClasses = new Class[0];
                                        // Look for a constructor
                                        Constructor c = theClass.getConstructor(argumentClasses);
                                        Object[] arguments = new Object[0];
                                        Object o = c.newInstance(arguments);
                                        if (!(o instanceof IRepositoryConnector))
                                                throw new LCFException("Class '"+className+"' does not implement IRepositoryConnector.");
                                        rc = (IRepositoryConnector)o;
                                        rc.connect(configParams);
                                }
                                catch (InvocationTargetException e)
                                {
                                        Throwable z = e.getTargetException();
                                        if (z instanceof Error)
                                                throw (Error)z;
                                        else
                                                throw (LCFException)z;
                                }
                                catch (ClassNotFoundException e)
                                {
                                        // If we see this exception, it COULD mean that the connector was uninstalled, and we happened to get here
                                        // after that occurred.
                                        // We return null because that is the signal that we cannot get a connector instance for that reason.
                                        if (connMgr.isInstalled(className) == false)
                                                return null;
                                        
                                        throw new LCFException("No class implementing IRepositoryConnector called '"+
                                                className+"'.",
                                                e);
                                }
                                catch (NoSuchMethodException e)
                                {
                                        throw new LCFException("No appropriate constructor for IRepositoryConnector implementation '"+
                                                className+"'.  Need xxx(ConfigParams).",
                                                e);
                                }
                                catch (SecurityException e)
                                {
                                        throw new LCFException("Protected constructor for IRepositoryConnector implementation '"+className+"'",
                                                e);
                                }
                                catch (IllegalAccessException e)
                                {
                                        throw new LCFException("Unavailable constructor for IRepositoryConnector implementation '"+className+"'",
                                                e);
                                }
                                catch (IllegalArgumentException e)
                                {
                                        throw new LCFException("Shouldn't happen!!!",e);
                                }
                                catch (InstantiationException e)
                                {
                                        throw new LCFException("InstantiationException for IRepositoryConnector implementation '"+className+"'",
                                                e);
                                }
                                catch (ExceptionInInitializerError e)
                                {
                                        throw new LCFException("ExceptionInInitializerError for IRepositoryConnector implementation '"+className+"'",
                                                e);
                                }
                        }
                        else
                                rc = (IRepositoryConnector)stack.remove(stack.size()-1);

                        numFree--;

                        rc.setThreadContext(threadContext);
                        return rc;
                }

                /** Release a repository connector to the pool.
                *@param connector is the connector.
                */
                public synchronized void releaseConnector(IRepositoryConnector connector)
                        throws LCFException
                {
                        if (connector == null)
                                return;
                        
                        // Make sure connector knows it's released
                        connector.clearThreadContext();
                        // Append
                        stack.add(connector);
                        numFree++;
                        notifyAll();
                }

                /** Notify all free connectors.
                */
                public synchronized void pollAll(IThreadContext threadContext)
                        throws LCFException
                {
                        int i = 0;
                        while (i < stack.size())
                        {
                                IRepositoryConnector rc = (IRepositoryConnector)stack.get(i++);
                                // Notify
                                rc.setThreadContext(threadContext);
                                rc.poll();
                                rc.clearThreadContext();
                        }
                }

                /** Release all free connectors.
                */
                public synchronized void releaseAll(IThreadContext threadContext)
                        throws LCFException
                {
                        while (stack.size() > 0)
                        {
                                IRepositoryConnector rc = (IRepositoryConnector)stack.remove(stack.size()-1);
                                // Disconnect
                                rc.setThreadContext(threadContext);
                                rc.disconnect();
                                rc.clearThreadContext();
                        }
                }

                /** Print pool stats */
                public synchronized void printStats()
                {
                        System.out.println(" Class name = "+key.getClassName()+"; Number free = "+Integer.toString(numFree));
                }
        }
        

        protected static class ConnectorTracker
        {
                protected IRepositoryConnector theConnector;
                protected long checkoutTime;
                protected Exception theTrace;

                public ConnectorTracker(IRepositoryConnector theConnector)
                {
                        this.theConnector = theConnector;
                        this.checkoutTime = System.currentTimeMillis();
                        this.theTrace = new Exception("Stack trace");
                }
                
                public boolean hasExpired(long currentTime)
                {
                        return (checkoutTime + 300000L < currentTime);
                }
                
                public void printDetails()
                {
                        Logging.threads.error("Connector instance may have been abandoned: "+theConnector.toString(),theTrace);
                }
        }
}
