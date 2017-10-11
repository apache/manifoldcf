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
import org.apache.manifoldcf.connectorcommon.interfaces.*;
import org.apache.manifoldcf.connectorcommon.common.XThreadInputStream;
import org.apache.manifoldcf.connectorcommon.common.InterruptibleSocketFactory;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import java.util.*;
import java.io.*;
import java.net.*;

import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.config.SocketConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.apache.http.HttpStatus;
import org.apache.http.HttpHost;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;

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

  /** This hash maps the server string (without port) to a pool throttling object, where
  * we can track the statistics and make sure we throttle appropriately */
  protected final Map<String,IConnectionThrottler> serverMap = new HashMap<String,IConnectionThrottler>();

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
  * @param connectionLimit is the maximum desired outstanding connections at any one time.
  * @param connectionTimeoutMilliseconds is the number of milliseconds to wait for the connection before timing out.
  */
  public synchronized IThrottledConnection createConnection(IThreadContext threadContext, String throttleGroupName,
    String serverName, int connectionLimit, int connectionTimeoutMilliseconds,
    String proxyHost, int proxyPort, String proxyAuthDomain, String proxyAuthUsername, String proxyAuthPassword,
    IAbortActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    IConnectionThrottler server;
    server = serverMap.get(serverName);
    if (server == null)
    {
      // Create a connection throttler for this server
      IThrottleGroups tg = ThrottleGroupsFactory.make(threadContext);
      server = tg.obtainConnectionThrottler(RSSConnector.rssThrottleGroupType, throttleGroupName, new String[]{serverName});
      serverMap.put(serverName,server);
    }

    return new ThrottledConnection(serverName, server,
      connectionTimeoutMilliseconds,connectionLimit,
      proxyHost,proxyPort,proxyAuthDomain,proxyAuthUsername,proxyAuthPassword,
      activities);
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
      // Since we don't have any actual pools here, this can be a no-op for now
      // MHL
      serverMap.clear();
    }
  }

  /** This class represents an established connection to a URL.
  */
  protected static class ThrottledConnection implements IThrottledConnection
  {
    /** The server fqdn */
    protected final String serverName;
    /** The throttling object we use to track connections */
    protected final IConnectionThrottler connectionThrottler;
    /** The throttling object we use to track fetches */
    protected final IFetchThrottler fetchThrottler;
    /** Connection timeout in milliseconds */
    protected final int connectionTimeoutMilliseconds;
    /** The client connection manager */
    protected final HttpClientConnectionManager connectionManager;
    /** The httpclient */
    protected final HttpClient httpClient;

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

    /** The thread that is actually doing the work */
    protected ExecuteMethodThread methodThread = null;
    /** Set if thread has been started */
    protected boolean threadStarted = false;
    
    /** Abort checker */
    protected final AbortChecker abortChecker;
    
    /** Constructor.
    */
    public ThrottledConnection(String serverName,
      IConnectionThrottler connectionThrottler,
      int connectionTimeoutMilliseconds, int connectionLimit,
      String proxyHost, int proxyPort, String proxyAuthDomain, String proxyAuthUsername, String proxyAuthPassword,
      IAbortActivity activities)
      throws ManifoldCFException, ServiceInterruption
    {
      this.serverName = serverName;
      this.connectionThrottler = connectionThrottler;
      this.connectionTimeoutMilliseconds = connectionTimeoutMilliseconds;
      this.abortChecker = new AbortChecker(activities);
      
      // Create the https scheme for this connection
      javax.net.ssl.SSLSocketFactory httpsSocketFactory = KeystoreManagerFactory.getTrustingSecureSocketFactory();;
      SSLConnectionSocketFactory myFactory = new SSLConnectionSocketFactory(new InterruptibleSocketFactory(httpsSocketFactory,connectionTimeoutMilliseconds),
        NoopHostnameVerifier.INSTANCE);

      PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager(RegistryBuilder.<ConnectionSocketFactory>create()
        .register("http", PlainConnectionSocketFactory.getSocketFactory())
        .register("https", myFactory)
        .build());
      poolingConnectionManager.setDefaultMaxPerRoute(1);
      poolingConnectionManager.setValidateAfterInactivity(2000);
      poolingConnectionManager.setDefaultSocketConfig(SocketConfig.custom()
        .setTcpNoDelay(true)
        .setSoTimeout(connectionTimeoutMilliseconds)
        .build());
      connectionManager = poolingConnectionManager;

      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

      RequestConfig.Builder requestBuilder = RequestConfig.custom()
          .setCircularRedirectsAllowed(true)
          .setSocketTimeout(connectionTimeoutMilliseconds)
          .setExpectContinueEnabled(true)
          .setConnectTimeout(connectionTimeoutMilliseconds)
          .setConnectionRequestTimeout(connectionTimeoutMilliseconds);

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

          credentialsProvider.setCredentials(
            new AuthScope(proxyHost, proxyPort),
            new NTCredentials(proxyAuthUsername, proxyAuthPassword, currentHost, proxyAuthDomain));
        }

        HttpHost proxy = new HttpHost(proxyHost, proxyPort);

        requestBuilder.setProxy(proxy);
      }

      httpClient = HttpClients.custom()
        .setConnectionManager(connectionManager)
        .setMaxConnTotal(1)
        .disableAutomaticRetries()
        .setDefaultRequestConfig(requestBuilder.build())
        .setDefaultCredentialsProvider(credentialsProvider)
        .setRequestExecutor(new HttpRequestExecutor(connectionTimeoutMilliseconds))
        .setRedirectStrategy(new LaxRedirectStrategy())
        .build();

      registerGlobalHandle(connectionLimit);
      try
      {
        int result = connectionThrottler.waitConnectionAvailable(abortChecker);
        if (result != IConnectionThrottler.CONNECTION_FROM_CREATION)
          throw new IllegalStateException("Got back unexpected value from waitForAConnection() of "+result);
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException(e.getMessage(),ManifoldCFException.INTERRUPTED);
      }
      catch (BreakException e)
      {
        abortChecker.rethrowExceptions();
      }
      fetchThrottler = connectionThrottler.getNewConnectionFetchThrottler();
    }

    /** Begin the fetch process.
    * @param fetchType is a short descriptive string describing the kind of fetch being requested.  This
    *        is used solely for logging purposes.
    */
    @Override
    public void beginFetch(String fetchType)
      throws ManifoldCFException, ServiceInterruption
    {
      this.fetchType = fetchType;
      fetchCounter = 0L;
      try
      {
        if (fetchThrottler.obtainFetchDocumentPermission(abortChecker) == false)
          throw new IllegalStateException("obtainFetchDocumentPermission() had unexpected return value");
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
      }
      catch (BreakException e)
      {
        abortChecker.rethrowExceptions();
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
    @Override
    public int executeFetch(String protocol, int port, String urlPath, String userAgent, String from,
      String lastETag, String lastModified)
      throws ManifoldCFException, ServiceInterruption
    {

      StringBuilder sb = new StringBuilder(protocol);
      sb.append("://").append(serverName);
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
      executeMethod.setHeader(new BasicHeader("Accept","*/*"));

      if (lastETag != null)
        executeMethod.setHeader(new BasicHeader("ETag",lastETag));
      if (lastModified != null)
        executeMethod.setHeader(new BasicHeader("Last-Modified",lastModified));
      // Create the execution thread.
      methodThread = new ExecuteMethodThread(this, fetchThrottler,
        httpClient, executeMethod);
      // Start the method thread, which will start the transaction
      try
      {
        methodThread.start();
        threadStarted = true;
        // We want to wait until at least the execution has fired, and then figure out where we
        // stand
        try
        {
          statusCode = methodThread.getResponseCode();
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
    @Override
    public int getResponseCode()
      throws ManifoldCFException, ServiceInterruption
    {
      return statusCode;
    }

    /** Get the response input stream.  It is the responsibility of the caller
    * to close this stream when done.
    */
    @Override
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
    @Override
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
    @Override
    public void doneFetch(IProcessActivity activities)
      throws ManifoldCFException
    {
      
      if (fetchType != null)
      {
        if (methodThread != null && threadStarted)
          methodThread.abort();
        long endTime = System.currentTimeMillis();

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
    @Override
    public void close()
      throws ManifoldCFException
    {
      // Clean up the connection pool.  This should do the necessary bookkeeping to release the one connection that's sitting there.
      connectionManager.shutdown();
      connectionThrottler.noteConnectionDestroyed();
      releaseGlobalHandle();
    }

  }

  /** This class throttles an input stream based on the specified byte rate parameters.  The
  * throttling takes place across all streams that are open to the server in question.
  */
  protected static class ThrottledInputstream extends InputStream
  {
    /** Throttled connection */
    protected final ThrottledConnection throttledConnection;
    /** Stream throttler */
    protected final IStreamThrottler streamThrottler;
    /** The stream we are wrapping. */
    protected final InputStream inputStream;

    /** Constructor.
    */
    public ThrottledInputstream(ThrottledConnection throttledConnection, IStreamThrottler streamThrottler, InputStream is)
    {
      this.throttledConnection = throttledConnection;
      this.streamThrottler = streamThrottler;
      this.inputStream = is;
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
        if (streamThrottler.obtainReadPermission(len) == false)
          throw new IllegalStateException("Throttler shut down while still active");
        int amt = 0;
        try
        {
          amt = inputStream.read(b,off,len);
          return amt;
        }
        finally
        {
          if (amt == -1)
            streamThrottler.releaseReadPermission(len,0);
          else
          {
            streamThrottler.releaseReadPermission(len,amt);
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
      finally
      {
        streamThrottler.closeStream();
      }
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
    /** The fetch throttler */
    protected final IFetchThrottler fetchThrottler;
    /** Client and method, all preconfigured */
    protected final HttpClient httpClient;
    protected final HttpRequestBase executeMethod;
    
    protected HttpResponse response = null;
    protected Throwable responseException = null;
    protected XThreadInputStream threadStream = null;
    protected InputStream bodyStream = null;
    protected boolean streamCreated = false;
    protected Throwable streamException = null;

    protected boolean abortThread = false;
    
    protected Throwable shutdownException = null;

    protected Throwable generalException = null;
    
    public ExecuteMethodThread(ThrottledConnection theConnection, IFetchThrottler fetchThrottler,
      HttpClient httpClient, HttpRequestBase executeMethod)
    {
      super();
      setDaemon(true);
      this.theConnection = theConnection;
      this.fetchThrottler = fetchThrottler;
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
            if (!abortThread)
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
          }
            
          // Start the transfer of the content
          if (responseException == null)
          {
            synchronized (this)
            {
              if (!abortThread)
              {
                try
                {
                  bodyStream = response.getEntity().getContent();
                  if (bodyStream != null)
                  {
                    bodyStream = new ThrottledInputstream(theConnection,fetchThrottler.createFetchStream(),bodyStream);
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

  /** This class furnishes an abort signal whenever the job activity says it should.
  * It should never be invoked from a background thread, only from a ManifoldCF thread.
  */
  protected static class AbortChecker implements IBreakCheck
  {
    protected final IAbortActivity activities;
    protected ServiceInterruption serviceInterruption = null;
    protected ManifoldCFException mcfException = null;
    
    public AbortChecker(IAbortActivity activities)
    {
      this.activities = activities;
    }
    
    @Override
    public long abortCheck()
      throws BreakException, InterruptedException
    {
      try
      {
        activities.checkJobStillActive();
        return 1000L;
      }
      catch (ServiceInterruption e)
      {
        serviceInterruption = e;
        throw new BreakException("Break requested: "+e.getMessage(),e);
      }
      catch (ManifoldCFException e)
      {
        if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
          throw new InterruptedException("Interrupted: "+e.getMessage());
        mcfException = e;
        throw new BreakException("Error during break check: "+e.getMessage(),e);
      }
    }
    
    public void rethrowExceptions()
      throws ManifoldCFException, ServiceInterruption
    {
      if (serviceInterruption != null)
        throw serviceInterruption;
      if (mcfException != null)
        throw mcfException;
    }
  }
  
}
