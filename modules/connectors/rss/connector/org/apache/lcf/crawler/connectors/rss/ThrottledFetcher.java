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
package org.apache.lcf.crawler.connectors.rss;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.crawler.system.Logging;
import org.apache.lcf.crawler.system.LCF;
import java.util.*;
import java.io.*;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.params.*;
import org.apache.commons.httpclient.protocol.*;
import org.apache.commons.httpclient.auth.*;

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
  public static final String _rcsid = "@(#)$Id$";

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
    throws LCFException
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
      throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
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
    int maxOpenConnectionsPerServer, long minimumMillisecondsPerFetchPerServer, int connectionLimit, int connectionTimeoutMilliseconds)
    throws LCFException, ServiceInterruption
  {
    Server server;
    server = (Server)serverMap.get(serverName);
    if (server == null)
    {
      server = new Server(serverName);
      serverMap.put(serverName,server);
    }

    return new ThrottledConnection(server,minimumMillisecondsPerBytePerServer,maxOpenConnectionsPerServer,minimumMillisecondsPerFetchPerServer,connectionTimeoutMilliseconds,connectionLimit);
  }

  /** Poll.  This method is designed to allow idle connections to be closed and freed.
  */
  public synchronized void poll()
    throws LCFException
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

  // Where to record result info/file data
  protected final static String resultLogFile = "/common/rss/resultlog";
  // Where to record the actual data
  protected final static String dataFileFolder = "/common/rss/data/";

  // This is the one instance of the output class
  protected static DataRecorder dataRecorder = new DataRecorder();

  /** This class takes care of recording data and results for posterity */
  protected static class DataRecorder
  {
    protected int documentNumber = 0;
    protected long startTime = System.currentTimeMillis();
    protected boolean initialized = false;

    public DataRecorder()
    {
    }

    protected String readFile(File f)
      throws IOException
    {
      InputStream is = new FileInputStream(f);
      try
      {
        Reader r = new InputStreamReader(is);
        try
        {
          char[] characterBuf = new char[32];
          int amt = r.read(characterBuf);
          String rval = new String(characterBuf,0,amt);
          return rval;
        }
        finally
        {
          r.close();
        }
      }
      finally
      {
        is.close();
      }
    }

    protected void writeFile(File f, String data)
      throws IOException
    {
      OutputStream os = new FileOutputStream(f);
      try
      {
        Writer w = new OutputStreamWriter(os);
        try
        {
          w.write(data);
        }
        finally
        {
          w.flush();
        }
      }
      finally
      {
        os.close();
      }
    }

    protected synchronized void initializeParameters()
    {
      if (initialized)
        return;

      // Create folder, if it doesn't yet exist
      try
      {
        new File(dataFileFolder).mkdirs();
      }
      catch (Exception e)
      {
        e.printStackTrace();
      }

      // Either read starting timestamp, or write it
      File timestampFile = new File("/common/rss/timestamp.log");
      if (timestampFile.exists())
      {
        try
        {
          String data = readFile(timestampFile);
          startTime = new Long(data).longValue();
        }
        catch (IOException e)
        {
          e.printStackTrace();
        }
      }
      else
      {
        try
        {
          writeFile(timestampFile,new Long(startTime).toString());
        }
        catch (IOException e)
        {
          e.printStackTrace();
        }
      }

      // Read starting document number, if it exists
      File documentNumberFile = new File("/common/rss/docnumber.log");
      if (documentNumberFile.exists())
      {
        try
        {
          String data = readFile(documentNumberFile);
          documentNumber = Integer.parseInt(data);
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }

      initialized = true;
    }

    public DataSession getSession(String url)
      throws LCFException
    {
      initializeParameters();
      return new DataSession(this,url);
    }

    /** Atomically write resultlog record, returning data file name to use */
    public synchronized String writeResponseRecord(String url, int responseCode, ArrayList headerNames, ArrayList headerValues)
      throws LCFException
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
            writer.write("Time: "+new Long(System.currentTimeMillis()-startTime).toString()+"\n");
            writer.write("URI: "+url+"\n");
            writer.write("File: "+documentName+"\n");
            writer.write("Code: "+Integer.toString(responseCode)+"\n");
            int i = 0;
            while (i < headerNames.size())
            {
              writer.write("Header: "+(String)headerNames.get(i)+":"+(String)headerValues.get(i)+"\n");
              i++;
            }
            writeFile(new File("/common/rss/docnumber.log"),new Integer(documentNumber).toString());
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
        throw new LCFException("Error recording file info: "+e.getMessage(),e);
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
      throws LCFException
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
    protected HttpMethodBase fetchMethod = null;
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
    /** The connection pool (max size 1) */
    protected MultiThreadedHttpConnectionManager connectionManager = null;
    /** Connection timeout in milliseconds */
    protected int connectionTimeoutMilliseconds;

    /** Hack added to record all access data from current crawler */
    protected DataSession dataSession = null;

    /** Constructor.
    */
    public ThrottledConnection(Server server, double minimumMillisecondsPerBytePerServer, int maxOpenConnectionsPerServer,
      long minimumMillisecondsPerFetchPerServer, int connectionTimeoutMilliseconds, int connectionLimit)
      throws LCFException
    {
      this.minimumMillisecondsPerBytePerServer = minimumMillisecondsPerBytePerServer;
      this.maxOpenConnectionsPerServer = maxOpenConnectionsPerServer;
      this.minimumMillisecondsPerFetchPerServer = minimumMillisecondsPerFetchPerServer;
      this.server = server;
      this.connectionTimeoutMilliseconds = connectionTimeoutMilliseconds;
      connectionManager = new MultiThreadedHttpConnectionManager();
      HttpConnectionManagerParams httpConParam = connectionManager.getParams();
      httpConParam.setMaxTotalConnections(1);
      httpConParam.setConnectionTimeout(connectionTimeoutMilliseconds);
      httpConParam.setSoTimeout(connectionTimeoutMilliseconds);
      registerGlobalHandle(connectionLimit);
      server.registerConnection(maxOpenConnectionsPerServer);
    }

    /** Begin the fetch process.
    * @param fetchType is a short descriptive string describing the kind of fetch being requested.  This
    *        is used solely for logging purposes.
    */
    public void beginFetch(String fetchType)
      throws LCFException
    {
      this.fetchType = fetchType;
      fetchCounter = 0L;
      try
      {
        server.beginFetch(minimumMillisecondsPerFetchPerServer);
      }
      catch (InterruptedException e)
      {
        throw new LCFException("Interrupted",LCFException.INTERRUPTED);
      }
    }

    /** Log the fetch of a number of bytes. */
    public void logFetchCount(int count)
    {
      fetchCounter += (long)count;
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
          rval = client.executeMethod(hostConfiguration,executeMethod);
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
      String proxyHost, int proxyPort, String proxyAuthDomain, String proxyAuthUsername, String proxyAuthPassword,
      String lastETag, String lastModified)
      throws LCFException, ServiceInterruption
    {

      StringBuffer sb = new StringBuffer(protocol);
      sb.append("://").append(server.getServerName());
      if (port != -1)
        sb.append(":").append(Integer.toString(port));
      sb.append(urlPath);
      myUrl = sb.toString();

      if (recordEverything)
        // Start a new data session
        dataSession = dataRecorder.getSession(myUrl);

      try
      {
        HttpClient client = new HttpClient(connectionManager);
        // Permit circular redirections, because that is how some sites set cookies
        client.getParams().setParameter(org.apache.commons.httpclient.params.HttpClientParams.ALLOW_CIRCULAR_REDIRECTS,new Boolean(true));
        fetchMethod = new GetMethod();
        HostConfiguration config = new HostConfiguration();

        config.setHost(server.getServerName(),port,Protocol.getProtocol(protocol));

        // If there's a proxy, set that too.
        if (proxyHost != null && proxyHost.length() > 0)
        {
          config.setProxy(proxyHost,proxyPort);
          if (proxyAuthUsername != null && proxyAuthUsername.length() > 0)
          {
            if (proxyAuthPassword == null)
              proxyAuthPassword = "";
            if (proxyAuthDomain == null)
              proxyAuthDomain = "";
            // Set up NTLM credentials for this fetch too.
            client.getState().setProxyCredentials(AuthScope.ANY,
              new NTCredentials(proxyAuthUsername,proxyAuthPassword,currentHost,proxyAuthDomain));
          }
        }

        startFetchTime = System.currentTimeMillis();
        fetchMethod.setURI(new URI(urlPath,true));

        // Set all appropriate headers
        fetchMethod.setRequestHeader("User-Agent",userAgent);
        fetchMethod.setRequestHeader("From",from);
        if (lastETag != null)
          fetchMethod.setRequestHeader("ETag",lastETag);
        if (lastModified != null)
          fetchMethod.setRequestHeader("Last-Modified",lastModified);

        fetchMethod.getParams().setSoTimeout(connectionTimeoutMilliseconds);
        // fetchMethod.getParams().setIntParameter("http.socket.timeout", connectionTimeoutMilliseconds);

        fetchMethod.setFollowRedirects(true);

        // Fire it off!
        try
        {
          ExecuteMethodThread t = new ExecuteMethodThread(client,config,fetchMethod);
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
        catch (java.net.SocketTimeoutException e)
        {
          throwable = e;
          long currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("Timed out waiting for IO for '"+myUrl+"': "+e.getMessage(), e, currentTime + 300000L,
            currentTime + 120L * 60000L,-1,false);
        }
        catch (org.apache.commons.httpclient.ConnectTimeoutException e)
        {
          throwable = e;
          long currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("Timed out waiting for connect for '"+myUrl+"': "+e.getMessage(), e, currentTime + 60L * 60000L,
            currentTime + 720L * 60000L,-1,false);
        }
        catch (InterruptedIOException e)
        {
          throw new LCFException("Interrupted",LCFException.INTERRUPTED);
        }
        catch (org.apache.commons.httpclient.CircularRedirectException e)
        {
          throwable = e;
          statusCode = FETCH_CIRCULAR_REDIRECT;
          if (recordEverything)
            dataSession.setResponseCode(statusCode);
          return STATUS_PAGEERROR;
        }
        catch (org.apache.commons.httpclient.NoHttpResponseException e)
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
        catch (IOException e)
        {
          // Treat this as a bad url.  We don't know what happened, but it isn't something we are going to naively
          // retry on.
          throwable = e;
          statusCode = FETCH_IO_ERROR;
          if (recordEverything)
            dataSession.setResponseCode(statusCode);
          return STATUS_PAGEERROR;
        }

      }
      catch (InterruptedException e)
      {
        // Drop the current connection on the floor, so it cannot be reused.
        fetchMethod = null;
        throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
      }
      catch (URIException e)
      {
        throwable = new LCFException("Illegal URI: '"+myUrl+"'",e);
        statusCode = FETCH_BAD_URI;
        if (recordEverything)
          dataSession.setResponseCode(statusCode);
        return STATUS_PAGEERROR;
      }
      catch (IllegalArgumentException e)
      {
        throwable = new LCFException("Illegal URI: '"+myUrl+"'",e);
        statusCode = FETCH_BAD_URI;
        if (recordEverything)
          dataSession.setResponseCode(statusCode);
        return STATUS_PAGEERROR;
      }
      catch (IllegalStateException e)
      {
        throwable = new LCFException("Illegal state while fetching URI: '"+myUrl+"'",e);
        statusCode = FETCH_SEQUENCE_ERROR;
        if (recordEverything)
          dataSession.setResponseCode(statusCode);
        return STATUS_PAGEERROR;
      }
      catch (ServiceInterruption e)
      {
        throw e;
      }
      catch (LCFException e)
      {
        throw e;
      }
      catch (Throwable e)
      {
        Logging.connectors.debug("RSS: Caught an unexpected exception: "+e.getMessage(),e);
        throwable = e;
        statusCode = FETCH_UNKNOWN_ERROR;
        if (recordEverything)
          dataSession.setResponseCode(statusCode);
        return STATUS_PAGEERROR;
      }
    }

    /** Get the http response code.
    *@return the response code.  This is either an HTTP response code, or one of the codes above.
    */
    public int getResponseCode()
      throws LCFException, ServiceInterruption
    {
      return statusCode;
    }

    /** Get the response input stream.  It is the responsibility of the caller
    * to close this stream when done.
    */
    public InputStream getResponseBodyStream()
      throws LCFException, ServiceInterruption
    {
      if (fetchMethod == null)
        throw new LCFException("Attempt to get a response when there is no method");
      try
      {
        if (recordEverything)
          dataSession.endHeader();
        InputStream bodyStream = fetchMethod.getResponseBodyAsStream();
        if (bodyStream == null)
          throw new LCFException("Failed to set up body response stream");
        return new ThrottledInputstream(this,server,bodyStream,minimumMillisecondsPerBytePerServer,dataSession);
      }
      catch (IOException e)
      {
        throw new LCFException("IO exception setting up response stream",e);
      }
      catch (IllegalStateException e)
      {
        throw new LCFException("State error getting response body",e);
      }
    }

    /** Get a specified response header, if it exists.
    *@param headerName is the name of the header.
    *@return the header value, or null if it doesn't exist.
    */
    public String getResponseHeader(String headerName)
      throws LCFException, ServiceInterruption
    {
      Header h = fetchMethod.getResponseHeader(headerName);
      if (h == null)
        return null;
      if (recordEverything)
        dataSession.addHeader(headerName,h.getValue());
      return h.getValue();
    }

    /** Done with the fetch.  Call this when the fetch has been completed.  A log entry will be generated
    * describing what was done.
    */
    public void doneFetch(IVersionActivity activities)
      throws LCFException
    {
      if (fetchType != null)
      {
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
        statusCode = -1;
        fetchType = null;
      }
    }

    /** Close the connection.  Call this to end this server connection.
    */
    public void close()
      throws LCFException
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

    protected DataSession dataSession;

    /** Constructor.
    */
    public ThrottledInputstream(ThrottledConnection connection, Server server, InputStream is, double minimumMillisecondsPerBytePerServer, DataSession dataSession)
    {
      this.throttledConnection = connection;
      this.server = server;
      this.inputStream = is;
      this.minimumMillisecondsPerBytePerServer = minimumMillisecondsPerBytePerServer;
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
        server.beginRead(len,minimumMillisecondsPerBytePerServer);
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
      throws LCFException
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
        throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
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
        LCF.sleep(waitAmount);
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
        LCF.sleep(waitTime);
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

}
