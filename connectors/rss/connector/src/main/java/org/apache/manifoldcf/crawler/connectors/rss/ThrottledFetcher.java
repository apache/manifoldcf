/* $Id: ThrottledFetcher.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.common.XThreadInputStream;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import java.util.*;
import java.io.*;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.HttpStatus;
import org.apache.http.HttpHost;
import org.apache.http.Header;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.message.BasicHeader;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.NoHttpResponseException;
import org.apache.http.HttpException;

/** This class uses httpclient to fetch stuff from webservers.  However, it additionally controls the fetch
* rate in two ways: first, controlling the overall bandwidth used per server, and second, limiting the number
* of simultaneous open connections per server.  It's also capable of limiting the maximum number of fetches
* per time period per server as well; however, this functionality is not strictly necessary at this time because
* the CF scheduler does that at a higher layer.
* An instance of this class would very probably need to have a lifetime consistent with the long-term nature
* of these values, and be static.
* This class sets up a different Http connection pool for each server, so that we can foist off onto the httpclient
* library the task of limiting the number of connections.  This means that we need periodic polling to determine
* when idle pooled connections can be freed.
*/
public class ThrottledFetcher
{
  public static final String _rcsid = "@(#)$Id: ThrottledFetcher.java 988245 2010-08-23 18:39:35Z kwright $";

  /** This flag determines whether we record everything to the disk, as a means of doing a web snapshot */
  protected static final boolean recordEverything = false;

  /** The read chunk length */
  protected static final int READ_CHUNK_LENGTH = 4096;

  /** This counter keeps track of the total outstanding handles across everything, because we do try to control that */
  protected static int globalHandleCount = 0;
  /** This is the lock object for that global handle counter */
  protected static Integer globalHandleCounterLock = new Integer(0);

  /** This hash maps the server string (without port) to a server object, where
  * we can track the statistics and make sure we throttle appropriately */
  protected Map serverMap = new HashMap();

  /** Reference count for how many connections to this pool there are */
  protected int refCount = 0;

  // Current host name
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

  /** Note that we're about to need a handle (and make sure we have enough) */
  protected static void registerGlobalHandle(int maxHandles)
    throws ManifoldCFException
  {
    try
    {
      synchronized (globalHandleCounterLock)
      {
        while (globalHandleCount >= maxHandles)
        {
          globalHandleCounterLock.wait();
        }
        globalHandleCount++;
      }
    }
    catch (InterruptedException e)
    {
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
  }

  /** Note that we're done with a handle (so we can free it) */
  protected static void releaseGlobalHandle()
  {
    synchronized (globalHandleCounterLock)
    {
      globalHandleCount--;
      globalHandleCounterLock.notifyAll();
    }
  }

  /** Constructor.
  */
  public ThrottledFetcher()
  {
  }

  /** Establish a connection to a specified URL.
  * @param serverName is the FQDN of the server, e.g. foo.metacarta.com
  * @param minimumMillisecondsPerBytePerServer is the average number of milliseconds to wait
  *       between bytes, on
  *       average, over all streams reading from this server.  That means that the
  *       stream will block on fetch until the number of bytes being fetched, done
  *       in the average time interval required for that fetch, would not exceed
  *       the desired bandwidth.
  * @param minimumMillisecondsPerFetchPerServer is the number of milliseconds
  *        between fetches, as a minimum, on a per-server basis.  Set
  *        to zero for no limit.
  * @param maxOpenConnectionsPerServer is the maximum number of open connections to allow for a single server.
  *        If more than this number of connections would need to be open, then this connection request will block
  *        until this number will no longer be exceeded.
  * @param connectionLimit is the maximum desired outstanding connections at any one time.
  * @param connectionTimeoutMilliseconds is the number of milliseconds to wait for the connection before timing out.
  */
  public synchronized IThrottledConnection createConnection(String serverName, double minimumMillisecondsPerBytePerServer,
    int maxOpenConnectionsPerServer, long minimumMillisecondsPerFetchPerServer, int connectionLimit, int connectionTimeoutMilliseconds,
    String proxyHost, int proxyPort, String proxyAuthDomain, String proxyAuthUsername, String proxyAuthPassword)
    throws ManifoldCFException, ServiceInterruption
  {
    Server server;
    server = (Server)serverMap.get(serverName);
    if (server == null)
    {
      server = new Server(serverName);
      serverMap.put(serverName,server);
    }

    return new ThrottledConnection(server,minimumMillisecondsPerBytePerServer,maxOpenConnectionsPerServer,minimumMillisecondsPerFetchPerServer,
      connectionTimeoutMilliseconds,connectionLimit,
      proxyHost,proxyPort,proxyAuthDomain,proxyAuthUsername,proxyAuthPassword);
  }

  /** Poll.  This method is designed to allow idle connections to be closed and freed.
  */
  public synchronized void poll()
    throws ManifoldCFException
  {
    // Nothing needed now; connections are released when we're done with them.
  }

  /** Note that there is a repository connection that is using this object. */
  public synchronized void noteConnectionEstablished()
  {
    refCount++;
  }

  /** Connection pool no longer needed.  Call this to indicate that this object no
  * longer needs to keep its pools available, for the moment.
  */
  public synchronized void noteConnectionReleased()
  {
    refCount--;
    if (refCount == 0)
    {
      // Close all the servers one by one
      Iterator iter = serverMap.keySet().iterator();
      while (iter.hasNext())
      {
        String serverName = (String)iter.next();
        Server server = (Server)serverMap.get(serverName);
        server.discard();
      }
      serverMap.clear();
    }
  }

  /** This class represents an established connection to a URL.
  */
  protected static class ThrottledConnection implements IThrottledConnection
  {
    /** The connection bandwidth we want */
    protected double minimumMillisecondsPerBytePerServer;
    /** The maximum open connections per server */
    protected int maxOpenConnectionsPerServer;
    /** The minimum time between fetches */
    protected long minimumMillisecondsPerFetchPerServer;
    /** The server object we use to track connections and fetches. */
    protected Server server;
    /** The method object */
    protected HttpRequestBase executeMethod = null;
    /** The start-fetch time */
    protected long startFetchTime = -1L;
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
    /** Connection timeout in milliseconds */
    protected int connectionTimeoutMilliseconds;

    /** The thread that is actually doing the work */
    protected ExecuteMethodThread methodThread = null;
    /** Set if thread has been started */
    protected boolean threadStarted = false;
    /** The client connection manager */
    protected ClientConnectionManager connectionManager = null;
    /** The httpclient */
    protected HttpClient httpClient = null;
    
    /** Constructor.
    */
    public ThrottledConnection(Server server, double minimumMillisecondsPerBytePerServer, int maxOpenConnectionsPerServer,
      long minimumMillisecondsPerFetchPerServer, int connectionTimeoutMilliseconds, int connectionLimit,
      String proxyHost, int proxyPort, String proxyAuthDomain, String proxyAuthUsername, String proxyAuthPassword)
      throws ManifoldCFException
    {
      this.minimumMillisecondsPerBytePerServer = minimumMillisecondsPerBytePerServer;
      this.maxOpenConnectionsPerServer = maxOpenConnectionsPerServer;
      this.minimumMillisecondsPerFetchPerServer = minimumMillisecondsPerFetchPerServer;
      this.server = server;
      this.connectionTimeoutMilliseconds = connectionTimeoutMilliseconds;
      PoolingClientConnectionManager localConnectionManager = new PoolingClientConnectionManager();
      localConnectionManager.setMaxTotal(1);
      connectionManager = localConnectionManager;
      
      BasicHttpParams params = new BasicHttpParams();
      params.setBooleanParameter(CoreConnectionPNames.TCP_NODELAY,true);
      params.setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK,false);
      params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT,connectionTimeoutMilliseconds);
      params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,connectionTimeoutMilliseconds);
      DefaultHttpClient localHttpClient = new DefaultHttpClient(connectionManager,params);
      localHttpClient.setRedirectStrategy(new DefaultRedirectStrategy());
      
      // If there's a proxy, set that too.
      if (proxyHost != null && proxyHost.length() > 0)
      {

        // Configure proxy authentication
        if (proxyAuthUsername != null && proxyAuthUsername.length() > 0)
        {
          if (proxyAuthPassword == null)
            proxyAuthPassword = "";
          if (proxyAuthDomain == null)
            proxyAuthDomain = "";

          localHttpClient.getCredentialsProvider().setCredentials(
            new AuthScope(proxyHost, proxyPort),
            new NTCredentials(proxyAuthUsername, proxyAuthPassword, currentHost, proxyAuthDomain));
        }

        HttpHost proxy = new HttpHost(proxyHost, proxyPort);

        httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
      }
      
      httpClient = localHttpClient;

      registerGlobalHandle(connectionLimit);
      server.registerConnection(maxOpenConnectionsPerServer);
    }

    /** Begin the fetch process.
    * @param fetchType is a short descriptive string describing the kind of fetch being requested.  This
    *        is used solely for logging purposes.
    */
    public void beginFetch(String fetchType)
      throws ManifoldCFException
    {
      this.fetchType = fetchType;
      fetchCounter = 0L;
      try
      {
        server.beginFetch(minimumMillisecondsPerFetchPerServer);
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
      }
      threadStarted = false;
    }

    /** Log the fetch of a number of bytes. */
    public void logFetchCount(int count)
    {
      fetchCounter += (long)count;
    }


    /** Execute the fetch and get the return code.  This method uses the
    * standard logging mechanism to keep track of the fetch attempt.  It also
    * signals the following three conditions: ServiceInterruption (if a dynamic
    * error occurs), OK, or a static error code (for a condition where retry is
    * not likely to be helpful).  The actual HTTP error code is NOT returned by
    * this method.
    * @param protocol is the protocol to use to perform the access, e.g. "http"
    * @param port is the port to use to perform the access, where -1 means "use the default"
    * @param urlPath is the path part of the url, e.g. "/robots.txt"
    * @param userAgent is the value of the userAgent header to use.
    * @param from is the value of the from header to use.
    * @param proxyHost is the proxy host, or null if none.
    * @param proxyPort is the proxy port, or -1 if none.
    * @param proxyAuthDomain is the proxy authentication domain, or null.
    * @param proxyAuthUsername is the proxy authentication user name, or null.
    * @param proxyAuthPassword is the proxy authentication password, or null.
    * @param lastETag is the requested lastETag header value.
    * @param lastModified is the requested lastModified header value.
    * @return the status code: success, static error, or dynamic error.
    */
    public int executeFetch(String protocol, int port, String urlPath, String userAgent, String from,
      String lastETag, String lastModified)
      throws ManifoldCFException, ServiceInterruption
    {

      StringBuilder sb = new StringBuilder(protocol);
      sb.append("://").append(server.getServerName());
      if (port != -1)
        sb.append(":").append(Integer.toString(port));
      sb.append(urlPath);
      myUrl = sb.toString();

      // Create the get method
      executeMethod = new HttpGet(myUrl);
      
      startFetchTime = System.currentTimeMillis();

      // Set all appropriate headers
      executeMethod.setHeader(new BasicHeader("User-Agent",userAgent));
      executeMethod.setHeader(new BasicHeader("From",from));
      if (lastETag != null)
        executeMethod.setHeader(new BasicHeader("ETag",lastETag));
      if (lastModified != null)
        executeMethod.setHeader(new BasicHeader("Last-Modified",lastModified));
      // Create the execution thread.
      methodThread = new ExecuteMethodThread(this, server,
        minimumMillisecondsPerBytePerServer, httpClient, executeMethod);
      // Start the method thread, which will start the transaction
      try
      {
        methodThread.start();
        threadStarted = true;
        // We want to wait until at least the execution has fired, and then figure out where we
        // stand
        try
        {
          int statusCode = methodThread.getResponseCode();
          long currentTime;
          switch (statusCode)
          {
          case HttpStatus.SC_OK:
            return STATUS_OK;
          case HttpStatus.SC_UNAUTHORIZED:
          case HttpStatus.SC_USE_PROXY:
            // Permanent errors that mean, "fetch not allowed"
            return STATUS_SITEERROR;
          case HttpStatus.SC_REQUEST_TIMEOUT:
          case HttpStatus.SC_GATEWAY_TIMEOUT:
          case HttpStatus.SC_SERVICE_UNAVAILABLE:
            // Temporary service interruption
            // May want to make the retry time a parameter someday
            currentTime = System.currentTimeMillis();
            throw new ServiceInterruption("Http response temporary error on '"+myUrl+"': "+Integer.toString(statusCode),
              null,currentTime + 60L * 60000L,currentTime + 1440L * 60000L,-1,false);
          case HttpStatus.SC_NOT_MODIFIED:
            return STATUS_NOCHANGE;
          case HttpStatus.SC_INTERNAL_SERVER_ERROR:
            // Fail for a while, but give up after 24 hours
            currentTime = System.currentTimeMillis();
            throw new ServiceInterruption("Http response internal server error on '"+myUrl+"': "+Integer.toString(statusCode),
              null,currentTime + 60L * 60000L,currentTime + 1440L * 60000L,-1,false);
          case HttpStatus.SC_GONE:
          case HttpStatus.SC_NOT_FOUND:
          case HttpStatus.SC_BAD_GATEWAY:
          case HttpStatus.SC_BAD_REQUEST:
          default:
            return STATUS_PAGEERROR;
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
        executeMethod = null;
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (java.net.MalformedURLException e)
      {
        throwable = new ManifoldCFException("Illegal URI: '"+myUrl+"'",e);
        statusCode = FETCH_BAD_URI;
        return STATUS_PAGEERROR;
      }
      catch (java.net.SocketTimeoutException e)
      {
        throwable = e;
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Timed out waiting for IO for '"+myUrl+"': "+e.getMessage(), e, currentTime + 300000L,
          currentTime + 120L * 60000L,-1,false);
      }
      catch (ConnectTimeoutException e)
      {
        throwable = e;
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Timed out waiting for connect for '"+myUrl+"': "+e.getMessage(), e, currentTime + 60L * 60000L,
          currentTime + 720L * 60000L,-1,false);
      }
      catch (InterruptedIOException e)
      {
        throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
      }
      catch (CircularRedirectException e)
      {
        throwable = e;
        statusCode = FETCH_CIRCULAR_REDIRECT;
        return STATUS_PAGEERROR;
      }
      catch (NoHttpResponseException e)
      {
        throwable = e;
        // Give up after 2 hours.
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Timed out waiting for response for '"+myUrl+"'", e, currentTime + 15L * 60000L,
          currentTime + 120L * 60000L,-1,false);
      }
      catch (java.net.ConnectException e)
      {
        throwable = e;
        // Give up after 6 hours.
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Timed out waiting for a connection for '"+myUrl+"'", e, currentTime + 1000000L,
          currentTime + 720L * 60000L,-1,false);
      }
      catch (java.net.NoRouteToHostException e)
      {
        // This exception means we know the IP address but can't get there.  That's either a firewall issue, or it's something transient
        // with the network.  Some degree of retry is probably wise.
        throwable = e;
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("No route to host for '"+myUrl+"'", e, currentTime + 1000000L,
          currentTime + 720L * 60000L,-1,false);
      }
      catch (HttpException e)
      {
        throwable = e;
        statusCode = FETCH_IO_ERROR;
        return STATUS_PAGEERROR;
      }
      catch (IOException e)
      {
        // Treat this as a bad url.  We don't know what happened, but it isn't something we are going to naively
        // retry on.
        throwable = e;
        statusCode = FETCH_IO_ERROR;
        return STATUS_PAGEERROR;
      }
      catch (Throwable e)
      {
        Logging.connectors.debug("RSS: Caught an unexpected exception: "+e.getMessage(),e);
        throwable = e;
        statusCode = FETCH_UNKNOWN_ERROR;
        return STATUS_PAGEERROR;
      }
    }

    /** Get the http response code.
    *@return the response code.  This is either an HTTP response code, or one of the codes above.
    */
    public int getResponseCode()
      throws ManifoldCFException, ServiceInterruption
    {
      return statusCode;
    }

    /** Get the response input stream.  It is the responsibility of the caller
    * to close this stream when done.
    */
    public InputStream getResponseBodyStream()
      throws ManifoldCFException, ServiceInterruption
    {
      if (executeMethod == null)
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
      catch (java.net.SocketTimeoutException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Timed out waiting for IO for '"+myUrl+"': "+e.getMessage(), e, currentTime + 300000L,
          currentTime + 120L * 60000L,-1,false);
      }
      catch (ConnectTimeoutException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Timed out waiting for connect for '"+myUrl+"': "+e.getMessage(), e, currentTime + 60L * 60000L,
          currentTime + 720L * 60000L,-1,false);
      }
      catch (InterruptedIOException e)
      {
        methodThread.interrupt();
        throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
      }
      catch (NoHttpResponseException e)
      {
        // Give up after 2 hours.
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Timed out waiting for response for '"+myUrl+"'", e, currentTime + 15L * 60000L,
          currentTime + 120L * 60000L,-1,false);
      }
      catch (java.net.ConnectException e)
      {
        // Give up after 6 hours.
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Timed out waiting for a stream connection for '"+myUrl+"'", e, currentTime + 1000000L,
          currentTime + 720L * 60000L,-1,false);
      }
      catch (java.net.NoRouteToHostException e)
      {
        // This exception means we know the IP address but can't get there.  That's either a firewall issue, or it's something transient
        // with the network.  Some degree of retry is probably wise.
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("No route to host for '"+myUrl+"'", e, currentTime + 1000000L,
          currentTime + 720L * 60000L,-1,false);
      }
      catch (HttpException e)
      {
        throw new ManifoldCFException("Http exception reading stream: "+e.getMessage(),e);
      }
      catch (IOException e)
      {
        throw new ManifoldCFException("I/O exception reading stream: "+e.getMessage(),e);
      }
    }

    /** Get a specified response header, if it exists.
    *@param headerName is the name of the header.
    *@return the header value, or null if it doesn't exist.
    */
    public String getResponseHeader(String headerName)
      throws ManifoldCFException, ServiceInterruption
    {
      if (executeMethod == null)
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
      catch (java.net.SocketTimeoutException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Timed out waiting for IO for '"+myUrl+"': "+e.getMessage(), e, currentTime + 300000L,
          currentTime + 120L * 60000L,-1,false);
      }
      catch (ConnectTimeoutException e)
      {
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Timed out waiting for connect for '"+myUrl+"': "+e.getMessage(), e, currentTime + 60L * 60000L,
          currentTime + 720L * 60000L,-1,false);
      }
      catch (InterruptedIOException e)
      {
        methodThread.interrupt();
        throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
      }
      catch (NoHttpResponseException e)
      {
        // Give up after 2 hours.
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Timed out waiting for response for '"+myUrl+"'", e, currentTime + 15L * 60000L,
          currentTime + 120L * 60000L,-1,false);
      }
      catch (java.net.ConnectException e)
      {
        // Give up after 6 hours.
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Timed out waiting for a connection for '"+myUrl+"'", e, currentTime + 1000000L,
          currentTime + 720L * 60000L,-1,false);
      }
      catch (java.net.NoRouteToHostException e)
      {
        // This exception means we know the IP address but can't get there.  That's either a firewall issue, or it's something transient
        // with the network.  Some degree of retry is probably wise.
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("No route to host for '"+myUrl+"'", e, currentTime + 1000000L,
          currentTime + 720L * 60000L,-1,false);
      }
      catch (HttpException e)
      {
        throw new ManifoldCFException("Http exception reading response: "+e.getMessage(),e);
      }
      catch (IOException e)
      {
        throw new ManifoldCFException("I/O exception reading response: "+e.getMessage(),e);
      }
    }

    /** Done with the fetch.  Call this when the fetch has been completed.  A log entry will be generated
    * describing what was done.
    */
    public void doneFetch(IVersionActivity activities)
      throws ManifoldCFException
    {
      
      if (fetchType != null)
      {
        // Abort the connection, if not already complete
        try
        {
          methodThread.abort();
        }
        catch (InterruptedException e)
        {
          throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
        catch (InterruptedIOException e)
        {
          throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
        catch (HttpException e)
        {
          throw new ManifoldCFException("Unexpected Http exception: "+e.getMessage(),e);
        }
        catch (IOException e)
        {
          throw new ManifoldCFException("Unexpected IO exception: "+e.getMessage(),e);
        }

        long endTime = System.currentTimeMillis();
        server.endFetch();

        activities.recordActivity(new Long(startFetchTime),RSSConnector.ACTIVITY_FETCH,
          new Long(fetchCounter),myUrl,Integer.toString(statusCode),(throwable==null)?null:throwable.getMessage(),null);

        Logging.connectors.info("RSS: FETCH "+fetchType+"|"+myUrl+"|"+new Long(startFetchTime).toString()+"+"+new Long(endTime-startFetchTime).toString()+"|"+
          Integer.toString(statusCode)+"|"+new Long(fetchCounter).toString()+"|"+((throwable==null)?"":(throwable.getClass().getName()+"| "+throwable.getMessage())));
        if (throwable != null)
        {
          if (Logging.connectors.isDebugEnabled())
            Logging.connectors.debug("RSS: Fetch exception for '"+myUrl+"'",throwable);
        }
        
        // Shut down (join) the connection thread, if any, and if it started
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
          catch (HttpException e)
          {
            throw new ManifoldCFException("Unexpected HTTP exception: "+e.getMessage(),e);
          }
          catch (IOException e)
          {
            throw new ManifoldCFException("Unexpected IO exception: "+e.getMessage(),e);
          }
          threadStarted = false;
          methodThread = null;
        }
        
        executeMethod = null;
        throwable = null;
        startFetchTime = -1L;
        myUrl = null;
        statusCode = -1;
        fetchType = null;
      }
    }

    /** Close the connection.  Call this to end this server connection.
    */
    public void close()
      throws ManifoldCFException
    {
      // Clean up the connection pool.  This should do the necessary bookkeeping to release the one connection that's sitting there.
      connectionManager.shutdown();
      server.releaseConnection();
      releaseGlobalHandle();
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
    /** The server object we use to track throttling */
    protected Server server;
    /** The stream we are wrapping. */
    protected InputStream inputStream;

    /** Constructor.
    */
    public ThrottledInputstream(ThrottledConnection connection, Server server, InputStream is, double minimumMillisecondsPerBytePerServer)
    {
      this.throttledConnection = connection;
      this.server = server;
      this.inputStream = is;
      this.minimumMillisecondsPerBytePerServer = minimumMillisecondsPerBytePerServer;
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
        server.beginRead(len,minimumMillisecondsPerBytePerServer);
        int amt = 0;
        try
        {
          amt = inputStream.read(b,off,len);
          return amt;
        }
        finally
        {
          if (amt == -1)
            server.endRead(len,0);
          else
          {
            server.endRead(len,amt);
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
      inputStream.close();
    }

  }

  /** This class represents the throttling stuff kept around for a single server.
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
  * For the "maximum open connections" limit, the best thing would be to establish a separate MultiThreadedConnectionPool
  * for each Server.  Then, the limit would be automatic.
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
  protected class Server
  {
    /** The fqdn of the server */
    protected String serverName;
    /** This is the time of the next allowed fetch (in ms since epoch) */
    protected long nextFetchTime = 0L;

    // Bandwidth throttling variables
    /** Reference count for bandwidth variables */
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

    /** Outstanding connection counter */
    protected int outstandingConnections = 0;

    /** Constructor */
    public Server(String serverName)
    {
      this.serverName = serverName;
    }

    /** Get the fqdn of the server */
    public String getServerName()
    {
      return serverName;
    }

    /** Register an outstanding connection (and wait until it can be obtained before proceeding) */
    public synchronized void registerConnection(int maxOutstandingConnections)
      throws ManifoldCFException
    {
      try
      {
        while (outstandingConnections >= maxOutstandingConnections)
        {
          wait();
        }
        outstandingConnections++;
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
    }

    /** Release an outstanding connection back into the pool */
    public synchronized void releaseConnection()
    {
      outstandingConnections--;
      notifyAll();
    }

    /** Note the start of a fetch operation.  Call this method just before the actual stream access begins.
    * May wait until schedule allows.
    */
    public void beginFetch(long minimumMillisecondsPerFetchPerServer)
      throws InterruptedException
    {
      //if (Logging.connectors.isTraceEnabled())
      //      Logging.connectors.trace("RSS: Note begin fetch for '"+serverName+"'");
      // First, do any waiting, and reschedule as needed
      long waitAmount = 0L;
      long currentTime = System.currentTimeMillis();

      // System.out.println("Begin fetch for server "+this.toString()+" with minimum milliseconds per fetch of "+new Long(minimumMillisecondsPerFetchPerServer).toString()+
      //      " Current time: "+new Long(currentTime).toString()+ " Next fetch time: "+new Long(nextFetchTime).toString());

      synchronized (this)
      {
        if (currentTime < nextFetchTime)
        {
          waitAmount = nextFetchTime-currentTime;
          nextFetchTime = nextFetchTime + minimumMillisecondsPerFetchPerServer;
        }
        else
          nextFetchTime = currentTime + minimumMillisecondsPerFetchPerServer;
      }
      if (waitAmount > 0L)
      {
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("RSS: Performing a fetch wait for server '"+serverName+"' for "+
          new Long(waitAmount).toString()+" ms.");
        ManifoldCF.sleep(waitAmount);
      }

      // System.out.println("For server "+this.toString()+", at "+new Long(System.currentTimeMillis()).toString()+", the next fetch time is now "+new Long(nextFetchTime).toString());

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
      //if (Logging.connectors.isTraceEnabled())
      //      Logging.connectors.trace("RSS: Begin fetch noted for '"+serverName+"'");

    }

    /** Note the end of a fetch operation.  Call this method just after the fetch completes.
    */
    public void endFetch()
    {
      //if (Logging.connectors.isTraceEnabled())
      //      Logging.connectors.trace("RSS: Note end fetch for '"+serverName+"'");

      synchronized (this)
      {
        refCount--;
      }

      //if (Logging.connectors.isTraceEnabled())
      //      Logging.connectors.trace("RSS: End fetch noted for '"+serverName+"'");

    }

    /** Note the start of an individual byte read of a specified size.  Call this method just before the
    * read request takes place.  Performs the necessary delay prior to reading specified number of bytes from the server.
    */
    public void beginRead(int byteCount, double minimumMillisecondsPerBytePerServer)
      throws InterruptedException
    {
      //if (Logging.connectors.isTraceEnabled())
      //      Logging.connectors.trace("RSS: Note begin read for '"+serverName+"'");

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
          //if (Logging.connectors.isTraceEnabled())
          //      Logging.connectors.trace("RSS: Read begin noted; gathering stats for '"+serverName+"'");

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
          Logging.connectors.debug("RSS: Performing a read wait on server '"+serverName+"' of "+
          new Long(waitTime).toString()+" ms.");
        ManifoldCF.sleep(waitTime);
      }

      //if (Logging.connectors.isTraceEnabled())
      //      Logging.connectors.trace("RSS: Begin read noted for '"+serverName+"'");

    }

    /** Note the end of an individual read from the server.  Call this just after an individual read completes.
    * Pass the actual number of bytes read to the method.
    */
    public void endRead(int originalCount, int actualCount)
    {
      //if (Logging.connectors.isTraceEnabled())
      //      Logging.connectors.trace("RSS: Note end read for '"+serverName+"'");

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

      //if (Logging.connectors.isTraceEnabled())
      //      Logging.connectors.trace("RSS: End read noted for '"+serverName+"'");

    }

    /** Discard this server.
    */
    public void discard()
    {
      // Nothing needed anymore
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
    /** The connection bandwidth we want */
    protected final double minimumMillisecondsPerBytePerServer;
    /** The server object we use to track connections and fetches. */
    protected final Server server;
    /** Client and method, all preconfigured */
    protected final HttpClient httpClient;
    protected final HttpRequestBase executeMethod;
    
    protected HttpResponse response = null;
    protected Throwable responseException = null;
    protected XThreadInputStream threadStream = null;
    protected boolean threadCreated = false;
    protected Throwable streamException = null;

    protected Throwable shutdownException = null;

    protected Throwable generalException = null;
    
    public ExecuteMethodThread(ThrottledConnection theConnection, Server server,
      double minimumMillisecondsPerBytePerServer,
      HttpClient httpClient, HttpRequestBase executeMethod)
    {
      super();
      setDaemon(true);
      this.theConnection = theConnection;
      this.server = server;
      this.minimumMillisecondsPerBytePerServer = minimumMillisecondsPerBytePerServer;
      this.httpClient = httpClient;
      this.executeMethod = executeMethod;
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
            try
            {
              response = httpClient.execute(executeMethod);
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
            
          // Start the transfer of the content
          if (responseException == null)
          {
            synchronized (this)
            {
              try
              {
                InputStream bodyStream = response.getEntity().getContent();
                if (bodyStream != null)
                {
                  bodyStream = new ThrottledInputstream(theConnection,server,bodyStream,minimumMillisecondsPerBytePerServer);
                  threadStream = new XThreadInputStream(bodyStream);
                }
                threadCreated = true;
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
          
          if (responseException == null && streamException == null)
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
          checkException(streamException);
          if (threadCreated)
            return threadStream;
          wait();
        }
      }
    }
    
    public void abort()
      throws InterruptedException, IOException, HttpException
    {
      // This will be called during the stream access, either
      // in addition to getSafeInputStream or in exchange.
      // So we wait for the stream, and when we have it we
      // kill it, and that will cause the whole thread to abort, 
      // if it isn't already done.
      while (true)
      {
        synchronized (this)
        {
          if (responseException != null)
            throw new IllegalStateException("Check for response before aborting stream");
          checkException(streamException);
          if (threadCreated)
          {
            if (threadStream != null)
              threadStream.abort();
            return;
          }
          wait();
        }
      }
    }
    
    public void finishUp()
      throws InterruptedException, IOException, HttpException
    {
      join();
      checkException(shutdownException);
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

}
