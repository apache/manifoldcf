/* $Id: ThrottledFetcher.java 989847 2010-08-26 17:52:30Z kwright $ */

/**`
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
package org.apache.manifoldcf.crawler.connectors.webcrawler;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.common.DeflateInputStream;
import org.apache.manifoldcf.core.common.XThreadInputStream;
import org.apache.manifoldcf.core.common.InterruptibleSocketFactory;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import java.util.*;
import java.io.*;
import java.net.*;
import java.util.zip.GZIPInputStream;
import java.util.concurrent.TimeUnit;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.NameValuePair;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.HttpStatus;
import org.apache.http.HttpHost;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.message.BasicHeader;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.cookie.params.CookieSpecPNames;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.ClientCookie;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicPathHandler;
import org.apache.http.impl.cookie.BrowserCompatSpec;
import org.apache.http.cookie.CookieSpecFactory;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.client.CookieStore;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.cookie.CookieIdentityComparator;
import org.apache.http.client.HttpRequestRetryHandler;

import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.client.RedirectException;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.NoHttpResponseException;
import org.apache.http.HttpException;

/** This class uses httpclient to fetch stuff from webservers.  However, it additionally controls the fetch
* rate in two ways: first, controlling the overall bandwidth used per server, and second, limiting the number
* of simultaneous open connections per server.
* An instance of this class would very probably need to have a lifetime consistent with the long-term nature
* of these values, and be static.
*/
public class ThrottledFetcher
{
  public static final String _rcsid = "@(#)$Id: ThrottledFetcher.java 989847 2010-08-26 17:52:30Z kwright $";

  /** This flag determines whether we record everything to the disk, as a means of doing a web snapshot */
  protected static final boolean recordEverything = false;

  protected static final long TIME_2HRS = 7200000L;
  protected static final long TIME_5MIN = 300000L;
  protected static final long TIME_15MIN = 1500000L;
  protected static final long TIME_6HRS = 6L * 60L * 60000L;
  protected static final long TIME_1DAY = 24L * 60L * 60000L;


  // The following static bin pools correspond to global resources that will be managed via ILockManager.
  
  /** This is the static pool of ConnectionBin's, keyed by bin name. */
  protected static Map<String,ConnectionBin> connectionBins = new HashMap<String,ConnectionBin>();
  /** This is the static pool of ThrottleBin's, keyed by bin name. */
  protected static Map<String,ThrottleBin> throttleBins = new HashMap<String,ThrottleBin>();

  /** This global lock protects the "distributed pool" resource, and insures that a connection
  * can get pulled out of all the right pools and wind up in only the hands of one thread. */
  protected static Integer poolLock = new Integer(0);

  /** Current host name */
  private static String currentHost = null;
  static
  {
    // Find the current host name
    try
    {
      java.net.InetAddress addr = java.net.InetAddress.getLocalHost();

      // Get hostname
      currentHost = addr.getHostName();
    }
    catch (java.net.UnknownHostException e)
    {
    }
  }

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
  *@param authentication is the page credentials object to use for the fetch.  If null, no credentials are available.
  *@param trustStore is the current trust store in effect for the fetch.
  *@param binNames is the set of bins, in order, that should be used for throttling this connection.
  *       Note that the bin names for a given IP address and port MUST be the same for every connection!
  *       This must be enforced by whatever it is that builds the bins - it must do so given an IP and port.
  *@param throttleDescription is the description of all the throttling that should take place.
  *@param connectionLimit isthe maximum number of connections permitted.
  *@return an IThrottledConnection object that can be used to fetch from the port.
  */
  public static IThrottledConnection getConnection(IThreadContext threadContext,
    String protocol, String server, int port,
    PageCredentials authentication,
    IKeystoreManager trustStore,
    IThrottleSpec throttleDescription, String[] binNames,
    int connectionLimit,
    String proxyHost, int proxyPort, String proxyAuthDomain, String proxyAuthUsername, String proxyAuthPassword)
    throws ManifoldCFException
  {
    // Create the https scheme for this connection
    javax.net.ssl.SSLSocketFactory baseFactory;
    String trustStoreString;
    if (trustStore != null)
    {
      baseFactory = trustStore.getSecureSocketFactory();
      trustStoreString = trustStore.getString();
    }
    else
    {
      baseFactory = KeystoreManagerFactory.getTrustingSecureSocketFactory();
      trustStoreString = null;
    }


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
        cb = connectionBins.get(binName);
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
          for (String binName : connectionBins.keySet())
          {
            ConnectionBin cb = connectionBins.get(binName);
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
          for (String binName : connectionBins.keySet())
          {
            ConnectionBin cb = connectionBins.get(binName);
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
                connectionToReuse = cb.findConnection(maxConnections,bins,protocol,server,port,authentication,trustStoreString,
                  proxyHost,proxyPort,proxyAuthDomain,proxyAuthUsername,proxyAuthPassword);
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
                  cb = connectionBins.get(binName);
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
            if (e instanceof ManifoldCFException)
              throw (ManifoldCFException)e;

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

            throw new ManifoldCFException("Unexpected exception encountered: "+e.getMessage(),e);
          }

          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("WEB: Successfully got connection to "+protocol+"://"+server+":"+port+" ("+new Long(System.currentTimeMillis()-startTime).toString()+" ms)");

          // If we have a connection located, activate it.
          if (connectionToReuse == null)
            connectionToReuse = new ThrottledConnection(protocol,server,port,authentication,baseFactory,trustStoreString,bins,
              proxyHost,proxyPort,proxyAuthDomain,proxyAuthUsername,proxyAuthPassword);
          connectionToReuse.setup(throttleDescription);
          return connectionToReuse;
        }
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException(e.getMessage(),ManifoldCFException.INTERRUPTED);
      }
    }
  }


  /** Flush connections that have timed out from inactivity. */
  public static void flushIdleConnections(IThreadContext threadContext)
    throws ManifoldCFException
  {
    synchronized (poolLock)
    {
      // Lock up everything for a moment, since otherwise we could delete something people
      // expect to stick around.
      synchronized (connectionBins)
      {
        // Time out connections that have been idle too long.  To do this, we need to go through
        // all connection bins and look at the pool
        for (String binName : connectionBins.keySet())
        {
          ConnectionBin cb = connectionBins.get(binName);
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
  * NOTE WELL: This resource must be constrained globally, across all JVMs!
  * To do that, we need an ILockManager to handle the global data for each bin.
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
      PageCredentials authentication, String trustStoreString,
      String proxyHost, int proxyPort, String proxyAuthDomain, String proxyAuthUsername, String proxyAuthPassword)
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

      if (!rval.matches(binNames,protocol,server,port,authentication,trustStoreString,
        proxyHost,proxyPort,proxyAuthDomain,proxyAuthUsername,proxyAuthPassword))
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
  * NOTE WELL: This resource must be constrained globally, across all JVMs!
  * To do that, we need an ILockManager to handle the global data for each bin.
  */
  protected static class ThrottleBin
  {
    /** This is the bin name which this throttle belongs to. */
    protected final String binName;
    /** This is the reference count for this bin (which records active references) */
    protected volatile int refCount = 0;
    /** The inverse rate estimate of the first fetch, in ms/byte */
    protected double rateEstimate = 0.0;
    /** Flag indicating whether a rate estimate is needed */
    protected volatile boolean estimateValid = false;
    /** Flag indicating whether rate estimation is in progress yet */
    protected volatile boolean estimateInProgress = false;
    /** The start time of this series */
    protected long seriesStartTime = -1L;
    /** Total actual bytes read in this series; this includes fetches in progress */
    protected long totalBytesRead = -1L;

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

    /** Abort the fetch.
    */
    public void abortFetch()
    {
      synchronized (this)
      {
        refCount--;
      }
    }
    
    /** Note the start of an individual byte read of a specified size.  Call this method just before the
    * read request takes place.  Performs the necessary delay prior to reading specified number of bytes from the server.
    */
    public void beginRead(int byteCount, double minimumMillisecondsPerBytePerServer)
      throws InterruptedException
    {
      long currentTime = System.currentTimeMillis();

      synchronized (this)
      {
        while (estimateInProgress)
          wait();
        if (estimateValid == false)
        {
          seriesStartTime = currentTime;
          estimateInProgress = true;
          // Add these bytes to the estimated total
          totalBytesRead += (long)byteCount;
          // Exit early; this thread isn't going to do any waiting
          return;
        }
      }

      // It is possible for the following code to get interrupted.  If that happens,
      // we have to unstick the threads that are waiting on the estimate!
      boolean finished = false;
      try
      {
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
          ManifoldCF.sleep(waitTime);
        }
        finished = true;
      }
      finally
      {
        if (!finished)
        {
          abortRead();
        }
      }
    }

    /** Abort a read in progress.
    */
    public void abortRead()
    {
      synchronized (this)
      {
        if (estimateInProgress)
        {
          estimateInProgress = false;
          notifyAll();
        }
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
        if (estimateInProgress)
        {
          if (actualCount == 0)
            // Didn't actually get any bytes, so use 0.0
            rateEstimate = 0.0;
          else
            rateEstimate = ((double)(currentTime - seriesStartTime))/(double)actualCount;
          estimateValid = true;
          estimateInProgress = false;
          notifyAll();
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
    protected PoolingClientConnectionManager connManager = null;
    /** The http client object. */
    protected AbstractHttpClient httpClient = null;
    /** The method object */
    protected HttpRequestBase fetchMethod = null;
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
    /** Proxy host */
    protected final String proxyHost;
    /** Proxy port */
    protected final int proxyPort;
    /** Proxy auth domain */
    protected final String proxyAuthDomain;
    /** Proxy auth user name */
    protected final String proxyAuthUsername;
    /** Proxy auth password */
    protected final String proxyAuthPassword;
    /** Https protocol */
    protected final javax.net.ssl.SSLSocketFactory httpsSocketFactory;

    /** The thread that is actually doing the work */
    protected ExecuteMethodThread methodThread = null;
    /** Set if thread has been started */
    protected boolean threadStarted = false;
    

    /** Constructor.  Create a connection with a specific server and port, and
    * register it as active against all bins. */
    public ThrottledConnection(String protocol, String server, int port, PageCredentials authentication,
      javax.net.ssl.SSLSocketFactory httpsSocketFactory, String trustStoreString, ConnectionBin[] connectionBins,
      String proxyHost, int proxyPort, String proxyAuthDomain, String proxyAuthUsername, String proxyAuthPassword)
    {
      this.proxyHost = proxyHost;
      this.proxyPort = proxyPort;
      this.proxyAuthDomain = proxyAuthDomain;
      this.proxyAuthUsername = proxyAuthUsername;
      this.proxyAuthPassword = proxyAuthPassword;
      this.protocol = protocol;
      this.server = server;
      this.port = port;
      this.authentication = authentication;
      this.httpsSocketFactory = httpsSocketFactory;
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
      String trustStoreString, String proxyHost, int proxyPort, String proxyAuthDomain, String proxyAuthUsername, String proxyAuthPassword)
    {
      if (this.trustStoreString == null || trustStoreString == null)
      {
        if (this.trustStoreString != trustStoreString)
          return false;
      }
      else
      {
        if (!this.trustStoreString.equals(trustStoreString))
          return false;
      }

      if (this.authentication == null || authentication == null)
      {
        if (this.authentication != authentication)
          return false;
      }
      else
      {
        if (!this.authentication.equals(authentication))
          return false;
      }

      if (this.proxyHost == null || proxyHost == null)
      {
        if (this.proxyHost != proxyHost)
          return false;
      }
      else
      {
        if (!this.proxyHost.equals(proxyHost))
          return false;
        if (this.proxyAuthDomain == null || proxyAuthDomain == null)
        {
          if (this.proxyAuthDomain != proxyAuthDomain)
            return false;
        }
        else
        {
          if (!this.proxyAuthDomain.equals(proxyAuthDomain))
            return false;
        }
        if (this.proxyAuthUsername == null || proxyAuthUsername == null)
        {
          if (this.proxyAuthUsername != proxyAuthUsername)
            return false;
        }
        else
        {
          if (!this.proxyAuthUsername.equals(proxyAuthUsername))
            return false;
        }
        if (this.proxyAuthPassword == null || proxyAuthPassword == null)
        {
          if (this.proxyAuthPassword != proxyAuthPassword)
            return false;
        }
        else
        {
          if (!this.proxyAuthPassword.equals(proxyAuthPassword))
            return false;
        }
      }
      
      if (this.proxyPort != proxyPort)
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
    public void setup(IThrottleSpec description)
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
        connManager.closeIdleConnections(idleTimeout, TimeUnit.MILLISECONDS);
        connManager.closeExpiredConnections();
        // Need to determine if there's a valid connection in the connection manager still, or if it is empty.
        //return connManager.getConnectionsInPool() == 0;
        return true;
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
      int lastOneDone = 0;
      try
      {
        for (int i = 0; i < throttleBinArray.length; i++)
        {
          throttleBinArray[i].beginRead(len,minMillisecondsPerByte[i]);
          lastOneDone = i + 1;
        }
      }
      finally
      {
        if (lastOneDone != throttleBinArray.length)
        {
          for (int i = 0; i < lastOneDone; i++)
          {
            throttleBinArray[i].abortRead();
          }
        }
      }
    }

    /** End a read operation, from within a stream */
    public void endRead(int origLen, int actualAmt)
    {
      // Consult with throttle bins
      Throwable e = null;
      for (int i = 0; i < throttleBinArray.length; i++)
      {
        try
        {
          throttleBinArray[i].endRead(origLen,actualAmt);
        }
        catch (Throwable e2)
        {
          e = e2;
        }
      }
      if (e != null)
      {
        if (e instanceof RuntimeException)
          throw (RuntimeException)e;
        else if (e instanceof Error)
          throw (Error)e;
        else
          throw new RuntimeException("Unknown exception: " + e.getMessage(),e);
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
    *        is used solely for logging purposes.
    */
    @Override
    public void beginFetch(String fetchType)
      throws ManifoldCFException
    {
      this.fetchType = fetchType;
      this.fetchCounter = 0L;
      int lastCreated = 0;
      try
      {
        // Find or create the needed throttle bins
        for (int i = 0; i < throttleBinArray.length; i++)
        {
          // Access the bins as we need them, and drop them when ref count goes to zero
          String binName = connectionBinArray[i].getBinName();
          ThrottleBin tb;
          synchronized (throttleBins)
          {
            tb = throttleBins.get(binName);
            if (tb == null)
            {
              tb = new ThrottleBin(binName);
              throttleBins.put(binName,tb);
            }
            tb.beginFetch();
          }
          throttleBinArray[i] = tb;
          lastCreated = i + 1;
        }
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
      }
      finally
      {
        if (lastCreated != throttleBinArray.length)
        {
          for (int i = 0; i < lastCreated; i++)
          {
            throttleBinArray[i].abortFetch();
          }
        }
      }
    }

    /** Execute the fetch and get the return code.  This method uses the
    * standard logging mechanism to keep track of the fetch attempt.  It also
    * signals the following conditions: ServiceInterruption (if a dynamic
    * error occurs), or ManifoldCFException if a fatal error occurs, or nothing if
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
    @Override
    public void executeFetch(String urlPath, String userAgent, String from, int connectionTimeoutMilliseconds,
      int socketTimeoutMilliseconds, boolean redirectOK, String host, FormData formData,
      LoginCookies loginCookies)
      throws ManifoldCFException, ServiceInterruption
    {
      // Set up scheme
      SSLSocketFactory myFactory = new SSLSocketFactory(new InterruptibleSocketFactory(httpsSocketFactory,connectionTimeoutMilliseconds),
        new AllowAllHostnameVerifier());
      Scheme myHttpsProtocol = new Scheme("https", 443, myFactory);

      int hostPort;
      String displayedPort;
      if (port != -1)
      {
        if (!(protocol.equals("http") && port == 80) &&
          !(protocol.equals("https") && port == 443))
        {
          displayedPort = ":"+Integer.toString(port);
          hostPort = port;
        }
        else
        {
          displayedPort = "";
          hostPort = -1;
        }
      }
      else
      {
        displayedPort = "";
        hostPort = -1;
      }

      StringBuilder sb = new StringBuilder(protocol);
      sb.append("://").append(server).append(displayedPort).append(urlPath);
      String fetchUrl = sb.toString();

      HttpHost fetchHost = new HttpHost(server,hostPort,protocol);
      HttpHost hostHost;
      
      if (host != null)
      {
        sb.setLength(0);
        sb.append(protocol).append("://").append(host).append(displayedPort).append(urlPath);
        myUrl = sb.toString();
        hostHost = new HttpHost(host,hostPort,protocol);
      }
      else
      {
        myUrl = fetchUrl;
        hostHost = fetchHost;
      }
      
      if (connManager == null)
      {
        PoolingClientConnectionManager localConnManager = new PoolingClientConnectionManager();
        localConnManager.setMaxTotal(1);
        localConnManager.setDefaultMaxPerRoute(1);
        connManager = localConnManager;
      }
      
      // Set up protocol registry
      connManager.getSchemeRegistry().register(myHttpsProtocol);
      
      long startTime = 0L;
      if (Logging.connectors.isDebugEnabled())
      {
        startTime = System.currentTimeMillis();
        Logging.connectors.debug("WEB: Waiting for an HttpClient object");
      }

      // If we already have an httpclient object, great.  Otherwise we have to get one, and initialize it with
      // those parameters that aren't expected to change.
      if (httpClient == null)
      {
        BasicHttpParams params = new BasicHttpParams();
        params.setParameter(ClientPNames.DEFAULT_HOST,fetchHost);
        params.setBooleanParameter(CoreConnectionPNames.TCP_NODELAY,true);
        params.setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK,false);
        params.setBooleanParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS,true);
        // MEDIUM_SECURITY compatibility level not supported in HttpComponents.  Try BROWSER_NETSCAPE?
        HttpClientParams.setCookiePolicy(params,CookiePolicy.BROWSER_COMPATIBILITY);
        params.setBooleanParameter(CookieSpecPNames.SINGLE_COOKIE_HEADER,new Boolean(true));

        DefaultHttpClient localHttpClient = new DefaultHttpClient(connManager,params);
        // No retries
        localHttpClient.setHttpRequestRetryHandler(new HttpRequestRetryHandler()
          {
            public boolean retryRequest(
              IOException exception,
              int executionCount,
              HttpContext context)
            {
              return false;
            }
         
          });
        localHttpClient.setRedirectStrategy(new DefaultRedirectStrategy());
        localHttpClient.getCookieSpecs().register(CookiePolicy.BROWSER_COMPATIBILITY, new CookieSpecFactory()
          {

            public CookieSpec newInstance(HttpParams params)
            {
              return new LaxBrowserCompatSpec();
            }
    
          }
        );

        // If there's a proxy, set that too.
        if (proxyHost != null && proxyHost.length() > 0)
        {
          // Configure proxy authentication
          if (proxyAuthUsername != null && proxyAuthUsername.length() > 0)
          {
            localHttpClient.getCredentialsProvider().setCredentials(
              new AuthScope(proxyHost, proxyPort),
              new NTCredentials(proxyAuthUsername, (proxyAuthPassword==null)?"":proxyAuthPassword, currentHost, (proxyAuthDomain==null)?"":proxyAuthDomain));
          }

          HttpHost proxy = new HttpHost(proxyHost, proxyPort);

          localHttpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
        }

        // Set up authentication to use
        if (authentication != null)
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("WEB: For "+myUrl+", discovered matching authentication credentials");
          localHttpClient.getCredentialsProvider().setCredentials(AuthScope.ANY,
            authentication.makeCredentialsObject(host));
        }
          
        httpClient = localHttpClient;
      }


      // Set the parameters we haven't keyed on (so these can change from request to request)
      httpClient.getParams().setIntParameter(CoreConnectionPNames.SO_TIMEOUT,socketTimeoutMilliseconds);
      httpClient.getParams().setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,connectionTimeoutMilliseconds);
      httpClient.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS,redirectOK);

      if (host != null)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: For "+myUrl+", setting virtual host to "+host);
        httpClient.getParams().setParameter(ClientPNames.VIRTUAL_HOST,hostHost);
      }


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
          StringBuilder psb = new StringBuilder(urlPath);
          Iterator iter = formData.getElementIterator();
          char appendChar;
          if (urlPath.indexOf("?") == -1)
            appendChar = '?';
          else
            appendChar = '&';
          try
          {
            while (iter.hasNext())
            {
              FormDataElement el = (FormDataElement)iter.next();
              psb.append(appendChar);
              appendChar = '&';
              String param = el.getElementName();
              String value = el.getElementValue();
              psb.append(java.net.URLEncoder.encode(param,"utf-8"));
              if (value != null)
              {
                psb.append('=').append(java.net.URLEncoder.encode(value,"utf-8"));
              }
            }
          }
          catch (java.io.UnsupportedEncodingException e)
          {
            throw new ManifoldCFException("Unsupported encoding: "+e.getMessage(),e);
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
        fetchMethod = new HttpGet(fullUrlPath);
        break;
      case FormData.SUBMITMETHOD_POST:
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: Post method for '"+urlPath+"'");
        // MUST be just the path, or apparently we wind up resetting the HostConfiguration
        HttpPost postMethod = new HttpPost(urlPath);
        List<NameValuePair> nvps = new ArrayList<NameValuePair>();

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
            nvps.add(new BasicNameValuePair(param,value));
          }
        }
        try
        {
          postMethod.setEntity(new UrlEncodedFormEntity(nvps,HTTP.UTF_8));
        }
        catch (java.io.UnsupportedEncodingException e)
        {
          throw new ManifoldCFException("Unsupported UTF-8 encoding: "+e.getMessage(),e);
        }
        fetchMethod = postMethod;
        break;
      default:
        throw new ManifoldCFException("Illegal method type: "+Integer.toString(pageFetchMethod));
      }

      // Set all appropriate headers and parameters
      fetchMethod.setHeader(new BasicHeader("User-Agent",userAgent));
      fetchMethod.setHeader(new BasicHeader("From",from));
      fetchMethod.setHeader(new BasicHeader("Accept","*/*"));
      fetchMethod.setHeader(new BasicHeader("Accept-Encoding","gzip,deflate"));

      // Use a custom cookie store
      CookieStore cookieStore = new OurBasicCookieStore();
      // If we have any cookies to set, set them.
      if (loginCookies != null)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("WEB: Adding "+Integer.toString(loginCookies.getCookieCount())+" cookies for '"+urlPath+"'");
        int h = 0;
        while (h < loginCookies.getCookieCount())
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("WEB:  Cookie '"+loginCookies.getCookie(h)+"' added");
          cookieStore.addCookie(loginCookies.getCookie(h++));
        }
      }


      // Copy out the current cookies, in case the fetch fails
      lastFetchCookies = loginCookies;

      //httpClient.setCookieStore(cookieStore);
      
      // Create the thread
      methodThread = new ExecuteMethodThread(this, httpClient, fetchMethod, cookieStore);
      try
      {
        methodThread.start();
        threadStarted = true;
        try
        {
          statusCode = methodThread.getResponseCode();
          lastFetchCookies = methodThread.getCookies();
          switch (statusCode)
          {
          case HttpStatus.SC_REQUEST_TIMEOUT:
          case HttpStatus.SC_GATEWAY_TIMEOUT:
          case HttpStatus.SC_SERVICE_UNAVAILABLE:
            // Temporary service interruption
            // May want to make the retry time a parameter someday
            long currentTime = System.currentTimeMillis();
            throw new ServiceInterruption("Http response temporary error on '"+myUrl+"': "+Integer.toString(statusCode),new ManifoldCFException("Service unavailable (code "+Integer.toString(statusCode)+")"),
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
        catch (InterruptedException e)
        {
          methodThread.interrupt();
          methodThread = null;
          threadStarted = false;
          throw e;
        }

      }
      catch (InterruptedException e)
      {
        // Drop the current connection on the floor, so it cannot be reused.
        fetchMethod = null;
        throwable = new ManifoldCFException("Interrupted: "+e.getMessage(),e);
        statusCode = FETCH_INTERRUPTED;
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (java.net.SocketTimeoutException e)
      {
        throwable = e;
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Timed out waiting for IO for '"+myUrl+"': "+e.getMessage(), e, currentTime + TIME_5MIN,
          currentTime + TIME_2HRS,-1,false);
      }
      catch (ConnectTimeoutException e)
      {
        throwable = e;
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Timed out waiting for connection for '"+myUrl+"': "+e.getMessage(), e, currentTime + TIME_5MIN,
          currentTime + TIME_2HRS,-1,false);
      }
      catch (InterruptedIOException e)
      {
        //Logging.connectors.warn("IO interruption seen",e);
        throwable = new ManifoldCFException("Interrupted: "+e.getMessage(),e);
        statusCode = FETCH_INTERRUPTED;
        throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
      }
      catch (RedirectException e)
      {
        throwable = e;
        statusCode = FETCH_CIRCULAR_REDIRECT;
        return;
      }
      catch (NoHttpResponseException e)
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
      catch (javax.net.ssl.SSLException e)
      {
        // Probably this is an incorrectly configured trust store
        throwable = new ManifoldCFException("SSL handshake error: "+e.getMessage()+"; check your connection's Certificate configuration",e);
        statusCode = FETCH_IO_ERROR;
        return;
      }
      catch (IOException e)
      {
        // Treat this as a bad url.  We don't know what happened, but it isn't something we are going to naively
        // retry on.
        throwable = e;
        statusCode = FETCH_IO_ERROR;
        return;
      }
      catch (Throwable e)
      {
        Logging.connectors.debug("WEB: Caught an unexpected exception: "+e.getMessage(),e);
        throwable = e;
        statusCode = FETCH_UNKNOWN_ERROR;
        return;
      }

    }

    /** Get the http response code.
    *@return the response code.  This is either an HTTP response code, or one of the codes above.
    */
    @Override
    public int getResponseCode()
      throws ManifoldCFException, ServiceInterruption
    {
      return statusCode;
    }

    /** Get the last fetch cookies.
    *@return the cookies now in effect from the last fetch.
    */
    @Override
    public LoginCookies getLastFetchCookies()
      throws ManifoldCFException, ServiceInterruption
    {
      if (Logging.connectors.isDebugEnabled())
      {
        Logging.connectors.debug("WEB: Retrieving cookies...");
        for (int i = 0; i < lastFetchCookies.getCookieCount(); i++)
        {
          Logging.connectors.debug("WEB:   Cookie '"+lastFetchCookies.getCookie(i)+"'");
        }
      }
      return lastFetchCookies;
    }

    /** Get response headers
    *@return a map keyed by header name containing a list of values.
    */
    @Override
    public Map<String,List<String>> getResponseHeaders()
      throws ManifoldCFException, ServiceInterruption
    {
      if (fetchMethod == null)
        throw new ManifoldCFException("Attempt to get headers when there is no method");
      if (methodThread == null || threadStarted == false)
        throw new ManifoldCFException("Attempt to get headers when no method thread");
      try
      {
        return methodThread.getResponseHeaders();
      }
      catch (InterruptedException e)
      {
        methodThread.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (HttpException e)
      {
        handleHTTPException(e,"reading headers");
      }
      catch (IOException e)
      {
        handleIOException(e,"reading headers");
      }
      return null;
    }

    /** Get a specified response header, if it exists.
    *@param headerName is the name of the header.
    *@return the header value, or null if it doesn't exist.
    */
    @Override
    public String getResponseHeader(String headerName)
      throws ManifoldCFException, ServiceInterruption
    {
      if (fetchMethod == null)
        throw new ManifoldCFException("Attempt to get a header when there is no method");
      if (methodThread == null || threadStarted == false)
        throw new ManifoldCFException("Attempt to get a header when no method thread");
      try
      {
        return methodThread.getFirstHeader(headerName);
      }
      catch (InterruptedException e)
      {
        methodThread.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (HttpException e)
      {
        handleHTTPException(e,"reading header");
      }
      catch (IOException e)
      {
        handleIOException(e,"reading header");
      }
      return null;
    }

    /** Get the response input stream.  It is the responsibility of the caller
    * to close this stream when done.
    */
    @Override
    public InputStream getResponseBodyStream()
      throws ManifoldCFException, ServiceInterruption
    {
      if (fetchMethod == null)
        throw new ManifoldCFException("Attempt to get an input stream when there is no method");
      if (methodThread == null || threadStarted == false)
        throw new ManifoldCFException("Attempt to get an input stream when no method thread");
      try
      {
        return methodThread.getSafeInputStream();
      }
      catch (InterruptedException e)
      {
        methodThread.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (IOException e)
      {
        handleIOException(e, "reading response stream");
      }
      catch (HttpException e)
      {
        handleHTTPException(e, "reading response stream");
      }
      return null;
    }

    /** Get limited response as a string.
    */
    @Override
    public String getLimitedResponseBody(int maxSize, String encoding)
      throws ManifoldCFException, ServiceInterruption
    {
      try
      {
        InputStream is = getResponseBodyStream();
        try
        {
          Reader r = new InputStreamReader(is,encoding);
          char[] buffer = new char[maxSize];
          int amt = r.read(buffer);
          if (amt == -1)
            return "";
          return new String(buffer,0,amt);
        }
        finally
        {
          is.close();
        }
      }
      catch (IOException e)
      {
        handleIOException(e,"reading limited response");
      }
      return null;
    }

    /** Note that the connection fetch was interrupted by something.
    */
    @Override
    public void noteInterrupted(Throwable e)
    {
      if (statusCode > 0)
      {
        throwable = new ManifoldCFException("Interrupted: "+e.getMessage(),e);
        statusCode = FETCH_INTERRUPTED;
      }
    }

    /** Done with the fetch.  Call this when the fetch has been completed.  A log entry will be generated
    * describing what was done.
    */
    @Override
    public void doneFetch(IVersionActivity activities)
      throws ManifoldCFException
    {
      if (fetchType != null)
      {
        // Abort the connection, if not already complete
        if (methodThread != null && threadStarted)
          methodThread.abort();

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

        // Shut down (join) the connection thread, if any, and if it started
        if (methodThread != null)
        {
          if (threadStarted)
          {
            try
            {
              methodThread.finishUp();
            }
            catch (InterruptedException e)
            {
              throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
            }
            threadStarted = false;
          }
          methodThread = null;
        }
        
        fetchMethod = null;
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
    @Override
    public void close()
      throws ManifoldCFException
    {
      synchronized (poolLock)
      {
        // Verify that all the connections that exist are in fact sane
        synchronized (connectionBins)
        {
          for (String connectionName : connectionBins.keySet())
          {
            ConnectionBin cb = connectionBins.get(connectionName);
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
          for (String connectionName : connectionBins.keySet())
          {
            ConnectionBin cb = connectionBins.get(connectionName);
            //cb.sanityCheck();
          }
        }
        // Wake up everything waiting on the pool lock
        poolLock.notifyAll();
      }
    }
    
    protected void handleHTTPException(HttpException e, String activity)
      throws ServiceInterruption, ManifoldCFException
    {
      long currentTime = System.currentTimeMillis();
      Logging.connectors.debug("Web: HTTP exception "+activity+" for '"+myUrl+"', retrying");
      throw new ServiceInterruption("HTTP exception "+activity+": "+e.getMessage(),e,currentTime+TIME_5MIN,-1L,2,false);
    }

    protected void handleIOException(IOException e, String activity)
      throws ServiceInterruption, ManifoldCFException
    {
      if (e instanceof java.net.SocketTimeoutException)
      {
        long currentTime = System.currentTimeMillis();
        Logging.connectors.debug("Web: Socket timeout exception "+activity+" for '"+myUrl+"', retrying");
        throw new ServiceInterruption("Socket timeout exception "+activity+": "+e.getMessage(),e,currentTime+TIME_5MIN,-1L,2,false);
      }
      if (e instanceof ConnectTimeoutException)
      {
        long currentTime = System.currentTimeMillis();
        Logging.connectors.debug("Web: Connect timeout exception "+activity+" for '"+myUrl+"', retrying");
        throw new ServiceInterruption("Connect timeout exception "+activity+": "+e.getMessage(),e,currentTime+TIME_5MIN,-1L,2,false);
      }
      if (e instanceof InterruptedIOException)
      {
        methodThread.interrupt();
        throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
      }
      if (e instanceof NoHttpResponseException)
      {
        // Give up after 2 hours.
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Timed out "+activity+" for '"+myUrl+"'", e, currentTime + 15L * 60000L,
          currentTime + 120L * 60000L,-1,false);
      }
      if (e instanceof java.net.ConnectException)
      {
        // Give up after 6 hours.
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Timed out "+activity+" for '"+myUrl+"'", e, currentTime + 1000000L,
          currentTime + 720L * 60000L,-1,false);
      }
      if (e instanceof java.net.NoRouteToHostException)
      {
        // This exception means we know the IP address but can't get there.  That's either a firewall issue, or it's something transient
        // with the network.  Some degree of retry is probably wise.
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("No route to host during "+activity+" for '"+myUrl+"'", e, currentTime + 1000000L,
          currentTime + 720L * 60000L,-1,false);
      }
      long currentTime = System.currentTimeMillis();
      Logging.connectors.debug("Web: IO exception "+activity+" for '"+myUrl+"', retrying");
      throw new ServiceInterruption("IO exception "+activity+": "+e.getMessage(),e,currentTime+TIME_5MIN,-1L,2,false);
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

    /** Constructor.
    */
    public ThrottledInputstream(ThrottledConnection connection, InputStream is)
    {
      this.throttledConnection = connection;
      this.inputStream = is;
    }

    /** Read a byte.
    */
    @Override
    public int read()
      throws IOException
    {
      byte[] byteArray = new byte[1];
      int count = read(byteArray,0,1);
      if (count == -1)
        return count;
      return ((int)byteArray[0]) & 0xff;
    }

    /** Read lots of bytes.
    */
    @Override
    public int read(byte[] b)
      throws IOException
    {
      return read(b,0,b.length);
    }

    /** Read lots of specific bytes.
    */
    @Override
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
    @Override
    public long skip(long n)
      throws IOException
    {
      // Not sure whether we should bother doing anything with this; it's not used.
      return inputStream.skip(n);
    }

    /** Get available.
    */
    @Override
    public int available()
      throws IOException
    {
      return inputStream.available();
    }

    /** Mark.
    */
    @Override
    public void mark(int readLimit)
    {
      inputStream.mark(readLimit);
    }

    /** Reset.
    */
    @Override
    public void reset()
      throws IOException
    {
      inputStream.reset();
    }

    /** Check if mark is supported.
    */
    @Override
    public boolean markSupported()
    {
      return inputStream.markSupported();
    }

    /** Close.
    */
    @Override
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
      catch (ConnectTimeoutException e)
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

  /** Class to override browser compatibility to make it not check cookie paths.  See CONNECTORS-97.
  */
  protected static class LaxBrowserCompatSpec extends BrowserCompatSpec
  {

    public LaxBrowserCompatSpec()
    {
      super();
      registerAttribHandler(ClientCookie.PATH_ATTR, new BasicPathHandler()
        {
          @Override
          public void validate(Cookie cookie, CookieOrigin origin) throws MalformedCookieException
          {
            // No validation
          }
              
        }
      );
    }
    
  }

  /** This thread does the actual socket communication with the server.
  * It's set up so that it can be abandoned at shutdown time.
  *
  * The way it works is as follows:
  * - it starts the transaction
  * - it receives the response, and saves that for the calling class to inspect
  * - it transfers the data part to an input stream provided to the calling class
  * - it shuts the connection down
  *
  * If there is an error, the sequence is aborted, and an exception is recorded
  * for the calling class to examine.
  *
  * The calling class basically accepts the sequence above.  It starts the
  * thread, and tries to get a response code.  If instead an exception is seen,
  * the exception is thrown up the stack.
  */
  protected static class ExecuteMethodThread extends Thread
  {
    /** The connection */
    protected final ThrottledConnection theConnection;
    /** Client and method, all preconfigured */
    protected final AbstractHttpClient httpClient;
    protected final HttpRequestBase executeMethod;
    protected final CookieStore cookieStore;
    
    protected HttpResponse response = null;
    protected Throwable responseException = null;
    protected LoginCookies cookies = null;
    protected Throwable cookieException = null;
    protected XThreadInputStream threadStream = null;
    protected InputStream bodyStream = null;
    protected boolean streamCreated = false;
    protected Throwable streamException = null;
    protected boolean abortThread = false;

    protected Throwable shutdownException = null;

    protected Throwable generalException = null;
    
    public ExecuteMethodThread(ThrottledConnection theConnection,
      AbstractHttpClient httpClient, HttpRequestBase executeMethod, CookieStore cookieStore)
    {
      super();
      setDaemon(true);
      this.theConnection = theConnection;
      this.httpClient = httpClient;
      this.executeMethod = executeMethod;
      this.cookieStore = cookieStore;
    }

    public void run()
    {
      try
      {
        try
        {
          // Call the execute method appropriately
          synchronized (this)
          {
            if (!abortThread)
            {
              try
              {
                HttpContext context = new BasicHttpContext();
                context.setAttribute(ClientContext.COOKIE_STORE,cookieStore);
                response = httpClient.execute(executeMethod,context);
              }
              catch (java.net.SocketTimeoutException e)
              {
                responseException = e;
              }
              catch (ConnectTimeoutException e)
              {
                responseException = e;
              }
              catch (InterruptedIOException e)
              {
                throw e;
              }
              catch (Throwable e)
              {
                responseException = e;
              }
              this.notifyAll();
            }
          }
          
          // Fetch the cookies
          if (responseException == null)
          {
            synchronized (this)
            {
              if (!abortThread)
              {
                try
                {
                  cookies = new CookieSet(cookieStore.getCookies());
                }
                catch (Throwable e)
                {
                  cookieException = e;
                }
                this.notifyAll();
              }
            }
          }

          // Start the transfer of the content
          if (cookieException == null && responseException == null)
          {
            synchronized (this)
            {
              if (!abortThread)
              {
                try
                {
                  boolean gzip = false;
                  boolean deflate = false;
                  Header ceheader = response.getEntity().getContentEncoding();
                  if (ceheader != null)
                  {
                    HeaderElement[] codecs = ceheader.getElements();
                    for (int i = 0; i < codecs.length; i++)
                    {
                      if (codecs[i].getName().equalsIgnoreCase("gzip"))
                      {
                        // GZIP
                        gzip = true;
                        break;
                      }
                      else if (codecs[i].getName().equalsIgnoreCase("deflate"))
                      {
                        // Deflate
                        deflate = true;
                        break;
                      }
                    }
                  }
                  bodyStream = response.getEntity().getContent();
                  if (bodyStream != null)
                  {
                    bodyStream = new ThrottledInputstream(theConnection,bodyStream);
                    if (gzip)
                      bodyStream = new GZIPInputStream(bodyStream);
                    else if (deflate)
                      bodyStream = new DeflateInputStream(bodyStream);
                    threadStream = new XThreadInputStream(bodyStream);
                  }
                  streamCreated = true;
                }
                catch (java.net.SocketTimeoutException e)
                {
                  streamException = e;
                }
                catch (ConnectTimeoutException e)
                {
                  streamException = e;
                }
                catch (InterruptedIOException e)
                {
                  throw e;
                }
                catch (Throwable e)
                {
                  streamException = e;
                }
                this.notifyAll();
              }
            }
          }
          
          if (cookieException == null && responseException == null && streamException == null)
          {
            if (threadStream != null)
            {
              // Stuff the content until we are done
              threadStream.stuffQueue();
            }
          }
          
        }
        finally
        {
          if (bodyStream != null)
          {
            try
            {
              bodyStream.close();
            }
            catch (IOException e)
            {
            }
            bodyStream = null;
          }
          synchronized (this)
          {
            try
            {
              executeMethod.abort();
            }
            catch (Throwable e)
            {
              shutdownException = e;
            }
            this.notifyAll();
          }
        }
      }
      catch (Throwable e)
      {
        // We catch exceptions here that should ONLY be InterruptedExceptions, as a result of the thread being aborted.
        this.generalException = e;
      }
    }

    public int getResponseCode()
      throws InterruptedException, IOException, HttpException
    {
      // Must wait until the response object is there
      while (true)
      {
        synchronized (this)
        {
          checkException(responseException);
          if (response != null)
            return response.getStatusLine().getStatusCode();
          wait();
        }
      }
    }

    public Map<String,List<String>> getResponseHeaders()
      throws InterruptedException, IOException, HttpException
    {
      // Must wait for the response object to appear
      while (true)
      {
        synchronized (this)
        {
          checkException(responseException);
          if (response != null)
          {
            Header[] headers = response.getAllHeaders();
            Map<String,List<String>> rval = new HashMap<String,List<String>>();
            int i = 0;
            while (i < headers.length)
            {
              Header h = headers[i++];
              String name = h.getName();
              String value = h.getValue();
              List<String> values = rval.get(name);
              if (values == null)
              {
                values = new ArrayList<String>();
                rval.put(name,values);
              }
              values.add(value);
            }
            return rval;
          }
          wait();
        }
      }

    }
    
    public String getFirstHeader(String headerName)
      throws InterruptedException, IOException, HttpException
    {
      // Must wait for the response object to appear
      while (true)
      {
        synchronized (this)
        {
          checkException(responseException);
          if (response != null)
          {
            Header h = response.getFirstHeader(headerName);
            if (h == null)
              return null;
            return h.getValue();
          }
          wait();
        }
      }
    }

    public LoginCookies getCookies()
      throws InterruptedException, IOException, HttpException
    {
      while (true)
      {
        synchronized (this)
        {
          if (responseException != null)
            throw new IllegalStateException("Check for response before getting cookies");
          checkException(cookieException);
          if (cookies != null)
            return cookies;
          wait();
        }
      }
    }
    
    public InputStream getSafeInputStream()
      throws InterruptedException, IOException, HttpException
    {
      // Must wait until stream is created, or until we note an exception was thrown.
      while (true)
      {
        synchronized (this)
        {
          if (responseException != null)
            throw new IllegalStateException("Check for response before getting stream");
          if (cookieException != null)
            throw new IllegalStateException("Check for cookies before getting stream");
          checkException(streamException);
          if (streamCreated)
            return threadStream;
          wait();
        }
      }
    }
    
    public void abort()
    {
      // This will be called during the finally
      // block in the case where all is well (and
      // the stream completed) and in the case where
      // there were exceptions.
      synchronized (this)
      {
        if (streamCreated)
        {
          if (threadStream != null)
            threadStream.abort();
        }
        abortThread = true;
      }
    }
    
    public void finishUp()
      throws InterruptedException
    {
      join();
    }
    
    protected synchronized void checkException(Throwable exception)
      throws IOException, HttpException
    {
      if (exception != null)
      {
        // Throw the current exception, but clear it, so no further throwing is possible on the same problem.
        Throwable e = exception;
        if (e instanceof IOException)
          throw (IOException)e;
        else if (e instanceof HttpException)
          throw (HttpException)e;
        else if (e instanceof RuntimeException)
          throw (RuntimeException)e;
        else if (e instanceof Error)
          throw (Error)e;
        else
          throw new RuntimeException("Unhandled exception of type: "+e.getClass().getName(),e);
      }
    }

  }

  protected static class OurBasicCookieStore implements CookieStore, Serializable {

    private static final long serialVersionUID = -7581093305228232025L;

    private final TreeSet<Cookie> cookies;

    public OurBasicCookieStore() {
      super();
      this.cookies = new TreeSet<Cookie>(new CookieIdentityComparator());
    }

    /**
     * Adds an {@link Cookie HTTP cookie}, replacing any existing equivalent cookies.
     * If the given cookie has already expired it will not be added, but existing
     * values will still be removed.
     *
     * @param cookie the {@link Cookie cookie} to be added
     *
     * @see #addCookies(Cookie[])
     *
     */
    public synchronized void addCookie(Cookie cookie) {
      if (cookie != null) {
        // first remove any old cookie that is equivalent
        cookies.remove(cookie);
        cookies.add(cookie);
      }
    }

    /**
     * Adds an array of {@link Cookie HTTP cookies}. Cookies are added individually and
     * in the given array order. If any of the given cookies has already expired it will
     * not be added, but existing values will still be removed.
     *
     * @param cookies the {@link Cookie cookies} to be added
     *
     * @see #addCookie(Cookie)
     *
     */
    public synchronized void addCookies(Cookie[] cookies) {
      if (cookies != null) {
        for (Cookie cooky : cookies) {
          this.addCookie(cooky);
        }
      }
    }

    /**
     * Returns an immutable array of {@link Cookie cookies} that this HTTP
     * state currently contains.
     *
     * @return an array of {@link Cookie cookies}.
     */
    public synchronized List<Cookie> getCookies() {
      //create defensive copy so it won't be concurrently modified
      return new ArrayList<Cookie>(cookies);
    }

    /**
     * Removes all of {@link Cookie cookies} in this HTTP state
     * that have expired by the specified {@link java.util.Date date}.
     *
     * @return true if any cookies were purged.
     *
     * @see Cookie#isExpired(Date)
     */
    public synchronized boolean clearExpired(final Date date) {
      if (date == null) {
        return false;
      }
      boolean removed = false;
      for (Iterator<Cookie> it = cookies.iterator(); it.hasNext();) {
        if (it.next().isExpired(date)) {
          it.remove();
            removed = true;
        }
      }
      return removed;
    }

    /**
     * Clears all cookies.
     */
    public synchronized void clear() {
      cookies.clear();
    }

    @Override
    public synchronized String toString() {
      return cookies.toString();
    }

  }

}
