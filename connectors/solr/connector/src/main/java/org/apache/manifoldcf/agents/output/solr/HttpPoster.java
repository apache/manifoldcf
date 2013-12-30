/* $Id: HttpPoster.java 991295 2010-08-31 19:12:14Z kwright $ */

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
package org.apache.manifoldcf.agents.output.solr;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.common.Base64;
import org.apache.manifoldcf.core.common.XMLDoc;
import org.apache.manifoldcf.core.common.DateParser;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.agents.system.*;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.*;
import javax.net.ssl.*;
import java.util.regex.*;

import org.apache.log4j.*;

import java.util.concurrent.TimeUnit;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.apache.solr.common.util.ContentStreamBase;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.common.SolrException;
import org.apache.solr.client.solrj.impl.HttpClientUtil;


/**
* Posts an input stream to SOLR
*
* @author James Sablatura, modified by Karl Wright
*/
public class HttpPoster
{
  public static final String _rcsid = "@(#)$Id: HttpPoster.java 991295 2010-08-31 19:12:14Z kwright $";

  /** Ingestion buffer size property. */
  public static String ingestBufferSizeProperty = "org.apache.manifoldcf.ingest.buffersize";
  public static String ingestCredentialsRealm = "org.apache.manifoldcf.ingest.credentialrealm";
  public static String ingestResponseRetryCount = "org.apache.manifoldcf.ingest.responseretrycount";
  public static String ingestResponseRetryInterval = "org.apache.manifoldcf.ingest.retryinterval";
  public static String ingestRescheduleInterval = "org.apache.manifoldcf.ingest.rescheduleinterval";
  public static String ingestURIProperty = "org.apache.manifoldcf.ingest.uri";
  public static String ingestUserProperty = "org.apache.manifoldcf.ingest.user";
  public static String ingestPasswordProperty = "org.apache.manifoldcf.ingest.password";
  public static String ingestMaxConnectionsProperty = "org.apache.manifoldcf.ingest.maxconnections";

  // Solrj connection-associated objects
  protected ClientConnectionManager connectionManager = null;
  protected SolrServer solrServer = null;
  
  // Action URI pieces
  private String postUpdateAction;
  private String postRemoveAction;
  private String postStatusAction;
  
  // Attribute names
  private String allowAttributeName;
  private String denyAttributeName;
  private String idAttributeName;
  private String modifiedDateAttributeName;
  private String createdDateAttributeName;
  private String indexedDateAttributeName;
  private String fileNameAttributeName;
  private String mimeTypeAttributeName;
  
  // Document max length
  private Long maxDocumentLength;

  // Commit-within flag
  private String commitWithin;

  // Constants we need
  private static final String LITERAL = "literal.";
  private static final String NOTHING = "__NOTHING__";
  private static final String ID_METADATA = "lcf_metadata_id";
  private static final String COMMITWITHIN_METADATA = "commitWithin";
  
  /** How long to wait before retrying a failed ingestion */
  private static final long interruptionRetryTime = 60000L;

  /** Initialize the SolrCloud http poster.
  */
  public HttpPoster(String zookeeperHosts, String collection,
    int zkClientTimeout, int zkConnectTimeout,
    String updatePath, String removePath, String statusPath,
    String allowAttributeName, String denyAttributeName, String idAttributeName,
    String modifiedDateAttributeName, String createdDateAttributeName, String indexedDateAttributeName,
    String fileNameAttributeName, String mimeTypeAttributeName,
    Long maxDocumentLength,
    String commitWithin)
    throws ManifoldCFException
  {
    // These are the paths to the handlers in Solr that deal with the actions we need to do
    this.postUpdateAction = updatePath;
    this.postRemoveAction = removePath;
    this.postStatusAction = statusPath;
    
    this.commitWithin = commitWithin;
    
    this.allowAttributeName = allowAttributeName;
    this.denyAttributeName = denyAttributeName;
    this.idAttributeName = idAttributeName;
    this.modifiedDateAttributeName = modifiedDateAttributeName;
    this.createdDateAttributeName = createdDateAttributeName;
    this.indexedDateAttributeName = indexedDateAttributeName;
    this.fileNameAttributeName = fileNameAttributeName;
    this.mimeTypeAttributeName = mimeTypeAttributeName;
    
    this.maxDocumentLength = maxDocumentLength;
    
    try
    {
      CloudSolrServer cloudSolrServer = new CloudSolrServer(zookeeperHosts/*, new ModifiedLBHttpSolrServer(HttpClientUtil.createClient(null))*/);
      cloudSolrServer.setZkClientTimeout(zkClientTimeout);
      cloudSolrServer.setZkConnectTimeout(zkConnectTimeout);
      cloudSolrServer.setDefaultCollection(collection);
      // Set the solrj instance we want to use
      solrServer = cloudSolrServer;
    }
    catch (MalformedURLException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }

  /** Initialize the standard http poster.
  */
  public HttpPoster(String protocol, String server, int port, String webapp, String core,
    int connectionTimeout, int socketTimeout,
    String updatePath, String removePath, String statusPath,
    String realm, String userID, String password,
    String allowAttributeName, String denyAttributeName, String idAttributeName,
    String modifiedDateAttributeName, String createdDateAttributeName, String indexedDateAttributeName,
    String fileNameAttributeName, String mimeTypeAttributeName,
    IKeystoreManager keystoreManager, Long maxDocumentLength,
    String commitWithin)
    throws ManifoldCFException
  {
    // These are the paths to the handlers in Solr that deal with the actions we need to do
    this.postUpdateAction = updatePath;
    this.postRemoveAction = removePath;
    this.postStatusAction = statusPath;
    
    this.commitWithin = commitWithin;
    
    this.allowAttributeName = allowAttributeName;
    this.denyAttributeName = denyAttributeName;
    this.idAttributeName = idAttributeName;
    this.modifiedDateAttributeName = modifiedDateAttributeName;
    this.createdDateAttributeName = createdDateAttributeName;
    this.indexedDateAttributeName = indexedDateAttributeName;
    this.fileNameAttributeName = fileNameAttributeName;
    this.mimeTypeAttributeName = mimeTypeAttributeName;
    
    this.maxDocumentLength = maxDocumentLength;

    String location = "";
    if (webapp != null)
      location = "/" + webapp;
    if (core != null)
    {
      if (webapp == null)
        throw new ManifoldCFException("Webapp must be specified if core is specified.");
      location += "/" + core;
    }

    // Initialize standard solr-j.
    // First, we need an HttpClient where basic auth is properly set up.
    PoolingClientConnectionManager localConnectionManager = new PoolingClientConnectionManager();
    localConnectionManager.setMaxTotal(1);
    SSLSocketFactory myFactory;
    if (keystoreManager != null)
    {
      myFactory = new SSLSocketFactory(keystoreManager.getSecureSocketFactory(),
        new AllowAllHostnameVerifier());
    }
    else
    {
      // Use the "trust everything" one
      myFactory = new SSLSocketFactory(KeystoreManagerFactory.getTrustingSecureSocketFactory(),
        new AllowAllHostnameVerifier());
    }
    Scheme myHttpsProtocol = new Scheme("https", 443, myFactory);
    localConnectionManager.getSchemeRegistry().register(myHttpsProtocol);
    connectionManager = localConnectionManager;
          
    BasicHttpParams params = new BasicHttpParams();
    // This one is essential to prevent us from reading from the content stream before necessary during auth, but
    // is incompatible with some proxies.
    params.setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE,true);
    params.setIntParameter(CoreProtocolPNames.WAIT_FOR_CONTINUE,socketTimeout);
    params.setBooleanParameter(CoreConnectionPNames.TCP_NODELAY,true);
    params.setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK,true);
    params.setBooleanParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS,true);
    params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT,socketTimeout);
    params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,connectionTimeout);
    params.setBooleanParameter(ClientPNames.HANDLE_REDIRECTS,true);
    DefaultHttpClient localClient = new DefaultHttpClient(connectionManager,params);

    // No retries
    localClient.setHttpRequestRetryHandler(new HttpRequestRetryHandler()
      {
        @Override
        public boolean retryRequest(
          IOException exception,
          int executionCount,
          HttpContext context)
        {
          return false;
        }
     
      });
    
    if (userID != null && userID.length() > 0 && password != null)
    {
      Credentials credentials = new UsernamePasswordCredentials(userID, password);
      if (realm != null)
        localClient.getCredentialsProvider().setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, realm), credentials);
      else
        localClient.getCredentialsProvider().setCredentials(AuthScope.ANY, credentials);
    }

    String httpSolrServerUrl = protocol + "://" + server + ":" + port + location;
    HttpSolrServer httpSolrServer = new HttpSolrServer(httpSolrServerUrl, localClient, new XMLResponseParser());
    httpSolrServer.setUseMultiPartPost(true);
    // Set the solrj instance we want to use
    solrServer = httpSolrServer;
  }

  /** Shut down the poster.
  */
  public void shutdown()
  {
    if (solrServer != null)
      solrServer.shutdown();
    solrServer = null;
    if (connectionManager != null)
      connectionManager.shutdown();
    connectionManager = null;
  }
  
  /** Cause a commit to happen.
  */
  public void commitPost()
    throws ManifoldCFException, ServiceInterruption
  {
    if (Logging.ingest.isDebugEnabled())
      Logging.ingest.debug("commitPost()");

    // Open a socket to ingest, and to the response stream to get the post result
    try
    {
      CommitThread t = new CommitThread();
      try
      {
        t.start();
        t.join();

        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof SolrServerException)
            throw (SolrServerException)thr;
          if (thr instanceof IOException)
            throw (IOException)thr;
          if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        return;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),ManifoldCFException.INTERRUPTED);
      }
    }
    catch (SolrServerException e)
    {
      handleSolrServerException(e, "commit");
      return;
    }
    catch (SolrException e)
    {
      handleSolrException(e, "commit");
      return;
    }
    catch (IOException ioe)
    {
      handleIOException(ioe, "commit");
      return;
    }
  }
  
  /** Handle a SolrServerException.
  * These exceptions seem to be catch-all exceptions having to do either with misconfiguration or
  * with underlying IO exceptions.
  * If this method doesn't throw an exception, it means that the exception should be interpreted
  * as meaning that the document or action is illegal and should not be repeated.
  */
  protected static void handleSolrServerException(SolrServerException e, String context)
    throws ManifoldCFException, ServiceInterruption
  {
    Throwable childException = e.getCause();
    if (childException instanceof IOException)
    {
      handleIOException((IOException)childException, context);
      return;
    }
    throw new ManifoldCFException("Unhandled SolrServerException: "+e.getMessage());
  }

  /** Handle a SolrException.
  * These exceptions are mainly Http errors having to do with actual responses from Solr.
  * If this method doesn't throw an exception, it means that the exception should be interpreted
  * as meaning that the document or action is illegal and should not be repeated.
  */
  protected static void handleSolrException(SolrException e, String context)
    throws ManifoldCFException, ServiceInterruption
  {
    int code = e.code();
    if (code == 0)
    {
      try
      {
        // Solrj doesn't always set the code properly.  If it doesn't, we have to parse it out of the exception string.  Ugh.
        Pattern p = Pattern.compile("non ok status:([0-9]*),");
        Matcher m = p.matcher(e.getMessage());
        if (m.find())
          code = Integer.parseInt(m.group(1));
      }
      catch (PatternSyntaxException e2)
      {
        throw new ManifoldCFException("Unexpected error: "+e2.getMessage());
      }
      catch (NumberFormatException e2)
      {
        throw new ManifoldCFException("Unexpected error: "+e2.getMessage());
      }
    }
      
    // Use the exception text to determine the proper result.
    if (code == 500 && e.getMessage().indexOf("org.apache.tika.exception.TikaException") != -1)
      // Can't process the document, so don't keep trying.
      return;

    // If the code is in the 400 range, the document will never be accepted, so indicate that.
    if (code >= 400 && code < 500)
      return;
    
    // The only other kind of return code we know how to handle is 50x.
    // For these, we should retry for a while.
    if (code == 500)
    {
      long currentTime = System.currentTimeMillis();
      
      // Log the error
      String message = "Solr exception during "+context+" ("+e.code()+"): "+e.getMessage();
      Logging.ingest.warn(message,e);
      throw new ServiceInterruption(message,
        e,
        currentTime + interruptionRetryTime,
        currentTime + 2L * 60L * 60000L,
        -1,
        true);
    }
    
    // Unknown code: end the job.
    throw new ManifoldCFException("Unhandled Solr exception during "+context+" ("+e.code()+"): "+e.getMessage());
  }
  
  /** Handle an IOException.
  * I'm not actually sure where these exceptions come from in SolrJ, but we handle them
  * as real I/O errors, meaning they should be retried.
  */
  protected static void handleIOException(IOException e, String context)
    throws ManifoldCFException, ServiceInterruption
  {
    if ((e instanceof InterruptedIOException) && (!(e instanceof java.net.SocketTimeoutException)))
      throw new ManifoldCFException(e.getMessage(), ManifoldCFException.INTERRUPTED);

    long currentTime = System.currentTimeMillis();
    
    if (e.getClass().getName().equals("java.net.SocketException"))
    {
      // In the past we would have treated this as a straight document rejection, and
      // treated it in the same manner as a 400.  The reasoning is that the server can
      // perfectly legally send out a 400 and drop the connection immediately thereafter,
      // this a race condition.
      // However, Solr 4.0 (or the Jetty version that the example runs on) seems
      // to have a bug where it drops the connection when two simultaneous documents come in
      // at the same time.  This is the final version of Solr 4.0 so we need to deal with
      // this.
      if (e.getMessage().toLowerCase(Locale.ROOT).indexOf("broken pipe") != -1 ||
        e.getMessage().toLowerCase(Locale.ROOT).indexOf("connection reset") != -1 ||
        e.getMessage().toLowerCase(Locale.ROOT).indexOf("target server failed to respond") != -1)
      {
        // Treat it as a service interruption, but with a limited number of retries.
        // In that way we won't burden the user with a huge retry interval; it should
        // give up fairly quickly, and yet NOT give up if the error was merely transient
        String message = "Server dropped connection during "+context+": "+e.getMessage();
        Logging.ingest.warn(message,e);
        throw new ServiceInterruption(message,
          e,
          currentTime + interruptionRetryTime,
          -1L,
          3,
          false);
      }
      
      // Other socket exceptions are service interruptions - but if we keep getting them, it means 
      // that a socket timeout is probably set too low to accept this particular document.  So
      // we retry for a while, then skip the document.
      String message2 = "Socket timeout exception during "+context+": "+e.getMessage();
      Logging.ingest.warn(message2,e);
      throw new ServiceInterruption(message2,
        e,
        currentTime + interruptionRetryTime,
        currentTime + 20L * 60000L,
        -1,
        false);
    }
    
    // Otherwise, no idea what the trouble is, so presume that retries might fix it.
    String message3 = "IO exception during "+context+": "+e.getMessage();
    Logging.ingest.warn(message3,e);
    throw new ServiceInterruption(message3,
      e,
      currentTime + interruptionRetryTime,
      currentTime + 2L * 60L * 60000L,
      -1,
      true);
  }
  
  /**
  * Post the input stream to ingest
  * @param documentURI is the document's uri.
  * @param document is the document structure to ingest.
  * @param arguments are the configuration arguments to pass in the post.  Key is argument name, value is a list of the argument values.
  * @param authorityNameString is the name of the governing authority for this document's acls, or null if none.
  * @param activities is the activities object, so we can report what's happening.
  * @return true if the ingestion was successful, or false if the ingestion is illegal.
  * @throws ManifoldCFException, ServiceInterruption
  */
  public boolean indexPost(String documentURI,
    RepositoryDocument document, Map arguments, Map<String, List<String>> sourceTargets,
    String authorityNameString, IOutputAddActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    if (Logging.ingest.isDebugEnabled())
      Logging.ingest.debug("indexPost(): '" + documentURI + "'");

    // The SOLR connector cannot deal with folder-level security at this time.  If they are seen, reject the document.
    if (document.countDirectoryACLs() != 0)
      return false;
    
    // If the document is too long, reject it.
    if (maxDocumentLength != null && document.getBinaryLength() > maxDocumentLength.longValue())
      return false;
    
    // Convert the incoming acls to qualified forms
    String[] shareAcls = convertACL(document.getShareACL(),authorityNameString,activities);
    String[] shareDenyAcls = convertACL(document.getShareDenyACL(),authorityNameString,activities);
    String[] acls = convertACL(document.getACL(),authorityNameString,activities);
    String[] denyAcls = convertACL(document.getDenyACL(),authorityNameString,activities);
    
    try
    {
      IngestThread t = new IngestThread(documentURI,document,arguments,sourceTargets,shareAcls,shareDenyAcls,acls,denyAcls,commitWithin);
      try
      {
        t.start();
        t.join();

        // Log the activity, if any, regardless of any exception
        if (t.getActivityCode() != null)
          activities.recordActivity(t.getActivityStart(),SolrConnector.INGEST_ACTIVITY,t.getActivityBytes(),documentURI,t.getActivityCode(),t.getActivityDetails());

        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof SolrServerException)
            throw (SolrServerException)thr;
          if (thr instanceof IOException)
            throw (IOException)thr;
          if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        return t.getRval();
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),ManifoldCFException.INTERRUPTED);
      }
    }
    catch (SolrServerException e)
    {
      handleSolrServerException(e, "indexing");
      return false;
    }
    catch (SolrException e)
    {
      handleSolrException(e, "indexing");
      return false;
    }
    catch (IOException ioe)
    {
      handleIOException(ioe, "indexing");
      return false;
    }

  }

  /** Post a check request.
  */
  public void checkPost()
    throws ManifoldCFException, ServiceInterruption
  {
    if (Logging.ingest.isDebugEnabled())
      Logging.ingest.debug("checkPost()");

    // Open a socket to ingest, and to the response stream to get the post result
    try
    {
      StatusThread t = new StatusThread();
      try
      {
        t.start();
        t.join();

        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof SolrServerException)
            throw (SolrServerException)thr;
          if (thr instanceof IOException)
            throw (IOException)thr;
          if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        return;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),ManifoldCFException.INTERRUPTED);
      }
    }
    catch (SolrServerException e)
    {
      handleSolrServerException(e, "check");
      return;
    }
    catch (SolrException e)
    {
      handleSolrException(e, "check");
      return;
    }
    catch (IOException ioe)
    {
      handleIOException(ioe, "check");
      return;
    }

  }

  /** Post a delete request.
  *@param documentURI is the document's URI.
  */
  public void deletePost(String documentURI, IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    if (Logging.ingest.isDebugEnabled())
      Logging.ingest.debug("deletePost(): '" + documentURI + "'");

    try
    {
      DeleteThread t = new DeleteThread(documentURI);
      try
      {
        t.start();
        t.join();

        // Log the activity, if any, regardless of any exception
        if (t.getActivityCode() != null)
          activities.recordActivity(t.getActivityStart(),SolrConnector.REMOVE_ACTIVITY,null,documentURI,t.getActivityCode(),t.getActivityDetails());

        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof SolrServerException)
            throw (SolrServerException)thr;
          if (thr instanceof IOException)
            throw (IOException)thr;
          if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        return;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),ManifoldCFException.INTERRUPTED);
      }
    }
    catch (SolrServerException e)
    {
      handleSolrServerException(e, "delete");
      return;
    }
    catch (SolrException e)
    {
      handleSolrException(e, "delete");
      return;
    }
    catch (IOException ioe)
    {
      handleIOException(ioe, "delete");
      return;
    }

  }

  /** Convert an unqualified ACL to qualified form.
  * @param acl is the initial, unqualified ACL.
  * @param authorityNameString is the name of the governing authority for this document's acls, or null if none.
  * @param activities is the activities object, so we can report what's happening.
  * @return the modified ACL.
  */
  protected static String[] convertACL(String[] acl, String authorityNameString, IOutputAddActivity activities)
    throws ManifoldCFException
  {
    if (acl != null)
    {
      String[] rval = new String[acl.length];
      int i = 0;
      while (i < rval.length)
      {
        rval[i] = activities.qualifyAccessToken(authorityNameString,acl[i]);
        i++;
      }
      return rval;
    }
    return new String[0];
  }

  /** Preprocess field name.
  * SolrJ has a bug where it does not URL-escape field names.  This causes carnage for
  * ManifoldCF, because it results in IllegalArgumentExceptions getting thrown deep in SolrJ.
  * See CONNECTORS-630.
  * In order to get around this, we need to URL-encode argument names, at least until the underlying
  * SolrJ issue is fixed.
  */
  protected static String preEncode(String fieldName)
  {
    try
    {
      return java.net.URLEncoder.encode(fieldName, "utf-8");
    }
    catch (IOException e)
    {
      throw new RuntimeException("Could not find utf-8 encoding!");
    }
  }
  
  /** Write a field */
  protected static void writeField(ModifiableSolrParams out, String fieldName, String[] fieldValues)
  {
    out.add(preEncode(fieldName), fieldValues);
  }
  
  /** Write a field */
  protected static void writeField(ModifiableSolrParams out, String fieldName, List<String> fieldValues)
  {
    String[] values = new String[fieldValues.size()];
    int i = 0;
    for (String fieldValue : fieldValues) {
      values[i++] = fieldValue;
    }
    writeField(out, fieldName, values);
  }
  
  /** Write a field */
  protected static void writeField(ModifiableSolrParams out, String fieldName, String fieldValue)
  {
    out.add(preEncode(fieldName), fieldValue);
  }

  /** Output an acl level */
  protected void writeACLs(ModifiableSolrParams out, String aclType, String[] acl, String[] denyAcl)
  {
    String metadataACLName = LITERAL + allowAttributeName + aclType;
    for (int i = 0; i < acl.length; i++)
    {
      writeField(out,metadataACLName,acl[i]);
    }
    String metadataDenyACLName = LITERAL + denyAttributeName + aclType;
    for (int i = 0; i < denyAcl.length; i++)
    {
      writeField(out,metadataDenyACLName,denyAcl[i]);
    }
  }
  
  /** Killable thread that does ingestions.
  * Java 1.5 stopped permitting thread interruptions to abort socket waits.  As a result, it is impossible to get threads to shutdown cleanly that are doing
  * such waits.  So, the places where this happens are segregated in their own threads so that they can be just abandoned.
  *
  * This thread does a single document ingestion.
  */
  protected class IngestThread extends java.lang.Thread
  {
    protected String documentURI;
    protected RepositoryDocument document;
    protected Map<String,List<String>> arguments;
    protected Map<String,List<String>> sourceTargets;
    protected String[] shareAcls;
    protected String[] shareDenyAcls;
    protected String[] acls;
    protected String[] denyAcls;
    protected String commitWithin;
    
    protected Long activityStart = null;
    protected Long activityBytes = null;
    protected String activityCode = null;
    protected String activityDetails = null;
    protected Throwable exception = null;
    protected boolean readFromDocumentStreamYet = false;
    protected boolean rval = false;

    public IngestThread(String documentURI, RepositoryDocument document,
      Map<String,List<String>> arguments, Map<String, List<String>> sourceTargets,
      String[] shareAcls, String[] shareDenyAcls, String[] acls, String[] denyAcls, String commitWithin)
    {
      super();
      setDaemon(true);
      this.documentURI = documentURI;
      this.document = document;
      this.arguments = arguments;
      this.shareAcls = shareAcls;
      this.shareDenyAcls = shareDenyAcls;
      this.acls = acls;
      this.denyAcls = denyAcls;
      this.sourceTargets = sourceTargets;
      this.commitWithin = commitWithin;
    }

    public void run()
    {
      long length = document.getBinaryLength();
      InputStream is = document.getBinaryStream();
      String contentType = document.getMimeType();
      String contentName = document.getFileName();

      try
      {
        // Do the operation!
        long fullStartTime = System.currentTimeMillis();

        // Open a socket to ingest, and to the response stream to get the post result
        try
        {
          ContentStreamUpdateRequest contentStreamUpdateRequest = new ContentStreamUpdateRequest(postUpdateAction);
          
          ModifiableSolrParams out = new ModifiableSolrParams();
          
          // Write the id field
          writeField(out,LITERAL+idAttributeName,documentURI);
          // Write the rest of the attributes
          if (modifiedDateAttributeName != null)
          {
            Date date = document.getModifiedDate();
            if (date != null)
              // Write value
              writeField(out,LITERAL+modifiedDateAttributeName,DateParser.formatISO8601Date(date));
          }
          if (createdDateAttributeName != null)
          {
            Date date = document.getCreatedDate();
            if (date != null)
              // Write value
              writeField(out,LITERAL+createdDateAttributeName,DateParser.formatISO8601Date(date));
          }
          if (indexedDateAttributeName != null)
          {
            Date date = document.getIndexingDate();
            if (date != null)
              // Write value
              writeField(out,LITERAL+indexedDateAttributeName,DateParser.formatISO8601Date(date));
          }
          if (fileNameAttributeName != null)
          {
            String fileName = document.getFileName();
            if (fileName != null)
              writeField(out,LITERAL+fileNameAttributeName,fileName);
          }
          if (mimeTypeAttributeName != null)
          {
            String mimeType = document.getMimeType();
            if (mimeType != null)
              writeField(out,LITERAL+mimeTypeAttributeName,mimeType);
          }
          
          // Write the access token information
          writeACLs(out,"share",shareAcls,shareDenyAcls);
          writeACLs(out,"document",acls,denyAcls);

          // Write the arguments
          for (String name : arguments.keySet())
          {
            List<String> values = arguments.get(name);
            writeField(out,name,values);
          }

          // Write the metadata, each in a field by itself
          Iterator<String> iter = document.getFields();
          while (iter.hasNext())
          {
            String fieldName = iter.next();
            List<String> mapping = sourceTargets.get(fieldName);
            if(mapping != null) {
              for(String newFieldName : mapping) {
                if(newFieldName != null && !newFieldName.isEmpty()) {
                  if (newFieldName.toLowerCase(Locale.ROOT).equals(idAttributeName.toLowerCase(Locale.ROOT))) {
                    newFieldName = ID_METADATA;
                  }
                  String[] values = document.getFieldAsStrings(fieldName);
                  writeField(out,LITERAL+newFieldName,values);
                }
              }
            } else {
              String newFieldName = fieldName;
              if (!newFieldName.isEmpty()) {
                if (newFieldName.toLowerCase(Locale.ROOT).equals(idAttributeName.toLowerCase(Locale.ROOT))) {
                  newFieldName = ID_METADATA;
                }
                String[] values = document.getFieldAsStrings(fieldName);
                writeField(out,LITERAL+newFieldName,values);
              }
            }
          }
             
          // These are unnecessary now in the case of non-solrcloud setups, because we overrode the SolrJ posting method to use multipart.
          //writeField(out,LITERAL+"stream_size",String.valueOf(length));
          //writeField(out,LITERAL+"stream_name",document.getFileName());
          
          // General hint for Tika
          if (document.getFileName() != null)
            writeField(out,"resource.name",document.getFileName());
          
          // Write the commitWithin parameter
          if (commitWithin != null)
            writeField(out,COMMITWITHIN_METADATA,commitWithin);

          contentStreamUpdateRequest.setParams(out);
          
          contentStreamUpdateRequest.addContentStream(new RepositoryDocumentStream(is,length,contentType,contentName));

          // Fire off the request.
          // Note: I need to know whether the document has been permanently rejected or not, but we currently have
          // no means to determine that.  Analysis of SolrServerExceptions that have been thrown is likely needed.
          try
          {
            readFromDocumentStreamYet = true;
            UpdateResponse response = contentStreamUpdateRequest.process(solrServer);
            
            // Successful completion
            activityStart = new Long(fullStartTime);
            activityBytes = new Long(length);
            activityCode = "OK";
            activityDetails = null;

            rval = true;
            return;
          }
          catch (SolrServerException e)
          {
            // Log what happened to us
            activityStart = new Long(fullStartTime);
            activityBytes = new Long(length);
            activityDetails = e.getMessage() +
              ((e.getCause() != null)?": "+e.getCause().getMessage():"");
            
            // Broken pipe exceptions we log specially because they usually mean
            // Solr has rejected the document, and the user will want to know that.
            if (e.getCause() != null && e.getCause().getClass().getName().equals("java.net.SocketException") &&
              (activityDetails.toLowerCase(Locale.ROOT).indexOf("broken pipe") != -1 ||
                activityDetails.toLowerCase(Locale.ROOT).indexOf("connection reset") != -1 ||
                activityDetails.toLowerCase(Locale.ROOT).indexOf("target server failed to respond") != -1))
              activityCode = "SOLR REJECT";
            else
              activityCode = "FAILED";

            // Rethrow; will interpret at a higher level
            throw e;
          }
          catch (SolrException e)
          {
            // Log what happened to us
            activityStart = new Long(fullStartTime);
            activityBytes = new Long(length);
            activityCode = Integer.toString(e.code());
            activityDetails = e.getMessage() +
              ((e.getCause() != null)?": "+e.getCause().getMessage():"");
            
            // Rethrow; we'll interpret at the next level
            throw e;
          }
        }
        catch (IOException ioe)
        {
          if ((ioe instanceof InterruptedIOException) && (!(ioe instanceof java.net.SocketTimeoutException)))
            return;
          
          activityStart = new Long(fullStartTime);
          activityCode = "IO ERROR";
          activityDetails = ioe.getMessage();

          // Log the error
          Logging.ingest.warn("Error indexing into Solr: "+ioe.getMessage(),ioe);

          throw ioe;
        }
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

    public Long getActivityStart()
    {
      return activityStart;
    }

    public Long getActivityBytes()
    {
      return activityBytes;
    }

    public String getActivityCode()
    {
      return activityCode;
    }

    public String getActivityDetails()
    {
      return activityDetails;
    }

    public boolean getReadFromDocumentStreamYet()
    {
      return readFromDocumentStreamYet;
    }

    public boolean getRval()
    {
      return rval;
    }
  }

  /** Killable thread that does deletions.
  * Java 1.5 stopped permitting thread interruptions to abort socket waits.  As a result, it is impossible to get threads to shutdown cleanly that are doing
  * such waits.  So, the places where this happens are segregated in their own threads so that they can be just abandoned.
  *
  * This thread does a single document deletion.
  */
  protected class DeleteThread extends java.lang.Thread
  {
    protected String documentURI;

    protected Long activityStart = null;
    protected String activityCode = null;
    protected String activityDetails = null;
    protected Throwable exception = null;

    public DeleteThread(String documentURI)
    {
      super();
      setDaemon(true);
      this.documentURI = documentURI;
    }

    public void run()
    {
      try
      {
        // Do the operation!
        long fullStartTime = System.currentTimeMillis();
        // Open a socket to ingest, and to the response stream to get the post result
        try
        {
          UpdateResponse response = new UpdateRequest(postRemoveAction).deleteById(documentURI).process(solrServer);
            
          // Success
          activityStart = new Long(fullStartTime);
          activityCode = "OK";
          activityDetails = null;
          return;
        }
        catch (InterruptedIOException ioe)
        {
          return;
        }
        catch (SolrServerException e)
        {
          activityStart = new Long(fullStartTime);
          activityCode = "FAILED";
          activityDetails = e.getMessage() +
            ((e.getCause() != null)?": "+e.getCause().getMessage():"");

          throw e;
        }
        catch (SolrException e)
        {
          activityStart = new Long(fullStartTime);
          activityCode = "FAILED";
          activityDetails = e.getMessage() +
            ((e.getCause() != null)?": "+e.getCause().getMessage():"");

          throw e;
        }
        catch (IOException ioe)
        {
          // Log the error
          Logging.ingest.warn("Error deleting document: "+ioe.getMessage(),ioe);

          activityStart = new Long(fullStartTime);
          activityCode = "IO ERROR";
          activityDetails = ioe.getMessage();

          throw ioe;
        }
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

    public Long getActivityStart()
    {
      return activityStart;
    }

    public String getActivityCode()
    {
      return activityCode;
    }

    public String getActivityDetails()
    {
      return activityDetails;
    }
  }
  
  /** Killable thread that does a commit.
  * Java 1.5 stopped permitting thread interruptions to abort socket waits.  As a result, it is impossible to get threads to shutdown cleanly that are doing
  * such waits.  So, the places where this happens are segregated in their own threads so that they can be just abandoned.
  *
  * This thread does a commit.
  */
  protected class CommitThread extends java.lang.Thread
  {
    protected Throwable exception = null;

    public CommitThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        try
        {
          // Do the operation!
          UpdateRequest updateRequest = new UpdateRequest(postUpdateAction + "?commit=true");
          UpdateResponse response = updateRequest.process(solrServer);
          //UpdateResponse response = solrServer.commit();
        }
        catch (InterruptedIOException ioe)
        {
          return;
        }
        catch (IOException ioe)
        {
          // Log the error
          Logging.ingest.warn("Error committing: "+ioe.getMessage(),ioe);
          throw ioe;
        }
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
  }


  /** Killable thread that does a status check.
  * Java 1.5 stopped permitting thread interruptions to abort socket waits.  As a result, it is impossible to get threads to shutdown cleanly that are doing
  * such waits.  So, the places where this happens are segregated in their own threads so that they can be just abandoned.
  *
  * This thread does a status check.
  */
  protected class StatusThread extends java.lang.Thread
  {
    protected Throwable exception = null;

    public StatusThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        // Do the operation!
        try
        {
          SolrPingResponse response = new SolrPing(postStatusAction).process(solrServer);
        }
        catch (InterruptedIOException ioe)
        {
          // Exit the thread.
          return;
        }
        catch (IOException ioe)
        {
          // Log the error
          Logging.ingest.warn("Error checking status: "+ioe.getMessage(),ioe);
          throw ioe;
        }
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
  }

  /** Class for importing documents into Solr via SolrJ
  */
  protected static class RepositoryDocumentStream extends ContentStreamBase
  {
    protected final InputStream is;
    protected final long length;
    protected final String contentType;
    protected final String contentName;
    
    public RepositoryDocumentStream(InputStream is, long length, String contentType, String contentName)
    {
      this.is = is;
      this.length = length;
      this.contentType = contentType;
      this.contentName = contentName;
    }
    
    @Override
    public Long getSize()
    {
      return new Long(length);
    }
    
    @Override
    public InputStream getStream() throws IOException
    {
      return is;
    }
    
    @Override
    public Reader getReader() throws IOException
    {
      return null;
    }

    @Override
    public String getContentType()
    {
      return contentType;
    }

    @Override
    public String getName()
    {
      return contentName;
    }
  }

  /** Special version of ping class where we can control the URL
  */
  protected static class SolrPing extends SolrRequest
  {
    private ModifiableSolrParams params;
    
    public SolrPing()
    {
      super( METHOD.GET, "/admin/ping" );
      params = new ModifiableSolrParams();
    }

    public SolrPing(String url)
    {
      super( METHOD.GET, url );
      params = new ModifiableSolrParams();
    }

    @Override
    public Collection<ContentStream> getContentStreams() {
      return null;
    }

    @Override
    public ModifiableSolrParams getParams() {
      return params;
    }

    @Override
    public SolrPingResponse process( SolrServer server ) throws SolrServerException, IOException 
    {
      long startTime = System.currentTimeMillis();
      SolrPingResponse res = new SolrPingResponse();
      res.setResponse( server.request( this ) );
      res.setElapsedTime( System.currentTimeMillis()-startTime );
      return res;
    }
  }

}

