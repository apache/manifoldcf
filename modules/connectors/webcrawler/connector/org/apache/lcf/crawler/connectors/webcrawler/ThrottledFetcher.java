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
package org.apache.lcf.crawler.connectors.webcrawler;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.crawler.system.Logging;
import org.apache.lcf.crawler.system.Metacarta;
import java.util.*;
import java.io.*;
import java.net.*;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.params.*;
import org.apache.commons.httpclient.protocol.*;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.auth.AuthScope;

/** This class uses httpclient to fetch stuff from webservers.  However, it additionally controls the fetch
* rate in two ways: first, controlling the overall bandwidth used per server, and second, limiting the number
* of simultaneous open connections per server.
* An instance of this class would very probably need to have a lifetime consistent with the long-term nature
* of these values, and be static.
*/
public class ThrottledFetcher
{
        public static final String _rcsid = "@(#)$Id$";

        /** This flag determines whether we record everything to the disk, as a means of doing a web snapshot */
        protected static final boolean recordEverything = false;

        protected static final long TIME_2HRS = 7200000L;
        protected static final long TIME_5MIN = 300000L;
        protected static final long TIME_15MIN = 1500000L;
        protected static final long TIME_6HRS = 6L * 60L * 60000L;
        protected static final long TIME_1DAY = 24L * 60L * 60000L;


        /** This is the static pool of ConnectionBin's, keyed by bin name. */
        protected static HashMap connectionBins = new HashMap();
        /** This is the static pool of ThrottleBin's, keyed by bin name. */
        protected static HashMap throttleBins = new HashMap();

        /** This global lock protects the "distributed pool" resource, and insures that a connection
        * can get pulled out of all the right pools and wind up in only the hands of one thread. */
        protected static Integer poolLock = new Integer(0);

        /** The read chunk length */
        protected static final int READ_CHUNK_LENGTH = 4096;

        /** Constructor.
        */
        public ThrottledFetcher()
        {
        }



        /** Obtain a connection to specified protocol, server, and port.  We use the protocol because the
        * setup for some protocols is extensive (e.g. https) and hopefully would not need to be repeated if
        * we distinguish connections based on that.
        *@param protocol is the protocol, e.g. "http"
        *@param server is the server IP address, e.g. "10.32.65.1"
        *@param port is the port to connect to, e.g. 80.  Pass -1 if the default port for the protocol is desired.
        *@param credentials is the credentials object to use for the fetch.  If null, no credentials are available.
        *@param binNames is the set of bins, in order, that should be used for throttling this connection.
        *       Note that the bin names for a given IP address and port MUST be the same for every connection!
        *	This must be enforced by whatever it is that builds the bins - it must do so given an IP and port.
        *@param throttleDescription is the description of all the throttling that should take place.
        *@return an IThrottledConnection object that can be used to fetch from the port.
        */
        public static IThrottledConnection getConnection(String protocol, String server, int port,
                PageCredentials authentication,
                IKeystoreManager trustStore,
                ThrottleDescription throttleDescription, String[] binNames,
                int connectionLimit)
                throws MetacartaException
        {
                // First, create a protocol factory object, if we can
                ProtocolFactory myFactory = new ProtocolFactory();
                String trustStoreString;
                ProtocolSocketFactory secureSocketFactory;
                if (trustStore != null)
                {
                        secureSocketFactory = new WebSecureSocketFactory(trustStore.getSecureSocketFactory());
                        trustStoreString = trustStore.getString();
                }
                else
                {
                        trustStoreString = null;
                        secureSocketFactory = new WebSecureSocketFactory(KeystoreManagerFactory.getTrustingSecureSocketFactory());
                }
                
                Protocol myHttpsProtocol = new Protocol("https", (ProtocolSocketFactory)secureSocketFactory, 443);
                myFactory.registerProtocol("https",myHttpsProtocol);

                ConnectionBin[] bins = new ConnectionBin[binNames.length];

                // Now, start looking for a connection
                int i = 0;
                while (i < binNames.length)
                {
                        String binName = binNames[i];	

                        // Find or create the bin object
                        ConnectionBin cb;
                        synchronized (connectionBins)
                        {
                                cb = (ConnectionBin)connectionBins.get(binName);
                                if (cb == null)
                                {
                                        cb = new ConnectionBin(binName);
                                        connectionBins.put(binName,cb);
                                }
                                //cb.sanityCheck();
                        }
                        bins[i] = cb;
                        i++;
                }
                
                ThrottledConnection connectionToReuse;

                long startTime = 0L;
                if (Logging.connectors.isDebugEnabled())
                {
                        startTime = System.currentTimeMillis();
                        Logging.connectors.debug("WEB: Waiting to start getting a connection to "+protocol+"://"+server+":"+port);
                }

                synchronized (poolLock)
                {

                    // If the number of outstanding connections is greater than the global limit, close pooled connections until we are under the limit
                    long idleTimeout = 64000L;
                    while (true)
                    {
                        int openCount = 0;

                        // Lock up everything for a moment
                        synchronized (connectionBins)
                        {
                                // Time out connections that have been idle too long.  To do this, we need to go through
                                // all connection bins and look at the pool
                                Iterator binIter = connectionBins.keySet().iterator();
                                while (binIter.hasNext())
                                {
                                        String binName = (String)binIter.next();
                                        ConnectionBin cb = (ConnectionBin)connectionBins.get(binName);
                                        openCount += cb.countConnections();
                                }
                        }

                        if (openCount < connectionLimit)
                                break;
                        
                        if (idleTimeout == 0L)
                        {
                                // Can't actually conclude anything here unfortunately

                                // Logging.connectors.warn("Web: Exceeding connection limit!  Open count = "+Integer.toString(openCount)+"; limit = "+Integer.toString(connectionLimit));
                                break;
                        }
                        idleTimeout = idleTimeout/4L;
                        
                        // Lock up everything for a moment, since otherwise we could delete something people
                        // expect to stick around.
                        synchronized (connectionBins)
                        {
                                // Time out connections that have been idle too long.  To do this, we need to go through
                                // all connection bins and look at the pool
                                Iterator binIter = connectionBins.keySet().iterator();
                                while (binIter.hasNext())
                                {
                                        String binName = (String)binIter.next();
                                        ConnectionBin cb = (ConnectionBin)connectionBins.get(binName);
                                        cb.flushIdleConnections(idleTimeout);
                                }
                        }
                    }

                    try
                    {
                        // Retry until we get the connection.
                        while (true)
                        {
                                if (Logging.connectors.isDebugEnabled())
                                        Logging.connectors.debug("WEB: Attempting to get connection to "+protocol+"://"+server+":"+port+" ("+new Long(System.currentTimeMillis()-startTime).toString()+" ms)");

                                i = 0;

                                connectionToReuse = null;
                                
                                try
                                {

                                        // Now, start looking for a connection
                                        while (i < binNames.length)
                                        {
                                                String binName = binNames[i];
                                                ConnectionBin cb = bins[i];

                                                // Figure out the connection limit for this bin, based on the throttle description
                                                int maxConnections = throttleDescription.getMaxOpenConnections(binName);
                                                
                                                // If no restriction, use a very large value.
                                                if (maxConnections == -1)
                                                        maxConnections = Integer.MAX_VALUE;
                                                else if (maxConnections == 0)
                                                        maxConnections = 1;

                                                // Now, do what we need to do to reserve our connection for this bin.
                                                // If we can't reserve it now, we plan on undoing everything we did, so
                                                // whatever we do must be reversible.  Furthermore, nothing we call here
                                                // should actually wait(); that will occur if we can't get what we need out
                                                // here at this level.

                                                if (connectionToReuse != null)
                                                {
                                                        // We have a reuse candidate already, so just make sure each remaining bin is within
                                                        // its limits.
                                                        cb.insureWithinLimits(maxConnections,connectionToReuse);
                                                }
                                                else
                                                {
                                                        connectionToReuse = cb.findConnection(maxConnections,bins,protocol,server,port,authentication,trustStoreString);
                                                }

                                                // Increment after we successfully handled this bin
                                                i++;
                                        }
                                        
                                        // That loop completed, meaning that we think we got a connection.  Now, go through all the bins and make sure there's enough time since the last
                                        // fetch.  If not, we have to clean everything up and try again.
                                        long currentTime = System.currentTimeMillis();

                                        // Global lock needed to insure that fetch time is updated across all bins simultaneously
                                        synchronized (connectionBins)
                                        {
                                                i = 0;
                                                while (i < binNames.length)
                                                {
                                                        String binName = binNames[i];
                                                        ConnectionBin cb = bins[i];
                                                        //cb.sanityCheck();
                                                        // Get the minimum time between fetches for this bin, based on the throttle description
                                                        long minMillisecondsPerFetch = throttleDescription.getMinimumMillisecondsPerFetch(binName);
                                                        if (cb.getLastFetchTime() + minMillisecondsPerFetch > currentTime)
                                                                throw new WaitException(cb.getLastFetchTime() + minMillisecondsPerFetch - currentTime);
                                                        i++;
                                                }
                                                i = 0;
                                                while (i < binNames.length)
                                                {
                                                        ConnectionBin cb = bins[i++];
                                                        cb.setLastFetchTime(currentTime);
                                                }
                                        }
                                                        
                                }
                                catch (Throwable e)
                                {
                                        // We have to free everything and retry, because otherwise we are subject to deadlock.
                                        // The only thing we have reserved is the connection, which we must free if there's a
                                        // problem.

                                        if (connectionToReuse != null)
                                        {
                                                // Return this connection to the pool.  That is, the pools for all the bins.
                                                int k = 0;
                                                while (k < binNames.length)
                                                {
                                                        String binName = binNames[k++];
                                                        ConnectionBin cb;
                                                        synchronized (connectionBins)
                                                        {
                                                                cb = (ConnectionBin)connectionBins.get(binName);
                                                                if (cb == null)
                                                                {
                                                                        cb = new ConnectionBin(binName);
                                                                        connectionBins.put(binName,cb);
                                                                }
                                                        }
                                                        //cb.sanityCheck();
                                                        cb.addToPool(connectionToReuse);
                                                        //cb.sanityCheck();
                                                }
                                                connectionToReuse = null;
                                                // We should not need to notify here because nothing has really changed from
                                                // when the attempt started to get the connection.  We just undid what we did.
                                        }


                                        if (e instanceof Error)
                                                throw (Error)e;
                                        if (e instanceof MetacartaException)
                                                throw (MetacartaException)e;

                                        if (e instanceof WaitException)
                                        {
                                                // Wait because we need a certain amount of time after a previous fetch.
                                                WaitException we = (WaitException)e;
                                                long waitAmount = we.getWaitAmount();
                                                if (Logging.connectors.isDebugEnabled())
                                                        Logging.connectors.debug("WEB: Waiting "+new Long(waitAmount).toString()+" ms before starting fetch on "+protocol+"://"+server+":"+port);
                                                // Really don't want to sleep inside the pool lock!
                                                // The easiest thing to do instead is to use a timed wait.  There is no reason why we need
                                                // to wake before the wait time is exceeded - but it's harmless, and the alternative is to
                                                // do more reorganization than probably is wise.
                                                poolLock.wait(waitAmount);
                                                continue;
                                        }
                                        
                                        if (e instanceof PoolException)
                                        {

                                                if (Logging.connectors.isDebugEnabled())
                                                        Logging.connectors.debug("WEB: Going into wait for connection to "+protocol+"://"+server+":"+port+" ("+new Long(System.currentTimeMillis()-startTime).toString()+" ms)");

                                                // Now, wait for something external to change.  The only thing that can help us is if
                                                // some other thread frees a connection.
                                                poolLock.wait();
                                                // Go back around and try again.
                                                continue;
                                        }
                                        
                                        throw new MetacartaException("Unexpected exception encountered: "+e.getMessage(),e);
                                }

                                if (Logging.connectors.isDebugEnabled())
                                        Logging.connectors.debug("WEB: Successfully got connection to "+protocol+"://"+server+":"+port+" ("+new Long(System.currentTimeMillis()-startTime).toString()+" ms)");

                                // If we have a connection located, activate it.
                                if (connectionToReuse == null)
                                        connectionToReuse = new ThrottledConnection(protocol,server,port,authentication,myFactory,trustStoreString,bins);
                                connectionToReuse.setup(throttleDescription);
                                return connectionToReuse;
                        }
                    }
                    catch (InterruptedException e)
                    {
                        throw new MetacartaException(e.getMessage(),MetacartaException.INTERRUPTED);
                    }
                }
        }


        /** Flush connections that have timed out from inactivity. */
        public static void flushIdleConnections()
                throws MetacartaException
        {
            synchronized (poolLock)
            {
                // Lock up everything for a moment, since otherwise we could delete something people
                // expect to stick around.
                synchronized (connectionBins)
                {
                        // Time out connections that have been idle too long.  To do this, we need to go through
                        // all connection bins and look at the pool
                        Iterator binIter = connectionBins.keySet().iterator();
                        while (binIter.hasNext())
                        {
                                String binName = (String)binIter.next();
                                ConnectionBin cb = (ConnectionBin)connectionBins.get(binName);
                                if (cb.flushIdleConnections(60000L))
                                {
                                        // Bin is no longer doing anything; get rid of it.
                                        // I've determined this is safe - inUseConnections is designed to prevent any active connection from getting
                                        // whacked.
                                        // Oops.  Hang results again when I enabled this, so out it goes again.
                                        //connectionBins.remove(binName);
                                        //binIter = connectionBins.keySet().iterator();
                                }
                        }
                }
            }
        }

        /** Connection pool for a bin.
        * An instance of this class tracks the connections that are pooled and that are in use for a specific bin.
        */
        protected static class ConnectionBin
        {
                /** This is the bin name which this connection pool belongs to */
                protected String binName;
                /** This is the number of connections in this bin that are signed out and presumably in use */
                protected int inUseConnections = 0;
                /** This is the last time a fetch was done on this bin */
                protected long lastFetchTime = 0L;
                /** This object is what we synchronize on when we are waiting on a connection to free up for this
                * bin.  This is a separate object, because we also want to protect the integrity of the
                * ConnectionBin object itself, for which we'll use the ConnectionBin's synchronizer. */
                protected Integer connectionWait = new Integer(0);
                /** This map contains ThrottledConnection objects that are in the pool, and are not in use. */
                protected HashMap freePool = new HashMap();

                /** Constructor. */
                public ConnectionBin(String binName)
                {
                        this.binName = binName;
                }

                /** Get the bin name. */
                public String getBinName()
                {
                        return binName;
                }

                /** Note the creation of an active connection that belongs to this bin.  The slots all must
                * have been reserved prior to the connection being created.
                */
                public synchronized void noteConnectionCreation()
                {
                        inUseConnections++;
                }

                /** Note the destruction of an active connection that belongs to this bin.
                */
                public synchronized void noteConnectionDestruction()
                {
                        inUseConnections--;
                }


                /** Activate a connection that should be in the pool.
                * Removes the connection from the pool.
                */
                public synchronized void takeFromPool(ThrottledConnection tc)
                {
                        // Remove this connection from the pool list
                        freePool.remove(tc);
                        inUseConnections++;
                }

                /** Put a connection into the pool.
                */
                public synchronized void addToPool(ThrottledConnection tc)
                {
                        // Add this connection to the pool list
                        freePool.put(tc,tc);
                        inUseConnections--;
                }

                /** Verify that this bin is within limits.
                */
                public synchronized void insureWithinLimits(int maxConnections, ThrottledConnection existingConnection)
                        throws PoolException
                {
                        //sanityCheck();

                        // See if the connection is in fact within the pool; if so, we just presume the limits are fine as they are.
                        // This is necessary because if the connection that's being checked for is freed, then we wreck the data structures.
                        if (existsInPool(existingConnection))
                                return;
                        
                        while (maxConnections > 0 && inUseConnections + freePool.size() > maxConnections)
                        {
                                //sanityCheck();
                                
                                // If there are any pool connections, free them one at a time
                                ThrottledConnection freeMe = getPoolConnection();
                                if (freeMe != null)
                                {
                                        // It's okay to call activate since we guarantee that only one thread is trying to grab
                                        // a connection at a time.
                                        freeMe.activate();
                                        freeMe.destroy();
                                        continue;
                                }

                                // Instead of waiting, throw a pool exception, so that we can wait and retry at the next level up.
                                throw new PoolException("Waiting for a connection");
                        }
                }

                /** This method is called only when there is no existing connection yet identified that can be used
                * for contacting the server and port specified.  This method returns a connection if a matching one can be found;
                * otherwise it returns null.
                * If a matching connection is found, it is activated before it is returned.  That removes the connection from all
                * pools in which it lives.
                */
                public synchronized ThrottledConnection findConnection(int maxConnections,
                        ConnectionBin[] binNames, String protocol, String server, int port,
                        PageCredentials authentication, String trustStoreString)
                        throws PoolException
                {
                        //sanityCheck();
                        
                        // First, wait until there's no excess.
                        while (maxConnections > 0 && inUseConnections + freePool.size() > maxConnections)
                        {
                                //sanityCheck();
                                // If there are any pool connections, free them one at a time
                                ThrottledConnection freeMe = getPoolConnection();
                                if (freeMe != null)
                                {
                                        // It's okay to call activate since we guarantee that only one thread is trying to grab
                                        // a connection at a time.
                                        freeMe.activate();
                                        freeMe.destroy();
                                        continue;
                                }

                                // Instead of waiting, throw a pool exception, so that we can wait and retry at the next level up.
                                throw new PoolException("Waiting for a connection");

                        }
                        
                        // Wait until there's a free one
                        if (maxConnections > 0 && inUseConnections > maxConnections-1)
                        {
                                // Instead of waiting, throw a pool exception, so that we can wait and retry at the next level up.
                                throw new PoolException("Waiting for a connection");
                        }

                        // A null return means that there is no existing pooled connection that matches, and the  caller is free to create a new connection
                        ThrottledConnection rval = getPoolConnection();
                        if (rval == null)
                                return null;
                        
                        // It's okay to call activate since we guarantee that only one thread is trying to grab
                        // a connection at a time.
                        rval.activate();
                        //sanityCheck();
                        
                        if (!rval.matches(binNames,protocol,server,port,authentication,trustStoreString))
                        {
                                // Destroy old connection.  That should free up space for a new creation.
                                rval.destroy();
                                // Return null to indicate that we can create a new connection now
                                return null;
                        }

                        // Existing entry matched.  Activate and return it.
                        return rval;
                }

                /** Note a new time for connection fetch for this pool.
                *@param currentTime is the time the fetch was started.
                */
                public synchronized void setLastFetchTime(long currentTime)
                {
                        if (currentTime > lastFetchTime)
                                lastFetchTime = currentTime;
                }
                
                /** Get the last fetch time.
                *@return the time.
                */
                public synchronized long getLastFetchTime()
                {
                        return lastFetchTime;
                }
                
                /** Count connections that are in use.
                *@return connections that are in use.
                */
                public synchronized int countConnections()
                {
                        return freePool.size() + inUseConnections;
                }
                
                /** Flush any idle connections.
                *@return true if the connection bin is now, in fact, empty.
                */
                public synchronized boolean flushIdleConnections(long idleTimeout)
                {
                        //sanityCheck();
                        
                        // We have to time out the pool connections.  When there are no pool connections
                        // left, AND the in-use counts are zero, we can delete the whole thing.
                        Iterator iter = freePool.keySet().iterator();
                        while (iter.hasNext())
                        {
                                ThrottledConnection tc = (ThrottledConnection)iter.next();
                                if (tc.flushIdleConnections(idleTimeout))
                                {
                                        // Can delete this connection, since it timed out.
                                        tc.activate();
                                        tc.destroy();
                                        iter = freePool.keySet().iterator();
                                }
                        }
                        
                        //sanityCheck();
                        
                        return (freePool.size() == 0 && inUseConnections == 0);
                }

                /** Grab a connection from the current pool.  This does not remove the connection from the pool;
                * it just sets it up so that later methods can do that.
                */
                protected ThrottledConnection getPoolConnection()
                {
                        if (freePool.size() == 0)
                                return null;
                        Iterator iter = freePool.keySet().iterator();
                        ThrottledConnection rval = (ThrottledConnection)iter.next();
                        return rval;
                }

                /** Check if a connection exists in the pool already.
                */
                protected boolean existsInPool(ThrottledConnection tc)
                {
                        return freePool.get(tc) != null;
                }
                
                public synchronized void sanityCheck()
                {
                        // Make sure all the connections in the current pool in fact have a reference to this bin.
                        Iterator iter = freePool.keySet().iterator();
                        while (iter.hasNext())
                        {
                                ThrottledConnection tc = (ThrottledConnection)iter.next();
                                tc.mustHaveReference(this);
                        }
                }
                
        }

        /** Throttles for a bin.
        * An instance of this class keeps track of the information needed to bandwidth throttle access
        * to a url belonging to a specific bin.
        *
        * In order to calculate
        * the effective "burst" fetches per second and bytes per second, we need to have some idea what the window is.
        * For example, a long hiatus from fetching could cause overuse of the server when fetching resumes, if the
        * window length is too long.
        *
        * One solution to this problem would be to keep a list of the individual fetches as records.  Then, we could
        * "expire" a fetch by discarding the old record.  However, this is quite memory consumptive for all but the
        * smallest intervals.
        *
        * Another, better, solution is to hook into the start and end of individual fetches.  These will, presumably, occur
        * at the fastest possible rate without long pauses spent doing something else.  The only complication is that
        * fetches may well overlap, so we need to "reference count" the fetches to know when to reset the counters.
        * For "fetches per second", we can simply make sure we "schedule" the next fetch at an appropriate time, rather
        * than keep records around.  The overall rate may therefore be somewhat less than the specified rate, but that's perfectly
        * acceptable.
        *
        * Some notes on the algorithms used to limit server bandwidth impact
        * ==================================================================
        *
        * In a single connection case, the algorithm we'd want to use works like this.  On the first chunk of a series,
        * the total length of time and the number of bytes are recorded.  Then, prior to each subsequent chunk, a calculation
        * is done which attempts to hit the bandwidth target by the end of the chunk read, using the rate of the first chunk
        * access as a way of estimating how long it will take to fetch those next n bytes.
        *
        * For a multi-connection case, which this is, it's harder to either come up with a good maximum bandwidth estimate,
        * and harder still to "hit the target", because simultaneous fetches will intrude.  The strategy is therefore:
        *
        * 1) The first chunk of any series should proceed without interference from other connections to the same server.
        *    The goal here is to get a decent quality estimate without any possibility of overwhelming the server.
        *
        * 2) The bandwidth of the first chunk is treated as the "maximum bandwidth per connection".  That is, if other
        *    connections are going on, we can presume that each connection will use at most the bandwidth that the first fetch
        *    took.  Thus, by generating end-time estimates based on this number, we are actually being conservative and
        *    using less server bandwidth.
        *
        * 3) For chunks that have started but not finished, we keep track of their size and estimated elapsed time in order to schedule when
        *    new chunks from other connections can start.
        *
        */
        protected static class ThrottleBin
        {
                /** This is the bin name which this throttle belongs to. */
                protected String binName;
                /** This is the reference count for this bin (which records active references) */
                protected int refCount = 0;
                /** The inverse rate estimate of the first fetch, in ms/byte */
                protected double rateEstimate = 0.0;
                /** Flag indicating whether a rate estimate is needed */
                protected boolean estimateValid = false;
                /** Flag indicating whether rate estimation is in progress yet */
                protected boolean estimateInProgress = false;
                /** The start time of this series */
                protected long seriesStartTime = -1L;
                /** Total actual bytes read in this series; this includes fetches in progress */
                protected long totalBytesRead = -1L;

                /** This object is used to gate access while the first chunk is being read */
                protected Integer firstChunkLock = new Integer(0);

                /** Constructor. */
                public ThrottleBin(String binName)
                {
                        this.binName = binName;
                }

                /** Get the bin name. */
                public String getBinName()
                {
                        return binName;
                }

                /** Note the start of a fetch operation for a bin.  Call this method just before the actual stream access begins.
                * May wait until schedule allows.
                */
                public void beginFetch()
                        throws InterruptedException
                {
                        synchronized (this)
                        {
                                if (refCount == 0)
                                {
                                        // Now, reset bandwidth throttling counters
                                        estimateValid = false;
                                        rateEstimate = 0.0;
                                        totalBytesRead = 0L;
                                        estimateInProgress = false;
                                        seriesStartTime = -1L;
                                }
                                refCount++;
                        }

                }

                /** Note the start of an individual byte read of a specified size.  Call this method just before the
                * read request takes place.  Performs the necessary delay prior to reading specified number of bytes from the server.
                */
                public void beginRead(int byteCount, double minimumMillisecondsPerBytePerServer)
                        throws InterruptedException
                {
                        long currentTime = System.currentTimeMillis();

                        synchronized (firstChunkLock)
                        {
                                while (estimateInProgress)
                                        firstChunkLock.wait();
                                if (estimateValid == false)
                                {
                                        seriesStartTime = currentTime;
                                        estimateInProgress = true;
                                        // Add these bytes to the estimated total
                                        synchronized (this)
                                        {
                                                totalBytesRead += (long)byteCount;
                                        }
                                        // Exit early; this thread isn't going to do any waiting
                                        return;
                                }
                        }

                        long waitTime = 0L;
                        synchronized (this)
                        {
                                // Add these bytes to the estimated total
                                totalBytesRead += (long)byteCount;

                                // Estimate the time this read will take, and wait accordingly
                                long estimatedTime = (long)(rateEstimate * (double)byteCount);

                                // Figure out how long the total byte count should take, to meet the constraint
                                long desiredEndTime = seriesStartTime + (long)(((double)totalBytesRead) * minimumMillisecondsPerBytePerServer);

                                // The wait time is the different between our desired end time, minus the estimated time to read the data, and the
                                // current time.  But it can't be negative.
                                waitTime = (desiredEndTime - estimatedTime) - currentTime;
                        }

                        if (waitTime > 0L)
                        {
                                if (Logging.connectors.isDebugEnabled())
                                        Logging.connectors.debug("WEB: Performing a read wait on bin '"+binName+"' of "+
                                                new Long(waitTime).toString()+" ms.");
                                Metacarta.sleep(waitTime);
                        }

                }

                /** Note the end of an individual read from the server.  Call this just after an individual read completes.
                * Pass the actual number of bytes read to the method.
                */
                public void endRead(int originalCount, int actualCount)
                {
                        long currentTime = System.currentTimeMillis();

                        synchronized (this)
                        {
                                totalBytesRead = totalBytesRead + (long)actualCount - (long)originalCount;
                        }

                        // Only one thread should get here if it's the first chunk, but we synchronize to be sure
                        synchronized (firstChunkLock)
                        {
                                if (estimateInProgress)
                                {
                                        if (actualCount == 0)
                                                // Didn't actually get any bytes, so use 0.0
                                                rateEstimate = 0.0;
                                        else
                                                rateEstimate = ((double)(currentTime - seriesStartTime))/(double)actualCount;
                                        estimateValid = true;
                                        estimateInProgress = false;
                                        firstChunkLock.notifyAll();
                                }
                        }
                }

                /** Note the end of a fetch operation.  Call this method just after the fetch completes.
                */
                public boolean endFetch()
                {
                        synchronized (this)
                        {
                                refCount--;
                                return (refCount == 0);
                        }

                }

        }

        // Where to record result info/file data
        protected final static String resultLogFile = "/common/web/resultlog";
        // Where to record the actual data
        protected final static String dataFileFolder = "/common/web/data/";
        
        // This is the one instance of the output class
        protected static DataRecorder dataRecorder = new DataRecorder();
        
        /** This class takes care of recording data and results for posterity */
        protected static class DataRecorder
        {
                protected int documentNumber = 0;
                
                public DataRecorder()
                {
                }
                
                public DataSession getSession(String url)
                        throws MetacartaException
                {
                        return new DataSession(this,url);
                }
                
                /** Atomically write resultlog record, returning data file name to use */
                public synchronized String writeResponseRecord(String url, int responseCode, ArrayList headerNames, ArrayList headerValues)
                        throws MetacartaException
                {
                        // Open log file
                        try
                        {
                                OutputStream os = new FileOutputStream(resultLogFile,true);
                                try
                                {
                                        OutputStreamWriter writer = new OutputStreamWriter(os,"utf-8");
                                        try
                                        {
                                                String documentName = Integer.toString(documentNumber++);
                                                writer.write("URI: "+url+"\n");
                                                writer.write("File: "+documentName+"\n");
                                                writer.write("Code: "+Integer.toString(responseCode)+"\n");
                                                int i = 0;
                                                while (i < headerNames.size())
                                                {
                                                        writer.write("Header: "+(String)headerNames.get(i)+":"+(String)headerValues.get(i)+"\n");
                                                        i++;
                                                }
                                                return documentName;
                                        }
                                        finally
                                        {
                                                writer.close();
                                        }
                                }
                                finally
                                {
                                        os.close();
                                }
                        }
                        catch (IOException e)
                        {
                                throw new MetacartaException("Error recording file info: "+e.getMessage(),e);
                        }
                                
                }
                
                
        }
        
        /** Helper class for the above */
        protected static class DataSession
        {
                protected DataRecorder dr;
                protected String url;
                protected int responseCode = 0;
                protected ArrayList headerNames = new ArrayList();
                protected ArrayList headerValues = new ArrayList();
                protected String documentName = null;
                
                public DataSession(DataRecorder dr, String url)
                {
                        this.dr = dr;
                        this.url = url;
                }
                
                public void setResponseCode(int responseCode)
                {
                        this.responseCode = responseCode;
                }
                
                public void addHeader(String headerName, String headerValue)
                {
                        headerNames.add(headerName);
                        headerValues.add(headerValue);
                }
                
                public void endHeader()
                        throws MetacartaException
                {
                        documentName = dr.writeResponseRecord(url,responseCode,headerNames,headerValues);
                }
                
                public void write(byte[] theBytes, int off, int length)
                        throws IOException
                {
                        if (documentName == null)
                                throw new IOException("Must end header before reading data!");
                        OutputStream os = new FileOutputStream(dataFileFolder+documentName,true);
                        try
                        {
                                os.write(theBytes,off,length);
                        }
                        finally
                        {
                                os.close();
                        }
                }
                
        }


        /** Throttled connections.  Each instance of a connection describes the bins to which it belongs,
        * along with the actual open connection itself, and the last time the connection was used. */
        protected static class ThrottledConnection implements IThrottledConnection
        {
                /** The connection has resolved pointers to the ConnectionBin structures that manage pool
                * maximums.  These are ONLY valid when the connection is actually in the pool. */
                protected ConnectionBin[] connectionBinArray;
                /** The connection has resolved pointers to the ThrottleBin structures that help manage
                * bandwidth throttling. */
                protected ThrottleBin[] throttleBinArray;
                /** These are the bandwidth limits, per bin */
                protected double[] minMillisecondsPerByte;
                /** Is the connection considered "active"? */
                protected boolean isActive;
                /** If not active, this is when it went inactive */
                protected long inactiveTime = 0L;

                /** Protocol */
                protected String protocol;
                /** Server */
                protected String server;
                /** Port */
                protected int port;
                /** Authentication */
                protected PageCredentials authentication;
                /** Trust store */
                protected IKeystoreManager trustStore;
                /** Trust store string */
                protected String trustStoreString;
                
                /** The http connection manager.  The pool is of size 1.  */
                protected MultiThreadedHttpConnectionManager connManager = null;
                /** The method object */
                protected HttpMethodBase fetchMethod = null;
                /** The error trace, if any */
                protected Throwable throwable = null;
                /** The current URL being fetched */
                protected String myUrl = null;
                /** The status code fetched, if any */
                protected int statusCode = FETCH_NOT_TRIED;
                /** The kind of fetch we are doing */
                protected String fetchType = null;
                /** The current bytes in the current fetch */
                protected long fetchCounter = 0L;
                /** The start of the current fetch */
                protected long startFetchTime = -1L;
                /** The cookies from the last fetch */
                protected LoginCookies lastFetchCookies = null;

                /** Protocol socket factory */
                protected ProtocolSocketFactory secureSocketFactory = null;
                protected ProtocolFactory myFactory = null;
                
                
                /** Hack added to record all access data from current crawler */
                protected DataSession dataSession = null;

                /** Constructor.  Create a connection with a specific server and port, and
                * register it as active against all bins. */
                public ThrottledConnection(String protocol, String server, int port, PageCredentials authentication,
                        ProtocolFactory myFactory, String trustStoreString, ConnectionBin[] connectionBins)
                {
                        this.protocol = protocol;
                        this.server = server;
                        this.port = port;
                        this.authentication = authentication;
                        this.myFactory = myFactory;
                        this.trustStoreString = trustStoreString;
                        this.connectionBinArray = connectionBins;
                        this.throttleBinArray = new ThrottleBin[connectionBins.length];
                        this.minMillisecondsPerByte = new double[connectionBins.length];
                        this.isActive = true;
                        int i = 0;
                        while (i < connectionBins.length)
                        {
                                connectionBins[i].noteConnectionCreation();
                                // We don't keep throttle bin references around, since these are transient
                                throttleBinArray[i] = null;
                                minMillisecondsPerByte[i] = 0.0;
                                i++;
                        }
                        

                }

                public void mustHaveReference(ConnectionBin cb)
                {
                        int i = 0;
                        while (i < connectionBinArray.length)
                        {
                                if (cb == connectionBinArray[i])
                                        return;
                                i++;
                        }
                        String msg = "Connection bin "+cb.toString()+" owns connection "+this.toString()+" for "+protocol+server+":"+port+
                                " but there is no back reference!";
                        Logging.connectors.error(msg);
                        System.out.println(msg);
                        new Exception(msg).printStackTrace();
                        System.exit(3);
                        //throw new RuntimeException(msg);
                }
                
                /** See if this instances matches a given server and port. */
                public boolean matches(ConnectionBin[] bins, String protocol, String server, int port, PageCredentials authentication,
                        String trustStoreString)
                {
                        if (this.trustStoreString == null && trustStoreString != null)
                                return false;
                        if (this.trustStoreString != null && trustStoreString == null)
                                return false;
                        if (this.trustStoreString != null && !this.trustStoreString.equals(trustStoreString))
                                return false;
                        
                        if (this.authentication == null && authentication != null)
                                return false;
                        if (this.authentication != null && authentication == null)
                                return false;
                        if (this.authentication != null && !this.authentication.equals(authentication))
                                return false;
                        
                        if (this.connectionBinArray.length != bins.length || !this.protocol.equals(protocol) || !this.server.equals(server) || this.port != port)
                                return false;
                        int i = 0;
                        while (i < bins.length)
                        {
                                if (connectionBinArray[i] != bins[i])
                                        return false;
                                i++;
                        }
                        return true;
                }

                /** Activate the connection. */
                public void activate()
                {
                        isActive = true;
                        int i = 0;
                        while (i < connectionBinArray.length)
                        {
                                connectionBinArray[i++].takeFromPool(this);
                        }
                }

                /** Set up the connection.  This allows us to feed all bins the correct bandwidth limit info.
                */
                public void setup(ThrottleDescription description)
                {
                        // Go through all bins, and set up the current limits.
                        int i = 0;
                        while (i < connectionBinArray.length)
                        {
                                String binName = connectionBinArray[i].getBinName();
                                minMillisecondsPerByte[i] = description.getMinimumMillisecondsPerByte(binName);
                                i++;
                        }
                }

                /** Do periodic bookkeeping.
                *@return true if the connection is no longer valid, and can be removed. */
                public boolean flushIdleConnections(long idleTimeout)
                {
                        if (isActive)
                                return false;

                        if (connManager != null)
                        {
                                connManager.closeIdleConnections(idleTimeout);
                                connManager.deleteClosedConnections();
                                // Need to determine if there's a valid connection in the connection manager still, or if it is empty.
                                return connManager.getConnectionsInPool() == 0;
                        }
                        else
                                return true;
                }

                /** Log the fetch of a number of bytes, from within a stream. */
                public void logFetchCount(int count)
                {
                        fetchCounter += (long)count;
                }

                /** Begin a read operation, from within a stream */
                public void beginRead(int len)
                        throws InterruptedException
                {
                        // Consult with throttle bins
                        int i = 0;
                        while (i < throttleBinArray.length)
                        {
                                throttleBinArray[i].beginRead(len,minMillisecondsPerByte[i]);
                                i++;
                        }
                }

                /** End a read operation, from within a stream */
                public void endRead(int origLen, int actualAmt)
                {
                        // Consult with throttle bins
                        int i = 0;
                        while (i < throttleBinArray.length)
                        {
                                throttleBinArray[i].endRead(origLen,actualAmt);
                                i++;
                        }
                }

                /** Destroy the connection forever */
                protected void destroy()
                {
                        if (isActive == false)
                                throw new RuntimeException("Trying to destroy an inactive connection");

                        // Kill the actual connection object.
                        if (connManager != null)
                        {
                                connManager.shutdown();
                                connManager = null;
                        }

                        // Call all the bins this belongs to, and decrement the in-use count.
                        int i = 0;
                        while (i < connectionBinArray.length)
                        {
                                ConnectionBin cb = connectionBinArray[i++];
                                cb.noteConnectionDestruction();
                        }
                }


                /** Begin the fetch process.
                * @param fetchType is a short descriptive string describing the kind of fetch being requested.  This
                *	 is used solely for logging purposes.
                */
                public void beginFetch(String fetchType)
                        throws MetacartaException
                {
                    try
                    {
                        this.fetchType = fetchType;
                        this.fetchCounter = 0L;
                        // Find or create the needed throttle bins
                        int i = 0;
                        while (i < throttleBinArray.length)
                        {
                                // Access the bins as we need them, and drop them when ref count goes to zero
                                String binName = connectionBinArray[i].getBinName();
                                ThrottleBin tb;
                                synchronized (throttleBins)
                                {
                                        tb = (ThrottleBin)throttleBins.get(binName);
                                        if (tb == null)
                                        {
                                                tb = new ThrottleBin(binName);
                                                throttleBins.put(binName,tb);
                                        }
                                        tb.beginFetch();
                                }
                                throttleBinArray[i] = tb;
                                i++;
                        }
                    }
                    catch (InterruptedException e)
                    {
                        throw new MetacartaException("Interrupted",MetacartaException.INTERRUPTED);
                    }
                }

                protected static class ExecuteMethodThread extends Thread
                {
                        protected HttpClient client;
                        protected HostConfiguration hostConfiguration;
                        protected HttpMethodBase executeMethod;
                        protected Throwable exception = null;
                        protected int rval = 0;

                        public ExecuteMethodThread(HttpClient client, HostConfiguration hostConfiguration, HttpMethodBase executeMethod)
                        {
                                super();
                                setDaemon(true);
                                this.client = client;
                                this.hostConfiguration = hostConfiguration;
                                this.executeMethod = executeMethod;
                        }
                                
                        public void run()
                        {
                                try
                                {
                                        // Call the execute method appropriately
                                        rval = client.executeMethod(hostConfiguration,executeMethod,null);
                                }
                                catch (Throwable e)
                                {
                                        this.exception = e;
                                }
                        }
                                
                        public Throwable getException()
                        {
                                return exception;
                        }
                                
                        public int getResponse()
                        {
                                return rval;
                        }
                }

                /** Execute the fetch and get the return code.  This method uses the
                * standard logging mechanism to keep track of the fetch attempt.  It also
                * signals the following conditions: ServiceInterruption (if a dynamic
                * error occurs), or MetacartaException if a fatal error occurs, or nothing if
                * a standard protocol error occurs.
                * Note that, for proxies etc, the idea is for this fetch request to handle whatever
                * redirections are needed to support proxies.
                * @param urlPath is the path part of the url, e.g. "/robots.txt"
                * @param userAgent is the value of the userAgent header to use.
                * @param from is the value of the from header to use.
                * @param connectionTimeoutMilliseconds is the maximum number of milliseconds to wait on socket connect.
                * @param redirectOK should be set to true if you want redirects to be automatically followed.
                * @param host is the value to use as the "Host" header, or null to use the default.
                * @param formData describes additional form arguments and how to fetch the page.
                * @param loginCookies describes the cookies that should be in effect for this page fetch.
                */
                public void executeFetch(String urlPath, String userAgent, String from, int connectionTimeoutMilliseconds,
                        int socketTimeoutMilliseconds, boolean redirectOK, String host, FormData formData,
                        LoginCookies loginCookies)
                        throws MetacartaException, ServiceInterruption
                {
                    StringBuffer sb = new StringBuffer(protocol);
                    sb.append("://").append(server);
                    if (port != -1)
                    {
                        if (!(protocol.equals("http") && port == 80) &&
                            !(protocol.equals("https") && port == 443))
                                sb.append(":").append(Integer.toString(port));
                    }
                    sb.append(urlPath);
                    String fetchUrl = sb.toString();
                    if (host != null)
                    {
                            sb.setLength(0);
                            sb.append(protocol).append("://").append(host);
                            if (port != -1)
                            {
                                if (!(protocol.equals("http") && port == 80) &&
                                    !(protocol.equals("https") && port == 443))
                                        sb.append(":").append(Integer.toString(port));
                            }
                            sb.append(urlPath);
                            myUrl = sb.toString();
                    }
                    else
                        myUrl = fetchUrl;

                    if (recordEverything)
                        // Start a new data session
                        dataSession = dataRecorder.getSession(myUrl);

                    try
                    {
                        if (connManager == null)
                                connManager = new MultiThreadedHttpConnectionManager();
                        HttpConnectionManagerParams httpConParam = connManager.getParams();
                        httpConParam.setDefaultMaxConnectionsPerHost(1);
                        httpConParam.setMaxTotalConnections(1);
                        httpConParam.setConnectionTimeout(connectionTimeoutMilliseconds);
                        httpConParam.setSoTimeout(socketTimeoutMilliseconds);
                        connManager.setParams(httpConParam);

                        long startTime = 0L;
                        if (Logging.connectors.isDebugEnabled())
                        {
                                startTime = System.currentTimeMillis();
                                Logging.connectors.debug("WEB: Waiting for an HttpClient object");
                        }


                        HttpClient client = new HttpClient(connManager);
                        // Permit circular redirections, because that is how some sites set cookies
                        client.getParams().setParameter(org.apache.commons.httpclient.params.HttpClientParams.ALLOW_CIRCULAR_REDIRECTS,new Boolean(true));
                        // If there are redirects, this is essential to make sure the right socket factory gets used
                        client.getParams().setParameter(org.apache.commons.httpclient.params.HttpClientParams.PROTOCOL_FACTORY,myFactory);

                        HostConfiguration clientConf = new HostConfiguration();
                        // clientConf.setLocalAddress(currentAddr);

                        // Set up protocol to use
                        clientConf.setParams(new HostParams());
                        clientConf.setHost(server,port,myFactory.getProtocol(protocol));

                        if (Logging.connectors.isDebugEnabled())
                                Logging.connectors.debug("WEB: Got an HttpClient object after "+new Long(System.currentTimeMillis()-startTime).toString()+" ms.");

                        startFetchTime = System.currentTimeMillis();

                        int pageFetchMethod = FormData.SUBMITMETHOD_GET;
                        if (formData != null)
                                pageFetchMethod = formData.getSubmitMethod();
                        switch (pageFetchMethod)
                        {
                        case FormData.SUBMITMETHOD_GET:
                                // MUST be just the path, or apparently we wind up resetting the HostConfiguration
                                // Add additional parameters to url path
                                String fullUrlPath;
                                if (formData != null)
                                {
                                        StringBuffer psb = new StringBuffer(urlPath);
                                        Iterator iter = formData.getElementIterator();
                                        char appendChar;
                                        if (urlPath.indexOf("?") == -1)
                                                appendChar = '?';
                                        else
                                                appendChar = '&';
                                        while (iter.hasNext())
                                        {
                                                FormDataElement e = (FormDataElement)iter.next();
                                                psb.append(appendChar);
                                                appendChar = '&';
                                                String param = e.getElementName();
                                                String value = e.getElementValue();
                                                psb.append(java.net.URLEncoder.encode(param,"utf-8"));
                                                if (value != null)
                                                        psb.append('=').append(java.net.URLEncoder.encode(value,"utf-8"));
                                        }
                                        fullUrlPath = psb.toString();
                                }
                                else
                                {
                                        fullUrlPath = urlPath;
                                }
                                // Hack; apparently httpclient treats // as a protocol specifier and so it rips off the first section of the path in that case.
                                while (fullUrlPath.startsWith("//"))
                                    fullUrlPath = fullUrlPath.substring(1);
                                if (Logging.connectors.isDebugEnabled())
                                    Logging.connectors.debug("WEB: Get method for '"+fullUrlPath+"'");
                                fetchMethod = new GetMethod(fullUrlPath);
                                break;
                        case FormData.SUBMITMETHOD_POST:
                                if (Logging.connectors.isDebugEnabled())
                                    Logging.connectors.debug("WEB: Post method for '"+urlPath+"'");
                                // MUST be just the path, or apparently we wind up resetting the HostConfiguration
                                PostMethod postMethod = new PostMethod(urlPath);
                                // Add parameters to post variables
                                if (formData != null)
                                {
                                        Iterator iter = formData.getElementIterator();
                                        while (iter.hasNext())
                                        {
                                                FormDataElement e = (FormDataElement)iter.next();
                                                String param = e.getElementName();
                                                String value = e.getElementValue();
                                                if (Logging.connectors.isDebugEnabled())
                                                    Logging.connectors.debug("WEB: Post parameter name '"+param+"' value '"+value+"' for '"+urlPath+"'");
                                                postMethod.addParameter(param,value);
                                        }
                                }
                                fetchMethod = postMethod;
                                break;
                        default:
                                throw new MetacartaException("Illegal method type: "+Integer.toString(pageFetchMethod));
                        }
                        
                        // Set all appropriate headers and parameters
                        fetchMethod.setRequestHeader("User-Agent",userAgent);
                        fetchMethod.setRequestHeader("From",from);
                        HttpMethodParams params = fetchMethod.getParams();
                        if (host != null)
                        {
                                if (Logging.connectors.isDebugEnabled())
                                        Logging.connectors.debug("WEB: For "+myUrl+", setting virtual host to "+host);
                                params.setVirtualHost(host);
                        }
                        params.setSoTimeout(socketTimeoutMilliseconds);
                        params.setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
                        params.setParameter(HttpMethodParams.SINGLE_COOKIE_HEADER,new Boolean(true));
                        fetchMethod.setParams(params);
                        fetchMethod.setFollowRedirects(redirectOK);

                        // Clear all current cookies
                        HttpState state = client.getState();
                        state.clearCookies();
                        
                        // If we have any cookies to set, set them.
                        if (loginCookies != null)
                        {
                                if (Logging.connectors.isDebugEnabled())
                                    Logging.connectors.debug("WEB: Adding "+Integer.toString(loginCookies.getCookieCount())+" cookies for '"+urlPath+"'");
                                int h = 0;
                                while (h < loginCookies.getCookieCount())
                                {
                                        state.addCookie(loginCookies.getCookie(h++));
                                }
                        }
                        
                        // Copy out the current cookies, in case the fetch fails
                        lastFetchCookies = loginCookies;

                        // Set up authentication to use
                        if (authentication != null)
                        {
                                if (Logging.connectors.isDebugEnabled())
                                        Logging.connectors.debug("WEB: For "+myUrl+", discovered matching authentication credentials");
                                state.setCredentials(AuthScope.ANY, authentication.makeCredentialsObject(host));
                        }
                        
                        // Set the state.  May not be necessary, but I'd rather not depend on undocumented httpclient implementation details.
                        client.setState(state);

                        // Fire it off!
                        try
                        {
                                ExecuteMethodThread t = new ExecuteMethodThread(client,clientConf,fetchMethod);
                                try
                                {
                                        t.start();
                                        t.join();
                                        Throwable thr = t.getException();
                                        if (thr != null)
                                        {
                                                throw thr;
                                        }
                                        statusCode = t.getResponse();
                                        if (recordEverything)
                                                dataSession.setResponseCode(statusCode);
                                }
                                catch (InterruptedException e)
                                {
                                        t.interrupt();
                                        // We need the caller to abandon any connections left around, so rethrow in a way that forces them to process the event properly.
                                        throw e;
                                }

                                // At least we didn't get an exception!  Copy out the current cookies for later reference.
                                lastFetchCookies = new CookieSet(client.getState().getCookies());
                                
                                switch (statusCode)
                                {
                                case HttpStatus.SC_REQUEST_TIMEOUT:
                                case HttpStatus.SC_GATEWAY_TIMEOUT:
                                case HttpStatus.SC_SERVICE_UNAVAILABLE:
                                        // Temporary service interruption
                                        // May want to make the retry time a parameter someday
                                        long currentTime = System.currentTimeMillis();
                                        throw new ServiceInterruption("Http response temporary error on '"+myUrl+"': "+Integer.toString(statusCode),new MetacartaException("Service unavailable (code "+Integer.toString(statusCode)+")"),
                                                currentTime + TIME_2HRS, currentTime + TIME_1DAY, -1, false);
                                case HttpStatus.SC_UNAUTHORIZED:
                                case HttpStatus.SC_USE_PROXY:
                                case HttpStatus.SC_OK:
                                case HttpStatus.SC_GONE:
                                case HttpStatus.SC_NOT_FOUND:
                                case HttpStatus.SC_BAD_GATEWAY:
                                case HttpStatus.SC_BAD_REQUEST:
                                case HttpStatus.SC_FORBIDDEN:
                                case HttpStatus.SC_INTERNAL_SERVER_ERROR:
                                default:
                                        return;
                                }

                        }
                        catch (java.net.SocketTimeoutException e)
                        {
                                throwable = e;
                                long currentTime = System.currentTimeMillis();
                                throw new ServiceInterruption("Timed out waiting for IO for '"+myUrl+"': "+e.getMessage(), e, currentTime + TIME_5MIN,
                                        currentTime + TIME_2HRS,-1,false);
                        }
                        catch (org.apache.commons.httpclient.ConnectTimeoutException e)
                        {
                                throwable = e;
                                long currentTime = System.currentTimeMillis();
                                throw new ServiceInterruption("Timed out waiting for connection for '"+myUrl+"': "+e.getMessage(), e, currentTime + TIME_5MIN,
                                        currentTime + TIME_2HRS,-1,false);
                        }
                        catch (InterruptedIOException e)
                        {
                                //Logging.connectors.warn("IO interruption seen",e);
                                throwable = new MetacartaException("Interrupted: "+e.getMessage(),e);
                                statusCode = FETCH_INTERRUPTED;
                                throw new MetacartaException("Interrupted",MetacartaException.INTERRUPTED);
                        }
                        catch (org.apache.commons.httpclient.RedirectException e)
                        {
                                throwable = e;
                                statusCode = FETCH_CIRCULAR_REDIRECT;
                                if (recordEverything)
                                        dataSession.setResponseCode(statusCode);
                                return;
                        }
                        catch (org.apache.commons.httpclient.NoHttpResponseException e)
                        {
                                throwable = e;
                                long currentTime = System.currentTimeMillis();
                                throw new ServiceInterruption("Timed out waiting for response for '"+myUrl+"': "+e.getMessage(), e, currentTime + TIME_15MIN,
                                        currentTime + TIME_2HRS,-1,false);
                        }
                        catch (java.net.ConnectException e)
                        {
                                throwable = e;
                                long currentTime = System.currentTimeMillis();
                                throw new ServiceInterruption("Timed out waiting for a connection for '"+myUrl+"': "+e.getMessage(), e, currentTime + TIME_2HRS,
                                        currentTime + TIME_6HRS,-1,false);
                        }
                        catch (IOException e)
                        {
                                // Treat this as a bad url.  We don't know what happened, but it isn't something we are going to naively
                                // retry on.
                                throwable = e;
                                statusCode = FETCH_IO_ERROR;
                                if (recordEverything)
                                        dataSession.setResponseCode(statusCode);
                                return;
                        }

                    }
                    catch (InterruptedException e)
                    {
                        // Drop the current connection, and in fact the whole pool, on the floor.
                        fetchMethod = null;
                        connManager = null;
                        throwable = new MetacartaException("Interrupted: "+e.getMessage(),e);
                        statusCode = FETCH_INTERRUPTED;
                        throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
                    }
                    catch (IllegalArgumentException e)
                    {
                        throwable = new MetacartaException("Illegal URI: '"+myUrl+"'",e);
                        statusCode = FETCH_BAD_URI;
                        if (recordEverything)
                                dataSession.setResponseCode(statusCode);
                        return;
                    }
                    catch (IllegalStateException e)
                    {
                        throwable = new MetacartaException("Illegal state while fetching URI: '"+myUrl+"'",e);
                        statusCode = FETCH_SEQUENCE_ERROR;
                        if (recordEverything)
                                dataSession.setResponseCode(statusCode);
                        return;
                    }
                    catch (ServiceInterruption e)
                    {
                        throw e;
                    }
                    catch (MetacartaException e)
                    {
                        throw e;
                    }
                    catch (Throwable e)
                    {
                        Logging.connectors.debug("WEB: Caught an unexpected exception: "+e.getMessage(),e);
                        throwable = e;
                        statusCode = FETCH_UNKNOWN_ERROR;
                        if (recordEverything)
                                dataSession.setResponseCode(statusCode);
                        return;
                    }

                }

                /** Get the http response code.
                *@return the response code.  This is either an HTTP response code, or one of the codes above.
                */
                public int getResponseCode()
                        throws MetacartaException, ServiceInterruption
                {
                        return statusCode;
                }

                /** Get the last fetch cookies.
                *@return the cookies now in effect from the last fetch.
                */
                public LoginCookies getLastFetchCookies()
                        throws MetacartaException, ServiceInterruption
                {
                        return lastFetchCookies;
                }
                
                /** Get a specified response header, if it exists.
                *@param headerName is the name of the header.
                *@return the header value, or null if it doesn't exist.
                */
                public String getResponseHeader(String headerName)
                        throws MetacartaException, ServiceInterruption
                {
                        Header h = fetchMethod.getResponseHeader(headerName);
                        if (h == null)
                                return null;
                        if (recordEverything)
                                dataSession.addHeader(headerName,h.getValue());
                        return h.getValue();
                }

                /** Get the response input stream.  It is the responsibility of the caller
                * to close this stream when done.
                */
                public InputStream getResponseBodyStream()
                        throws MetacartaException, ServiceInterruption
                {
                        if (fetchMethod == null)
                                throw new MetacartaException("Attempt to get a response when there is no method");
                        try
                        {
                                if (recordEverything)
                                        dataSession.endHeader();
                                InputStream bodyStream = fetchMethod.getResponseBodyAsStream();
                                if (bodyStream == null)
                                {
                                        Logging.connectors.debug("Web: Couldn't set up response stream for '"+myUrl+"', retrying");
                                        throw new ServiceInterruption("Failed to set up body response stream for "+myUrl,null,TIME_5MIN,-1L,2,false);
                                }
                                return new ThrottledInputstream(this,bodyStream,dataSession);
                        }
                        catch (java.net.SocketTimeoutException e)
                        {
                                Logging.connectors.debug("Web: Socket timeout exception setting up response stream for '"+myUrl+"', retrying");
                                throw new ServiceInterruption("Socket timeout exception setting up response stream: "+e.getMessage(),e,System.currentTimeMillis()+TIME_5MIN,-1L,2,false);
                        }
                        catch (org.apache.commons.httpclient.ConnectTimeoutException e)
                        {
                                Logging.connectors.debug("Web: Connect timeout exception setting up response stream for '"+myUrl+"', retrying");
                                throw new ServiceInterruption("Connect timeout exception setting up response stream: "+e.getMessage(),e,System.currentTimeMillis()+TIME_5MIN,-1L,2,false);
                        }
                        catch (InterruptedIOException e)
                        {
                                //Logging.connectors.warn("IO interruption seen: "+e.getMessage(),e);
                                throw new MetacartaException("Interrupted: "+e.getMessage(),e,MetacartaException.INTERRUPTED);
                        }
                        catch (IOException e)
                        {
                                Logging.connectors.debug("Web: IO exception setting up response stream for '"+myUrl+"', retrying");
                                throw new ServiceInterruption("IO exception setting up response stream: "+e.getMessage(),e,System.currentTimeMillis()+TIME_5MIN,-1L,2,false);
                        }
                        catch (IllegalStateException e)
                        {
                                Logging.connectors.debug("Web: State error getting response body for '"+myUrl+"', retrying");
                                throw new ServiceInterruption("State error getting response body: "+e.getMessage(),e,TIME_5MIN,-1L,2,false);
                        }
                }

                /** Note that the connection fetch was interrupted by something.
                */
                public void noteInterrupted(Throwable e)
                {
                        if (statusCode > 0)
                        {
                                throwable = new MetacartaException("Interrupted: "+e.getMessage(),e);
                                statusCode = FETCH_INTERRUPTED;
                        }
                }

                /** Done with the fetch.  Call this when the fetch has been completed.  A log entry will be generated
                * describing what was done.
                */
                public void doneFetch(IVersionActivity activities)
                        throws MetacartaException
                {
                        if (fetchType != null)
                        {
                                long endTime = System.currentTimeMillis();
                                int i = 0;
                                while (i < throttleBinArray.length)
                                {
                                        synchronized (throttleBins)
                                        {
                                                if (throttleBinArray[i].endFetch())
                                                        throttleBins.remove(throttleBinArray[i].getBinName());
                                        }
                                        throttleBinArray[i] = null;
                                        i++;
                                }

                                activities.recordActivity(new Long(startFetchTime),WebcrawlerConnector.ACTIVITY_FETCH,
                                        new Long(fetchCounter),myUrl,Integer.toString(statusCode),(throwable==null)?null:throwable.getMessage(),null);

                                Logging.connectors.info("WEB: FETCH "+fetchType+"|"+myUrl+"|"+new Long(startFetchTime).toString()+"+"+new Long(endTime-startFetchTime).toString()+"|"+
                                        Integer.toString(statusCode)+"|"+new Long(fetchCounter).toString()+"|"+((throwable==null)?"":(throwable.getClass().getName()+"| "+throwable.getMessage())));
                                if (throwable != null)
                                {
                                        if (Logging.connectors.isDebugEnabled())
                                                Logging.connectors.debug("WEB: Fetch exception for '"+myUrl+"'",throwable);
                                }


                                // Clear out all the parameters
                                if (fetchMethod != null)
                                {
                                        try
                                        {
                                                fetchMethod.releaseConnection();
                                        }
                                        catch (IllegalStateException e)
                                        {
                                                // looks like the fetch method didn't have one, or it was already released.  Just eat the exception.
                                        }
                                        fetchMethod = null;
                                }
                                throwable = null;
                                startFetchTime = -1L;
                                myUrl = null;
                                statusCode = FETCH_NOT_TRIED;
                                lastFetchCookies = null;
                                fetchType = null;
                        }

                }

                /** Close the connection.  Call this to end this server connection.
                */
                public void close()
                        throws MetacartaException
                {
                        synchronized (poolLock)
                        {
                                // Verify that all the connections that exist are in fact sane
                                synchronized (connectionBins)
                                {
                                        Iterator iter = connectionBins.keySet().iterator();
                                        while (iter.hasNext())
                                        {
                                                String connectionName = (String)iter.next();
                                                ConnectionBin cb = (ConnectionBin)connectionBins.get(connectionName);
                                                //cb.sanityCheck();
                                        }
                                }

                                // Leave the connection alive, but mark it as inactive, and return it to the appropriate pools.
                                isActive = false;
                                inactiveTime = System.currentTimeMillis();
                                int i = 0;
                                while (i < connectionBinArray.length)
                                {
                                        connectionBinArray[i++].addToPool(this);
                                }
                                // Verify that all the connections that exist are in fact sane
                                synchronized (connectionBins)
                                {
                                        Iterator iter = connectionBins.keySet().iterator();
                                        while (iter.hasNext())
                                        {
                                                String connectionName = (String)iter.next();
                                                ConnectionBin cb = (ConnectionBin)connectionBins.get(connectionName);
                                                //cb.sanityCheck();
                                        }
                                }
                                // Wake up everything waiting on the pool lock
                                poolLock.notifyAll();
                        }
                }

        }
        

        /** This class throttles an input stream based on the specified byte rate parameters.  The
        * throttling takes place across all streams that are open to the server in question.
        */
        protected static class ThrottledInputstream extends InputStream
        {
                /** Stream throttling parameters */
                protected double minimumMillisecondsPerBytePerServer;
                /** The throttled connection we belong to */
                protected ThrottledConnection throttledConnection;
                /** The stream we are wrapping. */
                protected InputStream inputStream;

                protected DataSession dataSession;

                /** Constructor.
                */
                public ThrottledInputstream(ThrottledConnection connection, InputStream is, DataSession dataSession)
                {
                        this.throttledConnection = connection;
                        this.inputStream = is;
                        this.dataSession = dataSession;
                }

                /** Read a byte.
                */
                public int read()
                        throws IOException
                {
                        byte[] byteArray = new byte[1];
                        int count = read(byteArray,0,1);
                        if (count == -1)
                                return count;
                        return (int)byteArray[0];
                }

                /** Read lots of bytes.
                */
                public int read(byte[] b)
                        throws IOException
                {
                        return read(b,0,b.length);
                }

                /** Read lots of specific bytes.
                */
                public int read(byte[] b, int off, int len)
                        throws IOException
                {
                        int totalCount = 0;
                        while (len > ThrottledFetcher.READ_CHUNK_LENGTH)
                        {
                                int amt = basicRead(b,off,ThrottledFetcher.READ_CHUNK_LENGTH,totalCount);
                                if (amt == -1)
                                {
                                        if (totalCount == 0)
                                                return amt;
                                        return totalCount;
                                }
                                totalCount += amt;
                                off += amt;
                                len -= amt;
                        }
                        if (len > 0)
                        {
                                int amt = basicRead(b,off,len,totalCount);
                                if (amt == -1)
                                {
                                        if (totalCount == 0)
                                                return amt;
                                        return totalCount;
                                }
                                return totalCount + amt;
                        }
                        return totalCount;
                }

                /** Basic read, which uses the server object to throttle activity.
                */
                protected int basicRead(byte[] b, int off, int len, int totalSoFar)
                        throws IOException
                {
                        try
                        {
                                throttledConnection.beginRead(len);
                                int amt = 0;
                                try
                                {
                                        amt = inputStream.read(b,off,len);
                                        if (recordEverything && amt != -1)
                                                dataSession.write(b,off,amt);
                                        return amt;
                                }
                                finally
                                {
                                        if (amt == -1)
                                                throttledConnection.endRead(len,0);
                                        else
                                        {
                                                throttledConnection.endRead(len,amt);
                                                throttledConnection.logFetchCount(amt);
                                        }
                                }
                        }
                        catch (InterruptedException e)
                        {
                                InterruptedIOException e2 = new InterruptedIOException("Interrupted");
                                e2.bytesTransferred = totalSoFar;
                                throw e2;
                        }
                }

                /** Skip
                */
                public long skip(long n)
                        throws IOException
                {
                        // Not sure whether we should bother doing anything with this; it's not used.
                        return inputStream.skip(n);
                }

                /** Get available.
                */
                public int available()
                        throws IOException
                {
                        return inputStream.available();
                }

                /** Mark.
                */
                public void mark(int readLimit)
                {
                        inputStream.mark(readLimit);
                }

                /** Reset.
                */
                public void reset()
                        throws IOException
                {
                        inputStream.reset();
                }

                /** Check if mark is supported.
                */
                public boolean markSupported()
                {
                        return inputStream.markSupported();
                }

                /** Close.
                */
                public void close()
                        throws IOException
                {
                        try
                        {
                                inputStream.close();
                        }
                        catch (java.net.SocketTimeoutException e)
                        {
                                Logging.connectors.debug("Socket timeout exception trying to close connection: "+e.getMessage(),e);
                        }
                        catch (org.apache.commons.httpclient.ConnectTimeoutException e)
                        {
                                Logging.connectors.debug("Socket connection timeout exception trying to close connection: "+e.getMessage(),e);
                        }
                        catch (InterruptedIOException e)
                        {
                                throw e;
                        }
                        catch (java.net.SocketException e)
                        {
                                Logging.connectors.debug("Connection reset while I was closing it: "+e.getMessage(),e);
                        }
                        catch (IOException e)
                        {
                                Logging.connectors.debug("IO Exception trying to close connection: "+e.getMessage(),e);
                        }
                }

        }

        /** Pool exception class */
        protected static class PoolException extends Exception
        {
                public PoolException(String message)
                {
                        super(message);
                }
        }

        /** Wait exception class */
        protected static class WaitException extends Exception
        {
                protected long amt;
                
                public WaitException(long amt)
                {
                        super("Wait needed");
                        this.amt = amt;
                }
                
                public long getWaitAmount()
                {
                        return amt;
                }
        }
                        
        /** HTTPClient secure socket factory, which implements SecureProtocolSocketFactory
        */
        protected static class WebSecureSocketFactory implements SecureProtocolSocketFactory
        {
                /** This is the javax.net socket factory.
                */
                protected javax.net.ssl.SSLSocketFactory socketFactory;

                /** Constructor */
                public WebSecureSocketFactory(javax.net.ssl.SSLSocketFactory socketFactory)
                {
                        this.socketFactory = socketFactory;
                }

                public Socket createSocket(
                        String host,
                        int port,
                        InetAddress clientHost,
                        int clientPort)
                        throws IOException, UnknownHostException
                {
                        return socketFactory.createSocket(
                            host,
                            port,
                            clientHost,
                            clientPort
                        );
                }

                public Socket createSocket(
                        final String host,
                        final int port,
                        final InetAddress localAddress,
                        final int localPort,
                        final HttpConnectionParams params
                    ) throws IOException, UnknownHostException, ConnectTimeoutException
                {
                        if (params == null)
                        {
                            throw new IllegalArgumentException("Parameters may not be null");
                        }
                        int timeout = params.getConnectionTimeout();
                        if (timeout == 0)
                        {
                            return createSocket(host, port, localAddress, localPort);
                        }
                        else
                        {
                            // We need to implement a connection timeout somehow - probably with a new thread.
                            SocketCreateThread thread = new SocketCreateThread(socketFactory,host,port,localAddress,localPort);
                            thread.start();
                            try
                            {
                                // Wait for thread to complete for only a certain amount of time!
                                thread.join(timeout);
                                // If join() times out, then the thread is going to still be alive.
                                if (thread.isAlive())
                                {
                                        // Kill the thread - not that this will necessarily work, but we need to try
                                        thread.interrupt();
                                        throw new ConnectTimeoutException("Secure connection timed out");
                                }
                                // The thread terminated.  Throw an error if there is one, otherwise return the result.
                                Throwable t = thread.getException();
                                if (t != null)
                                {
                                        if (t instanceof java.net.SocketTimeoutException)
                                                throw (java.net.SocketTimeoutException)t;
                                        else if (t instanceof org.apache.commons.httpclient.ConnectTimeoutException)
                                                throw (org.apache.commons.httpclient.ConnectTimeoutException)t;
                                        else if (t instanceof InterruptedIOException)
                                                throw (InterruptedIOException)t;
                                        else if (t instanceof IOException)
                                                throw (IOException)t;
                                        else if (t instanceof UnknownHostException)
                                                throw (UnknownHostException)t;
                                        else if (t instanceof Error)
                                                throw (Error)t;
                                        else if (t instanceof RuntimeException)
                                                throw (RuntimeException)t;
                                        throw new Error("Received an unexpected exception: "+t.getMessage(),t);
                                }
                                return thread.getResult();
                            }
                            catch (InterruptedException e)
                            {
                                throw new InterruptedIOException("Interrupted: "+e.getMessage());
                            }
                        }
                }

                public Socket createSocket(String host, int port)
                        throws IOException, UnknownHostException
                {
                        return socketFactory.createSocket(
                                host,
                                port
                                );
                }

                public Socket createSocket(
                        Socket socket,
                        String host,
                        int port,
                        boolean autoClose)
                        throws IOException, UnknownHostException
                {
                        return socketFactory.createSocket(
                                socket,
                                host,
                                port,
                                autoClose
                                );
                }

                public boolean equals(Object obj)
                {
                        if (obj == null || !(obj instanceof WebSecureSocketFactory))
                                return false;
                        // Each object is unique
                        return super.equals(obj);
                }

                public int hashCode()
                {
                        return super.hashCode();
                }    

        }

        /** Create a secure socket in a thread, so that we can "give up" after a while if the socket fails to connect.
        */
        protected static class SocketCreateThread extends Thread
        {
                // Socket factory
                protected javax.net.ssl.SSLSocketFactory socketFactory;
                protected String host;
                protected int port;
                protected InetAddress clientHost;
                protected int clientPort;

                // The return socket
                protected Socket rval = null;
                // The return error
                protected Throwable throwable = null;
                
                /** Create the thread */
                public SocketCreateThread(javax.net.ssl.SSLSocketFactory socketFactory,
                        String host,
                        int port,
                        InetAddress clientHost,
                        int clientPort)
                {
                        this.socketFactory = socketFactory;
                        this.host = host;
                        this.port = port;
                        this.clientHost = clientHost;
                        this.clientPort = clientPort;
                        setDaemon(true);
                }
                
                public void run()
                {
                        try
                        {
                                rval = socketFactory.createSocket(host,port,clientHost,clientPort);
                        }
                        catch (Throwable e)
                        {
                                throwable = e;
                        }
                }
                
                public Throwable getException()
                {
                        return throwable;
                }
                
                public Socket getResult()
                {
                        return rval;
                }
        }
        
}
